/**
 * Topics: one way to group feeds and saved articles, the same as the Android app's Topics.kt. A
 * topic is a Folder (a name and an icon, in the user's order): a feed is in one topic at most
 * (`folderId`), a saved article has any number of them, as its tags (by name, case ignored). The
 * sync file keeps folders and tags as they were; these rules keep the two in step.
 */
import { changeItem, cleanTags, feedArticleFor, renameTag, type Item } from "./items";
import { feedFoldersToTags, feedTags, removeFolder, type Folder, type Library } from "./library";

const key = (name: string) => name.trim().toLowerCase();

/** The id of the topic made for a tag that has none yet: the same on every device, so two devices make one. */
export const topicIdFor = (name: string) => "topic:" + key(name);

/** The default icon of a topic made for a tag, as on Android (IconCatalog.DEFAULT). */
export const DEFAULT_TOPIC_ICON = "folder";

/**
 * Every tag on a saved article has its topic, and no two topics share a name: a tag without one gets
 * a topic of its own at the end; topics with the same name become the first of them, with the feeds
 * of all. The same library when nothing changes. Applied after every change.
 */
export function topicsForTags(lib: Library): Library {
  const first = new Map<string, Folder>();
  const moved = new Map<string, string>();
  for (const topic of lib.folders) {
    const there = first.get(key(topic.title));
    if (there) moved.set(topic.id, there.id);
    else first.set(key(topic.title), topic);
  }
  const missing: string[] = [];
  const seen = new Set<string>();
  for (const item of lib.items)
    for (const tag of item.tags) {
      const name = tag.trim();
      if (!name || first.has(key(name)) || seen.has(key(name))) continue;
      seen.add(key(name));
      missing.push(name);
    }
  if (missing.length === 0 && moved.size === 0) return lib;
  const topics = lib.folders.filter((f) => !moved.has(f.id));
  const ids = new Set(topics.map((f) => f.id));
  const added = missing.flatMap((name) => {
    const id = topicIdFor(name);
    if (ids.has(id)) return [];
    ids.add(id);
    return [{ id, title: name, icon: DEFAULT_TOPIC_ICON }];
  });
  const feeds = moved.size === 0 ? lib.feeds : lib.feeds.map((f) => (f.folderId && moved.has(f.folderId) ? { ...f, folderId: moved.get(f.folderId)! } : f));
  return { ...lib, folders: [...topics, ...added], feeds };
}

/** The topics in a new order, by id (#314: dragging their chips); topics not named keep their place at the end. As Android's reorderTopics. */
export function reorderTopics(lib: Library, ids: string[]): Library {
  const byId = new Map(lib.folders.map((f) => [f.id, f]));
  const ordered = [...new Set(ids)].flatMap((id) => (byId.has(id) ? [byId.get(id)!] : []));
  const rest = lib.folders.filter((f) => !ordered.includes(f));
  const result = [...ordered, ...rest];
  return result.every((f, i) => f === lib.folders[i]) ? lib : { ...lib, folders: result };
}

/** `list` with the entry at `from` moved to `to`; the same list when either is out of range. As Android's ChipOrder.moved. */
export function moved<T>(list: T[], from: number, to: number): T[] {
  if (from < 0 || to < 0 || from >= list.length || to >= list.length || from === to) return list;
  const out = [...list];
  out.splice(to, 0, ...out.splice(from, 1));
  return out;
}

/** The topic with this name (case ignored), or undefined. */
export const topicNamed = (lib: Library, name: string) => lib.folders.find((f) => key(f.title) === key(name));

/** A new topic at the end, or the library as it is when the name is blank or taken (case ignored). */
export function addTopic(lib: Library, id: string, title: string, icon: string = DEFAULT_TOPIC_ICON): Library {
  if (!title.trim() || topicNamed(lib, title)) return lib;
  return { ...lib, folders: [...lib.folders, { id, title: title.trim(), icon }] };
}


/**
 * Renames a topic and sets its icon, on its feeds and on every saved article that has it. Renaming
 * it to another topic's name joins the two: the other one stays (with its icon) and gets this one's
 * feeds and articles.
 */
export function renameTopic(lib: Library, id: string, title: string, icon: string): Library {
  const topic = lib.folders.find((f) => f.id === id);
  const clean = title.trim();
  if (!topic || !clean) return lib;
  const other = lib.folders.find((f) => f.id !== id && key(f.title) === key(clean));
  if (!other) {
    const updated = { ...lib, folders: lib.folders.map((f) => (f.id === id ? { ...f, title: clean, icon } : f)) };
    return feedFoldersToTags(renameTag(updated, topic.title, clean));
  }
  const joined = {
    ...lib,
    folders: lib.folders.filter((f) => f.id !== id),
    feeds: lib.feeds.map((f) => (f.folderId === id ? { ...f, folderId: other.id } : f)),
  };
  return feedFoldersToTags(renameTag(joined, topic.title, other.title));
}

