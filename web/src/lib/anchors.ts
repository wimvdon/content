/**
 * Highlights anchored by quote, not position (#102), the same rules as the Android app's
 * data/Anchors.kt: the quote, some text before and after it, and which occurrence it is. That finds
 * the passage again in another version of the text (the feed's or the full article). Typographic
 * differences (curly or straight quotes, dashes, spaces, ligatures, letter case as a last resort)
 * don't count, so a highlight made on one device is found on the other.
 */

import type { Highlight } from "./items";

/** Where a highlight is in a text: found as it was made, found roughly, or not found. */
export type Anchor = { kind: "exact"; start: number; end: number } | { kind: "approximate"; start: number; end: number } | { kind: "notFound" };

/** How much text before and after the quote is kept. */
export const CONTEXT = 32;
/** At least this many characters of surroundings or of the quote to look for. */
const MIN_PIECE = 8;
/** How much of the quote's start and end is looked for when the quote itself isn't found. */
const PIECE = 16;

const FOLD: Record<string, string> = {
  "‘": "'",
  "’": "'",
  "‚": "'",
  "‛": "'",
  "′": "'",
  "´": "'",
  "`": "'",
  "“": '"',
  "”": '"',
  "„": '"',
  "‟": '"',
  "″": '"',
  "«": '"',
  "»": '"',
  "‐": "-",
  "‑": "-",
  "‒": "-",
  "–": "-",
  "—": "-",
  "―": "-",
  "−": "-",
  "…": "...",
  "ﬀ": "ff",
  "ﬁ": "fi",
  "ﬂ": "fl",
  "ﬃ": "ffi",
  "ﬄ": "ffl",
  // Invisible: soft hyphen, zero-width spaces and joiners, byte order mark.
  "­": "",
  "​": "",
  "‌": "",
  "‍": "",
  "⁠": "",
  "﻿": "",
};

/** A text with its typographic variants folded, and where each folded character came from. */
class Folded {
  readonly text: string;
  private readonly origin: number[];
  private readonly originalLength: number;

  constructor(original: string) {
    let out = "";
    const from: number[] = [];
    let lastSpace = false;
    for (let i = 0; i < original.length; i++) {
      const c = original[i];
      const replacement = FOLD[c] ?? (/\s/.test(c) ? " " : c);
      if (replacement === "") continue;
      if (replacement === " ") {
        if (lastSpace) continue;
        lastSpace = true;
      } else {
        lastSpace = false;
      }
      for (const r of replacement) {
        out += r;
        from.push(i);
      }
    }
    this.text = out;
    this.origin = from;
    this.originalLength = original.length;
  }

  /** The original index of folded index i. */
  originalIndex(i: number): number {
    return i >= this.origin.length ? this.originalLength : this.origin[i];
  }

  /** The original end (exclusive) of a folded range ending at end. */
  originalEnd(end: number): number {
    if (end <= 0) return 0;
    if (end > this.origin.length) return this.originalLength;
    return this.origin[end - 1] + 1;
  }

  /** The folded index at or after original index i. */
  foldedIndex(i: number): number {
    const at = this.origin.findIndex((o) => o >= i);
    return at < 0 ? this.text.length : at;
  }
}

/** Typographic variants made the same, spaces of any kind and length made one space. */
export const fold = (text: string) => new Folded(text).text.trim();

/** A new highlight of start to end in text. */
export function createHighlight(text: string, start: number, end: number, id: string, now: number, note = ""): Highlight {
  const from = Math.min(Math.max(start, 0), text.length);
  const to = Math.min(Math.max(end, from), text.length);
  const quote = text.slice(from, to);
  const folded = new Folded(text);
  const q = fold(quote);
  // Which occurrence: how many times the quote appears before this one.
  const foldedStart = folded.foldedIndex(from);
  let occurrence = 0;
  if (q) {
    let at = folded.text.indexOf(q);
    while (at >= 0 && at < foldedStart) {
      occurrence++;
      at = folded.text.indexOf(q, at + 1);
    }
  }
  return {
    id,
    quote: quote.trim(),
    prefix: text.slice(Math.max(from - CONTEXT, 0), from),
    suffix: text.slice(to, Math.min(to + CONTEXT, text.length)),
    occurrence,
    note,
    createdAt: now,
  };
}

/** Finds highlight in text. */
export function resolve(highlight: Highlight, text: string): Anchor {
  const folded = new Folded(text);
  const quote = fold(highlight.quote);
  if (!quote) return { kind: "notFound" };
  // Letter case as a last resort for the quote itself (a heading in capitals, for example).
  return exact(folded, folded.text, quote, highlight) ?? exact(folded, folded.text.toLowerCase(), quote.toLowerCase(), highlight) ?? approximate(folded, quote, highlight);
}

