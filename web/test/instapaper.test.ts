import { describe, expect, it } from "vitest";
import {
  authorizeUrl,
  exchangeCode,
  InstapaperApi,
  InstapaperError,
  newState,
  parseCallback,
  reorderedPositions,
  type Folder,
  type Problem,
} from "../src/lib/instapaper";

const PROXY = "https://worker.example";

interface Call {
  url: string;
  init: RequestInit;
}

function fakeFetch(answer: () => Response | Promise<Response>) {
  const calls: Call[] = [];
  const doFetch = async (url: string, init: RequestInit = {}) => {
    calls.push({ url, init });
    return answer();
  };
  return { doFetch, calls };
}

const ok = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json" } });
const headers = (call: Call) => call.init.headers as Record<string, string>;

describe("sign-in", () => {
  it("builds Instapaper's sign-in address", () => {
    const url = new URL(authorizeUrl("abc123", "the-client", "https://app.example/instapaper/callback"));
    expect(url.origin + url.pathname).toBe("https://www.instapaper.com/oauth2/authorize");
    expect(Object.fromEntries(url.searchParams)).toEqual({
      client_id: "the-client",
      redirect_uri: "https://app.example/instapaper/callback",
      response_type: "code",
      state: "abc123",
    });
  });

  it("makes a different unguessable state every time", () => {
    const a = newState();
    expect(a).toMatch(/^[0-9a-f]{32}$/);
    expect(newState()).not.toBe(a);
  });

  it("reads what Instapaper sent back", () => {
    expect(parseCallback("?code=1a2b&state=xyz")).toEqual({ kind: "code", code: "1a2b", state: "xyz" });
    expect(parseCallback("?error=access_denied&state=xyz")).toEqual({ kind: "denied" });
    expect(parseCallback("?code=1a2b")).toEqual({ kind: "invalid" });
    expect(parseCallback("")).toEqual({ kind: "invalid" });
  });

  it("swaps the code for a session through the Worker", async () => {
    const { doFetch, calls } = fakeFetch(() => ok({ access_token: "tok", user: { id: 42, username: "me@example.com" }, account: { key: "abc.def" } }));
    expect(await exchangeCode("1a2b", doFetch, PROXY)).toEqual({ token: "tok", userId: 42, username: "me@example.com", accountKey: "abc.def" });
    expect(calls[0].url).toBe(`${PROXY}/instapaper/token`);
    expect(calls[0].init.method).toBe("POST");
    expect(JSON.parse(String(calls[0].init.body))).toEqual({ code: "1a2b" });
  });

  it("signs in without an account when the server has none", async () => {
    const { doFetch } = fakeFetch(() => ok({ access_token: "tok", user: { id: 42, username: "me" }, account: null }));
    expect((await exchangeCode("1a2b", doFetch, PROXY)).accountKey).toBeNull();
  });

  it("reports why the exchange failed", async () => {
    const problem = async (answer: () => Response | Promise<Response>) => {
      try {
        await exchangeCode("c", fakeFetch(answer).doFetch, PROXY);
      } catch (e) {
        return (e as InstapaperError).problem;
      }
      return "none";
    };
    expect(await problem(() => new Response("no", { status: 400 }))).toBe("invalid");
    expect(await problem(() => new Response("no", { status: 502 }))).toBe("server");
    expect(await problem(() => ok({ user: {} }))).toBe("server");
    expect(await problem(() => Promise.reject(new Error("offline")))).toBe("offline");
  });
});

