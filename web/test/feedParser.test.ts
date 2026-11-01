import { describe, expect, it } from "vitest";
import { parseFeedDate } from "../src/lib/dates";
import { fallbackId, NotAFeedError, parseFeed } from "../src/lib/feedParser";
import { javaHashCode, snippet, toPlainText } from "../src/lib/html";

describe("parseFeed", () => {
  it("reads RSS 2.0 with guids, links, content and pictures", () => {
    const feed = parseFeed(
      `<?xml version="1.0"?>
      <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/" xmlns:media="http://search.yahoo.com/mrss/" xmlns:dc="http://purl.org/dc/elements/1.1/">
        <channel>
          <title>Example &amp; Co</title>
          <link>https://example.com/</link>
          <item>
            <title>First &lt;b&gt;post&lt;/b&gt;</title>
            <link>/posts/1</link>
            <guid isPermaLink="false">id-1</guid>
            <description>Short</description>
            <content:encoded><![CDATA[<p>Longer text</p>]]></content:encoded>
            <pubDate>Tue, 03 Jun 2008 11:05:30 GMT</pubDate>
            <dc:creator>Ann</dc:creator>
            <media:thumbnail url="http://example.com/a.jpg"/>
          </item>
          <item>
            <title>Second</title>
            <link>https://example.com/posts/2</link>
          </item>
        </channel>
      </rss>`,
      "https://example.com/feed.xml",
    );
    expect(feed.title).toBe("Example & Co");
    expect(feed.siteUrl).toBe("https://example.com/");
    const [first, second] = feed.items;
    expect(first.id).toBe("id-1");
    expect(first.title).toBe("First post");
    expect(first.link).toBe("https://example.com/posts/1");
    expect(first.html).toBe("<p>Longer text</p>");
    expect(first.author).toBe("Ann");
    expect(first.publishedAt).toBe(Date.UTC(2008, 5, 3, 11, 5, 30));
    expect(first.imageUrl).toBe("https://example.com/a.jpg");
    // Without a guid the link is the id, like on Android.
    expect(second.id).toBe("https://example.com/posts/2");
  });

  it("reads Atom with xml:base and xhtml content", () => {
    const feed = parseFeed(
      `<feed xmlns="http://www.w3.org/2005/Atom" xml:base="https://blog.example/">
        <title type="text">Blog</title>
        <link rel="alternate" href="/"/>
        <author><name>Bob</name></author>
        <entry>
          <id>tag:blog.example,2024:1</id>
          <title>Hello</title>
          <link href="posts/hello"/>
          <updated>2024-01-02T03:04:05+01:00</updated>
          <content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml"><p>Hi <b>there</b></p></div></content>
        </entry>
      </feed>`,
      "https://blog.example/atom.xml",
    );
    expect(feed.title).toBe("Blog");
    expect(feed.siteUrl).toBe("https://blog.example/");
    const entry = feed.items[0];
    expect(entry.id).toBe("tag:blog.example,2024:1");
    expect(entry.link).toBe("https://blog.example/posts/hello");
    expect(entry.author).toBe("Bob");
    expect(entry.html).toBe("<p>Hi <b>there</b></p>");
    expect(entry.publishedAt).toBe(Date.UTC(2024, 0, 2, 2, 4, 5));
  });

  it("reads RSS 1.0 and feeds with HTML entities", () => {
    const feed = parseFeed(
      `<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns="http://purl.org/rss/1.0/">
        <channel><title>Old&nbsp;style</title><link>https://old.example/</link></channel>
        <item><title>A &mdash; B</title><link>https://old.example/1</link></item>
      </rdf:RDF>`,
      "https://old.example/rss",
    );
    expect(feed.title).toBe("Old style");
    expect(feed.items[0].title).toBe("A — B");
  });

  it("gives items without guid or link the same id as the Android app", () => {
    const feed = parseFeed(`<rss><channel><title>T</title><item><title>Only text</title><description>Body</description></item></channel></rss>`, "https://x.example/");
    expect(feed.items[0].id).toBe(fallbackId("Only text", null, "Body"));
    expect(fallbackId("Only text", null, "Body")).toBe("hash:" + javaHashCode("Only text|null|Body").toString(16));
  });

  it("keeps only http and https links, so a feed can't make a link run script", () => {
    const rss = parseFeed(
      `<rss version="2.0"><channel><title>T</title><link>javascript:alert(1)</link>
        <item><title>A</title><link>javascript:alert(document.domain)</link><guid>https://example.com/a</guid></item>
        <item><title>B</title><link>data:text/html,hi</link></item>
      </channel></rss>`,
      "https://example.com/feed.xml",
    );
    expect(rss.siteUrl).toBeNull();
    expect(rss.items[0].link).toBe("https://example.com/a");
    expect(rss.items[1].link).toBeNull();
    const atom = parseFeed(
      `<feed xmlns="http://www.w3.org/2005/Atom"><title>T</title><link rel="alternate" href="javascript:void(0)"/>
        <entry><title>A</title><id>a</id><link rel="alternate" href="JavaScript:alert(1)"/></entry>
        <entry><title>B</title><id>b</id><link rel="alternate" href="/b"/></entry>
      </feed>`,
      "https://example.com/feed.xml",
    );
    expect(atom.siteUrl).toBeNull();
    expect(atom.items[0].link).toBeNull();
    expect(atom.items[1].link).toBe("https://example.com/b");
  });

  it("refuses web pages", () => {
    expect(() => parseFeed("<html><body>Hi</body></html>", "https://x.example/")).toThrow(NotAFeedError);
    expect(() => parseFeed("not xml at all", "https://x.example/")).toThrow(NotAFeedError);
  });
});

describe("javaHashCode", () => {
  it("matches Java's String.hashCode", () => {
    expect(javaHashCode("")).toBe(0);
    expect(javaHashCode("hello")).toBe(99162322);
    // Negative in Java; unsigned here, like Kotlin's toUInt().
    expect(javaHashCode("Hello, world!")).toBe(-1880044555 >>> 0);
  });
});

describe("dates", () => {
  it("reads RFC 822 and ISO 8601 variants", () => {
    expect(parseFeedDate("Tue, 3 Jun 2008 11:05:30 GMT")).toBe(Date.UTC(2008, 5, 3, 11, 5, 30));
    expect(parseFeedDate("03 Jun 08 11:05 +0200")).toBe(Date.UTC(2008, 5, 3, 9, 5));
    expect(parseFeedDate("Wed, 04 June 2008 10:00:00 EST")).toBe(Date.UTC(2008, 5, 4, 15));
    expect(parseFeedDate("2008-06-03T11:05:30.123Z")).toBe(Date.UTC(2008, 5, 3, 11, 5, 30, 123));
    expect(parseFeedDate("2008-06-03")).toBe(Date.UTC(2008, 5, 3));
    expect(parseFeedDate("someday")).toBeNull();
    expect(parseFeedDate("")).toBeNull();
    expect(parseFeedDate("2008-02-30T00:00:00Z")).toBeNull();
  });
});

describe("html", () => {
  it("makes plain text and snippets", () => {
    expect(toPlainText("<p>One</p><p>Two&nbsp;three</p><script>x()</script>")).toBe("One Two three");
    expect(snippet("<p>" + "word ".repeat(50) + "</p>", 20)).toBe("word word word word…");
  });
});
