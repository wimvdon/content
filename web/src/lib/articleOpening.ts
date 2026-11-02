import { isWebLink } from "./items";
import type { Article } from "./library";
import { isItemArticle } from "./saved";

/**
 * Always open the website, as on Android (`ArticleOpening`): the address a feed article opens
 * instead of the reader, in a new tab, or null for the reader (the setting off, a saved article,
 * or no web link).
 */
export function websiteFor(alwaysOpenWebsite: boolean, article: Article): string | null {
  const link = article.link?.trim();
  if (!alwaysOpenWebsite || !link || isItemArticle(article) || !isWebLink(link)) return null;
  return link;
}
