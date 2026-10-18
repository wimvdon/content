/**
 * Reader accounts: one sync document per account, kept encrypted, shared by the user's devices
 * (docs/sync.md, docs/account.md). For now the only way in is signing in with Instapaper: when
 * instapaper.ts swaps the sign-in code it learns the Instapaper user ID and asks for a device key
 * here ([newDeviceKey]).
 *
 *  - GET    /account/sync    the document and its version (304 with If-None-Match: "<version>")
 *  - PUT    /account/sync    {"baseVersion": n, "data": {...}}: saved as n + 1, or 409 with the
 *                            current version and document when another device saved first
 *  - GET    /account/live    a WebSocket that says {"version": n} whenever the document changes
 *  - POST   /account/signout forgets this device's key
 *  - DELETE /account         deletes the document and every device key
 *
 * Every call sends the device key as "Authorization: Bearer <key>" (the WebSocket, which a
 * browser can't give headers, as ?key=). A key is "<account id>.<secret>". The account id picks
 * the account's Durable Object; only a SHA-256 hash of the secret is stored.
 *
 * The document is compressed and encrypted (AES-256-GCM) with a key derived for this account from
 * the ACCOUNT_KEY secret. Nothing is logged.
 */

import type { Env } from "./index";

/** The largest document a device may save (its JSON text). */
export const MAX_DOCUMENT_BYTES = 8 * 1024 * 1024;
/** Durable Object storage takes values up to 2 MiB; the compressed, encrypted document must fit. */
const MAX_STORED_BYTES = 2 * 1024 * 1024 - 1024;
/** A device's "last used" date is updated at most this often (it costs a write). */
const TOUCH_MS = 24 * 60 * 60 * 1000;
/** Devices per account; signing in on one more forgets the one unused the longest. */
export const MAX_DEVICES = 50;

const KEY_PATTERN = /^([0-9a-f]{32})\.([A-Za-z0-9_-]{43})$/;
const FORMAT = "rss-reader-sync";

// ---------------------------------------------------------------------------------------------
// The Worker side: checks the request and passes it to the account's Durable Object.

export async function handleAccount(request: Request, env: Env, url: URL): Promise<Response> {
  const origin = request.headers.get("Origin");
  // The web app sends its address; the Android app sends none. Other websites are refused.
  if (origin && !allowedOrigins(env).includes(origin)) return plain(403, "Not allowed");
  const cors = origin ? corsHeaders(origin) : {};
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });

  const route = routeOf(request.method, url.pathname);
  if (!route) return plain(KNOWN_PATHS.includes(url.pathname) ? 405 : 404, "Not available", cors);
  if (!env.ACCOUNTS || !env.ACCOUNT_KEY) return plain(503, "Accounts aren't set up on this server", cors);

  if (env.LIMITER) {
    const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
    const { success } = await env.LIMITER.limit({ key: ip });
    if (!success) return plain(429, "Too many requests", cors);
  }

  const auth = route === "live" ? (url.searchParams.get("key") ?? "") : (request.headers.get("Authorization") ?? "").replace(/^Bearer /, "");
  const key = KEY_PATTERN.exec(auth);
  if (!key) return plain(401, "Sign in again", cors);
  const [, accountId, secret] = key;
  if (route === "live" && request.headers.get("Upgrade")?.toLowerCase() !== "websocket") return plain(426, "WebSocket only", cors);

  const headers = new Headers({ "X-Account": accountId, "X-Secret": secret });
  const ifNoneMatch = request.headers.get("If-None-Match");
  if (ifNoneMatch) headers.set("If-None-Match", ifNoneMatch);
  if (route === "live") headers.set("Upgrade", "websocket");
  let body: string | undefined;
  if (route === "write") {
    body = await request.text();
    if (body.length > MAX_DOCUMENT_BYTES + 1024) return plain(413, "Too large", cors);
  }
  const stub = env.ACCOUNTS.get(env.ACCOUNTS.idFromName(accountId));
  const answer = await stub.fetch(`https://account/${route}`, { method: request.method, headers, body });
  if (answer.webSocket) return answer;
  const out = new Headers(answer.headers);
  for (const [k, v] of Object.entries(cors)) out.set(k, v);
  return new Response(answer.body, { status: answer.status, headers: out });
}

const KNOWN_PATHS = ["/account", "/account/sync", "/account/live", "/account/signout"];

type Route = "read" | "write" | "live" | "signout" | "delete";

