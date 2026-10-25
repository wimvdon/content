import { describe, expect, it } from "vitest";
import { Account, AccountData, MAX_DEVICES, MAX_ROWS, MAX_TEXT_BYTES, accountIdOf, addDeviceKey, documentText, itemChanges, knownVersionOf, sinceOf, type ItemsPage, type ItemsWritten, type Storage } from "../src/account";
import { handle, type Env } from "../src/index";
import { TOKEN_URL } from "../src/instapaper";

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
  async list<T>({ prefix, start, limit }: { prefix: string; start?: string; limit?: number }) {
    const keys = [...this.map.keys()].filter((k) => k.startsWith(prefix) && (start === undefined || k >= start)).sort();
    return new Map(keys.slice(0, limit ?? keys.length).map((k) => [k, this.map.get(k) as T]));
  }
  async deleteAll() {
    this.map.clear();
  }
}

/** Durable Objects in memory: one Account per name, as Cloudflare would keep them. */
function accounts() {
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
  };
  return { env, objects };
}

const doc = (read: Record<string, number> = {}) => ({ format: "rss-reader-sync", version: 2, folders: [], feeds: [], read, settings: {} });

/** A new device key for account `userId`, as a sign-in (email.ts) gives one. */
async function signIn(env: Env, userId = 42): Promise<string> {
  return (await addDeviceKey(env, await accountIdOf(MASTER, "email", userId)))!;
}

function call(env: Env, method: string, path: string, key: string | null, body?: unknown, headers: Record<string, string> = {}) {
  return handle(
    new Request("https://worker.example" + path, {
      method,
      headers: { Origin: APP, ...(key ? { Authorization: `Bearer ${key}` } : {}), ...headers },
      body: body === undefined ? undefined : JSON.stringify(body),
    }),
    env,
    async () => new Response("unused", { status: 500 }),
  );
}

describe("signing in", () => {
  it("gives every sign-in a new device key to the same account", async () => {
    const { env } = accounts();
    const first = await signIn(env);
    const second = await signIn(env);
    expect(first).toMatch(/^[0-9a-f]{32}\.[A-Za-z0-9_-]{43}$/);
    expect(first).not.toBe(second);
    expect(first.split(".")[0]).toBe(second.split(".")[0]);
    expect((await signIn(env, 43)).split(".")[0]).not.toBe(first.split(".")[0]);
  });

  it("signing in with Instapaper opens no Content account (#215)", async () => {
    const { env } = accounts();
    const response = await handle(
      new Request("https://worker.example/instapaper/token", { method: "POST", body: JSON.stringify({ code: "abc" }) }),
      env,
      async (input) => {
        expect(input).toBe(TOKEN_URL);
        return new Response(JSON.stringify({ access_token: "tok", user: { id: 42 } }));
      },
    );
    expect(await response.json()).toEqual({ access_token: "tok", user: { id: 42, username: "" } });
  });

  it("derives account ids that depend on the secret", async () => {
    const a = await accountIdOf(MASTER, "instapaper", 42);
    expect(a).toBe(await accountIdOf(MASTER, "instapaper", 42));
    expect(a).not.toBe(await accountIdOf(MASTER + "x", "instapaper", 42));
    await expect(accountIdOf("short", "instapaper", 42)).rejects.toThrow();
  });
});

