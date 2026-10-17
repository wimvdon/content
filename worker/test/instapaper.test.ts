import { describe, expect, it } from "vitest";
import { handle, type Env } from "../src/index";
import { API_BASE, TOKEN_URL } from "../src/instapaper";

const APP = "https://app.example";
const env: Env = {
  ALLOWED_ORIGINS: APP + ", http://localhost:5173",
  INSTAPAPER_CLIENT_ID: "client-id",
  INSTAPAPER_CLIENT_SECRET: "client-secret",
  INSTAPAPER_REDIRECT_URI: APP + "/instapaper/callback",
};

interface Call {
  input: string;
  init?: RequestInit;
}

function upstream(answer: () => Response | Promise<Response>) {
  const calls: Call[] = [];
  const fetcher = async (input: string, init?: RequestInit) => {
    calls.push({ input, init });
    return answer();
  };
  return { fetcher, calls };
}

const jsonResponse = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json" } });

function tokenRequest(body: unknown, init: { origin?: string | null; method?: string } = {}) {
  const origin = init.origin === undefined ? APP : init.origin;
  return new Request("https://worker.example/instapaper/token", {
    method: init.method ?? "POST",
    headers: { ...(origin ? { Origin: origin } : {}), "Content-Type": "application/json" },
    body: init.method === "GET" ? undefined : typeof body === "string" ? body : JSON.stringify(body),
  });
}

function apiRequest(path: string, init: RequestInit & { origin?: string | null } = {}) {
  const { origin = APP, headers, ...rest } = init;
  return new Request("https://worker.example/instapaper/api" + path, {
    ...rest,
    headers: { ...(origin ? { Origin: origin } : {}), ...(headers as Record<string, string>) },
  });
}

const BEARER = { Authorization: "Bearer abcdef123456" };

