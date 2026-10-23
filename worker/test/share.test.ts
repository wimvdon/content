import { describe, expect, it } from "vitest";
import { Account, type Storage } from "../src/account";
import { handle, type Env } from "../src/index";
import { shareOf } from "../src/share";

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
function setup() {
  const objects = new Map<string, Account>();
  const namespace = {
    idFromName: (name: string) => name,
    get: (id: string) => {
      if (!objects.has(id)) objects.set(id, new Account({ storage: new MemoryStorage(), getWebSockets: () => [] } as unknown as DurableObjectState, env));
      const account = objects.get(id)!;
      return { fetch: (input: string, init?: RequestInit) => account.fetch(new Request(input, init)) };
    },
  } as unknown as DurableObjectNamespace;
  const env: Env = { ALLOWED_ORIGINS: APP, ACCOUNTS: namespace, ACCOUNT_KEY: MASTER, RESEND_API_KEY: "re", WEB_APP_ORIGIN: APP };
  let lastText = "";
  const fetcher = async (_: string, init?: RequestInit) => {
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
  return { call, signIn };
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
    const sent = await call("POST", "/account/share", ann, { to: "bob@example.com", from: { email: "ann@example.com", name: "Ann" }, items: [article], tag: "Reading club", message: "  Read this one!  " });
    expect(sent.status).toBe(204);
    const inbox = (await (await call("GET", "/account/inbox", bob)).json()) as { messages: { id: string; from: unknown; items: unknown[]; tag: string }[] };
    expect(inbox.messages).toHaveLength(1);
    expect(inbox.messages[0].from).toEqual({ email: "ann@example.com", name: "Ann" });
    expect(inbox.messages[0].items).toEqual([{ url: "https://example.com/a", title: "An article", imageUrl: null, description: "" }]);
    expect(inbox.messages[0].tag).toBe("Reading club");
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

  it("cleans up what is shared", () => {
    expect(shareOf({ to: "b@x.be", from: { email: "a@x.be" }, items: [{ url: "javascript:alert(1)" }] })).toBeNull();
    const ok = shareOf({ to: " B@X.be ", from: { email: "a@x.be", name: "  Ann " }, items: [{ url: "https://x.be/1", imageUrl: "http://x.be/p.jpg" }], tag: "  " })!;
    expect(ok.to).toBe("b@x.be");
    expect(ok.from.name).toBe("Ann");
    expect(ok.items[0]).toEqual({ url: "https://x.be/1", title: "https://x.be/1", imageUrl: null, description: "" });
    expect(ok.tag).toBeNull();
  });
});
