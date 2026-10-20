/**
 * Highlights in the article's page (#102): the page's text as one text, its paragraphs joined by a
 * blank line like the Android reader's ArticleText, so a highlight anchors the same way on both;
 * where a selection is in that text; and drawing the marks.
 */

import { PARAGRAPH_SEPARATOR } from "./anchors";

const BLOCKS = new Set(["P", "H1", "H2", "H3", "H4", "H5", "H6", "LI", "BLOCKQUOTE", "PRE", "FIGCAPTION", "DIV", "SECTION", "ARTICLE", "TD", "TH", "DT", "DD"]);

/** The page's text, and where each text node starts in it. */
export interface PageText {
  text: string;
  segments: { node: Text; start: number }[];
}

function blockOf(node: Node, root: HTMLElement): Node {
  let at: Node | null = node.parentNode;
  while (at && at !== root) {
    if (at instanceof HTMLElement && BLOCKS.has(at.tagName)) return at;
    at = at.parentNode;
  }
  return root;
}

export function pageText(root: HTMLElement): PageText {
  const walker = root.ownerDocument.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let text = "";
  const segments: PageText["segments"] = [];
  let lastBlock: Node | null = null;
  for (let node = walker.nextNode() as Text | null; node; node = walker.nextNode() as Text | null) {
    const value = node.data;
    if (!value.trim() && blockOf(node, root) === root) continue;
    const block = blockOf(node, root);
    if (lastBlock && block !== lastBlock && text) text += PARAGRAPH_SEPARATOR;
    lastBlock = block;
    segments.push({ node, start: text.length });
    text += value;
  }
  return { text, segments };
}

/** Where a point of a DOM range is in the page's text; null outside it. */
export function offsetOf(page: PageText, node: Node, offset: number): number | null {
  if (node.nodeType === Node.TEXT_NODE) {
    const segment = page.segments.find((s) => s.node === node);
    return segment ? segment.start + Math.min(offset, segment.node.data.length) : null;
  }
  // An element: the first text at or after that point.
  const point = node.ownerDocument!.createRange();
  point.setStart(node, offset);
  const next = page.segments.find((s) => point.comparePoint(s.node, 0) >= 0);
  return next ? next.start : page.text.length;
}

/** Wraps start to end of the page's text in marks for highlight id (one per text node it crosses). */
export function mark(root: HTMLElement, start: number, end: number, id: string, approximate: boolean) {
  const page = pageText(root);
  for (const { node, start: at } of page.segments) {
    const from = Math.max(start, at) - at;
    const to = Math.min(end, at + node.data.length) - at;
    if (from >= to) continue;
    let target = node;
    if (from > 0) target = target.splitText(from);
    if (to - from < target.data.length) target.splitText(to - from);
    const el = root.ownerDocument.createElement("mark");
    el.dataset.highlight = id;
    if (approximate) el.dataset.approximate = "true";
    target.parentNode!.insertBefore(el, target);
    el.appendChild(target);
  }
}
