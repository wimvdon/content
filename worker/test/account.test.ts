import { describe, expect, it } from "vitest";
import { Account, AccountData, MAX_DEVICES, accountIdOf, documentText, knownVersionOf, type Storage } from "../src/account";
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

async function signIn(env: Env, userId = 42): Promise<string> {
  const fetcher = async () => new Response(JSON.stringify({ access_token: "tok", user: { id: userId, username: "me" } }));
  const response = await handle(
    new Request("https://worker.example/instapaper/token", { method: "POST", headers: { Origin: APP }, body: JSON.stringify({ code: "abc" }) }),
    env,
    fetcher,
  );
  const body = (await response.json()) as { account: { key: string } | null };
  return body.account!.key;
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

  it("gives no key when accounts aren't set up", async () => {
    const { env } = accounts();
    const response = await handle(
      new Request("https://worker.example/instapaper/token", { method: "POST", body: JSON.stringify({ code: "abc" }) }),
      { ...env, ACCOUNT_KEY: undefined },
      async (input) => {
        expect(input).toBe(TOKEN_URL);
        return new Response(JSON.stringify({ access_token: "tok", user: { id: 42 } }));
      },
    );
    expect(((await response.json()) as { account: unknown }).account).toBeNull();
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
