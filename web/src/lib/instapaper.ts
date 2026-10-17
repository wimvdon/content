/**
 * Instapaper (API v2) in the web app: sign-in and the calls. Instapaper's API doesn't allow calls from
 * a browser, so everything goes through the feed loader Worker (worker/src/instapaper.ts), which also
 * holds the client secret. The access token never expires; it lives in this browser only.
 */

import { INSTAPAPER_CLIENT_ID, INSTAPAPER_REDIRECT_URI, PROXY_URL } from "./config";

export const AUTHORIZE_URL = "https://www.instapaper.com/oauth2/authorize";
export const CALLBACK_PATH = "/instapaper/callback";

export interface Tag {
  id: number;
  name: string;
  slug: string;
  count: number;
}

export interface Bookmark {
  id: number;
  url: string | null;
  title: string | null;
  description: string | null;
  image: string | null;
  liked: boolean;
  archived: boolean;
  /** When it was saved, as a Unix timestamp (seconds). */
  time: number;
  folder_id: number | null;
  tags: Tag[];
}

export interface Folder {
  id: number;
  title: string;
  slug: string;
  position: number;
  public: boolean;
  count: number;
}

export interface Session {
  token: string;
  userId: number | null;
  username: string;
}

/** Which list of bookmarks: Unread ("home"), the archive, the liked ones, one folder or one tag. */
export type View = { kind: "home" | "archive" | "liked" } | { kind: "folder"; id: number } | { kind: "tag"; name: string };

export type Problem = "auth" | "notApproved" | "rateLimit" | "offline" | "server" | "invalid";

export class InstapaperError extends Error {
  readonly problem: Problem;

  constructor(problem: Problem) {
    super(problem);
    this.problem = problem;
  }
}

function problemOf(status: number): Problem {
  if (status === 401) return "auth";
  if (status === 403) return "notApproved";
  if (status === 429) return "rateLimit";
  if (status >= 400 && status < 500) return "invalid";
  return "server";
}

// Sign-in

export function newState(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

/** Instapaper's sign-in page. `state` comes back unchanged and proves the answer is for this sign-in. */
export function authorizeUrl(state: string, clientId = INSTAPAPER_CLIENT_ID, redirectUri = INSTAPAPER_REDIRECT_URI): string {
  const url = new URL(AUTHORIZE_URL);
  url.search = new URLSearchParams({ client_id: clientId, redirect_uri: redirectUri, response_type: "code", state }).toString();
  return url.toString();
}

export type Callback = { kind: "code"; code: string; state: string } | { kind: "denied" } | { kind: "invalid" };

/** What Instapaper put in the address when it sent the user back. */
export function parseCallback(search: string): Callback {
  const params = new URLSearchParams(search);
  if (params.get("error")) return { kind: "denied" };
  const code = params.get("code");
  const state = params.get("state");
  return code && state ? { kind: "code", code, state } : { kind: "invalid" };
}

type Fetch = (input: string, init?: RequestInit) => Promise<Response>;
const browserFetch: Fetch = (input, init) => fetch(input, init);

/** Swaps the one-time code for an access token, through the Worker (it adds the client secret). */
export async function exchangeCode(code: string, doFetch: Fetch = browserFetch, proxy = PROXY_URL): Promise<Session> {
  let response: Response;
  try {
    response = await doFetch(`${proxy}/instapaper/token`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ code }),
    });
  } catch {
    throw new InstapaperError("offline");
  }
  if (!response.ok) throw new InstapaperError(response.status === 400 ? "invalid" : "server");
  const body = (await response.json().catch(() => null)) as { access_token?: unknown; user?: { id?: unknown; username?: unknown } } | null;
  if (!body || typeof body.access_token !== "string" || !body.access_token) throw new InstapaperError("server");
  return {
    token: body.access_token,
    userId: typeof body.user?.id === "number" ? body.user.id : null,
    username: typeof body.user?.username === "string" ? body.user.username : "",
  };
}

// The calls

export interface Page {
  bookmarks: Bookmark[];
  total: number;
}

export class InstapaperApi {
  private readonly token: string;
  private readonly doFetch: Fetch;
  private readonly base: string;

