/**
 * Embedded content (video players, posts) in articles, shown as a card that opens it in a new
 * tab, like the Android app's ArticleBlocks.embedFor. Nothing plays inside the page.
 */

export type EmbedKind = "youtube" | "vimeo" | "video" | "other";

export interface Embed {
  kind: EmbedKind;
  url: string;
  /** A preview picture, when the service has a known one. */
  thumbnailUrl: string | null;
}

const YOUTUBE_ID = /(?:youtube(?:-nocookie)?\.com\/(?:embed\/|watch\?v=|v\/|shorts\/)|youtu\.be\/)([A-Za-z0-9_-]{6,20})/;
const VIMEO_ID = /vimeo\.com\/(?:video\/)?(\d+)/;

/** The card for an iframe's address, or null when there is nothing to open. */
export function embedFor(src: string | null | undefined, baseUrl: string): Embed | null {
  const url = httpsUrl(src, baseUrl);
  if (!url) return null;
  const youtube = YOUTUBE_ID.exec(url);
  if (youtube) {
    const id = youtube[1];
    return { kind: "youtube", url: `https://www.youtube.com/watch?v=${id}`, thumbnailUrl: `https://i.ytimg.com/vi/${id}/hqdefault.jpg` };
  }
  const vimeo = VIMEO_ID.exec(url);
  if (vimeo) return { kind: "vimeo", url: `https://vimeo.com/${vimeo[1]}`, thumbnailUrl: null };
  return { kind: "other", url, thumbnailUrl: null };
}

/** The card for a video file, with its poster as preview, or null without a source. */
export function videoEmbed(src: string | null | undefined, poster: string | null | undefined, baseUrl: string): Embed | null {
  const url = httpsUrl(src, baseUrl);
  return url ? { kind: "video", url, thumbnailUrl: httpsUrl(poster, baseUrl) } : null;
}

/** An absolute https address (http is upgraded), or null. */
function httpsUrl(href: string | null | undefined, baseUrl: string): string | null {
  if (!href?.trim()) return null;
  try {
    const url = new URL(href.trim(), baseUrl);
    if (url.protocol === "http:") url.protocol = "https:";
    return url.protocol === "https:" ? url.toString() : null;
  } catch {
    return null;
  }
}
