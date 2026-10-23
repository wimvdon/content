/**
 * Reader accounts: one sync document per account, kept encrypted, shared by the user's devices
 * (docs/sync.md, docs/account.md). Two ways in: signing in with Instapaper (when instapaper.ts
 * swaps the sign-in code it learns the Instapaper user ID and asks for a device key here,
 * [newDeviceKey]) and an emailed sign-in link (email.ts, #166).
 *
 *  - GET    /account/sync    the document and its version (304 with If-None-Match: "<version>")
 *  - PUT    /account/sync    {"baseVersion": n, "data": {...}}: saved as n + 1, or 409 with the
 *                            current version and document when another device saved first
 *  - GET    /account/items?since=n  the items changed after items version n (#106, see below)
 *  - POST   /account/items   {"rows": [{"key", "baseVersion", "data"}]}: each row saved when nobody
 *                            changed it since baseVersion, else returned as a conflict
 *  - GET    /account/texts?key=k  the stored text of item k (its article, as the reader shows it), or 404
 *  - POST   /account/texts   {"key", "html"}: stores the text when the item has none yet (201); one
 *                            already there wins and comes back (200), so every device shows the same
 *  - GET    /account/live    a WebSocket that says {"version": n, "items": m} whenever the document
 *                            or the items change
 *  - POST   /account/signout forgets this device's key
 *  - DELETE /account         deletes the document and every device key
 *
 * Every call sends the device key as "Authorization: Bearer <key>" (the WebSocket, which a
 * browser can't give headers, as ?key=). A key is "<account id>.<secret>". The account id picks
 * the account's Durable Object; only a SHA-256 hash of the secret is stored.
 *
 * The document is compressed and encrypted (AES-256-GCM) with a key derived for this account from
 * the ACCOUNT_KEY secret. Nothing is logged.
 *
 * Items (#106): the reading list, bookmarks, notes, highlights and read marks are synced one row
 * per item instead of in the document, so the account grows with the library. Each row is a key
 * chosen by the apps (docs/sync.md) and its JSON data, or null once deleted. Rows are stored
 * encrypted, under an id derived from the key (so the stored keys say nothing), with the items
 * version of the save that last changed them: "changes since a version" is one list in version order.
 */

import { emailClaim } from "./email";
import type { Env } from "./index";

/** The most item rows one save may send, and one read returns (more come on the next page). */
export const MAX_ROWS = 500;
/** The largest item row (its JSON data) and the longest key. */
export const MAX_ROW_BYTES = 64 * 1024;
export const MAX_KEY_LENGTH = 2048;
/** The largest item save (its body). */
export const MAX_ITEMS_BODY = 4 * 1024 * 1024;
/** The largest stored article text (its HTML), and its save's body. */
export const MAX_TEXT_BYTES = 1024 * 1024;
const MAX_TEXT_BODY = MAX_TEXT_BYTES + MAX_KEY_LENGTH + 1024;

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
  if (route === "write" || route === "writeItems" || route === "writeText") {
    body = await request.text();
    const limit = route === "write" ? MAX_DOCUMENT_BYTES + 1024 : route === "writeText" ? MAX_TEXT_BODY : MAX_ITEMS_BODY;
    if (body.length > limit) return plain(413, "Too large", cors);
  }
  const stub = env.ACCOUNTS.get(env.ACCOUNTS.idFromName(accountId));
  const search = route === "items" || route === "readText" ? url.search : "";
  const answer = await stub.fetch(`https://account/${route}${search}`, { method: request.method, headers, body });
  if (answer.webSocket) return answer;
  const out = new Headers(answer.headers);
  for (const [k, v] of Object.entries(cors)) out.set(k, v);
  return new Response(answer.body, { status: answer.status, headers: out });
}

const KNOWN_PATHS = ["/account", "/account/sync", "/account/items", "/account/texts", "/account/live", "/account/signout"];

type Route = "read" | "write" | "items" | "writeItems" | "readText" | "writeText" | "live" | "signout" | "delete";

function routeOf(method: string, path: string): Route | null {
  if (path === "/account/sync") return method === "GET" ? "read" : method === "PUT" ? "write" : null;
  if (path === "/account/items") return method === "GET" ? "items" : method === "POST" ? "writeItems" : null;
  if (path === "/account/texts") return method === "GET" ? "readText" : method === "POST" ? "writeText" : null;
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
  return addDeviceKey(env, await accountIdOf(env.ACCOUNT_KEY, provider, userId));
}

