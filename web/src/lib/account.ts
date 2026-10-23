/**
 * The user's Reader account on the feed loader (worker/src/account.ts, docs/account.md): one
 * synced document per account, read and saved with a version so two devices never overwrite each
 * other, and a live connection that says when another device saved. Signing in with Instapaper
 * gives this browser its key (instapaper.ts).
 */

import { PROXY_URL } from "./config";
import type { SharedBy } from "./items";
import { inboxOf, type InboxMessage, type SharedArticle } from "./sharing";
import type { ItemChange, ItemRow, ItemsPage, ItemsWritten, RowData } from "./syncRows";

export type SyncProblem = "offline" | "signedOut" | "server" | "unreadableFile" | "notSetUp";

export class SyncError extends Error {
  readonly problem: SyncProblem;

  constructor(problem: SyncProblem) {
    super(problem);
    this.problem = problem;
  }
}

export type ReadAnswer = { kind: "unchanged"; version: number } | { kind: "document"; version: number; text: string | null };
export type WriteAnswer = { kind: "saved"; version: number } | { kind: "conflict"; version: number; text: string | null };

type Fetch = (input: string, init?: RequestInit) => Promise<Response>;
const browserFetch: Fetch = (input, init) => fetch(input, init);

export class AccountClient {
  private readonly key: string;
  private readonly doFetch: Fetch;
  private readonly base: string;

  constructor(key: string, doFetch: Fetch = browserFetch, base = PROXY_URL) {
    this.key = key;
    this.doFetch = doFetch;
    this.base = base;
  }

  /** The document and its version, or "unchanged" when knownVersion is still the latest. */
  async read(knownVersion: number | null): Promise<ReadAnswer> {
    const response = await this.call("/account/sync", { headers: knownVersion === null ? {} : { "If-None-Match": `"${knownVersion}"` } });
    if (response.status === 304) return { kind: "unchanged", version: knownVersion ?? 0 };
    return { kind: "document", ...(await documentOf(response)) };
  }

  /** Saves text (a sync document) unless another device saved after baseVersion. */
  async write(baseVersion: number, text: string): Promise<WriteAnswer> {
    const response = await this.call("/account/sync", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: `{"baseVersion":${baseVersion},"data":${text}}`,
    }, [409]);
    if (response.status === 409) return { kind: "conflict", ...(await documentOf(response)) };
    const body = (await response.json().catch(() => null)) as { version?: unknown } | null;
    if (typeof body?.version !== "number") throw new SyncError("server");
    return { kind: "saved", version: body.version };
  }

  /** The item rows changed after items version `since` (#106, syncRows.ts). */
  async readItems(since: number): Promise<ItemsPage> {
    const body = (await (await this.call(`/account/items?since=${since}`, {})).json().catch(() => null)) as { version?: unknown; rows?: unknown; more?: unknown } | null;
    if (!body || typeof body.version !== "number") throw new SyncError("server");
    return { version: body.version, rows: rowsOf(body.rows), more: body.more === true };
  }

  /** Saves item rows; those another device changed first come back as conflicts. */
  async writeItems(rows: ItemChange[]): Promise<ItemsWritten> {
    const response = await this.call("/account/items", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ rows }) });
    const body = (await response.json().catch(() => null)) as { version?: unknown; saved?: unknown; conflicts?: unknown } | null;
    if (!body || typeof body.version !== "number") throw new SyncError("server");
    const saved: Record<string, number> = {};
    for (const s of Array.isArray(body.saved) ? body.saved : []) {
      if (typeof s?.key === "string" && typeof s.version === "number") saved[s.key] = s.version;
    }
    return { version: body.version, saved, conflicts: rowsOf(body.conflicts) };
  }

  /** Whether email has a Content account (#167), to say so before sharing with it. */
  async hasAccount(email: string): Promise<boolean> {
    const response = await this.call("/account/share/check", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email: email.trim() }) });
    return ((await response.json().catch(() => null)) as { exists?: unknown } | null)?.exists === true;
  }

  /** Shares articles with the Content user at `to` (#167), as `from`; false when the address has no account. */
  async share(to: string, from: SharedBy, items: SharedArticle[], tag: string | null): Promise<boolean> {
    const response = await this.call("/account/share", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ to: to.trim(), from, items, tag }) }, [404]);
    return response.status !== 404;
  }

  /** What other Content users shared with this account and no device has taken in yet. */
  async inbox(): Promise<InboxMessage[]> {
    return inboxOf(await (await this.call("/account/inbox", {})).json().catch(() => null));
  }

  /** Takes messages out of the inbox once they are in the library. */
  async inboxDone(ids: string[]): Promise<void> {
    await this.call("/account/inbox/done", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ ids }) });
  }

  /** The text stored for item `key` (one per saved article, shared by every device), or null when none is. */
  async text(key: string): Promise<string | null> {
    const response = await this.call(`/account/texts?key=${encodeURIComponent(key)}`, {}, [404]);
    if (response.status === 404) return null;
    return htmlOf(await response.json().catch(() => null));
  }

  /** Stores `html` as item `key`'s text unless one is there already; gives back the one that is stored. */
  async storeText(key: string, html: string): Promise<string | null> {
    const response = await this.call("/account/texts", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ key, html }) });
    return htmlOf(await response.json().catch(() => null));
  }

  /** Forgets this browser's key on the server. */
  async signOut(): Promise<void> {
    await this.call("/account/signout", { method: "POST" });
  }

  /** Deletes everything the account keeps, on every device's behalf. */
  async deleteAll(): Promise<void> {
    await this.call("/account", { method: "DELETE" });
  }

  private async call(path: string, init: RequestInit, alsoOk: number[] = []): Promise<Response> {
    let response: Response;
    try {
      response = await this.doFetch(this.base + path, {
        ...init,
        headers: { ...(init.headers as Record<string, string>), Authorization: `Bearer ${this.key}` },
        cache: "no-store",
      });
    } catch {
      throw new SyncError("offline");
    }
    if (response.ok || response.status === 304 || alsoOk.includes(response.status)) return response;
    throw new SyncError(response.status === 401 ? "signedOut" : response.status === 503 ? "notSetUp" : "server");
  }
}