  constructor(token: string, doFetch: Fetch = browserFetch, proxy = PROXY_URL) {
    this.token = token;
    this.doFetch = doFetch;
    this.base = `${proxy}/instapaper/api`;
  }

  private async call<T>(method: string, path: string, body?: unknown): Promise<T> {
    let response: Response;
    try {
      response = await this.doFetch(this.base + path, {
        method,
        headers: { Authorization: `Bearer ${this.token}`, ...(body === undefined ? {} : { "Content-Type": "application/json" }) },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch {
      throw new InstapaperError("offline");
    }
    if (!response.ok) throw new InstapaperError(problemOf(response.status));
    const text = await response.text();
    if (!text) return undefined as T;
    try {
      return JSON.parse(text) as T;
    } catch {
      throw new InstapaperError("server");
    }
  }

  list(view: View, offset = 0, limit = 25): Promise<Page> {
    const query = new URLSearchParams({ limit: String(limit), offset: String(offset) });
    if (view.kind === "folder") {
      query.set("section", "folder");
      query.set("folder_id", String(view.id));
    } else if (view.kind === "tag") {
      query.set("section", "tag");
      query.set("tag", view.name);
    } else {
      query.set("section", view.kind);
    }
    return this.call("GET", `/bookmarks?${query}`);
  }

  /** Saving an address the account already has doesn't duplicate it; Instapaper moves it back to the top. */
  save(url: string, title?: string): Promise<Bookmark> {
    return this.call("POST", "/bookmarks", { url, ...(title ? { title } : {}) });
  }

  /** To the archive, back to Unread ("home"), or into a folder. */
  move(id: number, to: "archive" | "home" | number): Promise<Bookmark> {
    return this.call("POST", `/bookmarks/${id}/move`, { section: String(to) });
  }

  like(id: number, liked: boolean): Promise<Bookmark> {
    return this.call(liked ? "POST" : "DELETE", `/bookmarks/${id}/like`);
  }

  async remove(id: number): Promise<void> {
    await this.call("DELETE", `/bookmarks/${id}`);
  }

  /** Tags to add (by name, created if new) and tags to remove (by id). Returns the bookmark's tags afterwards. */
  async setTags(id: number, add: string[], removeIds: number[]): Promise<Tag[]> {
    const answer = await this.call<{ tags?: Tag[] }>("POST", `/bookmarks/${id}/tags`, {
      add_tags: add.map((name) => ({ name })),
      remove_tags: removeIds.map((tagId) => ({ id: tagId })),
    });
    return answer?.tags ?? [];
  }

  async folders(): Promise<Folder[]> {
    const answer = await this.call<{ folders?: Folder[] }>("GET", "/folders");
    return [...(answer?.folders ?? [])].sort((a, b) => a.position - b.position);
  }

  createFolder(title: string): Promise<Folder> {
    return this.call("POST", "/folders", { title });
  }

  async deleteFolder(id: number): Promise<void> {
    await this.call("DELETE", `/folders/${id}`);
  }

  /** Positions start at 1. Folders left out keep theirs. Returns every folder in the new order. */
  async reorderFolders(order: { folder_id: number; position: number }[]): Promise<Folder[]> {
    const answer = await this.call<{ folders?: Folder[] }>("POST", "/folders/reorder", { order });
    return [...(answer?.folders ?? [])].sort((a, b) => a.position - b.position);
  }

  async tags(): Promise<Tag[]> {
    const answer = await this.call<{ tags?: Tag[] }>("GET", "/tags");
    return answer?.tags ?? [];
  }

  createTag(name: string): Promise<Tag> {
    return this.call("POST", "/tags", { name });
  }

  renameTag(id: number, name: string): Promise<Tag> {
    return this.call("POST", `/tags/${id}`, { name });
  }
}

/** The new order after moving the folder at `index` by `delta`, as positions for reorderFolders. */
export function reorderedPositions(folders: Folder[], index: number, delta: number): { folder_id: number; position: number }[] | null {
  const target = index + delta;
  if (index < 0 || index >= folders.length || target < 0 || target >= folders.length) return null;
  const next = [...folders];
  const [moved] = next.splice(index, 1);
  next.splice(target, 0, moved);
  return next.map((f, i) => ({ folder_id: f.id, position: i + 1 }));
}