describe("/account/sync", () => {
  it("starts empty, saves, and reads back", async () => {
    const { env } = accounts();
    const key = await signIn(env);
    let response = await call(env, "GET", "/account/sync", key);
    expect(response.status).toBe(200);
    expect(response.headers.get("Access-Control-Allow-Origin")).toBe(APP);
    expect(await response.json()).toEqual({ version: 0, data: null });

    response = await call(env, "PUT", "/account/sync", key, { baseVersion: 0, data: doc({ "f a": 1 }) });
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ version: 1 });

    response = await call(env, "GET", "/account/sync", await signIn(env));
    expect(await response.json()).toEqual({ version: 1, data: doc({ "f a": 1 }) });
  });

  it("answers 304 when the device already has the version", async () => {
    const { env } = accounts();
    const key = await signIn(env);
    await call(env, "PUT", "/account/sync", key, { baseVersion: 0, data: doc() });
    const response = await call(env, "GET", "/account/sync", key, undefined, { "If-None-Match": '"1"' });
    expect(response.status).toBe(304);
    expect(response.headers.get("ETag")).toBe('"1"');
  });

  it("refuses a save based on an old version and returns the current document", async () => {
    const { env } = accounts();
    const phone = await signIn(env);
    const laptop = await signIn(env);
    await call(env, "PUT", "/account/sync", phone, { baseVersion: 0, data: doc({ "f a": 1 }) });
    const response = await call(env, "PUT", "/account/sync", laptop, { baseVersion: 0, data: doc({ "f b": 2 }) });
    expect(response.status).toBe(409);
    expect(await response.json()).toEqual({ version: 1, data: doc({ "f a": 1 }) });
  });

  it("refuses what isn't a sync document", async () => {
    const { env } = accounts();
    const key = await signIn(env);
    expect((await call(env, "PUT", "/account/sync", key, { baseVersion: 0, data: { hello: 1 } })).status).toBe(400);
    expect((await call(env, "PUT", "/account/sync", key, { data: doc() })).status).toBe(400);
  });

  it("refuses unknown keys, other websites, and other methods", async () => {
    const { env } = accounts();
    const key = await signIn(env);
    const [account] = key.split(".");
    expect((await call(env, "GET", "/account/sync", null)).status).toBe(401);
    expect((await call(env, "GET", "/account/sync", `${account}.${"A".repeat(43)}`)).status).toBe(401);
    expect((await call(env, "GET", "/account/sync", key, undefined, { Origin: "https://evil.example" })).status).toBe(403);
    expect((await call(env, "POST", "/account/sync", key)).status).toBe(405);
    expect((await call(env, "GET", "/account/device", key)).status).toBe(404);
  });

  it("works for the Android app, which sends no Origin", async () => {
    const { env } = accounts();
    const key = await signIn(env);
    const response = await handle(
      new Request("https://worker.example/account/sync", { headers: { Authorization: `Bearer ${key}` } }),
      env,
      async () => new Response(null, { status: 500 }),
    );
    expect(response.status).toBe(200);
  });

  it("stores the document encrypted", async () => {
    const { env, objects } = accounts();
    const key = await signIn(env);
    await call(env, "PUT", "/account/sync", key, { baseVersion: 0, data: doc({ "https://secret.example/feed x": 1 }) });
    const stored = [...objects.values()][0].storage.map.get("document") as { data: Uint8Array };
    expect(new TextDecoder().decode(stored.data)).not.toContain("secret.example");
  });

  it("answers 503 when accounts aren't set up", async () => {
    const { env } = accounts();
    const key = await signIn(env);
    expect((await call({ ...env, ACCOUNTS: undefined }, "GET", "/account/sync", key)).status).toBe(503);
  });
});

describe("/account/texts", () => {
  it("keeps the first device's text of an article, and gives every device that one", async () => {
    const { env } = accounts();
    const phone = await signIn(env);
    const laptop = await signIn(env);
    const key = "example.com/story";
    expect((await call(env, "GET", `/account/texts?key=${encodeURIComponent(key)}`, phone)).status).toBe(404);
    let response = await call(env, "POST", "/account/texts", phone, { key, html: "<p>From the phone</p>" });
    expect(response.status).toBe(201);
    expect(await response.json()).toEqual({ html: "<p>From the phone</p>" });
    // The laptop extracted the page too, a little differently: the stored one wins and comes back.
    response = await call(env, "POST", "/account/texts", laptop, { key, html: "<p>From the laptop</p>" });
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ html: "<p>From the phone</p>" });
    response = await call(env, "GET", `/account/texts?key=${encodeURIComponent(key)}`, laptop);
    expect(response.headers.get("Access-Control-Allow-Origin")).toBe(APP);
    expect(await response.json()).toEqual({ html: "<p>From the phone</p>" });
  });

  it("keeps texts per account, and refuses what isn't a text", async () => {
    const { env } = accounts();
    const mine = await signIn(env, 42);
    const other = await signIn(env, 43);
    await call(env, "POST", "/account/texts", mine, { key: "a/1", html: "<p>Mine</p>" });
    expect((await call(env, "GET", "/account/texts?key=a%2F1", other)).status).toBe(404);
    expect((await call(env, "GET", "/account/texts", mine)).status).toBe(400);
    expect((await call(env, "POST", "/account/texts", mine, { key: "a/2" })).status).toBe(400);
    expect((await call(env, "POST", "/account/texts", mine, { key: "a/3", html: "x".repeat(MAX_TEXT_BYTES + 1) })).status).toBe(413);
    expect((await call(env, "GET", "/account/texts?key=a%2F1", null)).status).toBe(401);
  });

  it("goes with the rest of the account's data", async () => {
    const { env } = accounts();
    const phone = await signIn(env);
    await call(env, "POST", "/account/texts", phone, { key: "a/1", html: "<p>Text</p>" });
    await call(env, "DELETE", "/account", phone);
    const again = await signIn(env);
    expect((await call(env, "GET", "/account/texts?key=a%2F1", again)).status).toBe(404);
  });
});