/** A new device key for the account [accountId] (an email sign-in, email.ts), or null when that didn't work. */
export async function addDeviceKey(env: Env, accountId: string): Promise<string | null> {
  if (!env.ACCOUNTS) return null;
  const stub = env.ACCOUNTS.get(env.ACCOUNTS.idFromName(accountId));
  const answer = await stub.fetch("https://account/device", { method: "POST", headers: { "X-Account": accountId } });
  if (!answer.ok) return null;
  const { secret } = (await answer.json()) as { secret: string };
  return `${accountId}.${secret}`;
}

/** The account of a sign-in: the same every time, and meaningless without ACCOUNT_KEY. */
export async function accountIdOf(masterKey: string, provider: string, userId: number | string): Promise<string> {
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
  delete(key: string): Promise<boolean>;
  /** Entries in key order, from start on, only those with the prefix, at most limit. */
  list<T>(options: { prefix: string; start?: string; limit?: number }): Promise<Map<string, T>>;
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

/** Something another Content user shared (#167): who, the articles, and the tag they were shared as (if any). */
export interface InboxMessage {
  id: string;
  at: number;
  from: { email: string; name: string };
  items: { url: string; title: string; imageUrl?: string | null; description?: string }[];
  tag?: string | null;
  /** A short message from the sender. */
  message?: string | null;
}

/** Messages kept per inbox; older ones go. */
export const MAX_INBOX = 200;

/** One item as the apps send and get it: its key, the items version that last changed it, its data (null: deleted). */
export interface ItemRow {
  key: string;
  version: number;
  data: unknown;
}

/** A row a device saves: what it was based on (0 for new) and the new data (null to delete). */
export interface ItemChange {
  key: string;
  baseVersion: number;
  data: unknown;
}

export interface ItemsPage {
  /** The items version this page brings the device to. */
  version: number;
  rows: ItemRow[];
  /** More rows after this page: ask again with since = version. */
  more: boolean;
}

export interface ItemsWritten {
  /** The items version after this save. */
  version: number;
  saved: { key: string; version: number }[];
  /** Rows another device changed since their baseVersion: what is there now, to merge and send again. */
  conflicts: ItemRow[];
}

interface SealedRow {
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

  async itemsVersion(): Promise<number> {
    return (await this.storage.get<number>("itemsVersion")) ?? 0;
  }

  /**
   * The rows changed after [since], in version order. A page ends between two versions (a save's
   * rows are never split), so the device can ask for the rest with since = page.version.
   */
  async readItems(since: number, limit = MAX_ROWS): Promise<ItemsPage> {
    const current = await this.itemsVersion();
    if (since >= current) return { version: current, rows: [], more: false };
    const found = await this.storage.list<SealedRow>({ prefix: "v:", start: versionKey(since + 1, ""), limit: limit + 1 });
    const entries = [...found.entries()];
    let more = false;
    if (entries.length > limit) {
      more = true;
      const lastVersion = versionOf(entries[limit][0]);
      // Cut before the version that doesn't fit, unless it is the only one (then it comes whole).
      const firstOfLast = entries.findIndex(([key]) => versionOf(key) === lastVersion);
      if (firstOfLast > 0) entries.splice(firstOfLast);
      else {
        const all = await this.storage.list<SealedRow>({ prefix: versionKey(lastVersion, "") });
        entries.splice(0, entries.length, ...all.entries());
      }
    }
    const rows: ItemRow[] = [];
    for (const [key, sealed] of entries) rows.push(await this.openRow(key, sealed));
    const version = more ? versionOf(entries[entries.length - 1][0]) : current;
    return { version, rows, more: more && version < current };
  }

  /** Saves each row whose stored version is still its baseVersion; the others come back as conflicts. */
  writeItems(changes: ItemChange[]): Promise<ItemsWritten> {
    return this.serial(async () => {
      const current = await this.itemsVersion();
      const next = current + 1;
      const saved: { key: string; version: number }[] = [];
      const conflicts: ItemRow[] = [];
      const accepted: { change: ItemChange; id: string; previous: number }[] = [];
      for (const change of changes) {
        const id = await this.rowId(change.key);
        const previous = (await this.storage.get<number>("r:" + id)) ?? 0;
        if (previous !== change.baseVersion) {
          const sealed = previous ? await this.storage.get<SealedRow>(versionKey(previous, id)) : undefined;
          conflicts.push(sealed ? await this.openRow(versionKey(previous, id), sealed) : { key: change.key, version: previous, data: null });
        } else {
          accepted.push({ change, id, previous });
        }
      }
      if (accepted.length === 0) return { version: current, saved, conflicts };
      for (const { change, id, previous } of accepted) {
        if (previous) await this.storage.delete(versionKey(previous, id));
        await this.storage.put(versionKey(next, id), await this.sealRow(next, id, change.key, change.data));
        await this.storage.put("r:" + id, next);
        saved.push({ key: change.key, version: next });
      }
      await this.storage.put("itemsVersion", next);
      return { version: next, saved, conflicts };
    });
  }

  /**
   * The stored text of an item (the article as the reader shows it), so every device shows the same
   * text and highlights find their passage in it. Null when none is stored.
   */
  async readText(key: string): Promise<string | null> {
    const id = await this.textId(key);
    const sealed = await this.storage.get<SealedRow>("t:" + id);
    return sealed ? this.openText(id, sealed) : null;
  }

  /**
   * Stores an item's text when it has none yet: the first device to extract the article decides,
   * and every other one gets that text back. Throws TooLarge when it doesn't fit.
   */
  writeText(key: string, html: string): Promise<{ stored: boolean; html: string }> {
    return this.serial(async () => {
      const id = await this.textId(key);
      const existing = await this.storage.get<SealedRow>("t:" + id);
      if (existing) return { stored: false, html: await this.openText(id, existing) };
      const iv = crypto.getRandomValues(new Uint8Array(12));
      const packed = await gzip(new TextEncoder().encode(html));
      const data = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv, additionalData: this.textLabel(id) }, await this.cipher(), packed));
      if (data.length > MAX_STORED_BYTES) throw new TooLarge();
      await this.storage.put("t:" + id, { iv, data } satisfies SealedRow);
      return { stored: true, html };
    });
  }

  private async textId(key: string): Promise<string> {
    return this.rowId(`text:${key}`);
  }

  private async openText(id: string, sealed: SealedRow): Promise<string> {
    const packed = await crypto.subtle.decrypt({ name: "AES-GCM", iv: sealed.iv, additionalData: this.textLabel(id) }, await this.cipher(), sealed.data);
    return new TextDecoder().decode(await gunzip(new Uint8Array(packed)));
  }

  /** Ties a stored text to its account and id, so it can't be moved to another item or account. */
  private textLabel(id: string): Uint8Array {
    return new TextEncoder().encode(`${this.accountId}:text:${id}`);
  }

  // The inbox (#167): what other Content users shared with this account, until a device takes it in.

  /** Adds a message shared with this account; the oldest go past MAX_INBOX. */
  addToInbox(message: InboxMessage): Promise<void> {
    return this.serial(async () => {
      const iv = crypto.getRandomValues(new Uint8Array(12));
      const plain = new TextEncoder().encode(JSON.stringify(message));
      const data = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv, additionalData: this.inboxLabel(message.id) }, await this.cipher(), plain));
      await this.storage.put(`i:${String(message.at).padStart(15, "0")}:${message.id}`, { iv, data } satisfies SealedRow);
      const all = [...(await this.storage.list<SealedRow>({ prefix: "i:" })).keys()];
      for (const key of all.slice(0, Math.max(0, all.length - MAX_INBOX))) await this.storage.delete(key);
    });
  }

  /** What is waiting in the inbox, oldest first. */
  async inbox(): Promise<InboxMessage[]> {
    const out: InboxMessage[] = [];
    for (const [key, sealed] of await this.storage.list<SealedRow>({ prefix: "i:" })) {
      const id = key.slice(key.lastIndexOf(":") + 1);
      try {
        const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: sealed.iv, additionalData: this.inboxLabel(id) }, await this.cipher(), sealed.data);
        out.push(JSON.parse(new TextDecoder().decode(plain)) as InboxMessage);
      } catch {
        // Not readable (moved, or damaged): left out.
      }
    }
    return out;
  }

  /** Takes messages out of the inbox once a device has taken them in. */
  inboxDone(ids: string[]): Promise<void> {
    return this.serial(async () => {
      const wanted = new Set(ids);
      for (const key of (await this.storage.list<SealedRow>({ prefix: "i:" })).keys()) {
        if (wanted.has(key.slice(key.lastIndexOf(":") + 1))) await this.storage.delete(key);
      }
    });
  }

  private inboxLabel(id: string): Uint8Array {
    return new TextEncoder().encode(`${this.accountId}:inbox:${id}`);
  }

  private idKey: Promise<CryptoKey> | null = null;

  /** The stored id of a row: says nothing about the key without ACCOUNT_KEY. */
  private async rowId(key: string): Promise<string> {
    this.idKey ??= derive(this.masterKey, `reader-item-id:${this.accountId}`, 32).then((raw) =>
      crypto.subtle.importKey("raw", raw, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]),
    );
    const mac = await crypto.subtle.sign("HMAC", await this.idKey, new TextEncoder().encode(key));
    return hex(new Uint8Array(mac)).slice(0, 40);
  }

  private async sealRow(version: number, id: string, key: string, data: unknown): Promise<SealedRow> {
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const plain = new TextEncoder().encode(JSON.stringify({ key, data: data ?? null }));
    const sealed = await crypto.subtle.encrypt({ name: "AES-GCM", iv, additionalData: this.rowLabel(version, id) }, await this.cipher(), plain);
    return { iv, data: new Uint8Array(sealed) };
  }

  private async openRow(storageKey: string, sealed: SealedRow): Promise<ItemRow> {
    const version = versionOf(storageKey);
    const id = storageKey.slice(storageKey.lastIndexOf(":") + 1);
    const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: sealed.iv, additionalData: this.rowLabel(version, id) }, await this.cipher(), sealed.data);
    const { key, data } = JSON.parse(new TextDecoder().decode(plain)) as { key: string; data: unknown };
    return { key, version, data };
  }

  /** Ties an encrypted row to its account, id and version, so it can't be moved or rolled back. */
  private rowLabel(version: number, id: string): Uint8Array {
    return new TextEncoder().encode(`${this.accountId}:item:${id}:${version}`);
  }

  private cipherKey: Promise<CryptoKey> | null = null;

  /** The account's encryption key, worked out once per Durable Object. */
  private cipher(): Promise<CryptoKey> {
    this.cipherKey ??= derive(this.masterKey, `reader-sync:${this.accountId}`, 32).then((raw) =>
      crypto.subtle.importKey("raw", raw, "AES-GCM", false, ["encrypt", "decrypt"]),
    );
    return this.cipherKey;
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

/** The storage key of a row: versions padded so they sort as numbers. */
function versionKey(version: number, id: string): string {
  return `v:${String(version).padStart(15, "0")}:${id}`;
}

function versionOf(storageKey: string): number {
  return Number(storageKey.slice(2, 17));
}

/** What a device may save to /account/items, or null when it isn't that. */
export function itemChanges(body: string): ItemChange[] | null {
  let value: unknown;
  try {
    value = JSON.parse(body);
  } catch {
    return null;
  }
  if (!isObject(value) || !Array.isArray(value.rows) || value.rows.length > MAX_ROWS) return null;
  const changes: ItemChange[] = [];
  const keys = new Set<string>();
  for (const row of value.rows) {
    if (!isObject(row) || typeof row.key !== "string" || row.key.length === 0 || row.key.length > MAX_KEY_LENGTH || keys.has(row.key)) return null;
    if (!Number.isSafeInteger(row.baseVersion) || (row.baseVersion as number) < 0) return null;
    const data = row.data ?? null;
    if (data !== null && (!isObject(data) || JSON.stringify(data).length > MAX_ROW_BYTES)) return null;
    keys.add(row.key);
    changes.push({ key: row.key, baseVersion: row.baseVersion as number, data });
  }
  return changes;
}

/** What a device may store at /account/texts: an item key and its article's HTML; "tooLarge", or null. */
export function textOf(body: string): { key: string; html: string } | "tooLarge" | null {
  let value: unknown;
  try {
    value = JSON.parse(body);
  } catch {
    return null;
  }
  if (!isObject(value) || typeof value.key !== "string" || typeof value.html !== "string") return null;
  if (value.key.length === 0 || value.key.length > MAX_KEY_LENGTH || value.html.length === 0) return null;
  if (value.html.length > MAX_TEXT_BYTES) return "tooLarge";
  return { key: value.key, html: value.html };
}

/** The since=n of /account/items (0 when missing), or null when it isn't a version. */
export function sinceOf(url: string): number | null {
  const since = new URL(url).searchParams.get("since") ?? "0";
  return /^\d{1,15}$/.test(since) ? Number(since) : null;
}

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
    case "items": {
      const since = sinceOf(request.url);
      if (since === null) return plain(400, "Give since=<items version>");
      return json(await data.readItems(since));
    }
    case "writeItems": {
      const changes = itemChanges(await request.text());
      if (!changes) return plain(400, `Send {"rows": [{"key", "baseVersion", "data"}]}, at most ${MAX_ROWS}`);
      return json(await data.writeItems(changes));
    }
    case "readText": {
      const key = new URL(request.url).searchParams.get("key") ?? "";
      if (!key || key.length > MAX_KEY_LENGTH) return plain(400, "Give key=<item key>");
      const html = await data.readText(key);
      return html === null ? plain(404, "No text stored") : json({ html });
    }
    case "writeText": {
      const text = textOf(await request.text());
      if (text === "tooLarge") return plain(413, "Too large");
      if (!text) return plain(400, 'Send {"key": <item key>, "html": <the article>}');
      try {
        const result = await data.writeText(text.key, text.html);
        return json({ html: result.html }, result.stored ? 201 : 200);
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

    // The inbox (share.ts, #167): added by the feed loader for a sender, read and emptied by this account's devices.
    if (route === "inboxAdd") {
      await this.data.addToInbox(JSON.parse(await request.text()) as InboxMessage);
      return new Response(null, { status: 204 });
    }
    if (route === "inbox" || route === "inboxDone") {
      if (!(await this.data.knows(request.headers.get("X-Secret") ?? ""))) return plain(401, "Sign in again");
      if (route === "inbox") return new Response(JSON.stringify({ messages: await this.data.inbox() }), { headers: { "Content-Type": "application/json" } });
      const body = JSON.parse(await request.text()) as { ids?: unknown };
      await this.data.inboxDone(Array.isArray(body.ids) ? body.ids.filter((x): x is string => typeof x === "string") : []);
      return new Response(null, { status: 204 });
    }
    // An address's directory (email.ts): which account it opens and which links were used.
    if (route === "emailClaim") return emailClaim(this.state.storage, await request.text(), Date.now());
    if (route === "emailAccount") {
      const account = await this.state.storage.get<string>("account");
      return account ? new Response(JSON.stringify({ account }), { headers: { "Content-Type": "application/json" } }) : plain(404, "No account");
    }
    if (route === "knows") return (await this.data.knows(request.headers.get("X-Secret") ?? "")) ? new Response(null, { status: 204 }) : plain(401, "Sign in again");

    if (route === "live") {
      if (!(await this.data.knows(request.headers.get("X-Secret") ?? ""))) return plain(401, "Sign in again");
      const [client, server] = Object.values(new WebSocketPair());
      // Hibernation: an open, quiet connection costs nothing, and "ping" is answered without waking.
      this.state.acceptWebSocket(server);
      this.state.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
      server.send(JSON.stringify({ version: await this.data.version(), items: await this.data.itemsVersion() }));
      return new Response(null, { status: 101, webSocket: client });
    }

    const response = await answer(this.data, route, request);
    const changed =
      (route === "write" && response.status === 200) ||
      (route === "writeItems" && response.status === 200 && ((await response.clone().json()) as ItemsWritten).saved.length > 0);
    if (changed) {
      const message = JSON.stringify({ version: await this.data.version(), items: await this.data.itemsVersion() });
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
export async function derive(masterKey: string, purpose: string, bytes: number): Promise<ArrayBuffer> {
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

export function hex(bytes: Uint8Array): string {
  return [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export function base64url(bytes: Uint8Array): string {
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

export function allowedOrigins(env: Env): string[] {
  return (env.ALLOWED_ORIGINS ?? "")
    .split(",")
    .map((o) => o.trim())
    .filter(Boolean);
}

export function corsHeaders(origin: string): Record<string, string> {
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

export function plain(status: number, text: string, headers: Record<string, string> = {}): Response {
  return new Response(text, { status, headers: { ...headers, "Content-Type": "text/plain; charset=utf-8" } });
}
