import { escapeHtml } from "./html";
import { addFeed, addFolder, newFeed, type Library } from "./library";

export interface OpmlEntry {
  url: string;
  title: string;
  folder: string | null;
}

/** Reads the feeds of an OPML file, with the folder (outer outline) each is in. */
export function parseOpml(text: string): OpmlEntry[] {
  const doc = new DOMParser().parseFromString(text, "application/xml");
  if (doc.getElementsByTagName("parsererror").length > 0 || doc.documentElement.localName !== "opml") return [];
  const body = doc.getElementsByTagName("body")[0];
  if (!body) return [];
  const entries: OpmlEntry[] = [];
  const walk = (el: Element, folder: string | null) => {
    for (const outline of Array.from(el.children).filter((c) => c.localName === "outline")) {
      const url = outline.getAttribute("xmlUrl")?.trim();
      const title = outline.getAttribute("title") ?? outline.getAttribute("text") ?? "";
      if (url) entries.push({ url, title: title.trim(), folder });
      else walk(outline, folder ?? (title.trim() || null));
    }
  };
  walk(body, null);
  const seen = new Set<string>();
  return entries.filter((e) => !seen.has(e.url) && seen.add(e.url));
}

export function writeOpml(title: string, lib: Library): string {
  const line = (f: { url: string; title: string }, indent: string) =>
    `${indent}<outline type="rss" text="${escapeHtml(f.title)}" title="${escapeHtml(f.title)}" xmlUrl="${escapeHtml(f.url)}"/>`;
  const parts = [`<?xml version="1.0" encoding="UTF-8"?>`, `<opml version="2.0">`, `  <head><title>${escapeHtml(title)}</title></head>`, `  <body>`];
  for (const folder of lib.folders) {
    const feeds = lib.feeds.filter((f) => f.folderId === folder.id);
    parts.push(`    <outline text="${escapeHtml(folder.title)}" title="${escapeHtml(folder.title)}">`);
    feeds.forEach((f) => parts.push(line(f, "      ")));
    parts.push(`    </outline>`);
  }
  lib.feeds.filter((f) => !f.folderId).forEach((f) => parts.push(line(f, "    ")));
  parts.push(`  </body>`, `</opml>`, "");
  return parts.join("\n");
}

/** Adds the entries' feeds and folders (matched by title). Returns the library and how many feeds were new. */
export function importOpml(lib: Library, entries: OpmlEntry[], newId: () => string): [Library, number] {
  let out = lib;
  let added = 0;
  for (const e of entries) {
    if (out.feeds.some((f) => f.url === e.url)) continue;
    let folderId: string | null = null;
    if (e.folder) {
      const existing = out.folders.find((f) => f.title.toLowerCase() === e.folder!.toLowerCase());
      folderId = existing?.id ?? newId();
      if (!existing) out = addFolder(out, { id: folderId, title: e.folder, icon: "folder" });
    }
    out = addFeed(out, { ...newFeed(e.url, e.title), folderId });
    added++;
  }
  return [out, added];
}