function routeOf(method: string, path: string): Route | null {
  if (path === "/account/sync") return method === "GET" ? "read" : method === "PUT" ? "write" : null;
  if (path === "/account/live") return method === "GET" ? "live" : null;
  if (path === "/account/signout") return method === "POST" ? "signout" : null;
  if (path === "/account") return method === "DELETE" ? "delete" : null;
  return null;
}

/**
 * A new device key for the account that belongs to this sign-in, or null when accounts aren't set
 * up. Only call it after the provider confirmed who the user is (instapaper.ts, after the swap).
 */
export async function newDeviceKey(env: Env, provider: "instapaper", userId: number): Promise<string | null> {
  if (!env.ACCOUNTS || !env.ACCOUNT_KEY) return null;
  const accountId = await accountIdOf(env.ACCOUNT_KEY, provider, userId);
  const stub = env.ACCOUNTS.get(env.ACCOUNTS.idFromName(accountId));
  const answer = await stub.fetch("https://account/device", { method: "POST", headers: { "X-Account": accountId } });
  if (!answer.ok) return null;
  const { secret } = (await answer.json()) as { secret: string };
  return `${accountId}.${secret}`;
}

/** The account of a sign-in: the same every time, and meaningless without ACCOUNT_KEY. */
export async function accountIdOf(masterKey: string, provider: string, userId: number): Promise<string> {
  const key = await crypto.subtle.importKey("raw", await derive(masterKey, "reader-account-id", 32), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const mac = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(`${provider}:${userId}`));
  return hex(new Uint8Array(mac)).slice(0, 32);
}

// ---------------------------------------------------------------------------------------------
// The Durable Object: one per account. Its storage holds the device keys and the document.

/** The part of Durable Object storage used here, so tests can give a Map. */
export interface Storage {
  get<T>(key: string): Promise<T | undefined>;
  put<T>(key: string, value: T): Promise<void>;
  deleteAll(): Promise<void>;
}

interface Device {
  created: number;
  used: number;
}

interface Stored {
  version: number;
  iv: Uint8Array;
  data: Uint8Array;
}

export type ReadResult = { kind: "unchanged"; version: number } | { kind: "document"; version: number; text: string | null };
export type WriteResult = { kind: "saved"; version: number } | { kind: "conflict"; version: number; text: string | null };

/** The account's data and rules, without anything Cloudflare-specific (tested on its own). */
export class AccountData {
  private queue: Promise<unknown> = Promise.resolve();

  constructor(
    private readonly storage: Storage,
    private readonly masterKey: string,
    private readonly accountId: string,
    private readonly clock: () => number = Date.now,
  ) {}

  /** One change at a time: encrypting takes a moment, and two saves must never both win. */
  private serial<T>(run: () => Promise<T>): Promise<T> {
    const next = this.queue.then(run, run);
    this.queue = next.catch(() => undefined);
    return next;
  }

  addDevice(): Promise<string> {
    return this.serial(async () => {
      const secret = base64url(crypto.getRandomValues(new Uint8Array(32)));
      const devices = (await this.storage.get<Record<string, Device>>("devices")) ?? {};
      const now = this.clock();
      devices[await sha256(secret)] = { created: now, used: now };
      const kept = Object.entries(devices)
        .sort((a, b) => b[1].used - a[1].used)
        .slice(0, MAX_DEVICES);
      await this.storage.put("devices", Object.fromEntries(kept));
      return secret;
    });
  }

  /** True when the secret is one of this account's devices. */
  async knows(secret: string): Promise<boolean> {
    const hash = await sha256(secret);
    const device = ((await this.storage.get<Record<string, Device>>("devices")) ?? {})[hash];
    if (!device) return false;
    if (this.clock() - device.used > TOUCH_MS) {
      await this.serial(async () => {
        const devices = (await this.storage.get<Record<string, Device>>("devices")) ?? {};
        if (devices[hash]) {
          devices[hash] = { ...devices[hash], used: this.clock() };
          await this.storage.put("devices", devices);
        }
      });
    }
    return true;
  }

  removeDevice(secret: string): Promise<void> {
    return this.serial(async () => {
      const hash = await sha256(secret);
      const devices = (await this.storage.get<Record<string, Device>>("devices")) ?? {};
      delete devices[hash];
      await this.storage.put("devices", devices);
    });
  }

  deleteAll(): Promise<void> {
    return this.serial(() => this.storage.deleteAll());
  }

  async version(): Promise<number> {
    return (await this.storage.get<Stored>("document"))?.version ?? 0;
  }

