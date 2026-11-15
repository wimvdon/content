/**
 * Which sender a newsletter is from (newsletterSenders.ts), on its own so the library can use it:
 * the sender's website, or on a platform many newsletters send from the website and the name, or
 * else the name. The same as the Android app's NewsletterSenders.keyOf.
 */

import type { Article } from "./library";

/**
 * Websites many unrelated newsletters send from, or that anyone mails from: there a sender is the
 * website and the name. The feed loader keeps only the domain without its mail subdomain.
 */
export const SHARED_SITES = new Set([
  "substack.com", "beehiiv.com", "buttondown.email", "buttondown.com", "ghost.io", "convertkit.com", "kit.com",
  "mailchimp.com", "mcsv.net", "list-manage.com", "mailerlite.com", "sendinblue.com", "brevo.com", "mailjet.com",
  "sendgrid.net", "medium.com", "patreon.com", "tinyletter.com", "revue.email", "getrevue.co", "linkedin.com",
  "gmail.com", "googlemail.com", "outlook.com", "hotmail.com", "live.com", "icloud.com", "me.com", "yahoo.com",
  "proton.me", "protonmail.com", "gmx.com", "gmx.net",
]);

const SITE = /^https:\/\/([a-z0-9-]+(?:\.[a-z0-9-]+)+)\/?$/i;

/** Which sender `article` of a newsletter feed is from; null when it names none. */
export function senderKeyOf(article: Article): string | null {
  const name = article.author?.trim().replace(/\s+/g, " ").toLowerCase() || null;
  const host = SITE.exec(article.authorUrl?.trim() ?? "")?.[1]?.toLowerCase() ?? null;
  if (host !== null && !SHARED_SITES.has(host)) return host;
  if (name === null) return null;
  return host !== null ? `${host}/${name}` : `name:${name}`;
}

/** The website of sender `key`, for its icon; null for one known by name alone. */
export const siteOfSender = (key: string): string | null => (key.startsWith("name:") ? null : key.split("/")[0]);
