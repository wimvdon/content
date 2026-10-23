import { describe, expect, it } from "vitest";
import { shareLink } from "../src/lib/shareLink";

describe("sharing a link from a row's menu (#221)", () => {
  it("uses the share sheet when there is one", async () => {
    const shared: unknown[] = [];
    expect(await shareLink("T", "https://a.example", { share: async (d) => void shared.push(d) })).toBe("shared");
    expect(shared).toEqual([{ title: "T", url: "https://a.example" }]);
  });

  it("copies the link otherwise, and says when it couldn't", async () => {
    const copied: string[] = [];
    expect(await shareLink("T", "https://a.example", { clipboard: { writeText: async (t) => void copied.push(t) } })).toBe("copied");
    expect(copied).toEqual(["https://a.example"]);
    expect(await shareLink("T", "https://a.example", { clipboard: { writeText: () => Promise.reject(new Error("no")) } })).toBe("failed");
    expect(await shareLink("T", "https://a.example", {})).toBe("failed");
  });
});
