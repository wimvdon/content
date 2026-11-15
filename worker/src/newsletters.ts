/**
 * Newsletter addresses: an email address per tag (and one without a tag) that turns the newsletters
 * sent to it into a feed, Kill the Newsletter's idea on Cloudflare (docs/newsletters.md). The apps ask
 * for each tag's address themselves (2026-11-12): one per tag id, never two, so asking again gives the
 * same one. A tag is known here only by a hash of its id (SHA-256, hex), so its name never comes here.
 *
 * Mail for the domain reaches this Worker through Cloudflare Email Routing: one rule sends
 * NEWSLETTER_ADDRESS (e.g. content@in.wim.to) here, and with subaddressing on, content+<code>@… arrives
 * too, with its code. A dedicated domain with a catch-all works as well (<code>@…). Each code is an
 * inbox: a Durable Object of the Account class named "newsletter:<code>" (no class of its own, so no
 * migration), holding its owner (the account) and its newest emails, encrypted like the
 * account's rows. Its feed is an Atom feed at /newsletters/<code>/<token>, the token derived from the
 * code with ACCOUNT_KEY, so knowing the address isn't enough to read it. The apps subscribe to that
 * feed like any other.
 *
 * Kept small on purpose (the Workers Free plan gives 10 ms of CPU per request): receiving only reads
 * the email and stores its HTML; attachments are dropped. At most KEEP emails per address, none older
 * than KEEP_DAYS. Nothing is logged.
 *
 *  - GET    /account/newsletters              this account's addresses: [{code, tagHash, address, feedUrl, createdAt}]
 *  - POST   /account/newsletters {"tagHash"}  the address of that tag (null: without a tag), made the first
 *                                             time (201), else the one it has (200); at most MAX_ADDRESSES
 *  - POST   /account/newsletters/new {"code"} a new address for that tag (2026-11-15): mail to the one it had
 *                                             still arrives for OLD_ADDRESS_DAYS, then is refused; same feed
 *  - DELETE /account/newsletters?code=<code>  the address stops taking mail; its emails are deleted
 *  - GET    /newsletters/<code>/<token>       the feed (no sign-in: the token is the key)
 *  - GET    /newsletters/<code>/e/<id>/<etok> one email as a page (the entries' link; it opens only that email)
 *  - GET    /newsletters/<code>/<token>/<id>  one email as a page (the entries' link before 2026-11-15)
 *  - POST   /newsletters/<code>/<token>/unsubscribe {"id"}  unsubscribes from that email's sender, or says
 *                                             how: {done} | {open: url} | {mailto} | {none} (2026-11-15)
 */

import PostalMime from "postal-mime";
import { allowedOrigins, corsHeaders, derive, plain, type Storage } from "./account";
import type { Env } from "./index";

/** Emails larger than this (as sent, with attachments) are refused. */
export const MAX_EMAIL_BYTES = 4 * 1024 * 1024;
/** The most HTML kept of one email; longer ones are cut. */
export const MAX_HTML_CHARS = 512 * 1024;
/** Emails kept per address, and for how long. */
export const KEEP = 100;
export const KEEP_DAYS = 90;
/** Entries in the feed, newest first. */
export const FEED_ENTRIES = 50;
/** Addresses per account: one per tag, and one without a tag. */
export const MAX_ADDRESSES = 200;
/** How long an address keeps taking mail after its tag got a new one (POST /account/newsletters/new). */
export const OLD_ADDRESS_DAYS = 14;
/** Old addresses still taking mail per tag: getting more new ones waits until one has stopped. */
export const MAX_OLD_ADDRESSES = 5;
/** A tag as the apps send it: the SHA-256 of its id, in hex. */
const TAG_HASH_PATTERN = /^[0-9a-f]{64}$/;

const DAY_MS = 24 * 60 * 60 * 1000;
const CODE_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";
const CODE_PATTERN = /^[a-z0-9]{12}$/;

/** One email as kept: its id, when it arrived, who sent it (and their website), its subject and its HTML. */
export interface NewsletterEmail {
  id: string;
  at: number;
  from: string;
  /** The sender's website, from their address's domain ("https://example.com"); missing in emails kept before 2026-11-15. */
  site?: string;
  /** True once an email kept before 2026-11-15 has had its website looked for ([guessSite]), found or not. */
  siteGuessed?: boolean;
  subject: string;
  html: string;
  /** How to unsubscribe, from its List-Unsubscribe headers ([unsubscribeOf]); missing in emails kept before 2026-11-15 or without them. */
  unsubscribe?: Unsubscribe;
}

/**
 * An email's List-Unsubscribe (RFC 2369) and List-Unsubscribe-Post (RFC 8058): its first https
 * address and its first mailto: link; [oneClick] when a POST to that https address unsubscribes
 * without a page in between.
 */
export interface Unsubscribe {
  https?: string;
  mailto?: string;
  oneClick: boolean;
}

/** Emails kept before 2026-11-15 given their sender's website per feed load, so a load stays within its CPU time. */
export const BACKFILL_PER_LOAD = 10;

/**
 * An address as its account lists it: the tag it belongs to (null: the one without a tag). [code]
 * names its inbox and its feed, which never change; mail arrives at [receiving] (2026-11-15, Get a
 * new address), or at [code] while it has never had a new one. The codes it had before keep
 * taking mail into the same inbox until their [OldCode.until] ([OLD_ADDRESS_DAYS]).
 */