describe("signing out and deleting", () => {
  it("forgets only this device on sign-out", async () => {
    const { env } = accounts();
    const phone = await signIn(env);
    const laptop = await signIn(env);
    expect((await call(env, "POST", "/account/signout", phone)).status).toBe(204);
    expect((await call(env, "GET", "/account/sync", phone)).status).toBe(401);
    expect((await call(env, "GET", "/account/sync", laptop)).status).toBe(200);
  });

  it("deletes the document and every device", async () => {
    const { env } = accounts();
    const phone = await signIn(env);
    const laptop = await signIn(env);
    await call(env, "PUT", "/account/sync", phone, { baseVersion: 0, data: doc() });
    expect((await call(env, "DELETE", "/account", phone)).status).toBe(204);
    expect((await call(env, "GET", "/account/sync", laptop)).status).toBe(401);
    const again = await signIn(env);
    expect(await (await call(env, "GET", "/account/sync", again)).json()).toEqual({ version: 0, data: null });
  });
});

describe("AccountData", () => {
  it("lets only one of two simultaneous saves win", async () => {
    const data = new AccountData(new MemoryStorage(), MASTER, "0".repeat(32));
    const results = await Promise.all([data.write(0, JSON.stringify(doc({ a: 1 }))), data.write(0, JSON.stringify(doc({ b: 1 })))]);
    expect(results.map((r) => r.kind).sort()).toEqual(["conflict", "saved"]);
  });

  it("can't open a document under another account id", async () => {
    const storage = new MemoryStorage();
    await new AccountData(storage, MASTER, "0".repeat(32)).write(0, JSON.stringify(doc()));
    await expect(new AccountData(storage, MASTER, "1".repeat(32)).read(null)).rejects.toThrow();
  });

  it("keeps at most MAX_DEVICES devices, forgetting the one unused the longest", async () => {
    let now = 0;
    const data = new AccountData(new MemoryStorage(), MASTER, "0".repeat(32), () => now);
    const first = await data.addDevice();
    for (let i = 0; i < MAX_DEVICES; i++) {
      now += 1;
      await data.addDevice();
    }
    expect(await data.knows(first)).toBe(false);
  });
});

describe("helpers", () => {
  it("reads the version from If-None-Match", () => {
    expect(knownVersionOf('"3"')).toBe(3);
    expect(knownVersionOf('W/"12"')).toBe(12);
    expect(knownVersionOf("7")).toBe(7);
    expect(knownVersionOf("*")).toBeNull();
    expect(knownVersionOf(null)).toBeNull();
  });

  it("accepts only sync documents", () => {
    expect(documentText(JSON.stringify({ baseVersion: 2, data: doc() }))).toEqual({ baseVersion: 2, text: JSON.stringify(doc()) });
    expect(documentText("nope")).toBeNull();
    expect(documentText(JSON.stringify({ baseVersion: -1, data: doc() }))).toBeNull();
    expect(documentText(JSON.stringify({ baseVersion: 1.5, data: doc() }))).toBeNull();
  });
});

