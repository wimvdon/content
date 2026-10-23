import { describe, expect, it } from "vitest";
import { Account, type Storage } from "../src/account";
import { emailContent, LINK_MS, makeToken, normalEmail, readToken } from "../src/email";
import { handle, type Env } from "../src/index";

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
    return new Map([...this.map.entries()].filter(([k]) => k.startsWith(prefix)) as [string, T][]);
  }
  async deleteAll() {
    this.map.clear();
  }
}

/** Durable Objects in memory, and the emails Resend was asked to send. */
function setup() {
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
  const env: Env = {
    ALLOWED_ORIGINS: APP,
    INSTAPAPER_CLIENT_ID: "client-id",
    INSTAPAPER_CLIENT_SECRET: "client-secret",
    INSTAPAPER_REDIRECT_URI: APP + "/instapaper/callback",
    ACCOUNTS: namespace,
    ACCOUNT_KEY: MASTER,
    RESEND_API_KEY: "re_test",
    WEB_APP_ORIGIN: APP,
  };
  const sent: { url: string; body: { to: string[]; text: string; from: string }; auth: string | null }[] = [];
  const fetcher = async (input: string, init?: RequestInit) => {
    if (input.startsWith("https://api.resend.com/")) {
      sent.push({ url: input, body: JSON.parse(String(init?.body)), auth: new Headers(init?.headers).get("Authorization") });
      return new Response(JSON.stringify({ id: "x" }));
    }
    // An Instapaper sign-in, for linking an existing account.
    return new Response(JSON.stringify({ access_token: "tok", user: { id: 42, username: "me" } }));
  };
  const post = (path: string, body: unknown, key?: string) =>
    handle(
      new Request("https://worker.example" + path, {
        method: "POST",
        headers: { Origin: APP, ...(key ? { Authorization: `Bearer ${key}` } : {}) },
        body: JSON.stringify(body),
      }),
      env,
      fetcher,
    );
  /** The token of the last link sent. */
  const lastToken = () => /#token=([^\s]+)/.exec(sent[sent.length - 1].body.text)![1];
  return { env, sent, post, lastToken, fetcher };
}

async function readDoc(env: Env, key: string) {
  return handle(new Request("https://worker.example/account/sync", { headers: { Origin: APP, Authorization: `Bearer ${key}` } }), env, async () => new Response());
}

