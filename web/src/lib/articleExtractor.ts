/**
 * Finds the article in a web page and returns it as simple HTML (paragraphs, headings, lists,
 * quotes, links, text styles, pictures and embedded videos), the way "reader view" in browsers
 * does. Finding the article is Defuddle (https://github.com/kepano/defuddle) as it is: no changes
 * to the page before it or to what it finds. This only keeps what the reader can show (the tags
 * below, absolute links, known video players). The Android app runs this same code (bundled from
 * src/android/), so both apps show the same article. The sample pages in /testdata/articles are
 * checked here.
 */

import Defuddle from "defuddle";
import { escapeHtml, toPlainText } from "./html";

/** Below this many characters of text the page is not treated as an article. */
export const MIN_TEXT_LENGTH = 250;

/** Tags kept in the result; other elements are replaced by their content. */
const KEPT_TAGS = new Set([
  "p", "br", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote", "pre", "code",
  "em", "i", "strong", "b", "u", "s", "sub", "sup", "a", "hr", "dl", "dt", "dd",
  "figure", "figcaption", "img", "iframe", "video",
]);

/**
 * What the reader can't show (scripts, drawings, form fields, players without a link), wherever
 * Defuddle leaves them. Everything else Defuddle keeps is shown (#251: no other choices).
 */
const DROPPED_TAGS = new Set(["script", "style", "noscript", "template", "svg", "math", "canvas", "textarea", "select", "input", "button", "object", "audio"]);

/** Embedded players worth a card in the reader; other iframes are usually ads or widgets. */
const KNOWN_EMBEDS =
  /youtube\.com|youtube-nocookie\.com|youtu\.be|vimeo\.com|twitter\.com|x\.com|instagram\.com|spotify\.com|soundcloud\.com|codepen\.io|dailymotion\.com|tiktok\.com|bsky\.app/i;

/** The article of `html` (downloaded from `pageUrl`) as simple HTML, or null when none is found. */
export function extractArticle(html: string, pageUrl: string): string | null {
  if (!html.trim()) return null;
  let content: string | undefined;
  try {
    // Only the page itself: no site-specific lookups on other servers.
    content = new Defuddle(new DOMParser().parseFromString(html, "text/html"), { url: pageUrl, useAsync: false }).parse().content;
  } catch {
    // A page Defuddle can't handle has no article for the reader.
    return null;
  }
  if (!content) return null;
  const result = serialize(new DOMParser().parseFromString(`<body>${content}</body>`, "text/html").body, pageUrl).trim();
  return toPlainText(result).length < MIN_TEXT_LENGTH ? null : result;
}

// ---- Writing the result -------------------------------------------------------------------

function serialize(el: Element, pageUrl: string): string {
  let out = "";
  for (const child of el.childNodes) {
    if (child.nodeType === Node.TEXT_NODE) out += escapeHtml((child as Text).data);
    else if (child.nodeType === Node.ELEMENT_NODE) out += serializeElement(child as Element, pageUrl);
  }
  return out;
}

function serializeElement(el: Element, pageUrl: string): string {
  const tag = el.localName;
  if (DROPPED_TAGS.has(tag)) return "";
  if (!KEPT_TAGS.has(tag)) return serialize(el, pageUrl);
  if (tag === "br" || tag === "hr") return `<${tag}>`;
  if (tag === "img") {
    let out = "<img";
    for (const name of ["src", "data-src", "data-lazy-src"]) {
      const value = el.getAttribute(name);
      const url = value !== null ? absolute(pageUrl, value) : null;
      if (url) out += attr(name, url);
    }
    for (const name of ["srcset", "data-srcset"]) {
      const value = el.getAttribute(name);
      if (value !== null) out += attr(name, resolveSrcset(pageUrl, value));
    }
    for (const name of ["width", "height", "alt"]) {
      const value = el.getAttribute(name);
      if (value !== null) out += attr(name, value);
    }
    return `${out}>`;
  }
  if (tag === "iframe") {
    const raw = el.getAttribute("src") ?? el.getAttribute("data-src");
    const src = raw !== null ? absolute(pageUrl, raw) : null;
    return src && KNOWN_EMBEDS.test(src) ? `<iframe${attr("src", src)}></iframe>` : "";
  }
  if (tag === "video") {
    const raw = el.getAttribute("src") ?? el.querySelector("source[src]")?.getAttribute("src") ?? null;
    const src = raw !== null ? absolute(pageUrl, raw) : null;
    if (!src) return "";
    const posterRaw = el.getAttribute("poster");
    const poster = posterRaw !== null ? absolute(pageUrl, posterRaw) : null;
    return `<video${attr("src", src)}${poster ? attr("poster", poster) : ""}></video>`;
  }
  if (tag === "a") {
    const raw = el.getAttribute("href");
    // Defuddle makes "#top" absolute; an anchor in the page itself is no link for the reader.
    const href = raw !== null && !inPage(pageUrl, raw) ? absolute(pageUrl, raw) : null;
    const inner = serialize(el, pageUrl);
    return href ? `<a href="${escapeHtml(href)}">${inner}</a>` : inner;
  }
  return `<${tag}>${serialize(el, pageUrl)}</${tag}>`;
}

const attr = (name: string, value: string) => ` ${name}="${escapeHtml(value)}"`;

function resolveSrcset(base: string, srcset: string): string {
  return srcset
    .split(",")
    .flatMap((part) => {
      const [first, rest] = part.trim().split(/\s+(.*)/s);
      const url = first ? absolute(base, first) : null;
      if (!url) return [];
      return [rest ? `${url} ${rest}` : url];
    })
    .join(", ");
}

/** Whether `href` only jumps within the page at `pageUrl` (`#top`, or the page's own address with a #part). */
function inPage(pageUrl: string, href: string): boolean {
  const url = absolute(pageUrl, href);
  if (href.trim().startsWith("#")) return true;
  return url !== null && url.includes("#") && url.split("#")[0] === new URL(pageUrl).toString().split("#")[0];
}

/** An absolute http(s) or mailto address, or null for in-page anchors, scripts and anything else. */
function absolute(base: string, href: string): string | null {
  const trimmed = href.trim();
  if (trimmed.startsWith("#") || /^javascript:/i.test(trimmed)) return null;
  try {
    const url = new URL(trimmed.replace(/ /g, "%20"), base);
    return ["http:", "https:", "mailto:"].includes(url.protocol) ? url.toString() : null;
  } catch {
    return null;
  }
}
