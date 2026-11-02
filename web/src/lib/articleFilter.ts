import { sortTime, type Article, type Library } from "./library";

/**
 * Hide articles with words (#277), as on Android (`ArticleFilter.kt`): feed articles whose title has
 * one of the user's words are left out of the feed lists, their counts and their tag chips. Search
 * still finds them.
 *
 * The words are typed in Settings › Article list, separated by commas, semicolons or new lines. A
 * word matches as a whole word or phrase, ignoring case: "AI" hides "New AI rules" and "ai: a recap",
 * not "said" or "Thailand". Only the title counts.
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

const isLetterOrDigit = (c: string) => /[\p{L}\p{N}]/u.test(c);

/** [text] (in lower case) has [word] with no letter or digit right before or after it. */
function containsWord(text: string, word: string): boolean {
  let from = 0;
  for (;;) {
    const at = text.indexOf(word, from);
    if (at < 0) return false;
    const end = at + word.length;
    if ((at === 0 || !isLetterOrDigit(text[at - 1])) && (end === text.length || !isLetterOrDigit(text[end]))) return true;
    from = at + 1;
  }
}

/** Whether a title with one of [words] is hidden. */
export function hidesTitle(words: string[], title: string): boolean {
  if (words.length === 0 || !title) return false;
  const text = title.toLowerCase();
  return words.some((w) => containsWord(text, w));
}

/** This library without the articles the words hide: what the feed lists show. */
export function withoutHidden(library: Library, words: string[]): Library {
  if (words.length === 0) return library;
  return { ...library, articles: library.articles.filter((a) => !hidesTitle(words, a.title)) };
}

/**
 * What the setting's dialog shows while typing: how many of [articles] the words hide, and the
 * newest [limit] of them, so you see what would go before saving.
 */
export function hiddenPreview(articles: Article[], words: string[], limit = PREVIEW_LIMIT): { count: number; newest: Article[] } {
  if (words.length === 0) return { count: 0, newest: [] };
  const hidden = articles.filter((a) => hidesTitle(words, a.title));
  return { count: hidden.length, newest: [...hidden].sort((a, b) => sortTime(b) - sortTime(a)).slice(0, limit) };
}
