import { describe, expect, it } from "vitest";
import { checkTarget, handle, MAX_BYTES, type Env } from "../src/index";

const env: Env = { ALLOWED_ORIGINS: "https://app.example, http://localhost:5173" };
const ask = (url: string, init: RequestInit = {}) =>
  new Request("https://worker.example/?url=" + encodeURIComponent(url), { headers: { Origin: "https://app.example" }, ...init });

function site(responses: Record<string, () => Response>) {
  const seen: string[] = [];
  const fetcher = async (input: string) => {
    seen.push(input);
    const answer = responses[input];
    if (!answer) throw new Error("offline");
    return answer();
  };
  return { fetcher, seen };
}

const feed = () => new Response("<rss/>", { headers: { "Content-Type": "application/rss+xml" } });

describe("handle", () => {
  it("passes a feed back with CORS headers and its final address", async () => {
    const { fetcher } = site({ "https://a.example/feed": feed });
    const response = await handle(ask("https://a.example/feed"), env, fetcher);
    expect(response.status).toBe(200);
    expect(await response.text()).toBe("<rss/>");
    expect(response.headers.get("Access-Control-Allow-Origin")).toBe("https://app.example");
    expect(response.headers.get("X-Final-Url")).toBe("https://a.example/feed");
  });

  it("refuses other sites and requests without an origin", async () => {
    const { fetcher, seen } = site({});
    const other = new Request("https://worker.example/?url=https://a.example/feed", { headers: { Origin: "https://evil.example" } });
    expect((await handle(other, env, fetcher)).status).toBe(403);
    expect((await handle(new Request("https://worker.example/?url=https://a.example/feed"), env, fetcher)).status).toBe(403);
    expect(seen).toEqual([]);
  });

  it("answers the browser's preflight and refuses other methods", async () => {
    const { fetcher } = site({});
    expect((await handle(ask("https://a.example/feed", { method: "OPTIONS" }), env, fetcher)).status).toBe(204);
    expect((await handle(ask("https://a.example/feed", { method: "POST" }), env, fetcher)).status).toBe(405);
  });

  it("follows redirects, but only to https", async () => {
    const { fetcher } = site({
      "https://a.example/old": () => new Response(null, { status: 301, headers: { Location: "/new" } }),
      "https://a.example/new": feed,
      "https://a.example/bad": () => new Response(null, { status: 302, headers: { Location: "http://a.example/feed" } }),
    });
    const moved = await handle(ask("https://a.example/old"), env, fetcher);
    expect(moved.status).toBe(200);
    expect(moved.headers.get("X-Final-Url")).toBe("https://a.example/new");
    expect((await handle(ask("https://a.example/bad"), env, fetcher)).status).toBe(502);
  });

  it("refuses pictures, other files and bodies that are too large", async () => {
    const { fetcher } = site({
      "https://a.example/pic": () => new Response("x", { headers: { "Content-Type": "image/png" } }),
      "https://a.example/big": () => new Response("x", { headers: { "Content-Type": "text/html", "Content-Length": String(MAX_BYTES + 1) } }),
      "https://a.example/huge": () => new Response(new Uint8Array(MAX_BYTES + 1), { headers: { "Content-Type": "text/html" } }),
    });
    expect((await handle(ask("https://a.example/pic"), env, fetcher)).status).toBe(415);
    expect((await handle(ask("https://a.example/big"), env, fetcher)).status).toBe(413);
    expect((await handle(ask("https://a.example/huge"), env, fetcher)).status).toBe(413);
  });

  it("reports missing pages and unreachable sites", async () => {
    const { fetcher } = site({ "https://a.example/gone": () => new Response("", { status: 404 }) });
    expect((await handle(ask("https://a.example/gone"), env, fetcher)).status).toBe(404);
    expect((await handle(ask("https://down.example/feed"), env, fetcher)).status).toBe(502);
  });

  it("stops at the rate limit", async () => {
    const { fetcher } = site({ "https://a.example/feed": feed });
    const limited: Env = { ...env, LIMITER: { limit: async () => ({ success: false }) } };
    expect((await handle(ask("https://a.example/feed"), limited, fetcher)).status).toBe(429);
  });
});

describe("checkTarget", () => {
  it("accepts public https addresses", () => {
    expect(checkTarget("https://a.example/feed?x=1#top")).toBe("https://a.example/feed?x=1");
  });

  it("refuses everything else", () => {
    for (const bad of [
      null,
      "",
      "not a url",
      "http://a.example/feed",
      "ftp://a.example/feed",
      "https://localhost/feed",
      "https://127.0.0.1/feed",
      "https://10.0.0.1/feed",
      "https://[::1]/feed",
      "https://printer.local/",
      "https://intranet/",
      "https://user:pw@a.example/",
      "https://a.example:8443/",
    ]) {
      expect(checkTarget(bad), String(bad)).toBeNull();
    }
  });
});
