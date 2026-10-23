import { describe, expect, it } from "vitest";
import { notCopied, pendingCopy, pushHighlights, RETRY_MS, type HighlightService, type RemoteHighlight } from "../src/lib/highlightCopy";
import { InstapaperError } from "../src/lib/instapaper";
import { InstapaperSync } from "../src/lib/instapaperSync";
import type { Highlight } from "../src/lib/items";
import { ServiceSyncError } from "../src/lib/serviceSync";

/** Highlights at a service in memory, with a limit on how many it makes. */
class Fake implements HighlightService {
  have = new Map<string, RemoteHighlight>();
  made = 0;
  fail: string | null = null;

  limit: number;

  constructor(limit = Infinity) {
    this.limit = limit;
  }

  async highlights(): Promise<RemoteHighlight[]> {
    return [...this.have.values()];
  }

  async addHighlight(_remoteId: string, text: string, note: string): Promise<RemoteHighlight> {
    if (this.fail) throw new ServiceSyncError(this.fail);
    if (this.made >= this.limit) throw new ServiceSyncError("limit");
    this.made++;
    const h = { remoteId: `r${this.made}`, text, note };
    this.have.set(h.remoteId, h);
    return h;
  }

  async removeHighlight(id: string): Promise<void> {
    this.have.delete(id);
  }
}

const h = (id: string, quote: string, note = "", createdAt = 0): Highlight => ({ id, quote, prefix: "", suffix: "", occurrence: 0, note, createdAt });

describe("copying highlights to a service (#164)", () => {
  it("copies new highlights and links them", async () => {
    const service = new Fake();
    const all = [h("a", "One"), h("b", "Two", "note", 1)];
    const pushed = await pushHighlights(service, "1", all, {}, 0);
    expect(pushed.problem).toBeNull();
    expect([...service.have.values()].map((r) => r.text).sort()).toEqual(["One", "Two"]);
    expect(pendingCopy(all, pushed.links, 0)).toBe(false);
  });

  it("knows when nothing needs a call", () => {
    const links = { a: { remoteId: "r1", note: "", refusedAt: null } };
    expect(pendingCopy([h("a", "One")], links, 0)).toBe(false);
    expect(pendingCopy([h("a", "One", "changed")], links, 0)).toBe(true);
    expect(pendingCopy([], links, 0)).toBe(true);
    expect(pendingCopy([h("b", "Two")], {}, 0)).toBe(true);
  });

  it("doesn't copy a passage that is already there", async () => {
    const service = new Fake();
    service.have.set("x", { remoteId: "x", text: "It’s here", note: "" });
    const pushed = await pushHighlights(service, "1", [h("a", "It's here")], {}, 0);
    expect(service.made).toBe(0);
    expect(pushed.links.a.remoteId).toBe("x");
  });

  it("makes the highlight again for a changed note, and deletes one deleted here", async () => {
    const service = new Fake();
    const first = await pushHighlights(service, "1", [h("a", "One")], {}, 0);
    const second = await pushHighlights(service, "1", [h("a", "One", "new note")], first.links, 0);
    expect([...service.have.values()].map((r) => r.note)).toEqual(["new note"]);
    const third = await pushHighlights(service, "1", [], second.links, 0);
    expect(service.have.size).toBe(0);
    expect(third.links).toEqual({});
  });

  it("keeps what the limit refused and tries it again a day later", async () => {
    const service = new Fake(1);
    const all = [h("a", "One", "", 1), h("b", "Two", "", 2), h("c", "Three", "", 3)];
    const pushed = await pushHighlights(service, "1", all, {}, 1000);
    expect(pushed.problem).toBeNull();
    expect(notCopied(pushed.links)).toEqual(new Set(["b", "c"]));
    expect(pendingCopy(all, pushed.links, 1000 + RETRY_MS - 1)).toBe(false);
    expect(pendingCopy(all, pushed.links, 1000 + RETRY_MS)).toBe(true);
    service.limit = 3;
    const later = await pushHighlights(service, "1", all, pushed.links, 1000 + RETRY_MS);
    expect(notCopied(later.links).size).toBe(0);
    expect(service.have.size).toBe(3);
  });

  it("stops at another problem and keeps what got through", async () => {
    const service = new Fake();
    service.fail = "offline";
    const pushed = await pushHighlights(service, "1", [h("a", "One")], {}, 0);
    expect(pushed.problem).toBe("offline");
    expect(pendingCopy([h("a", "One")], pushed.links, 0)).toBe(true);
  });

  it("names Instapaper's refusal the limit", async () => {
    const api = {
      addHighlight: async () => {
        throw new InstapaperError("notApproved");
      },
    } as unknown as ConstructorParameters<typeof InstapaperSync>[0];
    await expect(new InstapaperSync(api).addHighlight("1", "One", "", 0)).rejects.toMatchObject({ problem: "limit" });
  });
});
