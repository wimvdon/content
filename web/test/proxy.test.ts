import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchViaProxy, FetchError } from "../src/lib/proxy";

afterEach(() => vi.unstubAllGlobals());

function answer(status: number, body = "", headers: Record<string, string> = {}) {
  const calls: string[] = [];
  vi.stubGlobal("fetch", async (input: string) => {
    calls.push(input);
    return new Response(body, { status, headers });
  });
  return calls;
}

describe("loading through the feed loader (proxy.ts)", () => {
  it("asks for an article page as a page", async () => {
    const calls = answer(200, "<p>hi</p>", { "Content-Type": "text/html", "X-Final-Url": "https://a.example/x" });
    const page = await fetchViaProxy("https://a.example/story", true);
    expect(calls[0]).toMatch(/\?url=https%3A%2F%2Fa\.example%2Fstory&kind=page$/);
    expect(page).toEqual({ text: "<p>hi</p>", url: "https://a.example/x" });
    await fetchViaProxy("https://a.example/feed");
    expect(calls[1]).not.toContain("kind=page");
  });

  it("tells a site that blocks the feed loader apart from other problems", async () => {
    answer(403);
    await expect(fetchViaProxy("https://a.example/story", true)).rejects.toMatchObject({ kind: "blocked" });
    answer(404);
    await expect(fetchViaProxy("https://a.example/story")).rejects.toMatchObject({ kind: "notFound" });
    answer(502);
    await expect(fetchViaProxy("https://a.example/story")).rejects.toBeInstanceOf(FetchError);
  });
});
