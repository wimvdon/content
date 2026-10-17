/** Loads feeds and pages through the Cloudflare Worker (worker/), because browsers block most of them otherwise. */

import { PROXY_URL } from "./config";

export type FetchProblem = "offline" | "notFound" | "server" | "tooLarge";

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

export async function fetchViaProxy(url: string): Promise<Fetched> {
  let response: Response;
  try {
    response = await fetch(`${PROXY_URL}/?url=${encodeURIComponent(url)}`);
  } catch {
    throw new FetchError("offline");
  }
  if (response.status === 404) throw new FetchError("notFound");
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
