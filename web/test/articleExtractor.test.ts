import { describe, expect, it } from "vitest";
import { extractArticle } from "../src/lib/articleExtractor";
import { toPlainText } from "../src/lib/html";

/** The sample pages in testdata/articles. The Android app runs this same extractor, so they hold for both apps. */
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

describe("sample pages (testdata/articles)", () => {
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

describe("extractArticle", () => {
  const paragraph = "This is a sentence of the real article, with enough words, commas and length to count as a paragraph of text.";
  const page = (body: string) => `<!DOCTYPE html><html><head><title>Page</title></head><body>${body}</body></html>`;

  it("finds the article without an article tag by its paragraphs", () => {
    const article = extractArticle(
      page(`<div class="menu"><a href="/a">Link one</a><a href="/b">Link two</a></div>
        <div class="content-wrapper"><div class="post-body">${`<p>${paragraph}</p>`.repeat(7)}</div></div>`),
      "https://example.com/",
    )!;
    expect(article.match(/<p>/g)).toHaveLength(7);
    expect(article).not.toContain("Link one");
  });

  it("keeps sections split over sibling containers", () => {
    const article = extractArticle(
      page(`<main><section><p>First part. ${paragraph}</p><p>${paragraph}</p></section>
        <section><p>Second part. ${paragraph}</p><p>${paragraph}</p></section></main>`),
      "https://example.com/",
    )!;
    expect(article).toContain("First part");
    expect(article).toContain("Second part");
  });

  it("gives no article for a page with only a menu and a line", () => {
    expect(extractArticle(page('<nav><a href="/">Home</a></nav><p>Too short.</p>'), "https://example.com/")).toBeNull();
  });

  it("keeps only the tags the reader shows, with absolute addresses", () => {
    const article = extractArticle(
      page(`<article><p>${paragraph} <span class="x" style="color:red">Styled</span> <a href="../up" onclick="x()">up</a></p>
        <p>${paragraph}</p><img src="pic.jpg" srcset="pic-2x.jpg 2x" onerror="x()"><p>${paragraph}</p></article>`),
      "https://example.com/a/b",
    )!;
    expect(article).toContain('<a href="https://example.com/up">up</a>');
    expect(article).toContain('src="https://example.com/a/pic.jpg"');
    expect(article).toContain('srcset="https://example.com/a/pic-2x.jpg 2x"');
    // Commas inside an address (Substack's image server) are no separators.
    const substack = "https://substackcdn.com/image/fetch/w_1456,c_limit,f_auto/https%3A%2F%2Fs3.example%2Fpic.png";
    const withCommas = extractArticle(
      page(`<article><p>${paragraph}</p><p>${paragraph}</p><img src="${substack}" srcset="${substack} 1456w, ${substack.replace("1456", "424")} 424w"><p>${paragraph}</p></article>`),
      "https://example.com/a/b",
    );
    expect(withCommas).toContain(`srcset="${substack} 1456w, ${substack.replace("1456", "424")} 424w"`);
    for (const word of ["span", "style", "onclick", "onerror", "class"]) expect(article).not.toContain(word);
  });
});