export interface NewsletterAddress {
  code: string;
  tagHash: string | null;
  createdAt: number;
  receiving?: string;
  old?: OldCode[];
}

/** A code an address had: it takes mail into its address's inbox until [until] (milliseconds), then refuses it. */
export interface OldCode {
  code: string;
  until: number;
}

/** The code mail arrives at now. */
export const receivingOf = (a: NewsletterAddress) => a.receiving ?? a.code;

/** Every object an address has: its inbox, and the codes that send mail into it (now or before). */
const codesOf = (a: NewsletterAddress) => [...new Set([a.code, receivingOf(a), ...(a.old ?? []).map((o) => o.code)])];

/** Until when the address's old codes still take mail (the latest of them), or null when none does any more. */
export function oldUntil(a: NewsletterAddress, now: number): number | null {
  const until = Math.max(0, ...(a.old ?? []).map((o) => o.until));
  return until > now ? until : null;
}

/** An address made before 2026-11-12, by name instead of per tag: no longer listed, removed on the next POST. */
const isLegacy = (a: NewsletterAddress) => (a.tagHash as string | null | undefined) === undefined;

// Codes, addresses and feed links.

/** A new random code: 12 letters and digits, without the ones easy to mix up (l, o, 0, 1). */
export function newCode(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(12));
  return [...bytes].map((b) => CODE_ALPHABET[b % CODE_ALPHABET.length]).join("");
}

/**
 * The code an email was sent to: what follows the "+" (news+<code>@…, subaddressing), or else the
 * whole local part (<code>@…, a catch-all on a domain of its own). Null when it isn't one.
 */
export function codeOf(recipient: string): string | null {
  const local = recipient.trim().toLowerCase().replace(/^<|>$/g, "").split("@")[0] ?? "";
  const code = local.includes("+") ? local.slice(local.indexOf("+") + 1) : local;
  return CODE_PATTERN.test(code) ? code : null;
}

/** The address for a code, from NEWSLETTER_ADDRESS (news@in.example → news+<code>@in.example); null when not set up. */
export function addressOf(base: string | undefined, code: string): string | null {
  const match = base?.trim().match(/^([^@\s+]+)@([^@\s]+\.[^@\s]+)$/);
  return match ? `${match[1]}+${code}@${match[2]}` : null;
}

