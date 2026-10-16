import { afterEach, describe, expect, it, vi } from "vitest";
import { saveToInstapaper } from "../src/lib/instapaper";

describe("saveToInstapaper", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("posts the link to the Simple API without reading the answer", async () => {
    const fetch = vi.fn(async () => new Response(null));
    vi.stubGlobal("fetch", fetch);
    await saveToInstapaper("me@example.com", "pw", "https://a.example/1", " Title ");
    const [url, init] = fetch.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("https://www.instapaper.com/api/add");
    expect(init.mode).toBe("no-cors");
    expect(String(init.body)).toBe("username=me%40example.com&password=pw&url=https%3A%2F%2Fa.example%2F1&title=Title");
  });
});