describe("InstapaperApi", () => {
  const api = (answer: () => Response | Promise<Response> = () => ok({})) => {
    const f = fakeFetch(answer);
    return { api: new InstapaperApi("secret-token", f.doFetch, PROXY), calls: f.calls };
  };

  it("sends the token and lists each kind of list", async () => {
    const { api: a, calls } = api(() => ok({ bookmarks: [], total: 0 }));
    await a.list({ kind: "home" });
    await a.list({ kind: "archive" }, 25, 10);
    await a.list({ kind: "liked" });
    await a.list({ kind: "folder", id: 9 });
    await a.list({ kind: "tag", name: "Cooking & baking" });
    const queries = calls.map((c) => Object.fromEntries(new URL(c.url).searchParams));
    expect(queries).toEqual([
      { limit: "25", offset: "0", section: "home" },
      { limit: "10", offset: "25", section: "archive" },
      { limit: "25", offset: "0", section: "liked" },
      { limit: "25", offset: "0", section: "folder", folder_id: "9" },
      { limit: "25", offset: "0", section: "tag", tag: "Cooking & baking" },
    ]);
    expect(calls.every((c) => c.url.startsWith(`${PROXY}/instapaper/api/bookmarks?`))).toBe(true);
    expect(calls.every((c) => headers(c).Authorization === "Bearer secret-token" && c.init.method === "GET")).toBe(true);
  });

  it("saves, moves, likes and deletes", async () => {
    const { api: a, calls } = api();
    await a.save("https://a.example/x", "X");
    await a.save("https://a.example/y");
    await a.move(7, "archive");
    await a.move(7, "home");
    await a.move(7, 12);
    await a.like(7, true);
    await a.like(7, false);
    await a.remove(7);
    expect(calls.map((c) => `${c.init.method} ${c.url.replace(`${PROXY}/instapaper/api`, "")}`)).toEqual([
      "POST /bookmarks",
      "POST /bookmarks",
      "POST /bookmarks/7/move",
      "POST /bookmarks/7/move",
      "POST /bookmarks/7/move",
      "POST /bookmarks/7/like",
      "DELETE /bookmarks/7/like",
      "DELETE /bookmarks/7",
    ]);
    expect(JSON.parse(String(calls[0].init.body))).toEqual({ url: "https://a.example/x", title: "X" });
    expect(JSON.parse(String(calls[1].init.body))).toEqual({ url: "https://a.example/y" });
    expect([2, 3, 4].map((i) => JSON.parse(String(calls[i].init.body)))).toEqual([{ section: "archive" }, { section: "home" }, { section: "12" }]);
    expect(headers(calls[0])["Content-Type"]).toBe("application/json");
    expect(headers(calls[7])["Content-Type"]).toBeUndefined();
  });

  it("tags a bookmark and returns its tags afterwards", async () => {
    const tags = [{ id: 1, name: "Cooking", slug: "cooking", count: 3 }];
    const { api: a, calls } = api(() => ok({ created_tags: [], tags }));
    expect(await a.setTags(7, ["Cooking"], [4])).toEqual(tags);
    expect(JSON.parse(String(calls[0].init.body))).toEqual({ add_tags: [{ name: "Cooking" }], remove_tags: [{ id: 4 }] });
  });

  it("lists folders in order and tags, and manages them", async () => {
    const folder = (id: number, position: number): Folder => ({ id, title: `F${id}`, slug: `f${id}`, position, public: false, count: 0 });
    const { api: a, calls } = api(() => ok({ folders: [folder(2, 2), folder(1, 1)], tags: [] }));
    expect((await a.folders()).map((f) => f.id)).toEqual([1, 2]);
    expect(await a.tags()).toEqual([]);
    await a.createFolder("Recipes");
    await a.deleteFolder(2);
    expect((await a.reorderFolders([{ folder_id: 2, position: 1 }])).map((f) => f.id)).toEqual([1, 2]);
    await a.createTag("Cooking");
    await a.renameTag(4, "Baking");
    expect(calls.map((c) => `${c.init.method} ${c.url.replace(`${PROXY}/instapaper/api`, "")}`)).toEqual([
      "GET /folders",
      "GET /tags",
      "POST /folders",
      "DELETE /folders/2",
      "POST /folders/reorder",
      "POST /tags",
      "POST /tags/4",
    ]);
    expect(JSON.parse(String(calls[2].init.body))).toEqual({ title: "Recipes" });
    expect(JSON.parse(String(calls[4].init.body))).toEqual({ order: [{ folder_id: 2, position: 1 }] });
    expect(JSON.parse(String(calls[6].init.body))).toEqual({ name: "Baking" });
  });

  it("copes with an empty answer and with a list missing from the answer", async () => {
    expect(await api(() => new Response("", { status: 200 })).api.remove(1)).toBeUndefined();
    expect(await api(() => ok({})).api.folders()).toEqual([]);
    expect(await api(() => ok({})).api.tags()).toEqual([]);
  });

  it("names the problem", async () => {
    const problemFor = async (answer: () => Response | Promise<Response>): Promise<Problem | "none"> => {
      try {
        await api(answer).api.list({ kind: "home" });
      } catch (e) {
        return (e as InstapaperError).problem;
      }
      return "none";
    };
    expect(await problemFor(() => new Response("{}", { status: 401 }))).toBe("auth");
    expect(await problemFor(() => new Response("{}", { status: 403 }))).toBe("notApproved");
    expect(await problemFor(() => new Response("{}", { status: 429 }))).toBe("rateLimit");
    expect(await problemFor(() => new Response("{}", { status: 400 }))).toBe("invalid");
    expect(await problemFor(() => new Response("{}", { status: 500 }))).toBe("server");
    expect(await problemFor(() => new Response("{}", { status: 502 }))).toBe("server");
    expect(await problemFor(() => new Response("not json", { status: 200 }))).toBe("server");
    expect(await problemFor(() => Promise.reject(new Error("offline")))).toBe("offline");
  });
});

describe("reorderedPositions", () => {
  const folders = [1, 2, 3].map((id): Folder => ({ id, title: `F${id}`, slug: "", position: id, public: false, count: 0 }));

  it("moves a folder up or down and numbers from 1", () => {
    expect(reorderedPositions(folders, 1, -1)).toEqual([
      { folder_id: 2, position: 1 },
      { folder_id: 1, position: 2 },
      { folder_id: 3, position: 3 },
    ]);
    expect(reorderedPositions(folders, 1, 1)?.map((x) => x.folder_id)).toEqual([1, 3, 2]);
  });

  it("does nothing at the ends", () => {
    expect(reorderedPositions(folders, 0, -1)).toBeNull();
    expect(reorderedPositions(folders, 2, 1)).toBeNull();
    expect(reorderedPositions(folders, 5, 1)).toBeNull();
  });
});
