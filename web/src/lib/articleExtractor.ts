/**
 * Finds the article in a web page and returns it as simple HTML (paragraphs, headings, lists,
 * quotes, links, text styles, pictures and embedded videos), the way "reader view" in browsers
 * does. The same steps as the Android app's feed/ArticleExtractor.kt, so both apps show the same
 * article: Readability (Firefox Reader View's engine) as it is (#236: no changes to the page before
 * it or to what it finds), its result turned into the reader's simple HTML (only the tags below,
 * absolute links, known video players), without the title the reader shows above it.
 * The shared sample pages in /testdata/articles check that both give the same text.
 */

import { Readability } from "@mozilla/readability";
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
 * Readability leaves them. Everything else Readability keeps is shown (#251: no other choices).
 */
const DROPPED_TAGS = new Set(["script", "style", "noscript", "template", "svg", "math", "canvas", "textarea", "select", "input", "button", "object", "audio"]);

/** Embedded players worth a card in the reader; other iframes are usually ads or widgets. */
const KNOWN_EMBEDS =
  /youtube\.com|youtube-nocookie\.com|youtu\.be|vimeo\.com|twitter\.com|x\.com|instagram\.com|spotify\.com|soundcloud\.com|codepen\.io|dailymotion\.com|tiktok\.com|bsky\.app/i;

const BLOCK_TAGS = new Set(["p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote", "pre", "hr", "dl", "dt", "dd", "div", "section", "article", "main", "td", "tr", "table"]);

/** Elements that make their own block in the result. */
const BLOCK_LIKE = new Set([...BLOCK_TAGS, "figure", "figcaption"]);

/** Shorter headings only count as the title when they match it exactly. */
const MIN_TITLE_LENGTH = 10;

/** The article of `html` (downloaded from `pageUrl`) as simple HTML, or null when none is found. */
export function extractArticle(html: string, pageUrl: string): string | null {
  if (!html.trim()) return null;
  const doc = new DOMParser().parseFromString(html, "text/html");
  const base = doc.createElement("base");
  base.href = pageUrl;
  doc.head.prepend(base);
  const titles = [doc.querySelector('meta[property="og:title"]')?.getAttribute("content"), doc.title].filter((t): t is string => !!t);
  let parsed: { title?: string | null; content?: string | null } | null;
  try {
    parsed = new Readability(doc).parse();
  } catch {
    // A page Readability can't handle has no article for the reader.
    parsed = null;
  }
  if (!parsed?.content) return null;
  const content = new DOMParser().parseFromString(`<body>${parsed.content}</body>`, "text/html").body;
  // The reader shows the title above the article already.
  const allTitles = [...titles, ...(parsed.title ? [parsed.title] : [])];
  [...content.querySelectorAll("h1, h2")].find((h) => allTitles.some((t) => isTitle(text(h), t)))?.remove();
  const result = serialize(content, pageUrl).replace(/(\s*<br>\s*){3,}/g, "<br><br>").trim();
  return toPlainText(result).length >= MIN_TEXT_LENGTH ? result : null;
}

/** An element's text as jsoup's text() gives it: whitespace collapsed, trimmed. */
const text = (el: Element) => (el.textContent ?? "").replace(/\s+/g, " ").trim();

/** Whether `heading` is the page's `title`, which often ends in the site's name ("Title | Site"). */
function isTitle(heading: string, title: string): boolean {
  const normalized = (s: string) =>
    s
      .toLowerCase()
      .replace(/[^\p{L}\p{N}]+/gu, " ")
      .trim();
  const h = normalized(heading);
  const t = normalized(title);
  return h !== "" && (h === t || (h.length >= MIN_TITLE_LENGTH && t.startsWith(`${h} `)));
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
  if (!KEPT_TAGS.has(tag)) {
    // A container holding only text and inline elements is a paragraph (sites that use a <div>
    // per paragraph); one holding blocks just passes them on.
    if (BLOCK_TAGS.has(tag) && !hasBlockDescendant(el)) {
      const inner = serialize(el, pageUrl);
      return toPlainText(inner) ? `<p>${inner.trim()}</p>` : "";
    }
    return serialize(el, pageUrl);
  }
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
    // Readability makes "#top" absolute; an anchor in the page itself is no link for the reader.
    const href = raw !== null && !inPage(pageUrl, raw) ? absolute(pageUrl, raw) : null;
    const inner = serialize(el, pageUrl);
    return href ? `<a href="${escapeHtml(href)}">${inner}</a>` : inner;
  }
  const inner = serialize(el, pageUrl);
  if (BLOCK_TAGS.has(tag) && !inner.trim()) return "";
  return `<${tag}>${inner}</${tag}>`;
}

function hasBlockDescendant(el: Element): boolean {
  return [...el.children].some((child) => BLOCK_LIKE.has(child.localName) || hasBlockDescendant(child));
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