  /** The document (null before the first save), or "unchanged" when the device has this version. */
  async read(knownVersion: number | null): Promise<ReadResult> {
    const stored = await this.storage.get<Stored>("document");
    const version = stored?.version ?? 0;
    if (knownVersion === version) return { kind: "unchanged", version };
    return { kind: "document", version, text: stored ? await this.open(stored) : null };
  }

  /** Saves the text when nobody saved since baseVersion; otherwise returns what is there now. */
  write(baseVersion: number, text: string): Promise<WriteResult> {
    return this.serial(async () => {
      const stored = await this.storage.get<Stored>("document");
      const version = stored?.version ?? 0;
      if (baseVersion !== version) return { kind: "conflict", version, text: stored ? await this.open(stored) : null };
      const next = version + 1;
      const sealed = await this.seal(next, text);
      if (sealed.data.byteLength > MAX_STORED_BYTES) throw new TooLarge();
      await this.storage.put("document", sealed);
      return { kind: "saved", version: next };
    });
  }

  private async cipher(): Promise<CryptoKey> {
    const raw = await derive(this.masterKey, `reader-sync:${this.accountId}`, 32);
    return crypto.subtle.importKey("raw", raw, "AES-GCM", false, ["encrypt", "decrypt"]);
  }

  private async seal(version: number, text: string): Promise<Stored> {
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const packed = await gzip(new TextEncoder().encode(text));
    const data = await crypto.subtle.encrypt({ name: "AES-GCM", iv, additionalData: this.label(version) }, await this.cipher(), packed);
    return { version, iv, data: new Uint8Array(data) };
  }

  private async open(stored: Stored): Promise<string> {
    const packed = await crypto.subtle.decrypt(
      { name: "AES-GCM", iv: stored.iv, additionalData: this.label(stored.version) },
      await this.cipher(),
      stored.data,
    );
    return new TextDecoder().decode(await gunzip(new Uint8Array(packed)));
  }

  /** Ties the encrypted document to its account and version, so it can't be moved or rolled back. */
  private label(version: number): Uint8Array {
    return new TextEncoder().encode(`${this.accountId}:${version}`);
  }
}

export class TooLarge extends Error {}

/** What a device may save: a sync document (docs/sync.md) as JSON text, or null when it isn't one. */
export function documentText(body: string): { baseVersion: number; text: string } | null {
  let value: unknown;
  try {
    value = JSON.parse(body);
  } catch {
    return null;
  }
  if (!isObject(value) || !Number.isSafeInteger(value.baseVersion) || (value.baseVersion as number) < 0) return null;
  const data = value.data;
  if (!isObject(data) || data.format !== FORMAT || typeof data.version !== "number") return null;
  const text = JSON.stringify(data);
  return text.length > MAX_DOCUMENT_BYTES ? null : { baseVersion: value.baseVersion as number, text };
}

/** The version in If-None-Match ("3" or 3), or null. */
export function knownVersionOf(header: string | null): number | null {
  const match = header ? /^(?:W\/)?"?(\d{1,15})"?$/.exec(header.trim()) : null;
  return match ? Number(match[1]) : null;
}

/** The answers of the account's Durable Object, given its data. The WebSocket part is in Account. */
export async function answer(data: AccountData, route: string, request: Request): Promise<Response> {
  if (route === "device") return json({ secret: await data.addDevice() });
  const secret = request.headers.get("X-Secret") ?? "";
  if (!(await data.knows(secret))) return plain(401, "Sign in again");
  switch (route) {
    case "read": {
      const result = await data.read(knownVersionOf(request.headers.get("If-None-Match")));
      const etag = `"${result.version}"`;
      if (result.kind === "unchanged") return new Response(null, { status: 304, headers: { ETag: etag, "Cache-Control": "no-store" } });
      return documentResponse(200, result.version, result.text);
    }
    case "write": {
      const doc = documentText(await request.text());
      if (!doc) return plain(400, 'Send {"baseVersion": n, "data": <sync document>}');
      try {
        const result = await data.write(doc.baseVersion, doc.text);
        return result.kind === "saved" ? json({ version: result.version }) : documentResponse(409, result.version, result.text);
      } catch (e) {
        if (e instanceof TooLarge) return plain(413, "Too large");
        throw e;
      }
    }
    case "signout":
      await data.removeDevice(secret);
      return new Response(null, { status: 204 });
    case "delete":
      await data.deleteAll();
      return new Response(null, { status: 204 });
    default:
      return plain(404, "Not found");
  }
}