function htmlOf(body: unknown): string | null {
  const html = (body as { html?: unknown } | null)?.html;
  return typeof html === "string" ? html : null;
}

export function rowsOf(value: unknown): ItemRow[] {
  if (!Array.isArray(value)) return [];
  return value.flatMap((r) => {
    if (typeof r?.key !== "string" || typeof r.version !== "number") return [];
    const data = typeof r.data === "object" && r.data !== null && !Array.isArray(r.data) ? (r.data as RowData) : null;
    return [{ key: r.key, version: r.version, data }];
  });
}

async function documentOf(response: Response): Promise<{ version: number; text: string | null }> {
  const body = (await response.json().catch(() => null)) as { version?: unknown; data?: unknown } | null;
  if (!body || typeof body.version !== "number") throw new SyncError("server");
  return { version: body.version, text: body.data === null || body.data === undefined ? null : JSON.stringify(body.data) };
}

/** How long to wait before connecting again after the live connection dropped (doubling, capped). */
export function retryDelay(failures: number): number {
  return Math.min(60_000, 2_000 * 2 ** Math.max(0, failures - 1));
}

/** The live connection's address: the WebSocket can't send headers, so the key goes along. */
export function liveUrl(key: string, base = PROXY_URL): string {
  return `${base.replace(/^http/, "ws")}/account/live?key=${encodeURIComponent(key)}`;
}

/** What a live message says: the document's version and the items version (null from servers before #106). */
export interface LiveVersions {
  document: number;
  items: number | null;
}

/** The versions in a live message, or null. */
export function versionOf(message: unknown): LiveVersions | null {
  if (typeof message !== "string") return null;
  try {
    const value = JSON.parse(message) as { version?: unknown; items?: unknown };
    return typeof value.version === "number" ? { document: value.version, items: typeof value.items === "number" ? value.items : null } : null;
  } catch {
    return null;
  }
}

const PING_MS = 45_000;

/**
 * The live connection while the page is visible: calls onVersion with the versions after every save
 * by another device (and the current ones right after connecting). Reconnects by itself until stopped.
 */
export class LiveUpdates {
  private socket: WebSocket | null = null;
  private retry: ReturnType<typeof setTimeout> | null = null;
  private ping: ReturnType<typeof setInterval> | null = null;
  private failures = 0;
  private running = false;
  private readonly key: string;
  private readonly onVersion: (versions: LiveVersions) => void;

