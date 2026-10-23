import { describe, expect, it } from "vitest";
import { MAX_REPORT_COMMENT, reportUrl } from "../src/lib/articleReport";

const params = (url: string) => new URL(url).searchParams;

describe("reporting a badly cleaned-up article (#181)", () => {
  it("opens a new issue with the link, the problem and the app", () => {
    const url = reportUrl("https://www.example.com/a?b=1&c=2", "junkLeft", "  The footer stays.  ", "Web");
    expect(url.startsWith("https://github.com/wimvdon/content/issues/new?")).toBe(true);
    const p = params(url);
    expect(p.get("labels")).toBe("parser");
    expect(p.get("title")).toBe("Article cleanup: junk left in on example.com");
    expect(p.get("body")).toBe("**Article:** https://www.example.com/a?b=1&c=2\n**Problem:** Junk left in\n**App:** Web\n\nThe footer stays.");
  });

  it("leaves out an empty comment and cuts a long one", () => {
    expect(params(reportUrl("https://example.com/a", "other", " ", "Web")).get("body")!.endsWith("\n")).toBe(false);
    const long = params(reportUrl("https://example.com/a", "other", "x".repeat(5000), "Web")).get("body")!;
    expect(long.split("\n\n")[1].length).toBe(MAX_REPORT_COMMENT);
  });

  it("matches the Android app's report", () => {
    const p = params(reportUrl("https://example.com/a", "textMissing", "a b", "Android", "4.4.0"));
    expect(p.get("body")).toBe("**Article:** https://example.com/a\n**Problem:** Text missing\n**App:** Android 4.4.0\n\na b");
  });
});
