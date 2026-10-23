import { describe, expect, it } from "vitest";
import { applyHighlights, notCopied, pendingCopy, RETRY_MS, syncHighlights, type HighlightLink, type HighlightService, type RemoteHighlight } from "../src/lib/highlightCopy";
import { InstapaperError } from "../src/lib/instapaper";
import { InstapaperSync } from "../src/lib/instapaperSync";
import type { Highlight } from "../src/lib/items";
import { ServiceSyncError } from "../src/lib/serviceSync";

/** Highlights at a service in memory, with a limit on how many it makes. */
class Fake implements HighlightService {
  have = new Map<string, RemoteHighlight>();
  made = 0;
  listed = 0;
  fail: string | null = null;
  limit: number;

  constructor(limit = Infinity) {
    this.limit = limit;
  }

  async highlights(): Promise<RemoteHighlight[]> {
    this.listed++;
    return [...this.have.values()];
  }

  async addHighlight(_remoteId: string, text: string, note: string, position: number): Promise<RemoteHighlight> {
    if (this.fail) throw new ServiceSyncError(this.fail);
    if (this.made >= this.limit) throw new ServiceSyncError("limit");
    this.made++;
    const h = { remoteId: `r${this.made}`, text, note, position };
    this.have.set(h.remoteId, h);
    return h;
  }

  async removeHighlight(id: string): Promise<void> {
    this.have.delete(id);
  }

  sync(highlights: Highlight[], links: Record<string, HighlightLink>, now = 0, look = false) {
    return syncHighlights(this, "instapaper", "1", highlights, links, now, look);
  }
}

const h = (id: string, quote: string, note = "", createdAt = 0): Highlight => ({ id, quote, prefix: "", suffix: "", occurrence: 0, note, createdAt });

describe("syncing highlights with a service (#164)", () => {
  it("copies new highlights and links them", async () => {
    const service = new Fake();
    const all = [h("a", "One"), h("b", "Two", "note", 1)];
    const synced = await service.sync(all, {});
    expect(synced.problem).toBeNull();
    expect([...service.have.values()].map((r) => r.text).sort()).toEqual(["One", "Two"]);
    expect(pendingCopy(all, synced.links, 0)).toBe(false);
  });

  it("costs no call with nothing to send, unless asked to look", async () => {
    const links = { a: { remoteId: "r1", note: "", refusedAt: null } };
    expect(pendingCopy([h("a", "One")], links, 0)).toBe(false);
    expect(pendingCopy([h("a", "One", "changed")], links, 0)).toBe(true);
    expect(pendingCopy([], links, 0)).toBe(true);
    const service = new Fake();
    service.have.set("r1", { remoteId: "r1", text: "One", note: "" });
    await service.sync([h("a", "One")], links);
    expect(service.listed).toBe(0);
    await service.sync([h("a", "One")], links, 0, true);
    expect(service.listed).toBe(1);
  });

  it("links a passage that is already there instead of copying it", async () => {
    const service = new Fake();
    service.have.set("x", { remoteId: "x", text: "It’s here", note: "" });
    const synced = await service.sync([h("a", "It's here")], {});
    expect(service.made).toBe(0);
    expect(synced.links.a.remoteId).toBe("x");
  });

  it("takes in a highlight made there", async () => {
    const service = new Fake();
    service.have.set("r9", { remoteId: "r9", text: " A passage ", note: "Their note", position: 2, createdAt: 5000 });
    const synced = await service.sync([], {}, 0, true);
    expect(synced.highlights).toEqual([{ id: "instapaper-r9", quote: "A passage", prefix: "", suffix: "", occurrence: 2, note: "Their note", createdAt: 5000 }]);
    expect(service.made).toBe(0);
    expect(pendingCopy(synced.highlights, synced.links, 0)).toBe(false);
  });

  it("deletes here what was deleted there", async () => {
    const service = new Fake();
    const first = await service.sync([h("a", "One"), h("b", "Two")], {});
    service.have.delete(first.links.a.remoteId!);
    const second = await service.sync(first.highlights, first.links, 0, true);
    expect(second.highlights.map((x) => x.id)).toEqual(["b"]);
    expect(second.links.a).toBeUndefined();
  });

  it("takes in a note changed there, unless it changed here too", async () => {
    const service = new Fake();
    const first = await service.sync([h("a", "One", "old")], {});
    const id = first.links.a.remoteId!;
    service.have.set(id, { ...service.have.get(id)!, note: "theirs" });
    const taken = await service.sync(first.highlights, first.links, 0, true);
    expect(taken.highlights[0].note).toBe("theirs");
    service.have.set(id, { ...service.have.get(id)!, note: "theirs again" });
    const both = await service.sync([h("a", "One", "mine")], taken.links, 0, true);
    expect(both.highlights[0].note).toBe("mine");
    expect([...service.have.values()].map((r) => r.note)).toEqual(["mine"]);
  });

  it("makes the highlight again for a changed note, and deletes one deleted here", async () => {
    const service = new Fake();
    const first = await service.sync([h("a", "One")], {});
    const second = await service.sync([h("a", "One", "new note")], first.links);
    expect([...service.have.values()].map((r) => r.note)).toEqual(["new note"]);
    const third = await service.sync([], second.links);
    expect(service.have.size).toBe(0);
    expect(third.links).toEqual({});
  });

  it("keeps what the limit refused and tries it again a day later", async () => {
    const service = new Fake(1);
    const all = [h("a", "One", "", 1), h("b", "Two", "", 2), h("c", "Three", "", 3)];
    const synced = await service.sync(all, {}, 1000);
    expect(synced.problem).toBeNull();
    expect(notCopied(synced.links)).toEqual(new Set(["b", "c"]));
    expect(pendingCopy(all, synced.links, 1000 + RETRY_MS - 1)).toBe(false);
    expect(pendingCopy(all, synced.links, 1000 + RETRY_MS)).toBe(true);
    service.limit = 3;
    const later = await service.sync(all, synced.links, 1000 + RETRY_MS);
    expect(notCopied(later.links).size).toBe(0);
    expect(service.have.size).toBe(3);
  });

  it("stops at another problem and keeps what got through", async () => {
    const service = new Fake();
    service.fail = "offline";
    const synced = await service.sync([h("a", "One")], {});
    expect(synced.problem).toBe("offline");
    expect(pendingCopy([h("a", "One")], synced.links, 0)).toBe(true);
  });

  it("lands what a sync changed on the highlights as they are now", () => {
    const before = [h("a", "One"), h("b", "Two", "old")];
    const after = [h("b", "Two", "theirs"), h("instapaper-r9", "New")];
    expect(applyHighlights([...before, h("c", "Three")], before, after)).toEqual([h("b", "Two", "theirs"), h("c", "Three"), h("instapaper-r9", "New")]);
    expect(applyHighlights([h("a", "One"), h("b", "Two", "mine")], before, after).find((x) => x.id === "b")?.note).toBe("mine");
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