/** The Durable Object class (wrangler.toml), one per account. */
export class Account {
  private data: AccountData | null = null;

  constructor(
    private readonly state: DurableObjectState,
    private readonly env: Env,
  ) {}

  async fetch(request: Request): Promise<Response> {
    const accountId = request.headers.get("X-Account") ?? "";
    if (!/^[0-9a-f]{32}$/.test(accountId) || !this.env.ACCOUNT_KEY) return plain(503, "Accounts aren't set up on this server");
    this.data ??= new AccountData(this.state.storage, this.env.ACCOUNT_KEY, accountId);
    const route = new URL(request.url).pathname.slice(1);

    if (route === "live") {
      if (!(await this.data.knows(request.headers.get("X-Secret") ?? ""))) return plain(401, "Sign in again");
      const [client, server] = Object.values(new WebSocketPair());
      // Hibernation: an open, quiet connection costs nothing, and "ping" is answered without waking.
      this.state.acceptWebSocket(server);
      this.state.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
      server.send(JSON.stringify({ version: await this.data.version() }));
      return new Response(null, { status: 101, webSocket: client });
    }

    const response = await answer(this.data, route, request);
    if (route === "write" && response.status === 200) {
      const message = JSON.stringify(await response.clone().json());
      for (const ws of this.state.getWebSockets()) {
        try {
          ws.send(message);
        } catch {
          // Already closing.
        }
      }
    }
    if (route === "delete" && response.status === 204) {
      for (const ws of this.state.getWebSockets()) ws.close(4401, "Account deleted");
    }
    return response;
  }

  webSocketMessage(): void {
    // Devices only listen; "ping" is answered automatically.
  }

  webSocketClose(ws: WebSocket, code: number): void {
    try {
      ws.close(code === 1005 || code === 1006 ? 1000 : code, "Bye");
    } catch {
      // Already closed.
    }
  }
}

// ---------------------------------------------------------------------------------------------

/** HKDF-SHA-256 of the master key (any text of at least 32 characters) for one purpose. */
async function derive(masterKey: string, purpose: string, bytes: number): Promise<ArrayBuffer> {
  if (masterKey.length < 32) throw new Error("ACCOUNT_KEY must be at least 32 characters");
  const base = await crypto.subtle.importKey("raw", new TextEncoder().encode(masterKey), "HKDF", false, ["deriveBits"]);
  return crypto.subtle.deriveBits({ name: "HKDF", hash: "SHA-256", salt: new Uint8Array(0), info: new TextEncoder().encode(purpose) }, base, bytes * 8);
}

async function sha256(text: string): Promise<string> {
  return hex(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text))));
}

async function gzip(bytes: Uint8Array): Promise<Uint8Array> {
  return pipe(bytes, new CompressionStream("gzip"));
}

async function gunzip(bytes: Uint8Array): Promise<Uint8Array> {
  return pipe(bytes, new DecompressionStream("gzip"));
}

async function pipe(bytes: Uint8Array, stream: CompressionStream | DecompressionStream): Promise<Uint8Array> {
  const out = new Response(new Blob([bytes]).stream().pipeThrough(stream));
  return new Uint8Array(await out.arrayBuffer());
}

function hex(bytes: Uint8Array): string {
  return [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function base64url(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function isObject(v: unknown): v is Record<string, unknown> {
  return typeof v === "object" && v !== null && !Array.isArray(v);
}

function documentResponse(status: number, version: number, text: string | null): Response {
  return new Response(`{"version":${version},"data":${text ?? "null"}}`, {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", ETag: `"${version}"`, "Cache-Control": "no-store" },
  });
}

function allowedOrigins(env: Env): string[] {
  return (env.ALLOWED_ORIGINS ?? "")
    .split(",")
    .map((o) => o.trim())
    .filter(Boolean);
}

function corsHeaders(origin: string): Record<string, string> {
  return {
    "Access-Control-Allow-Origin": origin,
    "Access-Control-Allow-Methods": "GET, PUT, POST, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "Authorization, Content-Type, If-None-Match",
    "Access-Control-Expose-Headers": "ETag",
    "Access-Control-Max-Age": "86400",
    Vary: "Origin",
  };
}

function json(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}

function plain(status: number, text: string, headers: Record<string, string> = {}): Response {
  return new Response(text, { status, headers: { ...headers, "Content-Type": "text/plain; charset=utf-8" } });
}