describe("POST /instapaper/token", () => {
  it("swaps the code for a token with the secret and the fixed redirect address", async () => {
    const { fetcher, calls } = upstream(() =>
      jsonResponse({ token_type: "Bearer", access_token: "tok123", user: { id: 42, username: "me@example.com" } }),
    );
    const response = await handle(tokenRequest({ code: "1a2b3c" }), env, fetcher);
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ access_token: "tok123", user: { id: 42, username: "me@example.com" } });
    expect(response.headers.get("Access-Control-Allow-Origin")).toBe(APP);
    expect(response.headers.get("Cache-Control")).toBe("no-store");

    expect(calls).toHaveLength(1);
    expect(calls[0].input).toBe(TOKEN_URL);
    expect(calls[0].init?.method).toBe("POST");
    const sent = new URLSearchParams(String(calls[0].init?.body));
    expect(Object.fromEntries(sent)).toEqual({
      client_id: "client-id",
      client_secret: "client-secret",
      redirect_uri: APP + "/instapaper/callback",
      code: "1a2b3c",
    });
  });

  it("never lets the caller choose the redirect address", async () => {
    const { fetcher, calls } = upstream(() => jsonResponse({ access_token: "tok123" }));
    await handle(tokenRequest({ code: "abc", redirect_uri: "https://evil.example/", client_secret: "x" }), env, fetcher);
    const sent = new URLSearchParams(String(calls[0].init?.body));
    expect(sent.get("redirect_uri")).toBe(APP + "/instapaper/callback");
    expect(sent.get("client_secret")).toBe("client-secret");
  });

  it("answers the Android app, which sends no Origin", async () => {
    const { fetcher } = upstream(() => jsonResponse({ access_token: "tok123", user: { id: 1, username: "a" } }));
    const response = await handle(tokenRequest({ code: "abc" }, { origin: null }), env, fetcher);
    expect(response.status).toBe(200);
    expect(response.headers.get("Access-Control-Allow-Origin")).toBeNull();
  });

  it("refuses other web sites", async () => {
    const { fetcher, calls } = upstream(() => jsonResponse({ access_token: "tok123" }));
    const response = await handle(tokenRequest({ code: "abc" }, { origin: "https://evil.example" }), env, fetcher);
    expect(response.status).toBe(403);
    expect(calls).toEqual([]);
  });

  it("only accepts POST and answers the browser's preflight", async () => {
    const { fetcher } = upstream(() => jsonResponse({}));
    expect((await handle(tokenRequest({}, { method: "GET" }), env, fetcher)).status).toBe(405);
    const preflight = await handle(tokenRequest({}, { method: "OPTIONS" }), env, fetcher);
    expect(preflight.status).toBe(204);
    expect(preflight.headers.get("Access-Control-Allow-Methods")).toContain("POST");
  });

  it("says so when the secret isn't set up", async () => {
    const { fetcher, calls } = upstream(() => jsonResponse({}));
    const bare: Env = { ...env, INSTAPAPER_CLIENT_SECRET: undefined };
    expect((await handle(tokenRequest({ code: "abc" }), bare, fetcher)).status).toBe(503);
    expect(calls).toEqual([]);
  });

  it("refuses a missing, malformed or odd code without calling Instapaper", async () => {
    const { fetcher, calls } = upstream(() => jsonResponse({}));
    for (const bad of ["not json", "[]", "{}", { code: 5 }, { code: "" }, { code: "a b" }, { code: "a&b=c" }, { code: "x".repeat(513) }]) {
      expect((await handle(tokenRequest(bad), env, fetcher)).status, JSON.stringify(bad)).toBe(400);
    }
    expect(calls).toEqual([]);
  });

  it("reports a rejected code, an unreachable Instapaper and a reply without a token", async () => {
    expect((await handle(tokenRequest({ code: "abc" }), env, upstream(() => jsonResponse({ error: "invalid_grant" }, 400)).fetcher)).status).toBe(400);
    expect((await handle(tokenRequest({ code: "abc" }), env, upstream(() => jsonResponse({}, 500)).fetcher)).status).toBe(502);
    expect((await handle(tokenRequest({ code: "abc" }), env, upstream(() => jsonResponse({ user: {} })).fetcher)).status).toBe(502);
    const offline = async () => {
      throw new Error("offline");
    };
    expect((await handle(tokenRequest({ code: "abc" }), env, offline)).status).toBe(502);
  });

  it("stops at the rate limit", async () => {
    const { fetcher } = upstream(() => jsonResponse({ access_token: "tok123" }));
    const limited: Env = { ...env, LIMITER: { limit: async () => ({ success: false }) } };
    expect((await handle(tokenRequest({ code: "abc" }), limited, fetcher)).status).toBe(429);
  });
});

