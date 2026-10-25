import { describe, expect, it } from "vitest";
import { CUT_NOTE, MAX_REPORT_COMMENT, MAX_REPORT_URL, report, reportUrl, textOf } from "../src/lib/articleReport";

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

describe("the text Content shows goes with a report (#251)", () => {
  it("sends the text as it is when it fits", () => {
    const { url, cut } = report("https://example.com/a", "textMissing", "", "Web", "", "First paragraph.\n\nSecond.");
    expect(cut).toBe(false);
    expect(params(url).get("body")!.endsWith("**Text in Content:**\n\n````\nFirst paragraph.\n\nSecond.\n````")).toBe(true);
  });

  it("cuts a long text short at a word so the address fits", () => {
    const text = Array.from({ length: 3000 }, (_, i) => `word${i + 1}`).join(" ");
    const { url, cut } = report("https://example.com/a", "textMissing", "", "Web", "", text);
    expect(cut).toBe(true);
    expect(url.length).toBeLessThanOrEqual(MAX_REPORT_URL);
    const body = params(url).get("body")!;
    expect(body).toContain("word1 word2");
    expect(body).toContain(CUT_NOTE);
    expect(body).toMatch(/word\d+… \(cut short/);
  });

  it("keeps one paragraph per block", () => {
    expect(textOf("<h2>Title</h2><p>One <b>two</b>.</p><ul><li>A point</li></ul>")).toBe("Title\n\nOne two.\n\nA point");
  });
});
