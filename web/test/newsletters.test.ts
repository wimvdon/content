import { describe, expect, it } from "vitest";
import { addToReadingList, changeItem, itemOf, itemsOf, setReadingState } from "../src/lib/items";
import { emptyLibrary, type Library } from "../src/lib/library";
import { hasUnreadNewsletter, isNewsletter, markNewsletterRead, markNewslettersRead, undoMarkNewslettersRead, unreadNewsletterKeys } from "../src/lib/newsletters";
import { defaultSavedFilter, isPlainFilter, noteOf, removeSaved, savedItems, showsNewsletters, withoutChips } from "../src/lib/saved";
import { itemData, itemOfRow as rowItemOf, mergeItems } from "../src/lib/syncRows";
import { applySynced, NEWSLETTER_DOT, syncedSettings } from "../src/lib/syncSettings";
import { defaultSettings } from "../src/lib/app";

const KEY = "instapaper.com/read/42";

/** Saved with a newsletter (unread when `unread`) and an article with a web address. */
function library(unread = true): Library {
  let lib = addToReadingList(emptyLibrary(), "https://www.instapaper.com/read/42", "A newsletter", 2)[0];
  lib = addToReadingList(lib, "https://a.example/1", "An article", 1)[0];
  return changeItem(lib, KEY, (i) => ({ ...i, unread }));
}

describe("newsletters (newsletters.ts), as on Android", () => {
  it("a newsletter is an article with Instapaper's page as its address", () => {
    expect(isNewsletter("https://www.instapaper.com/read/42")).toBe(true);
    expect(isNewsletter("https://instapaper.com/read/7")).toBe(true);
    expect(isNewsletter("https://a.example/1")).toBe(false);
    expect(isNewsletter("https://www.instapaper.com/u")).toBe(false);
    expect(isNewsletter(null)).toBe(false);
  });

  it("marking read reads it, and nothing else", () => {
    const lib = library();
    expect(hasUnreadNewsletter(lib)).toBe(true);
    const read = markNewsletterRead(lib, KEY);
    expect(hasUnreadNewsletter(read)).toBe(false);
    expect(markNewsletterRead(read, KEY)).toBe(read);
    expect(markNewsletterRead(read, "nothing")).toBe(read);
  });

  it("archiving or removing it reads it, and back to read it stays read", () => {
    const archived = setReadingState(library(), KEY, "archived");
    expect(itemOf(archived, KEY)?.unread).toBe(false);
    expect(hasUnreadNewsletter(removeSaved(library(), KEY))).toBe(false);
    expect(itemOf(setReadingState(archived, KEY, "toread"), KEY)?.unread).toBe(false);
  });

  it("mark all as read takes the unread ones shown, and Undo brings them back", () => {
    const lib = library();
    const keys = unreadNewsletterKeys(savedItems(lib, defaultSavedFilter));
    expect(keys).toEqual([KEY]);
    const read = markNewslettersRead(lib, keys);
    expect(hasUnreadNewsletter(read)).toBe(false);
    expect(hasUnreadNewsletter(undoMarkNewslettersRead(read, keys))).toBe(true);
    // Archived meanwhile: Undo leaves it read.
    expect(hasUnreadNewsletter(undoMarkNewslettersRead(setReadingState(read, KEY, "archived"), keys))).toBe(false);
  });

  it("the Newsletters chip shows the newsletters, and only while there are any", () => {
    const lib = library(false);
    const chosen = { ...defaultSavedFilter, newslettersOnly: true };
    expect(savedItems(lib, chosen).map((i) => i.key)).toEqual([KEY]);
    expect(showsNewsletters(lib, defaultSavedFilter)).toBe(true);
    const none = removeSaved(lib, KEY);
    expect(showsNewsletters(none, defaultSavedFilter)).toBe(false);
    // Still chosen: it stays, so it can be let go.
    expect(showsNewsletters(none, chosen)).toBe(true);
    expect(isPlainFilter(chosen)).toBe(false);
    expect(isPlainFilter(withoutChips({ ...chosen, likedOnly: true, tags: ["x"] }))).toBe(true);
  });

  it("an unread newsletter's card says so", () => {
    expect(noteOf(itemOf(library(), KEY)!).unread).toBe(true);
    expect(noteOf(itemOf(library(), "a.example/1")!).unread).toBe(false);
  });

  it("unread syncs, and read on either device is read", () => {
    const item = itemOf(library(), KEY)!;
    const data = itemData(item);
    expect(data.unread).toBe(true);
    expect(rowItemOf(KEY, data, undefined)?.unread).toBe(true);
    // A row from an app before this has no "unread": read.
    const { unread: _, ...older } = data;
    expect(rowItemOf(KEY, older, undefined)?.unread).toBe(false);
    expect(mergeItems(item, { ...item, liked: true }, { ...item, unread: false }).unread).toBe(false);
    expect(mergeItems(item, { ...item, liked: true }, { ...item, note: "x" }).unread).toBe(true);
  });

  it("the stored library keeps it, and who shared an item", () => {
    const item = { ...itemOf(library(), KEY)!, sharedBy: { email: "a@b.c", name: "A", at: 3, waits: true, read: false } };
    const [back] = itemsOf([JSON.parse(JSON.stringify(item))]);
    expect(back.unread).toBe(true);
    expect(back.sharedBy).toEqual(item.sharedBy);
  });

  it("the dot setting syncs under articles.", () => {
    const values = syncedSettings({ ...defaultSettings, newsletterDot: true });
    expect(values[NEWSLETTER_DOT]).toBe("true");
    expect(NEWSLETTER_DOT).toBe("articles.newsletterDot");
    expect(applySynced(defaultSettings, values).newsletterDot).toBe(true);
    expect(applySynced({ ...defaultSettings, newsletterDot: true }, {}).newsletterDot).toBe(true);
  });
});
