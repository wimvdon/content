import { describe, expect, it } from "vitest";
import { instapaperText } from "../src/lib/instapaper";
import { instapaperBookmarkId, instapaperReadLink } from "../src/lib/instapaperLinks";

describe("Instapaper articles without an address (instapaperLinks.ts)", () => {
  it("get Instapaper's page as their address, which names them again", () => {
    expect(instapaperReadLink(1234)).toBe("https://www.instapaper.com/read/1234");
    expect(instapaperBookmarkId("https://www.instapaper.com/read/1234")).toBe(1234);
    expect(instapaperBookmarkId("https://instapaper.com/read/1234/")).toBe(1234);
  });

  it("other addresses are not Instapaper articles", () => {
    for (const url of ["https://www.instapaper.com/u", "https://www.instapaper.com/read/12/extra", "http://www.instapaper.com/read/12", "https://example.com/read/12", null]) {
      expect(instapaperBookmarkId(url)).toBeNull();
    }
  });
});

describe("instapaperText", () => {
  it("asks the feed loader with the token and the article, and takes its HTML", async () => {
    const calls: { url: string; init?: RequestInit }[] = [];
    const html = await instapaperText("tok123", 42, async (url, init) => {
      calls.push({ url, init });
      return new Response(JSON.stringify({ title: "News", html: "<p>Hi</p>" }));
    }, "https://worker.example");
    expect(html).toBe("<p>Hi</p>");
    expect(calls[0].url).toBe("https://worker.example/instapaper/text");
    expect(new Headers(calls[0].init!.headers).get("Authorization")).toBe("Bearer tok123");
    expect(JSON.parse(String(calls[0].init?.body))).toEqual({ id: 42 });
  });

  it("gives no text when the credits are used up, nothing came, or it's offline", async () => {
    expect(await instapaperText("t", 1, async () => new Response("Payment required", { status: 402 }), "https://w")).toBeNull();
    expect(await instapaperText("t", 1, async () => new Response(JSON.stringify({ html: "" })), "https://w")).toBeNull();
    expect(await instapaperText("t", 1, async () => Promise.reject(new Error("offline")), "https://w")).toBeNull();
  });
});
