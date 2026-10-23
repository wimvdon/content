/** Loads feeds and pages through the Cloudflare Worker (worker/), because browsers block most of them otherwise. */

import { PROXY_URL } from "./config";
import { extensionPresent, fetchViaExtension } from "./extension";

/** "blocked": the site refuses the feed loader (a bot check or a block of data centers). */
export type FetchProblem = "offline" | "notFound" | "server" | "tooLarge" | "blocked";

export class FetchError extends Error {
  readonly kind: FetchProblem;

  constructor(kind: FetchProblem) {
    super(kind);
    this.kind = kind;
  }
}

export interface Fetched {
  text: string;
  url: string;
}

/**
 * Loads `url`; `page` asks for an article page as a browser would. With the browser extension
 * installed (#168), an article page comes from the user's own browser (their sign-ins included),
 * and a feed the feed loader is refused comes that way too; otherwise through the Worker.
 */
export async function fetchViaProxy(url: string, page = false): Promise<Fetched> {
  if (page && extensionPresent()) {
    const own = await fromExtension(url);
    if (own) return own;
  }
  try {
    return await fromWorker(url, page);
  } catch (e) {
    if (!page && e instanceof FetchError && e.kind === "blocked" && extensionPresent()) {
      const own = await fromExtension(url);
      if (own) return own;
    }
    throw e;
  }
}

/** The page from the extension, or null when it couldn't (the Worker is asked then). */
async function fromExtension(url: string): Promise<Fetched | null> {
  try {
    const answer = await fetchViaExtension(url);
    return { text: decodeText(answer.bytes, answer.contentType), url: answer.url };
  } catch {
    return null;
  }
}

/** Loads `url` through the Worker. */
async function fromWorker(url: string, page: boolean): Promise<Fetched> {
  let response: Response;
  try {
    response = await fetch(`${PROXY_URL}/?url=${encodeURIComponent(url)}${page ? "&kind=page" : ""}`);
  } catch {
    throw new FetchError("offline");
  }
  if (response.status === 404) throw new FetchError("notFound");
  if (response.status === 403) throw new FetchError("blocked");
  if (response.status === 413) throw new FetchError("tooLarge");
  if (!response.ok) throw new FetchError("server");
  const bytes = new Uint8Array(await response.arrayBuffer());
  return { text: decodeText(bytes, response.headers.get("Content-Type") ?? ""), url: response.headers.get("X-Final-Url") ?? url };
}

/** Decodes with the charset from the header, the XML declaration or the HTML meta tag, else UTF-8. */
export function decodeText(bytes: Uint8Array, contentType: string): string {
  const head = new TextDecoder("latin1").decode(bytes.slice(0, 1024));
  const charset =
    /charset=["']?([\w-]+)/i.exec(contentType)?.[1] ??
    /<\?xml[^>]*encoding=["']([\w-]+)["']/i.exec(head)?.[1] ??
    /<meta[^>]+charset=["']?([\w-]+)/i.exec(head)?.[1] ??
    "utf-8";
  try {
    return new TextDecoder(charset).decode(bytes);
  } catch {
    return new TextDecoder("utf-8").decode(bytes);
  }
}
