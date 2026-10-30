import { describe, expect, it } from "vitest";
import { Account, type Storage } from "../src/account";
import { handle, type Env } from "../src/index";
import { fromBase64Url, toBase64Url, vapidJwt } from "../src/push";
import { previewOf, shareOf } from "../src/share";

const APP = "https://app.example";
const MASTER = "a-test-master-key-of-at-least-32-characters";

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
  async list<T>({ prefix }: { prefix: string }) {
    const keys = [...this.map.keys()].filter((k) => k.startsWith(prefix)).sort();
    return new Map(keys.map((k) => [k, this.map.get(k) as T]));
  }
  async deleteAll() {
    this.map.clear();
  }
}

/** Accounts in memory, and a way to sign in with an email link. */
function setup(vapid?: { publicKey: string; privateKey: string }) {
  const objects = new Map<string, Account>();
  const namespace = {
    idFromName: (name: string) => name,
    get: (id: string) => {
      if (!objects.has(id)) objects.set(id, new Account({ storage: new MemoryStorage(), getWebSockets: () => [] } as unknown as DurableObjectState, env));
      const account = objects.get(id)!;
      return { fetch: (input: string, init?: RequestInit) => account.fetch(new Request(input, init)) };
    },
  } as unknown as DurableObjectNamespace;
  const env: Env = { ALLOWED_ORIGINS: APP, ACCOUNTS: namespace, ACCOUNT_KEY: MASTER, RESEND_API_KEY: "re", WEB_APP_ORIGIN: APP, VAPID_PUBLIC_KEY: vapid?.publicKey, VAPID_PRIVATE_KEY: vapid?.privateKey };
  let lastText = "";
  // Gravatar's profiles (#234), by the SHA-256 of the address; Resend's emails otherwise.
  const profiles = new Map<string, string>();
  const gravatarAsked: string[] = [];
  // Push services (#235): what was poked, and addresses that are gone.
  const poked: { url: string; headers: Record<string, string> }[] = [];
  const gone = new Set<string>();
  const fetcher = async (input: string, init?: RequestInit) => {
    if (input.startsWith("https://push.example/")) {
      poked.push({ url: input, headers: Object.fromEntries(new Headers(init?.headers).entries()) });
      return new Response(null, { status: gone.has(input) ? 410 : 201 });
    }
    if (input.startsWith("https://api.gravatar.com/v3/profiles/")) {
      const hash = input.slice("https://api.gravatar.com/v3/profiles/".length);
      gravatarAsked.push(hash);
      const name = profiles.get(hash);
      return name === undefined ? new Response('{"error":"Profile not found"}', { status: 404 }) : new Response(JSON.stringify({ hash, display_name: name }));
    }
    lastText = JSON.parse(String(init?.body)).text;
    return new Response("{}");
  };
  const call = (method: string, path: string, key: string | null, body?: unknown) =>
    handle(
      new Request("https://worker.example" + path, {
        method,
        headers: { Origin: APP, ...(key ? { Authorization: `Bearer ${key}` } : {}) },
        body: body === undefined ? undefined : JSON.stringify(body),
      }),
      env,
      fetcher,
    );
  const signIn = async (email: string) => {
    await call("POST", "/account/email/start", null, { email });
    const token = /#token=(\S+)/.exec(lastText)![1];
    return ((await (await call("POST", "/account/email/finish", null, { token })).json()) as { key: string }).key;
  };
  const addProfile = async (email: string, name: string) => profiles.set(await sha256(email), name);
  return { call, signIn, addProfile, gravatarAsked, poked, gone };
}

