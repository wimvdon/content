import { parseFeedDate } from "./dates";
import { escapeHtml, javaHashCode, snippet, toPlainText } from "./html";

/** Reads RSS 2.0, RSS 1.0 (RDF) and Atom like the Android app (feed/FeedParser.kt), with the same article ids. */

export interface ParsedItem {
  id: string;
  title: string;
  link: string | null;
  author: string | null;
  html: string;
  publishedAt: number | null;
  imageUrl: string | null;
}

export interface ParsedFeed {
  title: string;
  siteUrl: string | null;
  items: ParsedItem[];
}

export class NotAFeedError extends Error {}

const ATOM_NS = "http://www.w3.org/2005/Atom";
const RSS1_NS = "http://purl.org/rss/1.0/";
const CONTENT_NS = "http://purl.org/rss/1.0/modules/content/";
const DC_NS = "http://purl.org/dc/elements/1.1/";
const XHTML_NS = "http://www.w3.org/1999/xhtml";
const MEDIA_NS = "http://search.yahoo.com/mrss/";
const ITUNES_NS = "http://www.itunes.com/dtds/podcast-1.0.dtd";
const RSS_NS = [null, "", RSS1_NS];
const ATOM_NSS = [ATOM_NS, "http://purl.org/atom/ns#", null, ""];
const IMAGE_FILE = /\.(jpe?g|png|webp|gif|avif)(\?|$)/i;

export function parseFeed(text: string, baseUrl: string): ParsedFeed {
  const root = parseXml(text);
  if (!root) throw new NotAFeedError("Not XML");
  switch (root.localName) {
    case "rss":
    case "RDF":
      return parseRss(child(root, "channel", RSS_NS) ?? root, root, baseUrl);
    case "feed":
      return parseAtom(root, baseUrl);
    default:
      throw new NotAFeedError("Unknown root element " + root.localName);
  }
}

function parseXml(text: string): Element | null {
  const trimmed = text.replace(/^﻿/, "").trimStart();
  const parse = (t: string) => {
    const doc = new DOMParser().parseFromString(t, "application/xml");
    return doc.getElementsByTagName("parsererror").length > 0 ? null : doc.documentElement;
  };
  // Hand-made feeds often use HTML entities XML doesn't know (&nbsp;, &mdash;, …).
  return parse(trimmed) ?? parse(replaceHtmlEntities(trimmed));
}

function replaceHtmlEntities(text: string): string {
  const decoder = document.createElement("textarea");
  return text.replace(/&([a-zA-Z][a-zA-Z0-9]{1,31});/g, (whole, name: string) => {
    if (["amp", "lt", "gt", "quot", "apos"].includes(name)) return whole;
    decoder.innerHTML = whole;
    const decoded = decoder.value;
    return decoded === whole ? `&amp;${name};` : Array.from(decoded).map((c) => `&#${c.codePointAt(0)};`).join("");
  });
}

function parseRss(channel: Element, root: Element, baseUrl: string): ParsedFeed {
  const siteLink = childText(channel, "link", RSS_NS);
  const siteUrl = siteLink ? webLink(baseUrl, siteLink) : null;
  const linkBase = siteUrl ?? baseUrl;
  let itemElements = children(channel, "item", RSS_NS);
  if (itemElements.length === 0) itemElements = children(root, "item", RSS_NS);
  const items = itemElements.map((item) => {
    const guidElement = child(item, "guid", RSS_NS);
    const guid = guidElement?.textContent?.trim() || null;
    const guidIsLink = guidElement?.getAttribute("isPermaLink")?.toLowerCase() !== "false";
    const linkText = childText(item, "link", RSS_NS);
    const link = (linkText ? webLink(linkBase, linkText) : null) ?? (guid && guidIsLink && guid.startsWith("http") ? guid : null);
    const description = childText(item, "description", RSS_NS) ?? "";
    const content = childText(item, "encoded", [CONTENT_NS]) ?? "";
    const html = content.length > description.length ? content : description;
    const titleText = childText(item, "title", RSS_NS);
    const title = titleText ? toPlainText(titleText) : "";
    const date = parseFeedDate(childText(item, "pubDate", RSS_NS) ?? childText(item, "date", [DC_NS]));
    const authorText = childText(item, "creator", [DC_NS]) ?? childText(item, "author", RSS_NS);
    return {
      id: guid ?? link ?? fallbackId(title, date, html),
      title: title || snippet(html, 80),
      link,
      author: authorText ? toPlainText(authorText) || null : null,
      html,
      publishedAt: date,
      imageUrl: itemImage(item, html, link ?? linkBase),
    };
  });
  const feedTitle = childText(channel, "title", RSS_NS);
  return { title: feedTitle ? toPlainText(feedTitle) : "", siteUrl, items };
}