describe("/account/items (#106)", () => {
  const row = (key: string, baseVersion: number, data: unknown) => ({ key, baseVersion, data });
  const items = async (env: Env, key: string, since = 0) => (await (await call(env, "GET", `/account/items?since=${since}`, key)).json()) as ItemsPage;
  const write = async (env: Env, key: string, rows: unknown[]) => {
    const response = await call(env, "POST", "/account/items", key, { rows });
    expect(response.status).toBe(200);
    return (await response.json()) as ItemsWritten;
  };

  it("starts empty, saves rows and sends the changes since a version", async () => {
    const { env } = accounts();
    const phone = await signIn(env);
    const laptop = await signIn(env);
    expect(await items(env, phone)).toEqual({ version: 0, rows: [], more: false });

    expect(await write(env, phone, [row("item:a", 0, { title: "A" }), row("read:f 1", 0, { time: 5 })])).toEqual({
      version: 1,
      saved: [
        { key: "item:a", version: 1 },
        { key: "read:f 1", version: 1 },
      ],
      conflicts: [],
    });
    expect(await write(env, phone, [row("item:a", 1, { title: "A2" })])).toMatchObject({ version: 2 });

    const all = await items(env, laptop);
    expect(all.version).toBe(2);
    expect(all.rows).toEqual([
      { key: "read:f 1", version: 1, data: { time: 5 } },
      { key: "item:a", version: 2, data: { title: "A2" } },
    ]);
    expect((await items(env, laptop, 1)).rows).toEqual([{ key: "item:a", version: 2, data: { title: "A2" } }]);
    expect(await items(env, laptop, 2)).toEqual({ version: 2, rows: [], more: false });
  });

  it("returns a row changed by another device as a conflict, and saves the others", async () => {
    const { env } = accounts();
    const phone = await signIn(env);
    const laptop = await signIn(env);
    await write(env, phone, [row("item:a", 0, { title: "phone" })]);
    const answer = await write(env, laptop, [row("item:a", 0, { title: "laptop" }), row("item:b", 0, { title: "B" })]);
    expect(answer.saved).toEqual([{ key: "item:b", version: 2 }]);
    expect(answer.conflicts).toEqual([{ key: "item:a", version: 1, data: { title: "phone" } }]);
    // Merged and sent again with the version it now knows.
    expect((await write(env, laptop, [row("item:a", 1, { title: "both" })])).saved).toEqual([{ key: "item:a", version: 3 }]);
  });

  it("keeps deleted rows as null, so other devices learn of the removal", async () => {
    const { env } = accounts();
    const key = await signIn(env);
    await write(env, key, [row("item:a", 0, { title: "A" })]);
    await write(env, key, [row("item:a", 1, null)]);
    expect((await items(env, key)).rows).toEqual([{ key: "item:a", version: 2, data: null }]);
    // Made again: the device that doesn't know the removal gets a conflict with it.
    const answer = await write(env, key, [row("item:a", 0, { title: "again" })]);
    expect(answer.conflicts).toEqual([{ key: "item:a", version: 2, data: null }]);
  });

  it("pages between saves", async () => {
    const { env } = accounts();
    const key = await signIn(env);
    const many = (from: number, n: number) => Array.from({ length: n }, (_, i) => row(`read:${from + i}`, 0, { time: i }));
    await write(env, key, many(0, MAX_ROWS));
    await write(env, key, many(MAX_ROWS, 10));
    const first = await items(env, key);
    expect(first).toMatchObject({ version: 1, more: true });
    expect(first.rows).toHaveLength(MAX_ROWS);
    const second = await items(env, key, first.version);
    expect(second).toMatchObject({ version: 2, more: false });
    expect(second.rows).toHaveLength(10);
  });

  it("stores rows encrypted, under ids that don't show the key", async () => {
    const { env, objects } = accounts();
    const key = await signIn(env);
    await write(env, key, [row("item:secret.example/story", 0, { url: "https://secret.example/story" })]);
    const stored = [...objects.values()][0].storage.map;
    for (const [name, value] of stored) {
      expect(name).not.toContain("secret");
      const bytes = (value as { data?: Uint8Array })?.data;
      if (bytes) expect(new TextDecoder().decode(bytes)).not.toContain("secret.example");
    }
  });

  it("refuses what isn't a list of rows", async () => {
    const { env } = accounts();
    const key = await signIn(env);
    expect((await call(env, "POST", "/account/items", key, { rows: [row("a", -1, null)] })).status).toBe(400);
    expect((await call(env, "GET", "/account/items?since=x", key)).status).toBe(400);
    expect((await call(env, "PUT", "/account/items", key, { rows: [] })).status).toBe(405);
    expect(itemChanges(JSON.stringify({ rows: [row("a", 0, null), row("a", 0, null)] }))).toBeNull();
    expect(itemChanges(JSON.stringify({ rows: [row("a", 0, [1])] }))).toBeNull();
    expect(itemChanges(JSON.stringify({ rows: Array.from({ length: MAX_ROWS + 1 }, (_, i) => row(`k${i}`, 0, null)) }))).toBeNull();
    expect(itemChanges(JSON.stringify({ rows: [row("a", 0, { x: 1 })] }))).toEqual([{ key: "a", baseVersion: 0, data: { x: 1 } }]);
    expect(sinceOf("https://x/account/items")).toBe(0);
    expect(sinceOf("https://x/account/items?since=12")).toBe(12);
  });

  it("deletes the rows with the account", async () => {
    const { env } = accounts();
    const key = await signIn(env);
    await write(env, key, [row("item:a", 0, { title: "A" })]);
    await call(env, "DELETE", "/account", key);
    const again = await signIn(env);
    expect(await items(env, again)).toEqual({ version: 0, rows: [], more: false });
  });
});
