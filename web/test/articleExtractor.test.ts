import { describe, expect, it } from "vitest";
import { extractArticle } from "../src/lib/articleExtractor";
import { toPlainText } from "../src/lib/html";

/** The sample pages both apps are tested on (testdata/articles), so they clean a page up the same way. */
interface Sample {
  about: string;
  url: string;
  html: string;
  text: string[];
  htmlContains: string[];
  absent: string[];
  noArticle: boolean;
}

const files = import.meta.glob<Sample>("../../testdata/articles/*.json", { eager: true, import: "default" });
const samples = Object.entries(files)
  .sort(([a], [b]) => a.localeCompare(b))
  .map(([path, sample]) => [path.split("/").pop()!, sample] as const);

describe("articles as on Android (testdata/articles)", () => {
  it("has the sample pages", () => expect(samples.length).toBeGreaterThan(5));

  for (const [file, sample] of samples) {
    it(`${file}: ${sample.about}`, () => {
      const article = extractArticle(sample.html, sample.url);
      if (sample.noArticle) {
        expect(article).toBeNull();
        return;
      }
      expect(article).not.toBeNull();
      const text = toPlainText(article!);
      for (const t of sample.text) expect(text, article!).toContain(t);
      for (const h of sample.htmlContains) expect(article, article!).toContain(h);
      for (const a of sample.absent) expect(article, article!).not.toContain(a);
    });
  }

  it("gives no article for empty or broken pages", () => {
    expect(extractArticle("", "https://example.com/")).toBeNull();
    expect(extractArticle("not < html > at all", "https://example.com/")).toBeNull();
  });
});
