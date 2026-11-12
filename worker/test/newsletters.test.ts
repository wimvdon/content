import { describe, expect, it } from "vitest";
import { Account, accountIdOf, addDeviceKey, type Storage } from "../src/account";
import { handle, type Env } from "../src/index";
import { KEEP, KEEP_DAYS, MAX_HTML_CHARS, NewsletterInbox, addressOf, atomFeed, cleanTagHash, codeOf, emailOf, feedToken, newCode, receiveEmail, type IncomingEmail } from "../src/newsletters";

const APP = "https://app.example";
const MASTER = "a-test-master-key-of-at-least-32-characters";
const DAY = 24 * 60 * 60 * 1000;

/** A tag as the apps send it: the SHA-256 of its id. */
const TECH = "a".repeat(64);

class MemoryStorage implements Storage {
  readonly map = new Map<string, unknown>();
  async get<T>(key: string) {
    return this.map.get(key) as T | undefined;
  }
  async put<T>(key: string, value: T) {
    this.map.set(key, structuredClone(value));
  }
  async delete(key: string) {
    return this.map.delete(key);
  }
  async list<T>({ prefix, start, limit }: { prefix: string; start?: string; limit?: number }) {
    const keys = [...this.map.keys()].filter((k) => k.startsWith(prefix) && (start === undefined || k >= start)).sort();
    return new Map(keys.slice(0, limit ?? keys.length).map((k) => [k, this.map.get(k) as T]));
  }
  async deleteAll() {
    this.map.clear();
  }
}

/** Durable Objects in memory: one Account per name, as Cloudflare would keep them. */
function accounts(newsletterAddress: string | undefined = "news@in.example.org") {
  const objects = new Map<string, { account: Account; storage: MemoryStorage }>();
  const namespace = {
    idFromName: (name: string) => name,
    get: (id: string) => {
      if (!objects.has(id)) {
        const storage = new MemoryStorage();
        const state = { storage, getWebSockets: () => [] } as unknown as DurableObjectState;
        objects.set(id, { account: new Account(state, env), storage });
      }
      const { account } = objects.get(id)!;
      return { fetch: (input: string, init?: RequestInit) => account.fetch(new Request(input, init)) };
    },
  } as unknown as DurableObjectNamespace;
  const env: Env = { ALLOWED_ORIGINS: APP, ACCOUNTS: namespace, ACCOUNT_KEY: MASTER, NEWSLETTER_ADDRESS: newsletterAddress };
  return { env, objects };
}

async function signIn(env: Env, userId = 42): Promise<string> {
  return (await addDeviceKey(env, await accountIdOf(MASTER, "email", userId)))!;
}