function parseAtom(feed: Element, baseUrl: string): ParsedFeed {
  const feedBase = xmlBase(feed, baseUrl);
  const feedAuthor = childText(child(feed, "author", ATOM_NSS), "name", ATOM_NSS);
  const items = children(feed, "entry", ATOM_NSS).map((entry) => {
    const entryBase = xmlBase(entry, feedBase);
    const href = alternateLink(entry);
    const link = href ? webLink(entryBase, href) : null;
    const contentEl = child(entry, "content", ATOM_NSS);
    const summaryEl = child(entry, "summary", ATOM_NSS);
    const content = contentEl ? atomText(contentEl) : "";
    const html = content || (summaryEl ? atomText(summaryEl) : "");
    const titleEl = child(entry, "title", ATOM_NSS);
    const title = titleEl ? toPlainText(atomText(titleEl)) : "";
    const date = parseFeedDate(childText(entry, "published", ATOM_NSS) ?? childText(entry, "updated", ATOM_NSS) ?? childText(entry, "issued", ATOM_NSS));
    return {
      id: childText(entry, "id", ATOM_NSS) ?? link ?? fallbackId(title, date, html),
      title: title || snippet(html, 80),
      link,
      author: childText(child(entry, "author", ATOM_NSS), "name", ATOM_NSS) ?? feedAuthor,
      html,
      publishedAt: date,
      imageUrl: itemImage(entry, html, link ?? entryBase),
    };
  });
  const titleEl = child(feed, "title", ATOM_NSS);
  const siteHref = alternateLink(feed);
  return {
    title: titleEl ? toPlainText(atomText(titleEl)) : "",
    siteUrl: siteHref ? webLink(feedBase, siteHref) : null,
    items,
  };
}

/** Same as the Android app: "hash:" + hex of Java's hashCode of title|date|first 500 characters. */
export function fallbackId(title: string, date: number | null, html: string): string {
  return "hash:" + javaHashCode(`${title}|${date ?? "null"}|${html.slice(0, 500)}`).toString(16);
}

function itemImage(item: Element, html: string, base: string): string | null {
  const own = elements(item);
  const media = children(item, "group", [MEDIA_NS]).flatMap(elements).concat(own);
  const candidates = [
    ...media
      .filter((el) => el.namespaceURI === MEDIA_NS && (el.localName === "content" || el.localName === "thumbnail"))
      .filter(
        (el) =>
          el.localName === "thumbnail" ||
          el.getAttribute("medium") === "image" ||
          (el.getAttribute("type") ?? "").startsWith("image/") ||
          IMAGE_FILE.test(el.getAttribute("url") ?? ""),
      )
      .sort((a, b) => (Number(b.getAttribute("width")) || 0) - (Number(a.getAttribute("width")) || 0))
      .map((el) => el.getAttribute("url") ?? ""),
    ...own
      .filter((el) => el.localName === "enclosure" || (el.localName === "link" && el.getAttribute("rel") === "enclosure"))
      .filter((el) => (el.getAttribute("type") ?? "").startsWith("image/") || IMAGE_FILE.test((el.getAttribute("url") ?? "") + (el.getAttribute("href") ?? "")))
      .map((el) => el.getAttribute("url") || el.getAttribute("href") || ""),
    ...children(item, "image", [ITUNES_NS]).map((el) => el.getAttribute("href") ?? ""),
  ];
  for (const c of candidates) {
    const url = imageUrl(c, base);
    if (url) return url;
  }
  return firstImage(html, base);
}

