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
 *  - DELETE /account/newsletters?code=<code>  the address stops taking mail; its emails are deleted
 *  - GET    /newsletters/<code>/<token>       the feed (no sign-in: the token is the key)
 *  - GET    /newsletters/<code>/<token>/<id>  one email as a page (the entries' link)
 */

import PostalMime from "postal-mime";
import { derive, plain, type Storage } from "./account";
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
/** A tag as the apps send it: the SHA-256 of its id, in hex. */
const TAG_HASH_PATTERN = /^[0-9a-f]{64}$/;

const DAY_MS = 24 * 60 * 60 * 1000;
const CODE_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";
const CODE_PATTERN = /^[a-z0-9]{12}$/;

/** One email as kept: its id, when it arrived, who sent it, its subject and its HTML. */
export interface NewsletterEmail {
  id: string;
  at: number;
  from: string;
  subject: string;
  html: string;
}

/** An address as its account lists it: the tag it belongs to (null: the one without a tag). */
export interface NewsletterAddress {
  code: string;
  tagHash: string | null;
  createdAt: number;
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

  /** Keeps an email that arrived ([raw], as sent); false when this address doesn't exist. */
  async receive(raw: ArrayBuffer | Uint8Array): Promise<boolean> {
    if (!(await this.owner())) return false;
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
    for (const [, sealed] of keys) {
      const email = await this.open(sealed);
      if (email) out.push(email);
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
  const html = parsed.html?.trim() ? parsed.html : textAsHtml(parsed.text ?? "");
  const id = [...crypto.getRandomValues(new Uint8Array(8))].map((b) => b.toString(16).padStart(2, "0")).join("");
  return { id, at: now, from, subject: parsed.subject?.trim() ?? "", html: html.length > MAX_HTML_CHARS ? html.slice(0, MAX_HTML_CHARS) : html };
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
export function atomFeed(code: string, feedUrl: string, emails: NewsletterEmail[], now: number): string {
  const iso = (ms: number) => new Date(ms).toISOString();
  const entries = emails
    .map(
      (e) => `  <entry>
    <id>urn:content:newsletter:${code}:${e.id}</id>
    <title>${escapeXml(e.subject || e.from || "Newsletter")}</title>
    <link rel="alternate" type="text/html" href="${escapeXml(`${feedUrl}/${e.id}`)}"/>
    <published>${iso(e.at)}</published>
    <updated>${iso(e.at)}</updated>
    <author><name>${escapeXml(e.from || "Newsletter")}</name></author>
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
    case "nlReceive": {
      const raw = await request.arrayBuffer();
      if (raw.byteLength > MAX_EMAIL_BYTES) return plain(413, "Too large");
      return (await inbox.receive(raw)) ? new Response(null, { status: 204 }) : plain(404, "Unknown address");
    }
    case "nlFeed": {
      if (!(await inbox.owner())) return plain(404, "Unknown address");
      const feedUrl = request.headers.get("X-Feed-Url") ?? "";
      return new Response(atomFeed(code, feedUrl, await inbox.emails(), Date.now()), {
        headers: { "Content-Type": "application/atom+xml; charset=utf-8", "Cache-Control": "private, max-age=60" },
      });
    }
    case "nlEntry": {
      const email = (await inbox.owner()) ? await inbox.email(request.headers.get("X-Entry") ?? "") : null;
      return email ? emailPage(email) : plain(404, "Not found");
    }
    case "nlWipe":
      await inbox.deleteAll();
      return new Response(null, { status: 204 });
    default:
      return plain(404, "Not found");
  }
}

export const isInboxRoute = (route: string) => route.startsWith("nl") && ["nlInit", "nlReceive", "nlFeed", "nlEntry", "nlWipe"].includes(route);

const inboxStub = (env: Env, code: string) => env.ACCOUNTS!.get(env.ACCOUNTS!.idFromName(`newsletter:${code}`));
const wipe = (env: Env, code: string) => inboxStub(env, code).fetch("https://newsletter/nlWipe", { method: "POST", headers: { "X-Code": code } });

/** The account's routes for its addresses (signed in, inside the account's Durable Object). */
export async function addressesAnswer(storage: Storage, env: Env, accountId: string, route: string, request: Request): Promise<Response> {
  const stored = async () => (await storage.get<NewsletterAddress[]>("newsletters")) ?? [];
  const all = await stored();
  const list = all.filter((a) => !isLegacy(a));
  const origin = request.headers.get("X-Origin") ?? "";
  const shown = async (a: NewsletterAddress) => ({
    ...a,
    address: addressOf(env.NEWSLETTER_ADDRESS, a.code),
    feedUrl: feedUrlOf(origin, a.code, await feedToken(env.ACCOUNT_KEY!, a.code)),
  });
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
    case "nlDelete": {
      const code = new URL(request.url).searchParams.get("code") ?? "";
      if (!list.some((a) => a.code === code)) return plain(404, "Not found");
      await wipe(env, code);
      await storage.put("newsletters", (await stored()).filter((a) => a.code !== code));
      return new Response(null, { status: 204 });
    }
    default:
      return plain(404, "Not found");
  }
}

/** Deletes every address of the account (the account is being deleted). */
export async function deleteAddresses(storage: Storage, env: Env): Promise<void> {
  for (const a of (await storage.get<NewsletterAddress[]>("newsletters")) ?? []) await wipe(env, a.code);
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
  const answer = await inboxStub(env, code).fetch("https://newsletter/nlReceive", { method: "POST", headers: { "X-Code": code }, body: raw });
  if (answer.status === 404) message.setReject("Unknown address");
  else if (answer.status === 413) message.setReject("Message too large");
  else if (!answer.ok) throw new Error("Couldn't keep the email");
}

/** GET /newsletters/<code>/<token>[/<id>]: the feed, or one email's page. */
export async function handleNewsletterFeed(url: URL, env: Env): Promise<Response> {
  const match = /^\/newsletters\/([a-z0-9]{12})\/([0-9a-f]{32})(?:\/([0-9a-f]{16}))?$/.exec(url.pathname);
  if (!match || !env.ACCOUNTS || !env.ACCOUNT_KEY) return plain(404, "Not found");
  const [, code, token, id] = match;
  if (token !== (await feedToken(env.ACCOUNT_KEY, code))) return plain(404, "Not found");
  const feedUrl = feedUrlOf(url.origin, code, token);
  return inboxStub(env, code).fetch(`https://newsletter/${id ? "nlEntry" : "nlFeed"}`, { headers: { "X-Code": code, "X-Feed-Url": feedUrl, "X-Entry": id ?? "" } });
}