describe("/instapaper/api", () => {
  it("lists bookmarks, passing the token and only the known query parameters", async () => {
    const { fetcher, calls } = upstream(() => jsonResponse({ bookmarks: [], total: 0 }));
    const response = await handle(
      apiRequest("/bookmarks?section=archive&limit=50&offset=10&evil=1&since=5", { headers: BEARER }),
      env,
      fetcher,
    );
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ bookmarks: [], total: 0 });
    expect(response.headers.get("Access-Control-Allow-Origin")).toBe(APP);
    expect(response.headers.get("Cache-Control")).toBe("no-store");

    const target = new URL(calls[0].input);
    expect(target.origin + target.pathname).toBe(API_BASE + "/bookmarks");
    expect(Object.fromEntries(target.searchParams)).toEqual({ section: "archive", limit: "50", offset: "10", since: "5" });
    expect((calls[0].init?.headers as Record<string, string>).Authorization).toBe(BEARER.Authorization);
  });

  it("saves, moves, likes and deletes", async () => {
    const { fetcher, calls } = upstream(() => jsonResponse({ id: 7 }));
    const post = (path: string, body: unknown) =>
      handle(apiRequest(path, { method: "POST", headers: BEARER, body: JSON.stringify(body) }), env, fetcher);

    expect((await post("/bookmarks", { url: "https://a.example/x", title: "X" })).status).toBe(200);
    expect(calls[0].input).toBe(API_BASE + "/bookmarks");
    expect(JSON.parse(String(calls[0].init?.body))).toEqual({ url: "https://a.example/x", title: "X" });
    expect((calls[0].init?.headers as Record<string, string>)["Content-Type"]).toBe("application/json");

    expect((await post("/bookmarks/7/move", { section: "archive" })).status).toBe(200);
    expect(calls[1].input).toBe(API_BASE + "/bookmarks/7/move");
    expect((await post("/bookmarks/7/like", {})).status).toBe(200);

    for (const path of ["/bookmarks/7", "/bookmarks/7/like"]) {
      expect((await handle(apiRequest(path, { method: "DELETE", headers: BEARER }), env, fetcher)).status).toBe(200);
    }
    expect(calls.map((c) => c.init?.method)).toEqual(["POST", "POST", "POST", "DELETE", "DELETE"]);
  });

  it("passes Instapaper's own errors on", async () => {
    const { fetcher } = upstream(() => jsonResponse({ error: { code: 401, message: "Authentication failed" } }, 401));
    const response = await handle(apiRequest("/bookmarks", { headers: BEARER }), env, fetcher);
    expect(response.status).toBe(401);
    expect(await response.json()).toEqual({ error: { code: 401, message: "Authentication failed" } });
  });

  it("only answers the web app", async () => {
    const { fetcher, calls } = upstream(() => jsonResponse({}));
    expect((await handle(apiRequest("/bookmarks", { headers: BEARER, origin: null }), env, fetcher)).status).toBe(403);
    expect((await handle(apiRequest("/bookmarks", { headers: BEARER, origin: "https://evil.example" }), env, fetcher)).status).toBe(403);
    expect(calls).toEqual([]);
  });

  it("needs a bearer token", async () => {
    const { fetcher, calls } = upstream(() => jsonResponse({}));
    const bad: Record<string, string>[] = [
      {},
      { Authorization: "Basic abcdef123456" },
      { Authorization: "Bearer short" },
      { Authorization: "Bearer a b c d e f g h" },
    ];
    for (const headers of bad) {
      expect((await handle(apiRequest("/bookmarks", { headers }), env, fetcher)).status, JSON.stringify(headers)).toBe(401);
    }
    expect(calls).toEqual([]);
  });

  it("refuses everything outside the list of calls", async () => {
    const { fetcher, calls } = upstream(() => jsonResponse({}));
    const ask = (path: string, method = "GET") => handle(apiRequest(path, { method, headers: BEARER }), env, fetcher);
    expect((await ask("/folders")).status).toBe(404);
    expect((await ask("/bookmarks/7/parse")).status).toBe(404);
    expect((await ask("/bookmarks/7/tags", "POST")).status).toBe(404);
    expect((await ask("/bookmarks/abc")).status).toBe(404);
    expect((await ask("/bookmarks/../account")).status).toBe(404);
    expect((await ask("/bookmarks", "DELETE")).status).toBe(405);
    expect((await ask("/bookmarks/7/move", "GET")).status).toBe(405);
    expect(calls).toEqual([]);
  });

  it("doesn't follow redirects with the token", async () => {
    const { fetcher } = upstream(() => new Response(null, { status: 302, headers: { Location: "https://evil.example/" } }));
    expect((await handle(apiRequest("/bookmarks", { headers: BEARER }), env, fetcher)).status).toBe(502);
  });

  it("answers the preflight and stops at the rate limit", async () => {
    const { fetcher } = upstream(() => jsonResponse({}));
    const preflight = await handle(apiRequest("/bookmarks", { method: "OPTIONS" }), env, fetcher);
    expect(preflight.status).toBe(204);
    expect(preflight.headers.get("Access-Control-Allow-Headers")).toContain("Authorization");
    const limited: Env = { ...env, LIMITER: { limit: async () => ({ success: false }) } };
    expect((await handle(apiRequest("/bookmarks", { headers: BEARER }), limited, fetcher)).status).toBe(429);
  });
});

describe("unknown /instapaper paths", () => {
  it("are not found", async () => {
    const { fetcher } = upstream(() => jsonResponse({}));
    const response = await handle(
      new Request("https://worker.example/instapaper/other", { headers: { Origin: APP } }),
      env,
      fetcher,
    );
    expect(response.status).toBe(404);
  });
});
