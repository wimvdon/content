/**
 * The address Content gives an Instapaper article that has none of its own: a forwarded email, an
 * uploaded PDF (Instapaper's "private content", whose `url` is null), as on Android
 * (`InstapaperLinks.kt`). It is Instapaper's own page for the article, so it opens there, and a web
 * address like any other, so the article syncs and is kept like any other. Its text comes from
 * Instapaper's parser through the feed loader (`/instapaper/text`), not from that page.
 */
export function instapaperReadLink(bookmarkId: number): string {
  return `https://www.instapaper.com/read/${bookmarkId}`;
}

const READ = /^https:\/\/(?:www\.)?instapaper\.com\/read\/(\d+)\/?$/i;

/** The Instapaper article `url` stands for, when it is such an address; else null. */
export function instapaperBookmarkId(url: string | null | undefined): number | null {
  const match = url?.trim().match(READ);
  return match ? Number(match[1]) : null;
}
