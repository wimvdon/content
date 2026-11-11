/**
 * + › Subscribe to newsletter: two ways to get newsletters into Content, each a page. Your own
 * address: the account's feed loader makes an email address for a tag whose newsletters arrive as a
 * feed under that tag (newsletterAddresses.ts, docs/newsletters.md); it needs a Content account. Or
 * straight into To read, through the user's own Save to Instapaper address (the emails come into
 * Saved through Instapaper, newsletters.ts). Instapaper's API doesn't give that address, so the page
 * links to Instapaper's Email page, which shows it once signed in. The same as the Android app's
 * NewsletterSubscribe.kt.
 */

/** Instapaper's Email page: signed in, it shows the Save to Instapaper address (and the folders' own). */
export const INSTAPAPER_EMAIL = "https://www.instapaper.com/docs/save/email";

export type NewsletterWay = "own" | "instapaper";

/** What the Instapaper page offers: connect first, or find the address and choose the setting. */
export type InstapaperStep = "connect" | "findAddress";

export const instapaperStep = (connected: boolean): InstapaperStep => (connected ? "findAddress" : "connect");

/** What the own-address page offers: sign in to a Content account first, or make addresses. */
export type OwnStep = "signIn" | "addresses";

export const ownStep = (signedIn: boolean): OwnStep => (signedIn ? "addresses" : "signIn");

/** Whether a tag can be given to a new address (the feed loader takes up to 100 characters). */
export const canCreate = (tag: string, busy: boolean): boolean => !busy && tag.trim() !== "" && tag.trim().length <= 100;