  constructor(key: string, onVersion: (versions: LiveVersions) => void) {
    this.key = key;
    this.onVersion = onVersion;
  }

  get connected(): boolean {
    return this.socket?.readyState === WebSocket.OPEN;
  }

  start() {
    if (this.running) return;
    this.running = true;
    this.connect();
  }

  stop() {
    this.running = false;
    if (this.retry) clearTimeout(this.retry);
    if (this.ping) clearInterval(this.ping);
    this.retry = null;
    this.ping = null;
    const socket = this.socket;
    this.socket = null;
    socket?.close(1000);
  }

  private connect() {
    let socket: WebSocket;
    try {
      socket = new WebSocket(liveUrl(this.key));
    } catch {
      this.scheduleRetry();
      return;
    }
    this.socket = socket;
    socket.onopen = () => {
      this.failures = 0;
      this.ping = setInterval(() => socket.readyState === WebSocket.OPEN && socket.send("ping"), PING_MS);
    };
    socket.onmessage = (event) => {
      const version = versionOf(event.data);
      if (version !== null) this.onVersion(version);
    };
    socket.onclose = (event) => {
      if (this.ping) clearInterval(this.ping);
      this.ping = null;
      if (this.socket !== socket) return;
      this.socket = null;
      // 4401: the account was deleted; the next sync says so.
      if (event.code !== 4401) this.scheduleRetry();
    };
  }

  private scheduleRetry() {
    if (!this.running) return;
    this.failures++;
    this.retry = setTimeout(() => {
      this.retry = null;
      if (this.running) this.connect();
    }, retryDelay(this.failures));
  }
}

/** Signed in to a Content account (#166): this browser's device key, and the email address when there is one. */
export interface AccountSession {
  key: string;
  email: string | null;
}

/** Where an emailed sign-in link opens the web app. */
export const SIGNIN_PATH = "/signin";

/** What an emailed link gave (#166): a device key for a sign-in (null when it added the address), and the address. */
export interface EmailLinkResult {
  key: string | null;
  email: string;
}

/** Why an emailed link couldn't be used. */
export type EmailLinkProblem = "expired" | "taken" | "failed";

/** The token of an emailed link's address part ("#token=..."), or null. */
export function emailLinkToken(hash: string): string | null {
  const params = new URLSearchParams(hash.replace(/^#/, ""));
  return params.get("token") || null;
}

/**
 * Emails a sign-in link to email (#166, worker/src/email.ts); with a device key, a link that adds
 * the address to that account. Throws a SyncError when it couldn't be sent.
 */
export async function sendEmailLink(email: string, key: string | null, doFetch: Fetch = browserFetch, base = PROXY_URL): Promise<void> {
  let response: Response;
  try {
    response = await doFetch(base + (key ? "/account/email/add" : "/account/email/start"), {
      method: "POST",
      headers: { "Content-Type": "application/json", ...(key ? { Authorization: `Bearer ${key}` } : {}) },
      body: JSON.stringify({ email: email.trim() }),
    });
  } catch {
    throw new SyncError("offline");
  }
  if (!response.ok) throw new SyncError(response.status === 401 ? "signedOut" : response.status === 503 ? "notSetUp" : "server");
}

/** Uses an emailed link's token: a sign-in or an added address, or why it didn't work. */
export async function finishEmailLink(token: string, doFetch: Fetch = browserFetch, base = PROXY_URL): Promise<EmailLinkResult | EmailLinkProblem> {
  try {
    const response = await doFetch(base + "/account/email/finish", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ token }) });
    if (response.status === 410) return "expired";
    if (response.status === 409) return "taken";
    if (!response.ok) return "failed";
    const body = (await response.json().catch(() => null)) as { key?: unknown; email?: unknown } | null;
    if (typeof body?.email !== "string" || !body.email) return "failed";
    return { key: typeof body.key === "string" && body.key ? body.key : null, email: body.email };
  } catch {
    return "failed";
  }
}

/** The account's picture (#166): Gravatar's for the address (SHA-256 of it, trimmed and in lower case); 404 when it has none. */
export async function gravatarUrl(email: string, size = 96): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(email.trim().toLowerCase()));
  const hash = [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
  return `https://gravatar.com/avatar/${hash}?s=${size}&d=404`;
}

