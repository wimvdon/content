import DOMPurify from "dompurify";
import { embedFor, videoEmbed, type Embed, type EmbedKind } from "./embeds";
import { t, type Key } from "./i18n";
import { candidateSize, parseSrcset, type SrcsetCandidate } from "./srcset";

/**
 * Article HTML made safe to show: no scripts, styles, forms or frames; video players and other
 * embeds become a card that opens them in a new tab (nothing plays inside the page); links open
 * in a new tab without telling the site where they came from; pictures load lazily and only over
 * https. With `showImages` off (Settings), pictures and embed previews are left out.
 */
export function sanitizeArticle(html: string, baseUrl: string, showImages = true): string {
  // An iframe's fallback content never shows, and with markup in it DOMPurify drops the whole
  // iframe (and so its card).
  const clean = DOMPurify.sanitize(html.replace(/(<iframe\b[^>]*>)[\s\S]*?<\/iframe\s*>/gi, "$1</iframe>"), {
    FORBID_TAGS: ["style", "form", "input", "button", "textarea", "select", "object", "embed"],
    // width and height stay for now: they say how big a picture is meant to be (see below).
    FORBID_ATTR: ["style", "class", "id"],
    ADD_TAGS: ["iframe"],
    RETURN_DOM_FRAGMENT: true,
  });
  for (const frame of Array.from(clean.querySelectorAll("iframe"))) replaceWithCard(frame, embedFor(frame.getAttribute("src"), baseUrl));
  for (const video of Array.from(clean.querySelectorAll("video"))) {
    const src = video.getAttribute("src") ?? video.querySelector("source[src]")?.getAttribute("src");
    replaceWithCard(video, videoEmbed(src, video.getAttribute("poster"), baseUrl));
  }
  for (const a of Array.from(clean.querySelectorAll("a[href]"))) {
    a.setAttribute("href", absolute(a.getAttribute("href")!, baseUrl) ?? "#");
    a.setAttribute("target", "_blank");
    a.setAttribute("rel", "noopener noreferrer");
  }
  for (const img of Array.from(clean.querySelectorAll("img"))) {
    if (showImages) fixImage(img, baseUrl);
    else img.remove();
  }
  for (const element of Array.from(clean.querySelectorAll("[width], [height]"))) {
    if (element.tagName !== "IMG") {
      element.removeAttribute("width");
      element.removeAttribute("height");
    }
  }
  separateParagraphs(clean);
  const holder = document.createElement("div");
  holder.appendChild(clean);
  return holder.innerHTML;
}

/** Pictures at most this wide (in CSS pixels) are icons or emoji: they stay small, in the line. */
export const SMALL_IMAGE = 48;

/** Parts of addresses that belong to tracking pixels and share buttons, not pictures (as on Android). */
const TRACKERS = ["feeds.feedburner.com/~r/", "/~ff/", "stats.wordpress.com", "pixel.wp.com", "/pixel.", "doubleclick.net"];

/**
 * A picture fit to show, or removed: the largest version a lazy-loading page offers (like the
 * Android app), https only, no tracking pixels. Its width stays as a limit, so an icon or emoji
 * isn't blown up to the width of the page; the height follows the width.
 */
function fixImage(img: Element, baseUrl: string) {
  const width = pixels(img.getAttribute("width"));
  const height = pixels(img.getAttribute("height"));
  const candidate =
    largestFromSrcset(img.getAttribute("srcset") ?? img.getAttribute("data-srcset")) ??
    img.getAttribute("data-src") ??
    img.getAttribute("data-lazy-src") ??
    img.getAttribute("src") ??
    "";
  const url = absolute(candidate, baseUrl);
  if (!url || !url.startsWith("https:") || (width !== null && width <= 2) || (height !== null && height <= 2) || TRACKERS.some((t) => url.toLowerCase().includes(t))) {
    img.remove();
    return;
  }
  img.setAttribute("src", url);
  for (const name of ["srcset", "data-srcset", "data-src", "data-lazy-src", "sizes", "height"]) img.removeAttribute(name);
  if (width !== null) img.setAttribute("width", String(width));
  else img.removeAttribute("width");
  if ((width !== null && width <= SMALL_IMAGE) || (width === null && height !== null && height <= SMALL_IMAGE) || /\/emoji\//i.test(url)) {
    img.setAttribute("class", "small");
    if (width === null) img.setAttribute("width", String(Math.min(height ?? 20, SMALL_IMAGE)));
  }
  img.setAttribute("loading", "lazy");
  img.setAttribute("referrerpolicy", "no-referrer");
}

/** A size attribute in pixels ("40", "40px"), or null when absent or relative ("100%"). */
function pixels(value: string | null): number | null {
  const match = value?.trim().match(/^(\d+(?:\.\d+)?)(px)?$/i);
  return match ? Math.round(Number(match[1])) : null;
}

/** The address of the widest picture in a srcset ("a.jpg 400w, b.jpg 800w"), or null. */
function largestFromSrcset(srcset: string | null): string | null {
  let best: SrcsetCandidate | null = null;
  for (const c of parseSrcset(srcset)) if (!best || candidateSize(c) > candidateSize(best)) best = c;
  return best?.url ?? null;
}

/**
 * Pages that separate paragraphs with two line breaks instead of <p> get a paragraph gap there
 * (the Android app does the same), so the text doesn't run together.
 */
function separateParagraphs(root: DocumentFragment) {
  for (const br of Array.from(root.querySelectorAll("br"))) {
    if (!br.isConnected && !root.contains(br)) continue;
    const run: Element[] = [br];
    let next = br.nextSibling;
    while (next && (next.nodeName === "BR" || (next.nodeType === Node.TEXT_NODE && !next.textContent?.trim()))) {
      if (next.nodeName === "BR") run.push(next as Element);
      next = next.nextSibling;
    }
    if (run.length < 2) continue;
    const gap = br.ownerDocument.createElement("span");
    gap.className = "gap";
    br.replaceWith(gap);
    for (const extra of run.slice(1)) extra.remove();
  }
}

const LABELS: Record<EmbedKind, Key> = { youtube: "embedYoutube", vimeo: "embedVimeo", video: "embedVideo", other: "embedOther" };

/** Puts an embed card (preview picture and label) where the player was, or removes it. */
function replaceWithCard(element: Element, embed: Embed | null) {
  if (!embed) {
    element.remove();
    return;
  }
  const doc = element.ownerDocument;
  const card = doc.createElement("a");
  card.className = "embed";
  card.setAttribute("href", embed.url);
  if (embed.thumbnailUrl) {
    const img = doc.createElement("img");
    img.setAttribute("src", embed.thumbnailUrl);
    img.setAttribute("alt", "");
    card.appendChild(img);
  }
  const label = doc.createElement("span");
  label.className = embed.kind === "other" ? "embed-label" : "embed-label embed-play";
  label.textContent = t(LABELS[embed.kind]);
  card.appendChild(label);
  element.replaceWith(card);
}

function absolute(href: string, base: string): string | null {
  try {
    const url = new URL(href, base);
    if (url.protocol === "http:") url.protocol = "https:";
    return ["https:", "mailto:"].includes(url.protocol) ? url.toString() : null;
  } catch {
    return null;
  }
}