async function sha256(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

const article = { url: "https://example.com/a", title: "An article" };

describe("sharing with other Content users (#167)", () => {
  it("says whether an address has an account", async () => {
    const { call, signIn } = setup();
    const ann = await signIn("ann@example.com");
    await signIn("bob@example.com");
    expect(await (await call("POST", "/account/share/check", ann, { email: "Bob@Example.com" })).json()).toEqual({ exists: true });
    expect(await (await call("POST", "/account/share/check", ann, { email: "nobody@example.com" })).json()).toEqual({ exists: false });
    expect((await call("POST", "/account/share/check", null, { email: "bob@example.com" })).status).toBe(401);
  });

  it("puts shared articles in the other account's inbox until a device takes them in", async () => {
    const { call, signIn } = setup();
    const ann = await signIn("ann@example.com");
    const bob = await signIn("bob@example.com");
    const sent = await call("POST", "/account/share", ann, { to: "bob@example.com", from: { email: "ann@example.com", name: "Ann" }, items: [article], message: "  Read this one!  " });
    expect(sent.status).toBe(204);
    const inbox = (await (await call("GET", "/account/inbox", bob)).json()) as { messages: { id: string; from: unknown; items: unknown[] }[] };
    expect(inbox.messages).toHaveLength(1);
    expect(inbox.messages[0].from).toEqual({ email: "ann@example.com", name: "Ann" });
    expect(inbox.messages[0].items).toEqual([{ url: "https://example.com/a", title: "An article", imageUrl: null, description: "" }]);
    expect((inbox.messages[0] as unknown as { message: string }).message).toBe("Read this one!");
    // Ann's own inbox stays empty.
    expect(((await (await call("GET", "/account/inbox", ann)).json()) as { messages: unknown[] }).messages).toEqual([]);
    expect((await call("POST", "/account/inbox/done", bob, { ids: [inbox.messages[0].id] })).status).toBe(204);
    expect(((await (await call("GET", "/account/inbox", bob)).json()) as { messages: unknown[] }).messages).toEqual([]);
  });

  it("refuses a sender that isn't who they say, and an address without an account", async () => {
    const { call, signIn } = setup();
    const ann = await signIn("ann@example.com");
    await signIn("bob@example.com");
    expect((await call("POST", "/account/share", ann, { to: "bob@example.com", from: { email: "bob@example.com", name: "Bob" }, items: [article] })).status).toBe(403);
    expect((await call("POST", "/account/share", ann, { to: "nobody@example.com", from: { email: "ann@example.com", name: "" }, items: [article] })).status).toBe(404);
    expect((await call("POST", "/account/share", ann, { to: "bob@example.com", from: { email: "ann@example.com" }, items: [] })).status).toBe(400);
    expect((await call("GET", "/account/inbox", "0".repeat(32) + "." + "a".repeat(43))).status).toBe(401);
  });

  it("finds the name on an address's Gravatar profile (#234)", async () => {
    const { call, signIn, addProfile, gravatarAsked } = setup();
    const ann = await signIn("ann@example.com");
    await addProfile("bob@example.com", " Bob Builder ");
    expect(await (await call("POST", "/account/profile", ann, { email: " Bob@Example.com " })).json()).toEqual({ name: "Bob Builder" });
    // Asked by the hash of the address, never the address itself.
    expect(gravatarAsked).toEqual([await sha256("bob@example.com")]);
    expect(await (await call("POST", "/account/profile", ann, { email: "nobody@example.com" })).json()).toEqual({ name: "" });
    expect((await call("POST", "/account/profile", ann, { email: "not an address" })).status).toBe(400);
    expect((await call("POST", "/account/profile", null, { email: "bob@example.com" })).status).toBe(401);
    expect((await call("GET", "/account/profile", ann)).status).toBe(405);
  });

  it("names the sender from their Gravatar profile when the device sent no name (#234)", async () => {
    const { call, signIn, addProfile } = setup();
    const ann = await signIn("ann@example.com");
    const bob = await signIn("bob@example.com");
    await addProfile("ann@example.com", "Ann");
    expect((await call("POST", "/account/share", ann, { to: "bob@example.com", from: { email: "ann@example.com", name: "" }, items: [article] })).status).toBe(204);
    const inbox = (await (await call("GET", "/account/inbox", bob)).json()) as { messages: { from: unknown }[] };
    expect(inbox.messages[0].from).toEqual({ email: "ann@example.com", name: "Ann" });
  });

  it("pokes the receiver's devices when something is shared, and forgets addresses that are gone (#235)", async () => {
    const { call, signIn, poked, gone } = setup(await vapidKeys());
    const ann = await signIn("ann@example.com");
    const bob = await signIn("bob@example.com");
    const bobPhone = await signIn("bob@example.com");
    expect(((await (await call("GET", "/account/push/key", bob)).json()) as { publicKey: string }).publicKey).toMatch(/^[A-Za-z0-9_-]{87}$/);
    expect((await call("PUT", "/account/push", bob, { kind: "web", endpoint: "https://push.example/bob-browser" })).status).toBe(204);
    expect((await call("PUT", "/account/push", bobPhone, { kind: "web", endpoint: "https://push.example/bob-old" })).status).toBe(204);
    expect((await call("PUT", "/account/push", bob, { kind: "web", endpoint: "http://insecure.example" })).status).toBe(400);
    expect((await call("PUT", "/account/push", null, { kind: "web", endpoint: "https://push.example/x" })).status).toBe(401);
    gone.add("https://push.example/bob-old");

    await call("POST", "/account/share", ann, { to: "bob@example.com", from: { email: "ann@example.com", name: "Ann" }, items: [article] });
    expect(poked.map((p) => p.url).sort()).toEqual(["https://push.example/bob-browser", "https://push.example/bob-old"]);
    expect(poked[0].headers.authorization).toMatch(/^vapid t=[\w-]+\.[\w-]+\.[\w-]+, k=[\w-]{87}$/);
    expect(poked[0].headers.ttl).toBe("86400");

    // The gone one was forgotten; signing out forgets the device's too.
    poked.length = 0;
    await call("POST", "/account/share", ann, { to: "bob@example.com", from: { email: "ann@example.com", name: "Ann" }, items: [article] });
    expect(poked.map((p) => p.url)).toEqual(["https://push.example/bob-browser"]);
    expect((await call("DELETE", "/account/push", bob)).status).toBe(204);
    poked.length = 0;
    await call("POST", "/account/share", ann, { to: "bob@example.com", from: { email: "ann@example.com", name: "Ann" }, items: [article] });
    expect(poked).toEqual([]);
  });

  it("sharing with yourself lands in your own inbox and pokes all your devices, the sending one too (#251)", async () => {
    const { call, signIn, poked } = setup(await vapidKeys());
    const phone = await signIn("ann@example.com");
    const laptop = await signIn("ann@example.com");
    expect((await call("PUT", "/account/push", phone, { kind: "web", endpoint: "https://push.example/ann-phone" })).status).toBe(204);
    expect((await call("PUT", "/account/push", laptop, { kind: "web", endpoint: "https://push.example/ann-laptop" })).status).toBe(204);
    expect((await call("POST", "/account/share", phone, { to: "ann@example.com", from: { email: "ann@example.com", name: "Ann" }, items: [article] })).status).toBe(204);
    expect(poked.map((p) => p.url).sort()).toEqual(["https://push.example/ann-laptop", "https://push.example/ann-phone"]);
    expect(((await (await call("GET", "/account/inbox", laptop)).json()) as { messages: unknown[] }).messages).toHaveLength(1);
  });

  it("has no notifications without the VAPID keys", async () => {
    const { call, signIn, poked } = setup();
    const ann = await signIn("ann@example.com");
    const bob = await signIn("bob@example.com");
    expect((await call("GET", "/account/push/key", bob)).status).toBe(404);
    await call("PUT", "/account/push", bob, { kind: "web", endpoint: "https://push.example/bob" });
    expect((await call("POST", "/account/share", ann, { to: "bob@example.com", from: { email: "ann@example.com", name: "Ann" }, items: [article] })).status).toBe(204);
    expect(poked).toEqual([]);
  });

  it("signs the VAPID token so the push service can check it", async () => {
    const keys = await vapidKeys();
    const jwt = await vapidJwt("https://push.example", "https://app.example", keys.publicKey, keys.privateKey, 1_000_000_000);
    const [header, payload, signature] = jwt.split(".");
    expect(JSON.parse(new TextDecoder().decode(fromBase64Url(header)))).toEqual({ typ: "JWT", alg: "ES256" });
    expect(JSON.parse(new TextDecoder().decode(fromBase64Url(payload)))).toEqual({ aud: "https://push.example", exp: 1_000_000 + 12 * 60 * 60, sub: "https://app.example" });
    const verifier = await crypto.subtle.importKey("raw", fromBase64Url(keys.publicKey), { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]);
    expect(await crypto.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, verifier, fromBase64Url(signature), new TextEncoder().encode(`${header}.${payload}`))).toBe(true);
  });

  it("cleans up what is shared", () => {
    expect(shareOf({ to: "b@x.be", from: { email: "a@x.be" }, items: [{ url: "javascript:alert(1)" }] })).toBeNull();
    const ok = shareOf({ to: " B@X.be ", from: { email: "a@x.be", name: "  Ann " }, items: [{ url: "https://x.be/1", imageUrl: "http://x.be/p.jpg" }] })!;
    expect(ok.to).toBe("b@x.be");
    expect(ok.from.name).toBe("Ann");
    expect(ok.items[0]).toEqual({ url: "https://x.be/1", title: "https://x.be/1", imageUrl: null, description: "" });
  });

  it("previews the first article for a phone's notification", () => {
    const share = shareOf({
      to: "b@x.be",
      from: { email: "a@x.be", name: "" },
      items: [{ url: "https://x.be/1", title: "One", imageUrl: "https://x.be/p.jpg", description: "Its intro." }, { url: "https://x.be/2" }],
      message: "Look",
    })!;
    expect(previewOf(share)).toEqual({ title: "One", intro: "Its intro.", imageUrl: "https://x.be/p.jpg", from: "a@x.be", count: 2 });
    // Without an intro, the message stands in.
    expect(previewOf({ ...share, items: [{ ...share.items[0], description: "" }] }).intro).toBe("Look");
  });
});

/** A VAPID key pair as web-push tools write it: the public point and the private scalar, base64url. */
async function vapidKeys(): Promise<{ publicKey: string; privateKey: string }> {
  const pair = (await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"])) as CryptoKeyPair;
  const raw = new Uint8Array((await crypto.subtle.exportKey("raw", pair.publicKey)) as ArrayBuffer);
  const jwk = (await crypto.subtle.exportKey("jwk", pair.privateKey)) as JsonWebKey;
  return { publicKey: toBase64Url(raw), privateKey: jwk.d! };
}