function allIndexes(haystack: string, needle: string): number[] {
  const out: number[] = [];
  let at = haystack.indexOf(needle);
  while (at >= 0) {
    out.push(at);
    at = haystack.indexOf(needle, at + 1);
  }
  return out;
}

function exact(folded: Folded, haystack: string, quote: string, highlight: Highlight): Anchor | null {
  const starts = allIndexes(haystack, quote);
  if (starts.length === 0) return null;
  let start = starts[0];
  if (starts.length > 1) {
    // Several: the one whose surroundings fit best; on a tie, the one counted when it was made.
    const prefix = new Folded(highlight.prefix).text.toLowerCase();
    const suffix = new Folded(highlight.suffix).text.toLowerCase();
    const lower = haystack.toLowerCase();
    const scores = starts.map((s) => commonSuffix(lower.slice(0, s), prefix) + commonPrefix(lower.slice(s + quote.length), suffix));
    const best = Math.max(...scores);
    const bestOnes = starts.map((_, i) => i).filter((i) => scores[i] === best);
    start = starts[bestOnes.includes(highlight.occurrence) ? highlight.occurrence : bestOnes[0]];
  }
  return { kind: "exact", start: folded.originalIndex(start), end: folded.originalEnd(start + quote.length) };
}

/** The text changed inside the quote: its surroundings, or its first and last words, mark where it most likely is. */
function approximate(folded: Folded, quote: string, highlight: Highlight): Anchor {
  const text = folded.text.toLowerCase();
  const q = quote.toLowerCase();
  const prefix = fold(highlight.prefix).toLowerCase().slice(-CONTEXT).trim();
  const suffix = fold(highlight.suffix).toLowerCase().slice(0, CONTEXT).trim();
  const maxLength = q.length * 2 + CONTEXT;
  // Between its surroundings.
  if (prefix.length >= MIN_PIECE && suffix.length >= MIN_PIECE) {
    let p = text.indexOf(prefix);
    while (p >= 0) {
      const from = p + prefix.length;
      const s = text.indexOf(suffix, from);
      if (s >= 0 && s - from <= maxLength) return approximateRange(folded, from, s);
      p = text.indexOf(prefix, p + 1);
    }
  }
  // From its first words to its last words (whole words only).
  let head = q.slice(0, PIECE);
  if (q.length > PIECE && head.includes(" ")) head = head.slice(0, head.lastIndexOf(" "));
  head = head.trim();
  let tail = q.slice(-PIECE);
  if (q.length > PIECE && tail.includes(" ")) tail = tail.slice(tail.indexOf(" ") + 1);
  tail = tail.trim();
  if (head.length >= MIN_PIECE && tail.length >= MIN_PIECE) {
    let h = text.indexOf(head);
    while (h >= 0) {
      const t = text.indexOf(tail, h + Math.max(Math.min(head.length, q.length - tail.length), 0));
      if (t >= 0 && t + tail.length - h <= maxLength) return approximateRange(folded, h, t + tail.length);
      h = text.indexOf(head, h + 1);
    }
  }
  // Only its start, or only what came right before it.
  if (head.length >= MIN_PIECE) {
    const at = text.indexOf(head);
    if (at >= 0) return approximateRange(folded, at, Math.min(at + q.length, text.length));
  }
  if (prefix.length >= MIN_PIECE) {
    const p = text.indexOf(prefix);
    if (p >= 0) {
      const from = p + prefix.length;
      return approximateRange(folded, from, Math.min(from + q.length, text.length));
    }
  }
  return { kind: "notFound" };
}

function approximateRange(folded: Folded, from: number, to: number): Anchor {
  // Without the spaces at either end.
  let a = from;
  let b = to;
  while (a < b && folded.text[a] === " ") a++;
  while (b > a && folded.text[b - 1] === " ") b--;
  return { kind: "approximate", start: folded.originalIndex(a), end: folded.originalEnd(b) };
}

function commonPrefix(a: string, b: string): number {
  let i = 0;
  while (i < a.length && i < b.length && a[i] === b[i]) i++;
  return i;
}

function commonSuffix(a: string, b: string): number {
  let i = 0;
  while (i < a.length && i < b.length && a[a.length - 1 - i] === b[b.length - 1 - i]) i++;
  return i;
}

/** The paragraphs of the reader joined into one text, as Android's ArticleText does. */
export const PARAGRAPH_SEPARATOR = "\n\n";
