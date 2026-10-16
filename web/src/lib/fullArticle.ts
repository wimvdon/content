import { Readability } from "@mozilla/readability";

/** The readable article from a web page (Firefox's Reader View engine), as HTML, or null. */
export function extractArticle(html: string, url: string): string | null {
  const doc = new DOMParser().parseFromString(html, "text/html");
  const base = doc.createElement("base");
  base.href = url;
  doc.head.prepend(base);
  const article = new Readability(doc, { charThreshold: 300 }).parse();
  return article?.content ?? null;
}

/** True when a feed gives only a summary, so the page is worth loading (like the Android app). */
export function isSummary(html: string): boolean {
  const text = html.replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim();
  return text.length < 600 || /(read more|continue reading|lees meer|verder lezen|\[…\]|\[\.\.\.\])\s*$/i.test(text);
}