/** The feed's secret part for a code: who has the address can send to it, only who has this can read it. */
export async function feedToken(masterKey: string, code: string): Promise<string> {
  const key = await crypto.subtle.importKey("raw", await derive(masterKey, "newsletter-feed", 32), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const mac = new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(code)));
  return [...mac.slice(0, 16)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export const feedUrlOf = (origin: string, code: string, token: string) => `${origin}/newsletters/${code}/${token}`;

/**
 * One email's own secret part: its link opens that email only, never the feed. Before 2026-11-15
 * an email's link was the feed's address with the email's id, so sharing one newsletter (Share,
 * Save to Instapaper, Report a problem) handed out every newsletter of its tag.
 */
export async function emailToken(masterKey: string, code: string, id: string): Promise<string> {
  const key = await crypto.subtle.importKey("raw", await derive(masterKey, "newsletter-email", 32), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const mac = new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(`${code}/${id}`)));
  return [...mac.slice(0, 16)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** One email's page: /newsletters/<code>/e/<id>/<emailToken>. */
export const emailUrlOf = (origin: string, code: string, id: string, token: string) => `${origin}/newsletters/${code}/e/${id}/${token}`;

/** The tag as sent: null (without a tag), a tag's hash, or undefined when it is neither. */
export function cleanTagHash(value: unknown): string | null | undefined {
  if (value === null) return null;
  return typeof value === "string" && TAG_HASH_PATTERN.test(value) ? value : undefined;
}

// One address's inbox, without anything Cloudflare-specific (tested on its own).

interface SealedEmail {
  iv: Uint8Array;
  data: Uint8Array;
}

export class NewsletterInbox {
  private key: CryptoKey | null = null;

  constructor(
    private readonly storage: Storage,
    private readonly masterKey: string,
    private readonly code: string,
    private readonly clock: () => number = Date.now,
  ) {}

  private async cryptoKey(): Promise<CryptoKey> {
    this.key ??= await crypto.subtle.importKey("raw", await derive(this.masterKey, `newsletter:${this.code}`, 32), "AES-GCM", false, ["encrypt", "decrypt"]);
    return this.key;
  }

  /** Makes this the inbox of [owner]; false when the code is already taken. */
  async create(owner: string): Promise<boolean> {
    if (await this.storage.get<string>("owner")) return false;
    await this.storage.put("owner", owner);
    return true;
  }

  owner(): Promise<string | undefined> {
    return this.storage.get<string>("owner");
  }

  /**
   * Makes this code send its mail to the inbox [inbox] of [owner] (a tag's new address,
   * 2026-11-15): it keeps no mail itself. False when the code is already taken.
   */
  async createForward(owner: string, inbox: string): Promise<boolean> {
    if (!(await this.create(owner))) return false;
    await this.storage.put("forward", inbox);
    return true;
  }

  /** The inbox this code sends its mail to; undefined when it is an inbox itself. */
  forward(): Promise<string | undefined> {
    return this.storage.get<string>("forward");
  }

  /** This code takes mail until [until] (milliseconds), then refuses it: its tag has a new address. An inbox's emails and feed stay. */
  retire(until: number): Promise<void> {
    return this.storage.put("until", until);
  }

  /** Whether this code no longer takes mail (its tag's new address does). */
  async stopped(): Promise<boolean> {
    const until = await this.storage.get<number>("until");
    return until !== undefined && this.clock() > until;
  }

  /**
   * Keeps an email that arrived ([raw], as sent); false when this address doesn't exist, has
   * stopped taking mail, or only sends its mail on. [forwarded]: it came through a code that sends
   * its mail here ([forward]), so it is kept also when this inbox's own code has stopped.
   */
  async receive(raw: ArrayBuffer | Uint8Array, forwarded = false): Promise<boolean> {
    if (!(await this.owner()) || (await this.forward())) return false;
    if (!forwarded && (await this.stopped())) return false;
    const email = await emailOf(raw, this.clock());
    const sealed = await this.seal(email);
    await this.storage.put(`m:${String(email.at).padStart(15, "0")}:${email.id}`, sealed);
    await this.prune();
    return true;
  }

  /** The newest [limit] emails, newest first. */
  async emails(limit = FEED_ENTRIES): Promise<NewsletterEmail[]> {
    const keys = [...(await this.storage.list<SealedEmail>({ prefix: "m:" })).entries()].sort(([a], [b]) => (a < b ? 1 : -1)).slice(0, limit);
    const out: NewsletterEmail[] = [];
    let backfilled = 0;
    for (const [key, sealed] of keys) {
      let email = await this.open(sealed);
      if (!email) continue;
      // Kept before senders had websites: look for it once, and keep what was found.
      if (!email.site && !email.siteGuessed && backfilled < BACKFILL_PER_LOAD) {
        backfilled++;
        const site = guessSite(email);
        email = { ...email, ...(site ? { site } : {}), siteGuessed: true };
        await this.storage.put(key, await this.seal(email));
      }
      out.push(email);
    }
    return out;
  }

  async email(id: string): Promise<NewsletterEmail | null> {
    if (!/^[0-9a-f]{16}$/.test(id)) return null;
    for (const [key, sealed] of await this.storage.list<SealedEmail>({ prefix: "m:" })) {
      if (key.endsWith(`:${id}`)) return this.open(sealed);
    }
    return null;
  }

  /** Forgets the address and its emails. */
  deleteAll(): Promise<void> {
    return this.storage.deleteAll();
  }

  /** At most KEEP emails, none older than KEEP_DAYS. */
  private async prune(): Promise<void> {
    const keys = [...(await this.storage.list<SealedEmail>({ prefix: "m:" })).keys()].sort();
    const oldest = this.clock() - KEEP_DAYS * DAY_MS;
    const tooMany = Math.max(0, keys.length - KEEP);
    for (const [i, key] of keys.entries()) {
      if (i < tooMany || Number(key.slice(2, 17)) < oldest) await this.storage.delete(key);
    }
  }

  private async seal(email: NewsletterEmail): Promise<SealedEmail> {
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const data = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv }, await this.cryptoKey(), new TextEncoder().encode(JSON.stringify(email))));
    return { iv, data };
  }

  private async open(sealed: SealedEmail): Promise<NewsletterEmail | null> {
    try {
      const plainText = await crypto.subtle.decrypt({ name: "AES-GCM", iv: sealed.iv }, await this.cryptoKey(), sealed.data);
      return JSON.parse(new TextDecoder().decode(plainText)) as NewsletterEmail;
    } catch {
      return null;
    }
  }
}

/** What is kept of an email: its HTML (or its text as HTML), cut at MAX_HTML_CHARS; no attachments. */
export async function emailOf(raw: ArrayBuffer | Uint8Array, now: number): Promise<NewsletterEmail> {
  const parsed = await PostalMime.parse(raw, { attachmentEncoding: "base64" });
  const from = parsed.from?.name?.trim() || parsed.from?.address?.trim() || "";
  const site = senderSite(parsed.from?.address);
  const html = parsed.html?.trim() ? parsed.html : textAsHtml(parsed.text ?? "");
  const id = [...crypto.getRandomValues(new Uint8Array(8))].map((b) => b.toString(16).padStart(2, "0")).join("");
  const header = (name: string) => parsed.headers?.find((h) => h.key === name)?.value;
  const unsubscribe = unsubscribeOf(header("list-unsubscribe"), header("list-unsubscribe-post"));
  return {
    id,
    at: now,
    from,
    ...(site ? { site } : {}),
    subject: parsed.subject?.trim() ?? "",
    html: html.length > MAX_HTML_CHARS ? html.slice(0, MAX_HTML_CHARS) : html,
    ...(unsubscribe ? { unsubscribe } : {}),
  };
}

/** The longest unsubscribe address kept; longer ones are left out. */
const MAX_UNSUBSCRIBE_CHARS = 2048;

/**
 * How an email says to unsubscribe, from its List-Unsubscribe header (`<https://…>, <mailto:…>`)
 * and List-Unsubscribe-Post (`List-Unsubscribe=One-Click`): the first https address, the first
 * mailto: link, and whether one-click works (it needs the https address). Null when neither is there.
 */
