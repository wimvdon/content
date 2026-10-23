import { describe, expect, it } from "vitest";
import { extensionPresent, fetchViaExtension, type MessageWindow } from "../src/lib/extension";

/** A window with an extension that answers fetches, as bridge.js does. */
function windowWith(answer: (url: string) => Record<string, unknown> | null): MessageWindow & { sent: unknown[] } {
  const listeners: ((event: MessageEvent) => void)[] = [];
  const origin = "https://app.example";
  const win = {
    sent: [] as unknown[],
    location: { origin },
    addEventListener: (_: "message", l: (event: MessageEvent) => void) => listeners.push(l),
    removeEventListener: (_: "message", l: (event: MessageEvent) => void) => listeners.splice(listeners.indexOf(l), 1),
    postMessage(message: unknown, target: string) {
      win.sent.push({ message, target });
      const m = message as { source: string; type: string; id: string; url: string };
      if (m.source !== "content-web" || m.type !== "fetch") return;
      const reply = answer(m.url);
      if (!reply) return;
      // Something else talks on the window too: ignored.
      for (const l of [...listeners]) l({ origin: "https://evil.example", data: { source: "content-extension", type: "fetched", id: m.id, ok: true, body: btoa("evil") } } as MessageEvent);
      for (const l of [...listeners]) l({ origin, data: { source: "content-extension", type: "fetched", id: m.id, ...reply } } as MessageEvent);
    },
  };
  return win;
}

describe("the browser extension (#168)", () => {
  it("knows when it is installed", () => {
    expect(extensionPresent({ documentElement: { dataset: { contentExtension: "1.0.0" } } })).toBe(true);
    expect(extensionPresent({ documentElement: { dataset: {} } })).toBe(false);
    expect(extensionPresent(undefined)).toBe(false);
  });

  it("asks it for a page and reads its bytes", async () => {
    const win = windowWith(() => ({ ok: true, url: "https://example.com/final", contentType: "text/html; charset=utf-8", body: btoa("<p>Hi</p>") }));
    const page = await fetchViaExtension("https://example.com/a", win);
    expect(new TextDecoder().decode(page.bytes)).toBe("<p>Hi</p>");
    expect(page.url).toBe("https://example.com/final");
    expect(page.contentType).toBe("text/html; charset=utf-8");
    expect(win.sent).toEqual([{ message: expect.objectContaining({ source: "content-web", type: "fetch", url: "https://example.com/a" }), target: "https://app.example" }]);
  });

  it("says why it couldn't, and gives up when it doesn't answer", async () => {
    await expect(fetchViaExtension("https://example.com/a", windowWith(() => ({ ok: false, problem: "notFound" })))).rejects.toThrow("notFound");
    await expect(fetchViaExtension("https://example.com/a", windowWith(() => null), 20)).rejects.toThrow("offline");
  });
});
