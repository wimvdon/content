/**
 * + › Subscribe to newsletter: two ways to get newsletters into Content, each a page of steps. As a
 * feed, through Kill the Newsletter (an email address whose mail becomes an Atom feed, added like any
 * feed); or straight into To read, through the user's own Save to Instapaper address (the emails
 * come into Saved through Instapaper, newsletters.ts). Instapaper's API doesn't give that address,
 * so the page links to Instapaper's Email page, which shows it once signed in. Content itself never
 * contacts either: the links open in a new tab. The same as the Android app's NewsletterSubscribe.kt.
 */

/** Where a Kill the Newsletter inbox is made. */
export const KILL_THE_NEWSLETTER = "https://kill-the-newsletter.com/";

/** Instapaper's Email page: signed in, it shows the Save to Instapaper address (and the folders' own). */
export const INSTAPAPER_EMAIL = "https://www.instapaper.com/docs/save/email";

export type NewsletterWay = "feed" | "instapaper";

/** What the Instapaper page offers: connect first, or find the address and choose the dot. */
export type InstapaperStep = "connect" | "findAddress";

export const instapaperStep = (connected: boolean): InstapaperStep => (connected ? "findAddress" : "connect");

/** The site a way's page links to. */
export const newsletterSite = (way: NewsletterWay): string => (way === "feed" ? KILL_THE_NEWSLETTER : INSTAPAPER_EMAIL);