export function unsubscribeOf(list: string | null | undefined, post: string | null | undefined): Unsubscribe | null {
  let https: string | undefined;
  let mailto: string | undefined;
  for (const match of (list ?? "").matchAll(/<([^>]*)>/g)) {
    const value = match[1].replace(/\s+/g, "");
    if (!value || value.length > MAX_UNSUBSCRIBE_CHARS) continue;
    if (!https && /^https:\/\//i.test(value)) https = value;
    else if (!mailto && /^mailto:[^@\s]+@[^@\s]+/i.test(value)) mailto = value;
  }
  if (!https && !mailto) return null;
  const oneClick = !!https && /(^|[\s;,])List-Unsubscribe\s*=\s*One-Click(\s|;|,|$)/i.test(post ?? "");
  return { ...(https ? { https } : {}), ...(mailto ? { mailto } : {}), oneClick };
}

/**
 * Words for "unsubscribe" in a link's text or address: English, Dutch, French, German, Spanish
 * and Italian, as newsletters write them.
 */
const UNSUBSCRIBE_WORDS =
  /unsubscri|uitschrijv|schrijf (je |u )?uit|afmeld|meld (je |u )?af|d[ée]sabonn|d[ée]sinscri|d[ée]sincri|abbestell|abmeld|darse de baja|date de baja|dar de baja|baja de (la )?suscripci|cancelar (la |tu |su )?suscripci|annulla(re)? (l'|la )?iscrizione|disiscri|cancella(re)? (l'|la )?iscrizione/i;

const decodeEntities = (text: string) =>
  text
    .replace(/&nbsp;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/&eacute;/gi, "é")
    .replace(/&#(\d+);/g, (_, n: string) => String.fromCodePoint(Number(n)))
    .replace(/&#x([0-9a-f]+);/gi, (_, n: string) => String.fromCodePoint(parseInt(n, 16)));

/**
 * An unsubscribe link in an email's HTML, for emails without List-Unsubscribe: the first link
 * whose text or address says unsubscribe in one of [UNSUBSCRIBE_WORDS]' languages, as an https
 * address on a public host ([isPublicHttps]). Null when there is none.
 */
export function unsubscribeLinkOf(html: string, isPublicHttps: (url: string) => string | null): string | null {
  for (const match of html.matchAll(/<a\b([^>]*)>([\s\S]*?)<\/a\s*>/gi)) {
    const href = /\bhref\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/i.exec(match[1]);
    const address = decodeEntities((href?.[1] ?? href?.[2] ?? href?.[3] ?? "").trim());
    if (!address) continue;
    const text = decodeEntities(match[2].replace(/<[^>]*>/g, " ")).replace(/\s+/g, " ");
    let readable = address;
    try {
      readable = decodeURIComponent(address);
    } catch {
      // Not encoded after all: as it is.
    }
    if (!UNSUBSCRIBE_WORDS.test(text) && !UNSUBSCRIBE_WORDS.test(readable)) continue;
    const checked = isPublicHttps(address);
    if (checked) return checked;
  }
  return null;
}

/** What the unsubscribe route answers the apps ([unsubscribeAnswer]). */
export type UnsubscribeAnswer = { done: true } | { open: string } | { mailto: string } | { none: true };

/** How long the one-click POST to a sender may take. */
export const UNSUBSCRIBE_TIMEOUT_MS = 10_000;

type Fetcher = (input: string, init?: RequestInit) => Promise<Response>;

/**
 * Unsubscribes from [email], or says how: with one-click, a POST of `List-Unsubscribe=One-Click`
 * to the sender's https address (on a public host only, no redirects followed, within
 * UNSUBSCRIBE_TIMEOUT_MS) and {done} when it answers 2xx; else that address to open, else the
 * mailto: link, else an unsubscribe link in the HTML, else {none}. The one POST is all this
 * contacts, and only when the user taps Unsubscribe.
 */
export async function unsubscribeAnswer(email: Pick<NewsletterEmail, "html" | "unsubscribe">, fetcher: Fetcher, isPublicHttps: (url: string) => string | null): Promise<UnsubscribeAnswer> {
  const how = email.unsubscribe;
  const https = how?.https ? isPublicHttps(how.https) : null;
  if (https && how?.oneClick) {
    try {
      const answer = await fetcher(https, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded", "User-Agent": USER_AGENT_UNSUBSCRIBE },
        body: "List-Unsubscribe=One-Click",
        redirect: "manual",
        signal: AbortSignal.timeout(UNSUBSCRIBE_TIMEOUT_MS),
      });
      await answer.body?.cancel().catch(() => {});
      if (answer.status >= 200 && answer.status < 300) return { done: true };
    } catch {
      // Unreachable or too slow: the address can still be opened.
    }
  }
  if (https) return { open: https };
  if (how?.mailto && /^mailto:[^@\s]+@[^@\s]+/i.test(how.mailto)) return { mailto: how.mailto };
  const link = unsubscribeLinkOf(email.html, isPublicHttps);
  return link ? { open: link } : { none: true };
}

/** How the feed loader names itself to a sender's unsubscribe address. */
const USER_AGENT_UNSUBSCRIBE = "Mozilla/5.0 (compatible; Content/unsubscribe; +https://github.com/wimvdon/content)";

/** Second-level labels under a country's domain that are still public, as in example.co.uk. */
const PUBLIC_SECOND_LEVEL = new Set(["co", "com", "net", "org", "gov", "edu", "ac", "ne", "or", "go"]);

/**
 * The website an email comes from: its sender's domain without the mail subdomain
 * (news@email.example.com → https://example.com; hi@example.co.uk → https://example.co.uk), so the
 * apps can show that site's icon. Null when the address has no usable domain.
 */
export function senderSite(address: string | null | undefined): string | null {
  const domain = address?.trim().toLowerCase().split("@").pop() ?? "";
  if (!address?.includes("@") || !/^[a-z0-9-]+(\.[a-z0-9-]+)+$/.test(domain)) return null;
  const labels = domain.split(".");
  const tld = labels[labels.length - 1];
  const keep = labels.length >= 3 && tld.length === 2 && PUBLIC_SECOND_LEVEL.has(labels[labels.length - 2]) ? 3 : 2;
  return `https://${labels.slice(-keep).join(".")}`;
}

/**
 * Websites whose links say nothing about who sent an email: mailing and tracking services, social
 * networks, app stores, link shorteners and file hosts. Their subdomains count too.
 */
const NOT_A_SENDER = [
  "list-manage.com", "mailchimp.com", "mcusercontent.com", "mcsv.net", "campaign-archive.com", "sendgrid.net", "sendgrid.com",
  "mailjet.com", "sendinblue.com", "brevo.com", "mailerlite.com", "mlsend.com", "hubspot.com", "hubspotemail.net", "hs-sites.com",
  "hubspotlinks.com", "hs-analytics.net", "klaviyo.com", "klclick.com", "klclick1.com", "createsend.com", "cmail19.com", "cmail20.com",
  "exacttarget.com", "rs6.net", "constantcontact.com", "mandrillapp.com", "mailgun.org", "amazonaws.com", "cloudfront.net",
  "googleusercontent.com", "google.com", "goo.gl", "bit.ly", "lnkd.in", "t.co", "facebook.com", "fb.com", "instagram.com",
  "twitter.com", "x.com", "linkedin.com", "youtube.com", "youtu.be", "tiktok.com", "pinterest.com", "threads.net", "bsky.app",
  "whatsapp.com", "apple.com", "w3.org", "schema.org", "gravatar.com", "wp.com", "imgur.com", "giphy.com",
];

const isNotASender = (host: string) => NOT_A_SENDER.some((d) => host === d || host.endsWith(`.${d}`));

/**
 * The website of an email kept before 2026-11-15, when only the sender's name was kept: the domain
 * of [NewsletterEmail.from] when it is an address (a sender without a name), or else the website
 * the email links to most (at least twice), leaving out mailing services and social networks.
 * Null when neither says.
 */
export function guessSite(email: Pick<NewsletterEmail, "from" | "html">): string | null {
  const fromAddress = senderSite(email.from.match(/[^\s<>"]+@[^\s<>"]+/)?.[0]);
  if (fromAddress) return fromAddress;
  const counts = new Map<string, number>();
  for (const match of email.html.matchAll(/href\s*=\s*["']?https?:\/\/([a-z0-9.-]+)/gi)) {
    const host = match[1].toLowerCase().replace(/\.$/, "");
    if (isNotASender(host)) continue;
    const site = senderSite(`x@${host}`);
    if (site) counts.set(site, (counts.get(site) ?? 0) + 1);
  }
  let best: string | null = null;
  let most = 1;
  for (const [site, count] of counts) {
    if (count > most) {
      best = site;
      most = count;
    }
  }
  return best;
}

function textAsHtml(text: string): string {
  return text
    .split(/\r?\n\r?\n/)
    .map((p) => p.trim())
    .filter(Boolean)
    .map((p) => `<p>${escapeXml(p).replace(/\r?\n/g, "<br>")}</p>`)
    .join("\n");
}

export function escapeXml(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

/** The address's emails as an Atom feed, titled Newsletters (the apps show it as that, in their language); each entry links to its page. */
export function atomFeed(code: string, feedUrl: string, emails: NewsletterEmail[], now: number, links: ReadonlyMap<string, string> = new Map()): string {
  const iso = (ms: number) => new Date(ms).toISOString();
  const entries = emails
    .map(
      (e) => `  <entry>
    <id>urn:content:newsletter:${code}:${e.id}</id>
    <title>${escapeXml(e.subject || e.from || "Newsletter")}</title>
    <link rel="alternate" type="text/html" href="${escapeXml(links.get(e.id) ?? `${feedUrl}/${e.id}`)}"/>
    <published>${iso(e.at)}</published>
    <updated>${iso(e.at)}</updated>
    <author><name>${escapeXml(e.from || "Newsletter")}</name>${e.site ? `<uri>${escapeXml(e.site)}</uri>` : ""}</author>
    <content type="html">${escapeXml(e.html)}</content>
  </entry>`,
    )
    .join("\n");
  return `<?xml version="1.0" encoding="utf-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <id>urn:content:newsletter:${code}</id>
  <title>Newsletters</title>
  <link rel="self" href="${escapeXml(feedUrl)}"/>
  <updated>${iso(emails[0]?.at ?? now)}</updated>
${entries}
</feed>
`;
}

/**
 * One email as a page: its HTML in a sandbox (no scripts, forms or plugins; pictures over https and
 * inline styles only), so a newsletter can't do anything on this Worker's address.
 */
export function emailPage(email: NewsletterEmail): Response {
  const page = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="referrer" content="no-referrer"><title>${escapeXml(email.subject || email.from)}</title></head><body>${email.html}</body></html>`;
  return new Response(page, {
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Content-Security-Policy": "sandbox; default-src 'none'; img-src https: data:; style-src 'unsafe-inline'; font-src https: data:",
      "Referrer-Policy": "no-referrer",
      "X-Content-Type-Options": "nosniff",
      "Cache-Control": "private, max-age=300",
    },
  });
}

// The Durable Object's side: the inbox's routes (Account.fetch passes them here) and the account's list.

/** Answers an inbox's routes, inside its Durable Object ("newsletter:<code>"). */
export async function inboxAnswer(storage: Storage, masterKey: string, route: string, request: Request): Promise<Response> {
  const code = request.headers.get("X-Code") ?? "";
  if (!CODE_PATTERN.test(code)) return plain(400, "No code");
  const inbox = new NewsletterInbox(storage, masterKey, code);
  switch (route) {
    case "nlInit": {
      const body = (await request.json()) as { owner?: unknown };
      if (typeof body.owner !== "string") return plain(400, "Send owner");
      return (await inbox.create(body.owner)) ? new Response(null, { status: 201 }) : plain(409, "Taken");
    }
    case "nlForward": {
      const body = (await request.json()) as { owner?: unknown; inbox?: unknown };
      if (typeof body.owner !== "string" || typeof body.inbox !== "string" || !CODE_PATTERN.test(body.inbox)) return plain(400, "Send owner and inbox");
      return (await inbox.createForward(body.owner, body.inbox)) ? new Response(null, { status: 201 }) : plain(409, "Taken");
    }
    case "nlRetire": {
      const body = (await request.json()) as { until?: unknown };
      if (typeof body.until !== "number") return plain(400, "Send until");
      if (!(await inbox.owner())) return plain(404, "Unknown address");
      await inbox.retire(body.until);
      return new Response(null, { status: 204 });
    }
    case "nlReceive": {
      // A tag's new code (or an old one still taking mail): the Worker sends the email on to its inbox.
      const forward = await inbox.forward();
      if (forward) {
        if (!(await inbox.stopped())) return new Response(JSON.stringify({ forward }), { headers: { "Content-Type": "application/json" } });
        // Stopped for good: nothing of it is needed any more.
        await inbox.deleteAll();
        return plain(404, "Unknown address");
      }
      const raw = await request.arrayBuffer();
      if (raw.byteLength > MAX_EMAIL_BYTES) return plain(413, "Too large");
      return (await inbox.receive(raw, request.headers.get("X-Forwarded") === "1")) ? new Response(null, { status: 204 }) : plain(404, "Unknown address");
    }
    case "nlFeed": {
      if (!(await inbox.owner())) return plain(404, "Unknown address");
      const feedUrl = request.headers.get("X-Feed-Url") ?? "";
      const emails = await inbox.emails();
      // Each entry links to its own page, which doesn't open the feed (emailToken).
      const origin = feedUrl.match(/^https?:\/\/[^/]+/)?.[0] ?? "";
      const links = new Map(await Promise.all(emails.map(async (e) => [e.id, emailUrlOf(origin, code, e.id, await emailToken(masterKey, code, e.id))] as const)));
      return new Response(atomFeed(code, feedUrl, emails, Date.now(), links), {
        headers: { "Content-Type": "application/atom+xml; charset=utf-8", "Cache-Control": "private, max-age=60" },
      });
    }
    case "nlEntry": {
      const email = (await inbox.owner()) ? await inbox.email(request.headers.get("X-Entry") ?? "") : null;
      return email ? emailPage(email) : plain(404, "Not found");
    }
    case "nlUnsubscribe": {
      // What the Worker needs to unsubscribe (handleNewsletterUnsubscribe): the email's headers' choice and its HTML.
      const email = (await inbox.owner()) ? await inbox.email(request.headers.get("X-Entry") ?? "") : null;
      if (!email) return plain(404, "Not found");
      return new Response(JSON.stringify({ html: email.html, ...(email.unsubscribe ? { unsubscribe: email.unsubscribe } : {}) }), { headers: { "Content-Type": "application/json" } });
    }
    case "nlWipe":
      await inbox.deleteAll();
      return new Response(null, { status: 204 });
    default:
      return plain(404, "Not found");
  }
}

export const isInboxRoute = (route: string) => route.startsWith("nl") && ["nlInit", "nlForward", "nlRetire", "nlReceive", "nlFeed", "nlEntry", "nlUnsubscribe", "nlWipe"].includes(route);

const inboxStub = (env: Env, code: string) => env.ACCOUNTS!.get(env.ACCOUNTS!.idFromName(`newsletter:${code}`));
const wipe = (env: Env, code: string) => inboxStub(env, code).fetch("https://newsletter/nlWipe", { method: "POST", headers: { "X-Code": code } });

/** The account's routes for its addresses (signed in, inside the account's Durable Object). */
export async function addressesAnswer(storage: Storage, env: Env, accountId: string, route: string, request: Request): Promise<Response> {
  const stored = async () => (await storage.get<NewsletterAddress[]>("newsletters")) ?? [];
  const all = await stored();
  const list = all.filter((a) => !isLegacy(a));
  const origin = request.headers.get("X-Origin") ?? "";
  const shown = async (a: NewsletterAddress) => {
    const until = oldUntil(a, Date.now());
    return {
      code: a.code,
      tagHash: a.tagHash,
      createdAt: a.createdAt,
      address: addressOf(env.NEWSLETTER_ADDRESS, receivingOf(a)),
      feedUrl: feedUrlOf(origin, a.code, await feedToken(env.ACCOUNT_KEY!, a.code)),
      // The address it had before Get a new address still takes mail until then.
      ...(until ? { oldUntil: until } : {}),
    };
  };
  const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json" } });
  switch (route) {
    case "nlList":
      return json({ addresses: await Promise.all(list.map(shown)) });
    case "nlCreate": {
      if (!addressOf(env.NEWSLETTER_ADDRESS, "x".repeat(12))) return plain(503, "Newsletter addresses aren't set up on this server");
      const tagHash = cleanTagHash(((await request.json().catch(() => ({}))) as { tagHash?: unknown }).tagHash);
      if (tagHash === undefined) return plain(400, 'Send {"tagHash": <the SHA-256 of its tag\'s id, or null>}');
      const has = list.find((a) => a.tagHash === tagHash);
      if (has) return json(await shown(has));
      // Addresses made by name, before one per tag: gone with their emails.
      for (const old of all.filter(isLegacy)) await wipe(env, old.code);
      if (list.length >= MAX_ADDRESSES) return plain(409, "Too many addresses");
      for (let attempt = 0; attempt < 3; attempt++) {
        const code = newCode();
        const made = await inboxStub(env, code).fetch("https://newsletter/nlInit", { method: "POST", headers: { "X-Code": code }, body: JSON.stringify({ owner: accountId }) });
        if (made.status !== 201) continue;
        // Two devices asking at once: the other request may have made the tag's address meanwhile.
        const now = (await stored()).filter((a) => !isLegacy(a));
        const theirs = now.find((a) => a.tagHash === tagHash);
        if (theirs) {
          await wipe(env, code);
          return json(await shown(theirs));
        }
        const address: NewsletterAddress = { code, tagHash, createdAt: Date.now() };
        await storage.put("newsletters", [...now, address]);
        return json(await shown(address), 201);
      }
      return plain(503, "Try again");
    }
    case "nlRenew": {
      // Get a new address (2026-11-15): a new code sends its mail into the same inbox, so the feed and
      // its emails stay; the code it had takes mail for OLD_ADDRESS_DAYS more, then refuses it.
      const code = ((await request.json().catch(() => ({}))) as { code?: unknown }).code;
      const before = typeof code === "string" ? list.find((a) => a.code === code) : undefined;
      if (!before) return plain(404, "Not found");
      const now = Date.now();
      if ((before.old ?? []).filter((o) => o.until > now).length >= MAX_OLD_ADDRESSES) return plain(429, "Too many new addresses; try again later");
      for (let attempt = 0; attempt < 3; attempt++) {
        const fresh = newCode();
        const made = await inboxStub(env, fresh).fetch("https://newsletter/nlForward", { method: "POST", headers: { "X-Code": fresh }, body: JSON.stringify({ owner: accountId, inbox: before.code }) });
        if (made.status !== 201) continue;
        // Read again (another device may have changed it meanwhile), and store without waiting on anything else.
        const latest = await stored();
        const address = latest.find((a) => a.code === before.code && !isLegacy(a));
        if (!address) {
          await wipe(env, fresh);
          return plain(404, "Not found");
        }
        const until = now + OLD_ADDRESS_DAYS * DAY_MS;
        const previous = receivingOf(address);
        const working = (address.old ?? []).filter((o) => o.until > now);
        const stopped = (address.old ?? []).filter((o) => o.until <= now);
        const renewed: NewsletterAddress = { ...address, receiving: fresh, old: [...working, { code: previous, until }] };
        await storage.put("newsletters", latest.map((a) => (a === address ? renewed : a)));
        await inboxStub(env, previous).fetch("https://newsletter/nlRetire", { method: "POST", headers: { "X-Code": previous }, body: JSON.stringify({ until }) });
        // Codes that stopped taking mail are forgotten, except the inbox itself, which holds the emails.
        for (const o of stopped) if (o.code !== address.code) await wipe(env, o.code);
        return json(await shown(renewed));
      }
      return plain(503, "Try again");
    }
    case "nlDelete": {
      const code = new URL(request.url).searchParams.get("code") ?? "";
      const gone = list.find((a) => a.code === code);
      if (!gone) return plain(404, "Not found");
      for (const c of codesOf(gone)) await wipe(env, c);
      await storage.put("newsletters", (await stored()).filter((a) => a.code !== code));
      return new Response(null, { status: 204 });
    }
    default:
      return plain(404, "Not found");
  }
}

/** Deletes every address of the account (the account is being deleted). */
export async function deleteAddresses(storage: Storage, env: Env): Promise<void> {
  for (const a of (await storage.get<NewsletterAddress[]>("newsletters")) ?? []) for (const c of codesOf(a)) await wipe(env, c);
}

// The Worker's side: email in, feeds out.

/** What the Worker needs of an incoming email (Cloudflare's ForwardableEmailMessage). */
export interface IncomingEmail {
  readonly to: string;
  readonly rawSize: number;
  readonly raw: ReadableStream<Uint8Array>;
  setReject(reason: string): void;
}

/** An email arrives (Email Routing): kept by its address's inbox, or refused. */
export async function receiveEmail(message: IncomingEmail, env: Env): Promise<void> {
  const code = codeOf(message.to);
  if (!code || !env.ACCOUNTS || !env.ACCOUNT_KEY) return message.setReject("Unknown address");
  if (message.rawSize > MAX_EMAIL_BYTES) return message.setReject("Message too large");
  const raw = await new Response(message.raw).arrayBuffer();
  let answer = await inboxStub(env, code).fetch("https://newsletter/nlReceive", { method: "POST", headers: { "X-Code": code }, body: raw });
  // A tag's new address (or an old one still taking mail): kept in that tag's inbox, so it lands in the same feed.
  if (answer.status === 200) {
    const forward = ((await answer.json().catch(() => ({}))) as { forward?: unknown }).forward;
    if (typeof forward !== "string" || !CODE_PATTERN.test(forward)) throw new Error("Couldn't keep the email");
    answer = await inboxStub(env, forward).fetch("https://newsletter/nlReceive", { method: "POST", headers: { "X-Code": forward, "X-Forwarded": "1" }, body: raw });
  }
  if (answer.status === 404) message.setReject("Unknown address");
  else if (answer.status === 413) message.setReject("Message too large");
  else if (!answer.ok) throw new Error("Couldn't keep the email");
}

/** GET /newsletters/<code>/<token>[/<id>]: the feed, or one email's page; /newsletters/<code>/e/<id>/<emailToken>: one email's page only. */
export async function handleNewsletterFeed(url: URL, env: Env): Promise<Response> {
  const single = /^\/newsletters\/([a-z0-9]{12})\/e\/([0-9a-f]{16})\/([0-9a-f]{32})$/.exec(url.pathname);
  if (single) {
    if (!env.ACCOUNTS || !env.ACCOUNT_KEY) return plain(404, "Not found");
    const [, code, id, token] = single;
    if (token !== (await emailToken(env.ACCOUNT_KEY, code, id))) return plain(404, "Not found");
    return inboxStub(env, code).fetch("https://newsletter/nlEntry", { headers: { "X-Code": code, "X-Entry": id } });
  }
  const match = /^\/newsletters\/([a-z0-9]{12})\/([0-9a-f]{32})(?:\/([0-9a-f]{16}))?$/.exec(url.pathname);
  if (!match || !env.ACCOUNTS || !env.ACCOUNT_KEY) return plain(404, "Not found");
  const [, code, token, id] = match;
  if (token !== (await feedToken(env.ACCOUNT_KEY, code))) return plain(404, "Not found");
  const feedUrl = feedUrlOf(url.origin, code, token);
  return inboxStub(env, code).fetch(`https://newsletter/${id ? "nlEntry" : "nlFeed"}`, { headers: { "X-Code": code, "X-Feed-Url": feedUrl, "X-Entry": id ?? "" } });
}

/**
 * POST /newsletters/<code>/<token>/unsubscribe {"id": "<email id>"}: unsubscribes from that email's
 * sender, or says how ([unsubscribeAnswer]). The feed's token is the key, as for the feed. The web
 * app calls it directly (CORS, only from ALLOWED_ORIGINS); the Android app sends no Origin.
 * [isPublicHttps] is the feed loader's own check (index.ts's checkTarget).
 */
export async function handleNewsletterUnsubscribe(request: Request, url: URL, env: Env, fetcher: Fetcher, isPublicHttps: (url: string) => string | null): Promise<Response> {
  const origin = request.headers.get("Origin");
  if (origin && !allowedOrigins(env).includes(origin)) return plain(403, "Not allowed");
  const cors = origin ? corsHeaders(origin) : {};
  const answer = (status: number, text: string) => plain(status, text, cors);
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
  if (request.method !== "POST") return answer(405, "Only POST");
  const match = /^\/newsletters\/([a-z0-9]{12})\/([0-9a-f]{32})\/unsubscribe$/.exec(url.pathname);
  if (!match || !env.ACCOUNTS || !env.ACCOUNT_KEY) return answer(404, "Not found");
  const [, code, token] = match;
  if (token !== (await feedToken(env.ACCOUNT_KEY, code))) return answer(404, "Not found");
  if (env.LIMITER) {
    const { success } = await env.LIMITER.limit({ key: request.headers.get("CF-Connecting-IP") ?? "unknown" });
    if (!success) return answer(429, "Too many requests");
  }
  const body = await request.text();
  if (body.length > 1024) return answer(413, "Too large");
  let id: unknown;
  try {
    id = (JSON.parse(body) as { id?: unknown }).id;
  } catch {
    id = undefined;
  }
  if (typeof id !== "string" || !/^[0-9a-f]{16}$/.test(id)) return answer(400, 'Send {"id": "<the email\'s id>"}');
  const found = await inboxStub(env, code).fetch("https://newsletter/nlUnsubscribe", { headers: { "X-Code": code, "X-Entry": id } });
  if (found.status === 404) return answer(404, "Not found");
  if (!found.ok) return answer(502, "Try again");
  const email = (await found.json()) as Pick<NewsletterEmail, "html" | "unsubscribe">;
  const result = await unsubscribeAnswer(email, fetcher, isPublicHttps);
  return new Response(JSON.stringify(result), { headers: { ...cors, "Content-Type": "application/json", "Cache-Control": "no-store" } });
}
