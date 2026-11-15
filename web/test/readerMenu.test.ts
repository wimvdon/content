import { describe, expect, it } from "vitest";
import { readerMenu } from "../src/lib/readerMenu";

describe("readerMenu", () => {
  it("offers a feed article not saved its read state and text switch", () => {
    expect(readerMenu({ feedArticle: true, saved: false, hasLink: true, canLoad: true })).toEqual(["markRead", "toggleText", "openWebsite", "report"]);
  });

  it("is a saved article's menu once saved, also right after Save in the reader", () => {
    const saved = ["openWebsite", "report", "remove"];
    expect(readerMenu({ feedArticle: true, saved: true, hasLink: true, canLoad: true })).toEqual(saved);
    expect(readerMenu({ feedArticle: false, saved: true, hasLink: true, canLoad: true })).toEqual(saved);
  });

  it("leaves out what needs a link", () => {
    expect(readerMenu({ feedArticle: true, saved: false, hasLink: false, canLoad: false })).toEqual(["markRead"]);
  });

  it("gives a newsletter feed's article no text switch", () => {
    expect(readerMenu({ feedArticle: true, saved: false, hasLink: true, canLoad: true, newsletter: true })).toEqual(["markRead", "originalEmail"]);
  });

  it("offers a newsletter feed's email Unsubscribe after Show original email, other articles never", () => {
    expect(readerMenu({ feedArticle: true, saved: false, hasLink: true, canLoad: true, newsletter: true, canUnsubscribe: true })).toEqual(["markRead", "originalEmail", "unsubscribe"]);
    expect(readerMenu({ feedArticle: true, saved: true, hasLink: true, canLoad: true, newsletter: true, canUnsubscribe: true })).toEqual(["originalEmail", "unsubscribe", "remove"]);
    expect(readerMenu({ feedArticle: true, saved: false, hasLink: true, canLoad: true, canUnsubscribe: true })).toEqual(["markRead", "toggleText", "openWebsite", "report"]);
  });
});