describe("signing in with an email link (#166)", () => {
  it("accepts addresses and keeps them in one form", () => {
    expect(normalEmail("  Me@Example.COM ")).toBe("me@example.com");
    expect(normalEmail("not an address")).toBeNull();
    expect(normalEmail("a@b")).toBeNull();
    expect(normalEmail(42)).toBeNull();
    expect(normalEmail("x".repeat(250) + "@a.be")).toBeNull();
  });

  it("signs tokens that can't be changed and end", async () => {
    const claims = { email: "me@example.com", expires: 1000, id: "abc" };
    const token = await makeToken(MASTER, claims);
    expect(await readToken(MASTER, token, 999)).toEqual(claims);
    expect(await readToken(MASTER, token, 1001)).toBeNull();
    expect(await readToken("another-master-key-of-at-least-32-characters", token, 0)).toBeNull();
    const [payload, mac] = token.split(".");
    const forged = btoa(JSON.stringify({ ...claims, email: "you@example.com" })).replace(/=+$/, "").replace(/\+/g, "-").replace(/\//g, "_");
    expect(await readToken(MASTER, `${forged}.${mac}`, 0)).toBeNull();
    expect(await readToken(MASTER, `${payload}`, 0)).toBeNull();
  });

  it("emails a link that signs in once", async () => {
    const { env, sent, post, lastToken } = setup();
    const start = await post("/account/email/start", { email: "Me@Example.com" });
    expect(start.status).toBe(204);
    expect(sent).toHaveLength(1);
    expect(sent[0].body.to).toEqual(["me@example.com"]);
    expect(sent[0].auth).toBe("Bearer re_test");
    expect(sent[0].body.text).toContain(`${APP}/signin#token=`);
    const token = lastToken();
    const finish = await post("/account/email/finish", { token });
    expect(finish.status).toBe(200);
    const { key, email } = (await finish.json()) as { key: string; email: string };
    expect(email).toBe("me@example.com");
    expect((await readDoc(env, key)).status).toBe(200);
    // The same link again: used.
    expect((await post("/account/email/finish", { token })).status).toBe(410);
  });

  it("opens the same account every time", async () => {
    const { post, lastToken } = setup();
    await post("/account/email/start", { email: "me@example.com" });
    const first = (await (await post("/account/email/finish", { token: lastToken() })).json()) as { key: string };
    await post("/account/email/start", { email: "ME@example.com" });
    const second = (await (await post("/account/email/finish", { token: lastToken() })).json()) as { key: string };
    expect(second.key.split(".")[0]).toBe(first.key.split(".")[0]);
    expect(second.key).not.toBe(first.key);
  });

  it("adds an address to an Instapaper account, which it then opens", async () => {
    const { env, post, lastToken, fetcher } = setup();
    const signIn = await handle(new Request("https://worker.example/instapaper/token", { method: "POST", headers: { Origin: APP }, body: JSON.stringify({ code: "abc" }) }), env, fetcher);
    const instapaperKey = ((await signIn.json()) as { account: { key: string } }).account.key;
    expect((await post("/account/email/add", { email: "me@example.com" })).status).toBe(401);
    expect((await post("/account/email/add", { email: "me@example.com" }, instapaperKey)).status).toBe(204);
    const added = await post("/account/email/finish", { token: lastToken() });
    expect(added.status).toBe(200);
    expect(await added.json()).toEqual({ email: "me@example.com" });
    await post("/account/email/start", { email: "me@example.com" });
    const { key } = (await (await post("/account/email/finish", { token: lastToken() })).json()) as { key: string };
    expect(key.split(".")[0]).toBe(instapaperKey.split(".")[0]);
  });

  it("doesn't move an address that already opens another account", async () => {
    const { env, post, lastToken, fetcher } = setup();
    await post("/account/email/start", { email: "me@example.com" });
    await post("/account/email/finish", { token: lastToken() });
    const signIn = await handle(new Request("https://worker.example/instapaper/token", { method: "POST", headers: { Origin: APP }, body: JSON.stringify({ code: "abc" }) }), env, fetcher);
    const instapaperKey = ((await signIn.json()) as { account: { key: string } }).account.key;
    await post("/account/email/add", { email: "me@example.com" }, instapaperKey);
    expect((await post("/account/email/finish", { token: lastToken() })).status).toBe(409);
  });

  it("refuses old links, bad addresses, other websites and other methods", async () => {
    const { env, post, sent } = setup();
    const old = await makeToken(MASTER, { email: "me@example.com", expires: Date.now() - 1, id: "old" });
    expect((await post("/account/email/finish", { token: old })).status).toBe(410);
    expect((await post("/account/email/finish", { token: "nonsense" })).status).toBe(410);
    expect((await post("/account/email/start", { email: "nope" })).status).toBe(400);
    expect(sent).toHaveLength(0);
    const elsewhere = await handle(new Request("https://worker.example/account/email/start", { method: "POST", headers: { Origin: "https://evil.example" }, body: "{}" }), env, async () => new Response());
    expect(elsewhere.status).toBe(403);
    const get = await handle(new Request("https://worker.example/account/email/start", { headers: { Origin: APP } }), env, async () => new Response());
    expect(get.status).toBe(405);
    expect((await post("/account/email/other", {})).status).toBe(404);
  });

  it("says when email isn't set up", async () => {
    const { env, fetcher } = setup();
    const response = await handle(
      new Request("https://worker.example/account/email/start", { method: "POST", headers: { Origin: APP }, body: JSON.stringify({ email: "me@example.com" }) }),
      { ...env, RESEND_API_KEY: undefined },
      fetcher,
    );
    expect(response.status).toBe(503);
  });

  it("writes an email that says what the link does and for how long", () => {
    const { subject, text, html } = emailContent("https://app.example/signin#token=a&b", "signin");
    expect(subject).toBe("Sign in to Content");
    expect(text).toContain("30 minutes");
    // English only (#219).
    expect(text).not.toContain("Open deze link");
    expect(html).not.toContain('lang="nl"');
    expect(html).toContain('href="https://app.example/signin#token=a&amp;b"');
    expect(LINK_MS).toBe(30 * 60 * 1000);
  });
});
