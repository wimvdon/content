import { describe, expect, it } from "vitest";
import { AccountClient, liveUrl, retryDelay, SyncError, versionOf } from "../src/lib/account";
import * as S from "../src/lib/sync";

const BASE = "https://worker.example";

function server(answer: (path: string, init: RequestInit) => Response) {
  const calls: { path: string; init: RequestInit }[] = [];
  const doFetch = async (input: string, init?: RequestInit) => {
    const path = input.slice(BASE.length);
    calls.push({ path, init: init ?? {} });
    return answer(path, init ?? {});
  };
  return { client: new AccountClient("acc.key", doFetch, BASE), calls };
}

const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json" } });
const doc = (urls: Record<string, number> = {}): S.SyncDocument => ({
  ...S.emptyDocument(),
  feeds: Object.keys(urls).map((url) => ({ url, title: "F", renamed: false, folderId: null, siteUrl: null, iconUrl: null })),
});
const urls = (text: string | null) => S.decode(text!)!.feeds.map((f) => f.url).sort();

describe("AccountClient", () => {
  it("reads with the key and the version it has", async () => {
    const { client, calls } = server(() => new Response(null, { status: 304 }));
    expect(await client.read(3)).toEqual({ kind: "unchanged", version: 3 });
    const headers = calls[0].init.headers as Record<string, string>;
    expect(headers.Authorization).toBe("Bearer acc.key");
    expect(headers["If-None-Match"]).toBe('"3"');
  });

  it("returns the document as text, or null before the first save", async () => {
    expect(await server(() => json({ version: 0, data: null })).client.read(null)).toEqual({ kind: "document", version: 0, text: null });
    const answer = await server(() => json({ version: 2, data: { format: "rss-reader-sync" } })).client.read(null);
    expect(answer).toEqual({ kind: "document", version: 2, text: '{"format":"rss-reader-sync"}' });
  });

  it("saves with the base version, and returns the other device's document on a conflict", async () => {
    const saved = server(() => json({ version: 4 }));
    expect(await saved.client.write(3, '{"a":1}')).toEqual({ kind: "saved", version: 4 });
    expect(saved.calls[0].init.method).toBe("PUT");
    expect(JSON.parse(String(saved.calls[0].init.body))).toEqual({ baseVersion: 3, data: { a: 1 } });
    const conflict = server(() => json({ version: 5, data: { b: 2 } }, 409));
    expect(await conflict.client.write(3, '{"a":1}')).toEqual({ kind: "conflict", version: 5, text: '{"b":2}' });
  });

  it("says why it failed", async () => {
    const problem = async (answer: () => Response) => {
      try {
        await server(answer).client.read(null);
      } catch (e) {
        return (e as SyncError).problem;
      }
      return "none";
    };
    expect(await problem(() => new Response("", { status: 401 }))).toBe("signedOut");
    expect(await problem(() => new Response("", { status: 503 }))).toBe("notSetUp");
    expect(await problem(() => new Response("", { status: 500 }))).toBe("server");
    expect(await problem(() => { throw new TypeError("offline"); })).toBe("offline");
  });
});

describe("live updates", () => {
  it("connect with the key and read versions", () => {
    expect(liveUrl("a.b c", BASE)).toBe("wss://worker.example/account/live?key=a.b%20c");
    expect(versionOf('{"version":7}')).toEqual({ document: 7, items: null });
    expect(versionOf('{"version":7,"items":3}')).toEqual({ document: 7, items: 3 });
    expect(versionOf("pong")).toBeNull();
    expect(versionOf(5)).toBeNull();
  });

  it("wait longer after each failure, at most a minute", () => {
    expect(retryDelay(1)).toBe(2_000);
    expect(retryDelay(2)).toBe(4_000);
    expect(retryDelay(20)).toBe(60_000);
  });
});

describe("syncRound", () => {
  /** An account in memory, like the Worker: versions, and conflicts for old bases. */
  function account(start: { version: number; text: string | null } = { version: 0, text: null }) {
    const state = { ...start, writes: 0 };
    const target: S.SyncTarget = {
      read: async (known) => (known === state.version ? { kind: "unchanged", version: known } : { kind: "document", version: state.version, text: state.text }),
      write: async (baseVersion, text) => {
        if (baseVersion !== state.version) return { kind: "conflict", version: state.version, text: state.text };
        state.writes++;
        state.version++;
        state.text = text;
        return { kind: "saved", version: state.version };
      },
    };
    return { state, target };
  }

  const now = Date.now();

  it("saves this browser's library as the first document", async () => {
    const { state, target } = account();
    const next = await S.syncRound(target, null, (_base, remote) => {
      expect(remote).toBeNull();
      return doc({ "f a": now });
    });
    expect(next.version).toBe(1);
    expect(urls(state.text)).toEqual(["f a"]);
  });

  it("doesn't save when nothing changed", async () => {
    const text = S.encode(doc({ "f a": now }));
    const { state, target } = account({ version: 4, text });
    const next = await S.syncRound(target, { version: 4, text }, (base, remote) => {
      expect(remote).toEqual(base);
      return remote!;
    });
    expect(next).toEqual({ version: 4, text });
    expect(state.writes).toBe(0);
  });

  it("merges again when another device saved in between, with what it merged as the base", async () => {
    const { state, target } = account({ version: 1, text: S.encode(doc({ "f a": now })) });
    let library = doc({ "f b": now });
    let calls = 0;
    const next = await S.syncRound(target, null, (base, remote) => {
      calls++;
      if (calls === 1) {
        // Another device adds feed "f c" while this one merges.
        state.version = 2;
        state.text = S.encode(doc({ "f a": now, "f c": now }));
      }
      library = remote ? S.merge(base, library, remote) : library;
      return library;
    });
    expect(calls).toBe(2);
    expect(next.version).toBe(3);
    expect(urls(state.text)).toEqual(["f a", "f b", "f c"]);
  });

  it("refuses a document from a newer version", async () => {
    const { target } = account({ version: 1, text: '{"format":"rss-reader-sync","version":99}' });
    await expect(S.syncRound(target, null, () => doc())).rejects.toMatchObject({ problem: "unreadableFile" });
  });
});