function call(env: Env, method: string, path: string, key: string | null, body?: unknown) {
  return handle(
    new Request("https://worker.example" + path, {
      method,
      headers: { Origin: APP, ...(key ? { Authorization: `Bearer ${key}` } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body),
    }),
    env,
    async () => new Response("unused", { status: 500 }),
  );
}

/** A plain email as Email Routing hands it over. */
function rawEmail({ subject = "Issue 12", from = '"The Weekly" <hello@weekly.example>', html = "<p>Hello <b>reader</b></p>", text = "", attachment = false } = {}): string {
  const boundary = "b1";
  const parts = [
    html ? `--${boundary}\r\nContent-Type: text/html; charset=utf-8\r\n\r\n${html}\r\n` : "",
    text ? `--${boundary}\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n${text}\r\n` : "",
    attachment ? `--${boundary}\r\nContent-Type: application/pdf\r\nContent-Disposition: attachment; filename="a.pdf"\r\nContent-Transfer-Encoding: base64\r\n\r\nJVBERi0xLjQK\r\n` : "",
  ].join("");
  return `From: ${from}\r\nTo: news+abc@in.example.org\r\nSubject: ${subject}\r\nMIME-Version: 1.0\r\nContent-Type: multipart/mixed; boundary="${boundary}"\r\n\r\n${parts}--${boundary}--\r\n`;
}

function message(to: string, raw: string) {
  const bytes = new TextEncoder().encode(raw);
  const out: { rejected: string | null } & IncomingEmail = {
    to,
    rawSize: bytes.byteLength,
    raw: new Response(bytes).body!,
    rejected: null,
    setReject(reason: string) {
      out.rejected = reason;
    },
  };
  return out;
}

describe("codes and addresses", () => {
  it("makes random codes of 12 letters and digits, without look-alikes", () => {
    const code = newCode();
    expect(code).toMatch(/^[a-z2-9]{12}$/);
    expect(code).not.toMatch(/[lo01]/);
    expect(newCode()).not.toBe(code);
  });

  it("finds the code after the + (subaddressing), or the whole local part (a catch-all)", () => {
    expect(codeOf("news+k7q2m9abcdef@in.example.org")).toBe("k7q2m9abcdef");
    expect(codeOf("<NEWS+K7Q2M9ABCDEF@in.example.org>")).toBe("k7q2m9abcdef");
    expect(codeOf("k7q2m9abcdef@news.example")).toBe("k7q2m9abcdef");
    expect(codeOf("news@in.example.org")).toBeNull();
    expect(codeOf("news+short@in.example.org")).toBeNull();
  });

  it("puts the code in NEWSLETTER_ADDRESS, and none without it", () => {
    expect(addressOf("news@in.example.org", "k7q2m9abcdef")).toBe("news+k7q2m9abcdef@in.example.org");
    expect(addressOf(undefined, "k7q2m9abcdef")).toBeNull();
    expect(addressOf("not an address", "k7q2m9abcdef")).toBeNull();
  });

  it("takes a tag's hash, or null for no tag, and refuses anything else", () => {
    expect(cleanTagHash(TECH)).toBe(TECH);
    expect(cleanTagHash("0f8fad5b-d9cb-469f-a165-70867728950e")).toBeUndefined();
    expect(cleanTagHash(null)).toBeNull();
    expect(cleanTagHash(undefined)).toBeUndefined();
    expect(cleanTagHash("")).toBeUndefined();
    expect(cleanTagHash("Tech news")).toBeUndefined();
    expect(cleanTagHash(TECH.toUpperCase())).toBeUndefined();
    expect(cleanTagHash(3)).toBeUndefined();
  });

  it("derives a feed token per code that doesn't give the code away", async () => {
    const token = await feedToken(MASTER, "k7q2m9abcdef");
    expect(token).toMatch(/^[0-9a-f]{32}$/);
    expect(await feedToken(MASTER, "k7q2m9abcdef")).toBe(token);
    expect(await feedToken(MASTER, "k7q2m9abcdeg")).not.toBe(token);
  });
});

describe("reading an email", () => {
  it("keeps the sender's name, the subject and the HTML, without attachments", async () => {
    const email = await emailOf(new TextEncoder().encode(rawEmail({ attachment: true })), 5);
    expect(email).toMatchObject({ at: 5, from: "The Weekly", subject: "Issue 12" });
    expect(email.html).toContain("<b>reader</b>");
    expect(JSON.stringify(email)).not.toContain("JVBERi0");
    expect(email.id).toMatch(/^[0-9a-f]{16}$/);
  });

  it("turns a text-only email into paragraphs, escaped", async () => {
    const email = await emailOf(new TextEncoder().encode(rawEmail({ html: "", text: "One <two>\r\nline\r\n\r\nThree" })), 5);
    expect(email.html).toBe("<p>One &lt;two&gt;<br>line</p>\n<p>Three</p>");
  });

  it("cuts very long HTML", async () => {
    const email = await emailOf(new TextEncoder().encode(rawEmail({ html: "<p>" + "x".repeat(MAX_HTML_CHARS + 100) + "</p>" })), 5);
    expect(email.html.length).toBe(MAX_HTML_CHARS);
  });
});

describe("an inbox", () => {
  it("is made once, and refuses mail before it is made", async () => {
    const storage = new MemoryStorage();
    const inbox = new NewsletterInbox(storage, MASTER, "k7q2m9abcdef");
    expect(await inbox.receive(new TextEncoder().encode(rawEmail()))).toBe(false);
    expect(await inbox.create("owner")).toBe(true);
    expect(await inbox.create("someone")).toBe(false);
    expect(await inbox.owner()).toBe("owner");
  });

  it("keeps the emails encrypted, newest first, at most KEEP and none older than KEEP_DAYS", async () => {
    let now = 1_000 * DAY;
    const storage = new MemoryStorage();
    const inbox = new NewsletterInbox(storage, MASTER, "k7q2m9abcdef", () => now);
    await inbox.create("owner");
    await inbox.receive(new TextEncoder().encode(rawEmail({ subject: "Old" })));
    now += (KEEP_DAYS + 1) * DAY;
    for (let i = 0; i < KEEP + 2; i++) {
      now += 1000;
      await inbox.receive(new TextEncoder().encode(rawEmail({ subject: `Issue ${i}` })));
    }
    const kept = await inbox.emails(1000);
    expect(kept.length).toBe(KEEP);
    expect(kept[0].subject).toBe(`Issue ${KEEP + 1}`);
    expect(kept.some((e) => e.subject === "Old")).toBe(false);
    expect(await inbox.email(kept[3].id)).toEqual(kept[3]);
    // Stored encrypted: the subject isn't readable in storage.
    expect(JSON.stringify([...storage.map.values()].map((v) => (v as { data?: Uint8Array }).data && [...(v as { data: Uint8Array }).data]))).not.toContain("Issue");
  });

  it("writes an Atom feed titled Newsletters, with each email's page as its link", () => {
    const feed = atomFeed("k7q2m9abcdef", "https://w.example/newsletters/k7q2m9abcdef/t", [{ id: "0123456789abcdef", at: 0, from: "The Weekly", subject: "Issue <1>", html: "<p>Hi</p>" }], 0);
    expect(feed).toContain("<title>Newsletters</title>");
    expect(feed).toContain("<title>Issue &lt;1&gt;</title>");
    expect(feed).toContain('href="https://w.example/newsletters/k7q2m9abcdef/t/0123456789abcdef"');
    expect(feed).toContain("<content type=\"html\">&lt;p&gt;Hi&lt;/p&gt;</content>");
    expect(feed).toContain("<name>The Weekly</name>");
  });
});

describe("newsletter addresses, from sign-in to feed", () => {
  it("makes an address for a tag, takes mail for it and serves it as a feed", async () => {
    const { env } = accounts();
    const key = await signIn(env);
    const made = await call(env, "POST", "/account/newsletters", key, { tagHash: TECH });
    expect(made.status).toBe(201);
    const address = (await made.json()) as { code: string; tagHash: string | null; address: string; feedUrl: string };
    expect(address.tagHash).toBe(TECH);
    expect(address.address).toBe(`news+${address.code}@in.example.org`);
    expect(address.feedUrl).toMatch(new RegExp(`^https://worker.example/newsletters/${address.code}/[0-9a-f]{32}$`));

    const list = (await (await call(env, "GET", "/account/newsletters", key)).json()) as { addresses: { code: string }[] };
    expect(list.addresses.map((a) => a.code)).toEqual([address.code]);

    const email = message(address.address, rawEmail());
    await receiveEmail(email, env);
    expect(email.rejected).toBeNull();

    const feed = await handle(new Request(address.feedUrl), env, async () => new Response("unused", { status: 500 }));
    expect(feed.status).toBe(200);
    expect(feed.headers.get("Content-Type")).toContain("application/atom+xml");
    const xml = await feed.text();
    expect(xml).toContain("<title>Newsletters</title>");
    expect(xml).toContain("<title>Issue 12</title>");

    // The entry's link: the email as a sandboxed page.
    const link = /rel="alternate" type="text\/html" href="([^"]+)"/.exec(xml)![1];
    const page = await handle(new Request(link), env, async () => new Response("unused", { status: 500 }));
    expect(page.status).toBe(200);
    expect(page.headers.get("Content-Security-Policy")).toContain("sandbox");
    expect(await page.text()).toContain("<b>reader</b>");

    // The web app loads it through the feed loader, which answers its own address itself.
    const proxied = await handle(new Request(`https://worker.example/?url=${encodeURIComponent(address.feedUrl)}`, { headers: { Origin: APP } }), env, async () => new Response("unused", { status: 500 }));
    expect(proxied.status).toBe(200);
    expect(proxied.headers.get("Access-Control-Allow-Origin")).toBe(APP);
    expect(await proxied.text()).toContain("Issue 12");
  });

  it("refuses a wrong feed token, an unknown address and a too large email", async () => {
    const { env } = accounts();
    const key = await signIn(env);
    const address = (await (await call(env, "POST", "/account/newsletters", key, { tagHash: TECH })).json()) as { code: string; address: string; feedUrl: string };
    const wrong = address.feedUrl.replace(/[0-9a-f]{32}$/, "0".repeat(32));
    expect((await handle(new Request(wrong), env, async () => new Response("", { status: 500 }))).status).toBe(404);

    const unknown = message("news+zzzzzzzzzzzz@in.example.org", rawEmail());
    await receiveEmail(unknown, env);
    expect(unknown.rejected).toBe("Unknown address");
    const noCode = message("news@in.example.org", rawEmail());
    await receiveEmail(noCode, env);
    expect(noCode.rejected).toBe("Unknown address");
    const big = message(address.address, rawEmail());
    Object.defineProperty(big, "rawSize", { value: 5 * 1024 * 1024 });
    await receiveEmail(big, env);
    expect(big.rejected).toBe("Message too large");
  });

  it("needs a sign-in, and a tag id, and NEWSLETTER_ADDRESS on the server", async () => {
    const { env } = accounts();
    expect((await call(env, "POST", "/account/newsletters", null, { tagHash: TECH })).status).toBe(401);
    const key = await signIn(env);
    expect((await call(env, "POST", "/account/newsletters", key, { tagHash: "Tech news" })).status).toBe(400);
    expect((await call(env, "POST", "/account/newsletters", key, {})).status).toBe(400);
    const off = accounts("");
    const offKey = await signIn(off.env);
    expect((await call(off.env, "POST", "/account/newsletters", offKey, { tagHash: TECH })).status).toBe(503);
  });

  it("removing an address stops its mail and deletes its emails; another account can't remove it", async () => {
    const { env, objects } = accounts();
    const key = await signIn(env);
    const other = await signIn(env, 7);
    const address = (await (await call(env, "POST", "/account/newsletters", key, { tagHash: TECH })).json()) as { code: string; address: string };
    await receiveEmail(message(address.address, rawEmail()), env);
    expect((await call(env, "DELETE", `/account/newsletters?code=${address.code}`, other)).status).toBe(404);
    expect((await call(env, "DELETE", `/account/newsletters?code=${address.code}`, key)).status).toBe(204);
    expect(objects.get(`newsletter:${address.code}`)!.storage.map.size).toBe(0);
    const later = message(address.address, rawEmail());
    await receiveEmail(later, env);
    expect(later.rejected).toBe("Unknown address");
    expect(((await (await call(env, "GET", "/account/newsletters", key)).json()) as { addresses: unknown[] }).addresses).toEqual([]);
  });

  it("gives each tag one address, and one without a tag: asking again gives the same", async () => {
    const { env } = accounts();
    const key = await signIn(env);
    const first = await call(env, "POST", "/account/newsletters", key, { tagHash: TECH });
    const again = await call(env, "POST", "/account/newsletters", key, { tagHash: TECH });
    expect(first.status).toBe(201);
    expect(again.status).toBe(200);
    const a = (await first.json()) as { code: string };
    expect(((await again.json()) as { code: string }).code).toBe(a.code);
    const general = await call(env, "POST", "/account/newsletters", key, { tagHash: null });
    expect(general.status).toBe(201);
    const g = (await general.json()) as { code: string; tagHash: string | null };
    expect(g.tagHash).toBeNull();
    expect(g.code).not.toBe(a.code);
    expect((await call(env, "POST", "/account/newsletters", key, { tagHash: null })).status).toBe(200);
    const list = (await (await call(env, "GET", "/account/newsletters", key)).json()) as { addresses: { code: string; tagHash: string | null }[] };
    expect(list.addresses.map((x) => [x.tagHash, x.code])).toEqual([
      [TECH, a.code],
      [null, g.code],
    ]);
  });

  it("two devices asking at once still get one address for the tag", async () => {
    const { env } = accounts();
    const key = await signIn(env);
    const [x, y] = await Promise.all([call(env, "POST", "/account/newsletters", key, { tagHash: TECH }), call(env, "POST", "/account/newsletters", key, { tagHash: TECH })]);
    const codes = [((await x.json()) as { code: string }).code, ((await y.json()) as { code: string }).code];
    expect(codes[0]).toBe(codes[1]);
    const list = (await (await call(env, "GET", "/account/newsletters", key)).json()) as { addresses: unknown[] };
    expect(list.addresses.length).toBe(1);
  });

  it("forgets addresses made by name before one per tag", async () => {
    const { env, objects } = accounts();
    const key = await signIn(env);
    const made = (await (await call(env, "POST", "/account/newsletters", key, { tagHash: TECH })).json()) as { code: string; address: string };
    await receiveEmail(message(made.address, rawEmail()), env);
    // Made the old way: a tag's name instead of its id.
    for (const object of objects.values()) {
      const list = (await object.storage.get<{ code: string; tagHash?: string | null; tag?: string; createdAt: number }[]>("newsletters")) ?? null;
      if (list) await object.storage.put("newsletters", list.map(({ code, createdAt }) => ({ code, tag: "Tech", createdAt })));
    }
    expect(((await (await call(env, "GET", "/account/newsletters", key)).json()) as { addresses: unknown[] }).addresses).toEqual([]);
    expect((await call(env, "POST", "/account/newsletters", key, { tagHash: TECH })).status).toBe(201);
    expect(objects.get(`newsletter:${made.code}`)!.storage.map.size).toBe(0);
  });

  it("deleting the account deletes its addresses", async () => {
    const { env, objects } = accounts();
    const key = await signIn(env);
    const address = (await (await call(env, "POST", "/account/newsletters", key, { tagHash: TECH })).json()) as { code: string; address: string };
    await receiveEmail(message(address.address, rawEmail()), env);
    expect((await call(env, "DELETE", "/account", key)).status).toBe(204);
    expect(objects.get(`newsletter:${address.code}`)!.storage.map.size).toBe(0);
  });
});
