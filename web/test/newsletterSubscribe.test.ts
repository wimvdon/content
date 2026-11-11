import { describe, expect, it } from "vitest";
import { instapaperStep, newsletterSite } from "../src/lib/newsletterSubscribe";

describe("Subscribe to newsletter (newsletterSubscribe.ts), as on Android", () => {
  it("each way links to its own site, over https", () => {
    expect(newsletterSite("feed")).toBe("https://kill-the-newsletter.com/");
    expect(newsletterSite("instapaper")).toBe("https://www.instapaper.com/docs/save/email");
  });

  it("Instapaper asks to connect first, then shows where the address is", () => {
    expect(instapaperStep(false)).toBe("connect");
    expect(instapaperStep(true)).toBe("findAddress");
  });
});
