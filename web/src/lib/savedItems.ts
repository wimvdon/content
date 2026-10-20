/**
 * The articles saved in this browser before #106 move into the library's items once, the same as
 * the Android app's move-in.
 */

import { cleanTags, isWebLink, itemOf, keyOfLink, newItem, putItem } from "./items";
import type { Library } from "./library";
import type { LocalSavedData } from "./localSaved";

/**
 * The library after moving in the articles saved in this browser before #106 (IndexedDB "saved"):
 * each becomes the item of its link on the reading list, with its title, time, like, archive and
 * tags. Links that turn out to be the same keep the newest.
 */
export function migrateSaved(lib: Library, saved: LocalSavedData): Library {
  const names = new Map(saved.tags.map((t) => [t.id, t.name]));
  return [...saved.articles]
    .filter((a) => isWebLink(a.url))
    .sort((a, b) => a.time - b.time)
    .reduce((acc, a) => {
      const time = a.time * 1000;
      const key = keyOfLink(a.url);
      const base = (key ? itemOf(acc, key) : undefined) ?? newItem(acc, a.url, a.title !== a.url ? a.title : null, time);
      if (!base) return acc;
      return putItem(acc, {
        ...base,
        title: a.title.trim() && a.title !== a.url ? a.title : base.title,
        readingList: { state: a.archived ? "archived" : "toread", addedAt: time },
        liked: base.liked || a.liked,
        tags: cleanTags([...base.tags, ...a.tagIds.flatMap((id) => (names.has(id) ? [names.get(id)!] : []))]),
      });
    }, lib);
}
