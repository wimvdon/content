import { touch, type CacheIndex } from "./fullArticle";
import { load, remove, save } from "./storage";

/**
 * Full articles kept in this browser (IndexedDB), so opening one again is instant, also after a
 * reload. Only a cache: anything that fails just means the article loads again. The version in
 * the keys lets a better extractor load articles again.
 */
const INDEX = "fullArticles-v3";
const PREFIX = "fullArticle-v4:";

const memory = new Map<string, string>();
let index: Promise<CacheIndex> | null = null;

function loadIndex(): Promise<CacheIndex> {
  index ??= load<CacheIndex>(INDEX).then((i) => i ?? {}, () => ({}));
  return index;
}

/** The article from this session, without waiting (null if it isn't in memory). */
export function cachedNow(link: string): string | null {
  return memory.get(link) ?? null;
}

export async function cached(link: string): Promise<string | null> {
  const known = memory.get(link) ?? (await load<string>(PREFIX + link).catch(() => undefined));
  if (!known) return null;
  memory.set(link, known);
  void used(link);
  return known;
}

export async function keep(link: string, html: string): Promise<void> {
  memory.set(link, html);
  await save(PREFIX + link, html).catch(() => undefined);
  await used(link);
}

async function used(link: string) {
  const result = touch(await loadIndex(), link, Date.now());
  index = Promise.resolve(result.index);
  await save(INDEX, result.index).catch(() => undefined);
  for (const old of result.removed) {
    memory.delete(old);
    await remove(PREFIX + old).catch(() => undefined);
  }
}