/** Removes a topic: its feeds stay, without a topic, and its saved articles stay, without it. */
export function removeTopic(lib: Library, id: string): Library {
  const topic = lib.folders.find((f) => f.id === id);
  if (!topic) return lib;
  const out = removeFolder(lib, id);
  const has = (i: Item) => i.tags.some((t) => key(t) === key(topic.title));
  return feedFoldersToTags({ ...out, items: out.items.map((i) => (has(i) ? { ...i, tags: i.tags.filter((t) => key(t) !== key(topic.title)) } : i)) });
}

/** How many feeds and how many saved articles (To read and Archive) each topic has, by id. */
export function topicCounts(lib: Library): Map<string, { feeds: number; saved: number; toRead: number }> {
  return new Map(
    lib.folders.map((t) => [
      t.id,
      {
        feeds: lib.feeds.filter((f) => f.folderId === t.id).length,
        saved: lib.items.filter((i) => i.readingList !== null && i.tags.some((x) => key(x) === key(t.title))).length,
        toRead: lib.items.filter((i) => i.readingList?.state === "toread" && i.tags.some((x) => key(x) === key(t.title))).length,
      },
    ]),
  );
}

/** Names (tags) in the order of their topics; a name without a topic comes after them, alphabetically. */
export function inTopicOrder(lib: Library, names: string[]): string[] {
  const position = new Map<string, number>();
  lib.folders.forEach((f, i) => {
    if (!position.has(key(f.title))) position.set(key(f.title), i);
  });
  const rank = (n: string) => position.get(key(n)) ?? Number.MAX_SAFE_INTEGER;
  return [...names].sort((a, b) => rank(a) - rank(b) || a.toLowerCase().localeCompare(b.toLowerCase()));
}

/** Every topic's name, in the user's order: what the save and Organise sheets offer. */
export const topicNames = (lib: Library) => lib.folders.map((f) => f.title);

/** The icon of each topic, by its name in lower case. */
export const topicIcons = (lib: Library) => new Map(lib.folders.map((f) => [key(f.title), f.icon]));

/**
 * A saved feed article gets its feed's topic (as Android's tagWithFeedFolder), in the spelling of
 * the topic. Nothing for a link that isn't a feed article, or a feed without a topic.
 */
export function tagWithFeedTopic(lib: Library, itemKey: string): Library {
  const item = lib.items.find((i) => i.key === itemKey);
  const article = item && ((item.feedUrl && item.articleId && lib.articles.find((a) => a.feedUrl === item.feedUrl && a.id === item.articleId)) || feedArticleFor(lib, item.url));
  const feed = article && lib.feeds.find((f) => f.url === article.feedUrl);
  const topic = feed?.folderId ? lib.folders.find((f) => f.id === feed.folderId) : undefined;
  if (!item || !topic?.title.trim() || item.tags.some((t) => key(t) === key(topic.title))) return lib;
  return changeItem(lib, itemKey, (i) => ({ ...i, tags: cleanTags([...i.tags, topic.title.trim()]) }));
}

/** Merges topic `fromId` into `intoId`: the other one stays, with its name and icon, and gets this one's feeds and saved articles. */
export function mergeTopic(lib: Library, fromId: string, intoId: string): Library {
  const into = lib.folders.find((f) => f.id === intoId);
  if (!into || fromId === intoId || !lib.folders.some((f) => f.id === fromId)) return lib;
  return renameTopic(lib, fromId, into.title, into.icon);
}


/**
 * Feeds' topic chips: the topics with articles in the state shown (unread ones with Unread), in the
 * user's order; one with nothing to show is left out, except the one chosen. Like Android's feedChips.
 */
export function feedChips(lib: Library, unreadOnly: boolean, chosen: string | null): string[] {
  const byUrl = new Map(lib.feeds.map((f) => [f.url, f]));
  return feedTags(lib).filter(
    (tag) =>
      (chosen !== null && key(tag) === key(chosen)) ||
      lib.articles.some((a) => (!unreadOnly || !a.read) && (byUrl.get(a.feedUrl)?.tags ?? []).some((t) => key(t) === key(tag))),
  );
}
