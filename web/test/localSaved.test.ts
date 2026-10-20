import { describe, expect, it } from "vitest";
import { emptyLocalSaved, localSavedOf } from "../src/lib/localSaved";

describe("Saved in this browser before #106", () => {
  it("reads damaged storage as empty, and never repeats an id", () => {
    expect(localSavedOf(undefined)).toEqual(emptyLocalSaved);
    expect(localSavedOf("nonsense")).toEqual(emptyLocalSaved);
    const data = localSavedOf({ articles: [{ id: 7, url: "https://a.example/", title: "", time: 1, liked: false, archived: false, tagIds: [] }], tags: [] });
    expect(data.nextId).toBe(8);
  });
});