/** An https picture address, resolved against [base]; null for inline or insecure ones. */
export function imageUrl(raw: string, base: string): string | null {
  const trimmed = raw.trim();
  if (!trimmed || trimmed.startsWith("data:")) return null;
  try {
    const url = new URL(trimmed, base);
    if (url.protocol === "http:") url.protocol = "https:";
    return url.protocol === "https:" ? url.toString() : null;
  } catch {
    return null;
  }
}

function firstImage(html: string, base: string): string | null {
  if (!html.includes("<img")) return null;
  const doc = new DOMParser().parseFromString(html, "text/html");
  for (const img of Array.from(doc.querySelectorAll("img"))) {
    const width = Number(img.getAttribute("width"));
    if (width > 0 && width < 50) continue;
    const url = imageUrl(img.getAttribute("src") ?? img.getAttribute("data-src") ?? "", base);
    if (url) return url;
  }
  return null;
}

function atomText(element: Element): string {
  const type = (element.getAttribute("type") ?? "").toLowerCase();
  if (type === "xhtml") {
    const div = child(element, "div", [XHTML_NS, null, ""]) ?? element;
    return Array.from(div.childNodes).map(serialize).join("").trim();
  }
  if (type === "html" || type === "text/html") return (element.textContent ?? "").trim();
  return escapeHtml((element.textContent ?? "").trim()).replace(/\n/g, "<br>");
}

function serialize(node: Node): string {
  if (node.nodeType === Node.TEXT_NODE || node.nodeType === Node.CDATA_SECTION_NODE) return escapeHtml(node.nodeValue ?? "");
  if (node.nodeType !== Node.ELEMENT_NODE) return "";
  const el = node as Element;
  const attrs = Array.from(el.attributes)
    .filter((a) => !a.name.startsWith("xmlns"))
    .map((a) => ` ${a.localName}="${escapeHtml(a.value)}"`)
    .join("");
  return `<${el.localName}${attrs}>${Array.from(el.childNodes).map(serialize).join("")}</${el.localName}>`;
}

function alternateLink(element: Element): string | null {
  const link = children(element, "link", ATOM_NSS).find((l) => {
    const rel = l.getAttribute("rel") ?? "";
    return (rel === "" || rel === "alternate") && l.hasAttribute("href");
  });
  return link?.getAttribute("href")?.trim() || null;
}

function xmlBase(element: Element, parentBase: string): string {
  const base = element.getAttributeNS("http://www.w3.org/XML/1998/namespace", "base");
  return base?.trim() ? resolve(parentBase, base) : parentBase;
}

/**
 * A link the reader can open: http or https only. A feed can't slip in a `javascript:` (or `data:`,
 * …) address that would run in the web app when the article is opened in a new tab.
 */
function webLink(base: string, href: string): string | null {
  const url = resolve(base, href);
  return /^https?:\/\//i.test(url) ? url : null;
}

function resolve(base: string, href: string): string {
  const trimmed = href.trim();
  try {
    return new URL(trimmed, base).toString();
  } catch {
    return trimmed;
  }
}

function elements(el: Element): Element[] {
  return Array.from(el.children);
}

function children(el: Element, localName: string, namespaces: (string | null)[]): Element[] {
  return elements(el).filter((c) => c.localName === localName && namespaces.includes(c.namespaceURI));
}

function child(el: Element | null | undefined, localName: string, namespaces: (string | null)[]): Element | null {
  return el ? (children(el, localName, namespaces)[0] ?? null) : null;
}

function childText(el: Element | null | undefined, localName: string, namespaces: (string | null)[]): string | null {
  return child(el, localName, namespaces)?.textContent?.trim() || null;
}
