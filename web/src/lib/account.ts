/**
 * The user's Reader account on the feed loader (worker/src/account.ts, docs/account.md): one
 * synced document per account, read and saved with a version so two devices never overwrite each
 * other, and a live connection that says when another device saved. Signing in with Instapaper
 * gives this browser its key (instapaper.ts).
 */

import { PROXY_URL } from "./config";
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
