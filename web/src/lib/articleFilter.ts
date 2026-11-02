import { sortTime, type Article, type Library } from "./library";

/**
 * Hide articles with words (#277), as on Android (`ArticleFilter.kt`): feed articles that match one
 * of the user's entries are left out of the feed lists, their counts and their tag chips. Search
 * still finds them.
 *
 * The entries are added one by one in Settings › Article list, as chips (2026-11-02), and stored one
 * per line (older settings may still use commas or semicolons, which still separate). An
 * entry of several words matches when every word is there, in any order, in the article's title or
 * its feed's name: "WIRED coupon code" hides WIRED's articles with "coupon code(s)" in their title,
 * "wired" alone all of WIRED. A word matches as a whole word, ignoring case: "AI" hides "New AI
 * rules", not "said" or "Thailand".
 */

/** How many hidden articles the setting's preview lists. */
export const PREVIEW_LIMIT = 5;

/** The words of [text], trimmed, without empty ones or duplicates (case ignored), in lower case. */
export function hiddenWords(text: string): string[] {
  const out: string[] = [];
  for (const part of text.split(/[,;\n\r]/)) {
    const word = part.trim().replace(/\s+/g, " ").toLowerCase();
    if (word && !out.includes(word)) out.push(word);
  }
  return out;
}

/**
 * The entries after adding [typed] (the dialog's field, 2026-11-02): one word or group of words, or
 * several separated by commas, each kept once.
 */
export function withEntry(entries: string[], typed: string): string[] {
  return hiddenWords([...entries, typed].join("\n"));
}

/** The entries without [entry] (its chip's X). */
export function withoutEntry(entries: string[], entry: string): string[] {
  return entries.filter((e) => e !== entry);
}

/** How the entries are stored and synced: one per line, which [hiddenWords] reads back. */
export function entriesText(entries: string[]): string {
  return entries.join("\n");
}

const isLetterOrDigit = (c: string) => /[\p{L}\p{N}]/u.test(c);

/** What may follow a word and still count as it: nothing, a plural or a possessive ("code" finds "Codes"). */
const ENDINGS = ["", "s", "es", "'s", "’s"];

/** [text] (in lower case) has [word] (or its plural or possessive) with no letter or digit right before or after it. */
function containsWord(text: string, word: string): boolean {
  let from = 0;
  for (;;) {
    const at = text.indexOf(word, from);
    if (at < 0) return false;
    const end = at + word.length;
    const endOk = ENDINGS.some((e) => text.startsWith(e, end) && (end + e.length === text.length || !isLetterOrDigit(text[end + e.length])));
    if ((at === 0 || !isLetterOrDigit(text[at - 1])) && endOk) return true;
    from = at + 1;
  }
}

/** Whether a title (of a feed named [source]) matches an entry: every one of its words is in the one or the other. */
export function hidesTitle(words: string[], title: string, source: string | null = null): boolean {
  if (words.length === 0) return false;
  const text = title.toLowerCase();
  const from = (source ?? "").toLowerCase();
  if (!text && !from) return false;
  return words.some((entry) => entry.split(" ").filter(Boolean).every((w) => containsWord(text, w) || containsWord(from, w)));
}

/** The name of each feed by its address: the words are looked for in it too. */
const feedNames = (library: Library) => new Map(library.feeds.map((f) => [f.url, f.title]));

/** This library without the articles the words hide: what the feed lists show. */
export function withoutHidden(library: Library, words: string[]): Library {
  if (words.length === 0) return library;
  const names = feedNames(library);
  return { ...library, articles: library.articles.filter((a) => !hidesTitle(words, a.title, names.get(a.feedUrl) ?? null)) };
}

/**
 * What the setting's dialog shows while typing: how many of [articles] the words hide, and the
 * newest [limit] of them, so you see what would go before saving.
 */
export function hiddenPreview(articles: Article[], words: string[], limit = PREVIEW_LIMIT, sourceOf: (a: Article) => string | null = () => null): { count: number; newest: Article[] } {
  if (words.length === 0) return { count: 0, newest: [] };
  const hidden = articles.filter((a) => hidesTitle(words, a.title, sourceOf(a)));
  return { count: hidden.length, newest: [...hidden].sort((a, b) => sortTime(b) - sortTime(a)).slice(0, limit) };
}
