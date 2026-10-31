import { describe, expect, it } from "vitest";
import { feedLinks, findFeed, normalizeUrl, substackPublication } from "../src/lib/discovery";
import { searchIcons, parseIcons } from "../src/lib/icons";
import { emptyLibrary, articlesFor, setRead, removeFolder, addFolder, addFeed, newFeed, moveFeed, renameFeed, mergeFeed, reorder, unreadKeys, removeFeed, sameSelection, MAX_ARTICLES_PER_FEED, moveWithinFolder, chipTag, choosesFolder } from "../src/lib/library";
import { importOpml, parseOpml, writeOpml } from "../src/lib/opml";
import { FetchError } from "../src/lib/proxy";
import { sanitizeArticle } from "../src/lib/sanitize";
import { embedFor, videoEmbed, VERTICAL_SIZE, VIDEO_SIZE } from "../src/lib/embeds";
import { playEmbed } from "../src/lib/embedPlayer";
import { needsFullText, touch, wantsFullText } from "../src/lib/fullArticle";
import { articleKey, type Article } from "../src/lib/library";
import { defaultReaderStyle, MAX_TEXT_SIZE, MIN_TEXT_SIZE, pageBackground, readerStyleOf, withTextSize } from "../src/lib/readerStyle";
import { scrolledPast } from "../src/lib/scrollRead";
import { pageClass } from "../src/components/readerPage";

describe("discovery", () => {
  it("takes any Substack link for its publication", () => {
    const burkeman = "https://oliverburkeman.substack.com/";
    expect(substackPublication("https://substack.com/@oliverburkeman/note/c-372342871?r=56ok7v")).toBe(burkeman);
    expect(substackPublication("https://substack.com/@OliverBurkeman")).toBe(burkeman);
    expect(substackPublication("https://open.substack.com/pub/oliverburkeman/p/some-post?r=1")).toBe(burkeman);
    expect(substackPublication("https://oliverburkeman.substack.com/p/some-post?utm_source=share")).toBe(burkeman);
    expect(substackPublication("https://substack.com/home/post/p-123")).toBeNull();
    expect(substackPublication("https://substack.com/")).toBeNull();
    expect(substackPublication("https://example.com/@someone")).toBeNull();
    expect(substackPublication("https://notsubstack.com/@someone")).toBeNull();
  });

  it("normalizes addresses", () => {
    expect(normalizeUrl("example.com")).toBe("https://example.com/");
    expect(normalizeUrl("http://example.com/feed")).toBe("https://example.com/feed");
    expect(normalizeUrl("feed://example.com/rss")).toBe("https://example.com/rss");
    expect(normalizeUrl("not a url")).toBeNull();
    expect(normalizeUrl("localhost")).toBeNull();
  });

  it("finds feeds linked from a page, or at common paths", async () => {
    expect(feedLinks('<link rel="alternate" type="application/rss+xml" href="/rss">', "https://a.example/")).toEqual(["https://a.example/rss"]);
    const rss = "<rss><channel><title>A</title></channel></rss>";
    const pages: Record<string, string> = { "https://a.example/": "<html><head></head></html>", "https://a.example/feed": rss };
    const result = await findFeed("a.example", async (url) => {
      if (!(url in pages)) throw new FetchError("notFound");
      return { text: pages[url], url };
    });
    expect(result.kind === "found" && result.url).toBe("https://a.example/feed");
    expect((await findFeed("???", async () => ({ text: "", url: "" }))).kind).toBe("invalid");
  });
});

describe("library", () => {
  it("handles folders, renames, read state and the Unread filter", () => {
    let lib = addFolder(emptyLibrary(), { id: "f", title: " News ", icon: "folder" });
    lib = addFeed(lib, newFeed("a", "A"));
    lib = moveFeed(lib, "a", "f");
    lib = mergeFeed(lib, "a", { title: "Feed A", siteUrl: null, items: [{ id: "1", title: "t", link: null, author: null, html: "", publishedAt: 5, imageUrl: null }] }, 10);
    expect(lib.folders[0].title).toBe("News");
    expect(lib.feeds[0].title).toBe("Feed A");
    lib = renameFeed(lib, "a", "Mine");
    lib = mergeFeed(lib, "a", { title: "Feed A", siteUrl: null, items: [] }, 20);
    expect(lib.feeds[0].title).toBe("Mine");
    expect(unreadKeys(lib, { kind: "folder", folderId: "f" }).size).toBe(1);
    lib = setRead(lib, new Set(["a 1"]), true);
    expect(articlesFor(lib, { kind: "all" }, true)).toEqual([]);
    expect(articlesFor(lib, { kind: "all" }, true, new Set(["a 1"])).length).toBe(1);
    lib = removeFolder(lib, "f");
    expect(lib.feeds[0].folderId).toBeNull();
    expect(reorder([1, 2, 3], 0, 1)).toEqual([2, 1, 3]);
    expect(reorder([1, 2, 3], 0, -1)).toEqual([1, 2, 3]);
  });
});

describe("read marks that leave this browser (#157)", () => {
  const items = (...ids: [string, number][]) => ids.map(([id, publishedAt]) => ({ id, title: id, link: null, author: null, html: "", publishedAt, imageUrl: null }));
  it("keeps the mark of a read article that no longer fits, not of an unread one", () => {
    let lib = addFeed(emptyLibrary(), newFeed("a", "A"));
    lib = mergeFeed(lib, "a", { title: "A", siteUrl: null, items: items(["old", 1], ["gone", 2]) }, 1000);
    lib = setRead(lib, new Set(["a old"]), true);
    const many = Array.from({ length: MAX_ARTICLES_PER_FEED }, (_, i) => [`n${i}`, 100 + i] as [string, number]);
    lib = mergeFeed(lib, "a", { title: "A", siteUrl: null, items: items(...many) }, 2000);
    expect(lib.articles.some((x) => x.id === "old")).toBe(false);
    expect(lib.readElsewhere).toEqual({ "a old": 1 });
  });
  it("drops marks older than the sync keeps", () => {
    const day = 24 * 60 * 60 * 1000;
    const lib = addFeed({ ...emptyLibrary(), readElsewhere: { "x 1": 10 * day, "x 2": 800 * day } }, newFeed("a", "A"));
    expect(mergeFeed(lib, "a", { title: "A", siteUrl: null, items: items(["1", 1]) }, 800 * day).readElsewhere).toEqual({ "x 2": 800 * day });
  });
  it("keeps a removed feed's read marks, so adding it again brings them back read", () => {
    let lib = addFeed(emptyLibrary(), newFeed("a", "A"));
    lib = mergeFeed(lib, "a", { title: "A", siteUrl: null, items: items(["1", 5], ["2", 6]) }, 10);
    lib = removeFeed(setRead(lib, new Set(["a 2"]), true), "a");
    expect(lib.readElsewhere).toEqual({ "a 2": 6 });
    lib = mergeFeed(addFeed(lib, newFeed("a", "A")), "a", { title: "A", siteUrl: null, items: items(["1", 5], ["2", 6]) }, 20);
    expect(lib.articles.map((x) => [x.id, x.read])).toEqual([["2", true], ["1", false]]);
  });
  it("knows the same selection", () => {
    expect(sameSelection({ kind: "folder", folderId: "f" }, { kind: "folder", folderId: "f" })).toBe(true);
    expect(sameSelection({ kind: "feed", feedUrl: "a" }, { kind: "feed", feedUrl: "b" })).toBe(false);
    expect(sameSelection({ kind: "all" }, { kind: "saved" })).toBe(false);
  });
  it("treats a folder and its chip as one choice", () => {
    const lib = { ...emptyLibrary(), folders: [{ id: "f1", title: "Tech", icon: "folder" }, { id: "f2", title: "News", icon: "folder" }] };
    expect(chipTag(lib, { kind: "folder", folderId: "f1" })).toBe("Tech");
    expect(chipTag(lib, { kind: "tag", tag: "news" })).toBe("news");
    expect(chipTag(lib, { kind: "folder", folderId: "gone" })).toBeNull();
    expect(chipTag(lib, { kind: "all" })).toBeNull();
    expect(choosesFolder({ kind: "folder", folderId: "f1" }, lib.folders[0])).toBe(true);
    expect(choosesFolder({ kind: "tag", tag: "TECH" }, lib.folders[0])).toBe(true);
    expect(choosesFolder({ kind: "tag", tag: "Tech" }, lib.folders[1])).toBe(false);
    expect(choosesFolder({ kind: "feed", feedUrl: "a" }, lib.folders[0])).toBe(false);
  });
});

describe("moving feeds in Manage feeds", () => {
  it("swaps with the next feed of the same folder, not one elsewhere", () => {
    let lib = addFolder(emptyLibrary(), { id: "f", title: "F", icon: "folder" });
    for (const u of ["a", "b", "c"]) lib = addFeed(lib, newFeed(u, u));
    lib = moveFeed(moveFeed(lib, "a", "f"), "c", "f");
    expect(moveWithinFolder(lib, "a", 1).feeds.map((f) => f.url)).toEqual(["c", "b", "a"]);
    expect(moveWithinFolder(lib, "c", 1)).toBe(lib);
    expect(moveWithinFolder(lib, "b", -1)).toBe(lib);
  });
});

describe("opml", () => {
  it("imports folders and exports them again", () => {
    const text = `<?xml version="1.0"?><opml version="2.0"><body>
      <outline text="News"><outline type="rss" text="A" xmlUrl="https://a.example/feed"/></outline>
      <outline type="rss" title="B" xmlUrl="https://b.example/feed"/>
      <outline type="rss" title="B again" xmlUrl="https://b.example/feed"/>
    </body></opml>`;
    const entries = parseOpml(text);
    expect(entries).toEqual([
      { url: "https://a.example/feed", title: "A", folder: "News" },
      { url: "https://b.example/feed", title: "B", folder: null },
    ]);
    const [lib, added] = importOpml(emptyLibrary(), entries, () => "id1");
    expect(added).toBe(2);
    expect(parseOpml(writeOpml("Reader", lib))).toEqual(entries);
    expect(parseOpml("<html/>")).toEqual([]);
  });
});

describe("sanitize", () => {
  it("removes scripts and handlers, fixes links and pictures", () => {
    const html = sanitizeArticle(
      '<p onclick="x()">Hi <a href="/more">more</a></p><script>alert(1)</script><img src="http://a.example/p.jpg"><img src="data:image/png;base64,xx">',
      "https://a.example/post",
    );
    expect(html).not.toContain("<script");
    expect(html).not.toContain("onclick");
    expect(html).toContain('href="https://a.example/more"');
    expect(html).toContain('target="_blank"');
    expect(html).toContain('src="https://a.example/p.jpg"');
    expect(html).not.toContain("data:");
  });

  it("shows players and embeds as cards, nothing loading from the service until one is clicked", () => {
    const html = sanitizeArticle(
      '<iframe src="https://www.youtube.com/embed/dQw4w9WgXcQ"><p>fallback</p></iframe><iframe src="//player.vimeo.com/video/123"></iframe>' +
        '<iframe src="https://posts.example/embed/9"></iframe><iframe src="javascript:alert(1)"></iframe><video poster="/p.jpg"><source src="/v.mp4"></video>',
      "https://a.example/post",
    );
    const doc = new DOMParser().parseFromString(html, "text/html");
    expect(doc.querySelector("iframe, video, source")).toBeNull();
    expect(html).not.toContain("javascript:");
    const cards = Array.from(doc.querySelectorAll("a.embed")).map((a) => [a.getAttribute("href"), a.querySelector("img")?.getAttribute("src") ?? null, a.getAttribute("target")]);
    expect(cards).toEqual([
      ["https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", "_blank"],
      ["https://vimeo.com/123", null, "_blank"],
      ["https://posts.example/embed/9", null, "_blank"],
      ["https://a.example/v.mp4", "https://a.example/p.jpg", "_blank"],
    ]);
    expect(doc.querySelector("a.embed img")?.getAttribute("referrerpolicy")).toBe("no-referrer");
    // The players to load on a click; the unknown embed has none and only opens.
    expect(Array.from(doc.querySelectorAll("a.embed")).map((a) => a.getAttribute("data-player"))).toEqual([
      "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1&rel=0&playsinline=1",
      "https://player.vimeo.com/video/123?autoplay=1",
      null,
      "https://a.example/v.mp4",
    ]);
  });

  it("puts the player in a clicked card's place, sandboxed, with Open in … under it", () => {
    document.body.innerHTML = sanitizeArticle('<p>a</p><iframe src="https://open.spotify.com/embed/track/abc"></iframe><iframe src="https://posts.example/1"></iframe>', "https://a.example/post");
    const [spotify, other] = Array.from(document.querySelectorAll<HTMLAnchorElement>("a.embed"));
    expect(spotify.textContent).toContain("Spotify");
    expect(playEmbed(other)).toBe(false);
    expect(playEmbed(spotify)).toBe(true);
    const frame = document.querySelector<HTMLIFrameElement>(".embed-player iframe")!;
    expect(frame.getAttribute("src")).toBe("https://open.spotify.com/embed/track/abc");
    expect(frame.getAttribute("sandbox")).not.toContain("allow-top-navigation");
    expect(frame.style.height).toBe("152px");
    const open = document.querySelector<HTMLAnchorElement>(".embed-open")!;
    expect(open.getAttribute("href")).toBe("https://open.spotify.com/track/abc");
    expect(open.textContent).toBe("Open in Spotify");
    expect(document.querySelectorAll("a.embed")).toHaveLength(1);
  });

  it("leaves out pictures and embed previews when Show images is off, but keeps the cards", () => {
    const html = sanitizeArticle('<p>Text<img src="https://a.example/p.jpg"></p><iframe src="https://www.youtube.com/embed/dQw4w9WgXcQ"></iframe>', "https://a.example/post", false);
    expect(html).not.toContain("<img");
    expect(html).toContain('class="embed"');
    expect(html).toContain("Text");
  });
});

describe("full articles from the web", () => {
  it("keeps small pictures small and drops tracking pixels", () => {
    const html = sanitizeArticle(
      '<p>Hi <img src="https://a.example/smile.png" width="16" height="16"> there</p>' +
        '<img src="https://a.example/photo.jpg" width="1200" height="800" style="width:100%">' +
        '<img src="https://a.example/logo.png" height="24">' +
        '<img src="https://s.w.org/images/core/emoji/15/svg/1f600.svg">' +
        '<img src="https://a.example/p.gif" width="1" height="1"><img src="https://stats.wordpress.com/b.gif">' +
        '<img src="https://a.example/wide.jpg" width="100%">',
      "https://a.example/post",
    );
    const doc = new DOMParser().parseFromString(html, "text/html");
    const images = Array.from(doc.querySelectorAll("img")).map((img) => [img.getAttribute("src"), img.getAttribute("width"), img.getAttribute("class")]);
    expect(images).toEqual([
      ["https://a.example/smile.png", "16", "small"],
      ["https://a.example/photo.jpg", "1200", null],
      ["https://a.example/logo.png", "24", "small"],
      ["https://s.w.org/images/core/emoji/15/svg/1f600.svg", "20", "small"],
      ["https://a.example/wide.jpg", null, null],
    ]);
    expect(doc.querySelector("[height]")).toBeNull();
  });

  it("uses the largest picture a lazy-loading page offers", () => {
    const html = sanitizeArticle(
      '<img src="data:image/gif;base64,R0lGOD" data-src="/lazy.jpg"><img src="/s.jpg" srcset="/s.jpg 400w, /l.jpg 1600w, /m.jpg 800w">',
      "https://a.example/post",
    );
    const doc = new DOMParser().parseFromString(html, "text/html");
    expect(Array.from(doc.querySelectorAll("img")).map((img) => img.getAttribute("src"))).toEqual(["https://a.example/lazy.jpg", "https://a.example/l.jpg"]);
    expect(html).not.toContain("srcset");
  });

  it("turns two line breaks into a paragraph gap, and keeps a single one", () => {
    const html = sanitizeArticle("<div>One<br><br>Two<br/> <br>Three<br>still three</div>", "https://a.example/");
    const doc = new DOMParser().parseFromString(html, "text/html");
    expect(doc.querySelectorAll(".gap")).toHaveLength(2);
    expect(doc.querySelectorAll("br")).toHaveLength(1);
    expect(doc.body.textContent).toBe("OneTwo Threestill three");
  });

  it("drops sizes from everything but pictures", () => {
    expect(sanitizeArticle('<table width="900"><tr><td height="40">x</td></tr></table>', "https://a.example/")).not.toMatch(/width|height/);
  });
});

describe("embeds", () => {
  it("finds YouTube and Vimeo videos in their player addresses", () => {
    expect(embedFor("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?rel=0", "https://a.example/")?.url).toBe("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
    expect(embedFor("https://youtu.be/dQw4w9WgXcQ", "https://a.example/")?.kind).toBe("youtube");
    expect(embedFor("https://vimeo.com/video/42", "https://a.example/")).toEqual({ kind: "vimeo", url: "https://vimeo.com/42", thumbnailUrl: null, player: "https://player.vimeo.com/video/42?autoplay=1", size: VIDEO_SIZE });
    expect(embedFor("http://posts.example/1", "https://a.example/")).toEqual({ kind: "other", url: "https://posts.example/1", thumbnailUrl: null });
    expect(embedFor("", "https://a.example/")).toBeNull();
    expect(videoEmbed(null, "/p.jpg", "https://a.example/")).toBeNull();
  });

  it("knows the major players, as on Android", () => {
    const base = "https://a.example/";
    const track = embedFor("//open.spotify.com/embed/track/4uLU6hMCjMI75M1A2tKUQC?utm_source=generator", base)!;
    expect([track.kind, track.url, track.player, track.size]).toEqual(["spotify", "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC", "https://open.spotify.com/embed/track/4uLU6hMCjMI75M1A2tKUQC", { aspect: 16 / 9, height: 152 }]);
    expect(embedFor("https://open.spotify.com/embed/playlist/37i9dQZF1DXcBWIGoYBM5M", base)?.size?.height).toBe(352);
    expect(embedFor("https://open.spotify.com/embed-podcast/episode/abc", base)?.url).toBe("https://open.spotify.com/episode/abc");
    const cloud = embedFor("https://w.soundcloud.com/player/?url=https%3A//soundcloud.com/artist/song&color=%23ff5500", base)!;
    expect([cloud.kind, cloud.url, cloud.size?.height]).toEqual(["soundcloud", "https://soundcloud.com/artist/song", 166]);
    const daily = embedFor("https://www.dailymotion.com/embed/video/x8abc12", base)!;
    expect([daily.kind, daily.url, daily.player]).toEqual(["dailymotion", "https://www.dailymotion.com/video/x8abc12", "https://www.dailymotion.com/embed/video/x8abc12?autoplay=1"]);
    expect(embedFor("https://geo.dailymotion.com/player.html?video=x8abc12", base)?.url).toBe("https://www.dailymotion.com/video/x8abc12");
    const song = embedFor("https://embed.music.apple.com/be/album/name/123?i=456", base)!;
    expect([song.kind, song.url, song.size?.height]).toEqual(["appleMusic", "https://music.apple.com/be/album/name/123?i=456", 175]);
    expect(embedFor("https://embed.music.apple.com/be/album/name/123", base)?.size?.height).toBe(450);
    expect(embedFor("https://embed.podcasts.apple.com/us/podcast/show/id1", base)?.kind).toBe("applePodcasts");
    expect(embedFor("https://bandcamp.com/EmbeddedPlayer/album=1/size=large/", base)?.kind).toBe("bandcamp");
    const tikTok = embedFor("https://www.tiktok.com/embed/v2/7212345678901234567", base)!;
    expect([tikTok.player, tikTok.size]).toEqual(["https://www.tiktok.com/player/v1/7212345678901234567", VERTICAL_SIZE]);
    expect(embedFor("https://www.youtube.com/live/abcdefgh123", base)?.kind).toBe("youtube");
    // Posts need their sites' scripts: a card that opens the post, no player.
    expect(embedFor("https://platform.twitter.com/embed/Tweet.html?dnt=true&id=1234567890", base)).toEqual({ kind: "x", url: "https://x.com/i/status/1234567890", thumbnailUrl: null });
    expect(embedFor("https://www.instagram.com/p/Cabc_12/embed/captioned", base)).toEqual({ kind: "instagram", url: "https://www.instagram.com/p/Cabc_12/", thumbnailUrl: null });
  });
});

describe("full articles", () => {
  const long = "<p>" + "word ".repeat(400) + "</p>";

  it("loads the page for short texts and for teasers that link to their article", () => {
    expect(needsFullText("<p>Short summary.</p>", "https://a.example/post")).toBe(true);
    expect(needsFullText(long, "https://a.example/post")).toBe(false);
    expect(needsFullText(long + '<a href="http://www.a.example/post/#more">Read full article</a>', "https://a.example/post")).toBe(true);
    expect(needsFullText(long + '<a href="https://a.example/other">Elsewhere</a>', "https://a.example/post")).toBe(false);
    expect(needsFullText(long, null)).toBe(false);
  });

  it("starts with the full article for summaries, always when saved (#208)", () => {
    expect(wantsFullText(true, true, true, false)).toBe(true);
    expect(wantsFullText(false, true, true, false)).toBe(false);
    expect(wantsFullText(false, false, true, false)).toBe(true);
    expect(wantsFullText(false, true, true, true)).toBe(true);
    expect(wantsFullText(true, true, false, true)).toBe(false);
  });

  it("keeps the most recently opened articles", () => {
    let index = {};
    const removed: string[] = [];
    for (const [i, link] of ["a", "b", "c", "a", "d"].entries()) {
      const result = touch(index, link, i, 3);
      index = result.index;
      removed.push(...result.removed);
    }
    expect(Object.keys(index).sort()).toEqual(["a", "c", "d"]);
    expect(removed).toEqual(["b"]);
  });
});

describe("reader style", () => {
  it("keeps text sizes in range and repairs stored styles", () => {
    expect(withTextSize(defaultReaderStyle, 99).textSize).toBe(MAX_TEXT_SIZE);
    expect(withTextSize(defaultReaderStyle, 1).textSize).toBe(MIN_TEXT_SIZE);
    expect(readerStyleOf(undefined)).toEqual(defaultReaderStyle);
    expect(readerStyleOf({ font: "sans", textSize: 40, spacing: "wide", background: "sepia", autoDark: "yes" })).toEqual({
      ...defaultReaderStyle,
      font: "sans",
      textSize: MAX_TEXT_SIZE,
      background: "sepia",
    });
  });

  it("uses the dark page when Auto dark is on and the app is dark", () => {
    const sepia = { ...defaultReaderStyle, background: "sepia" as const };
    expect(pageBackground(sepia, true)).toBe("sepia");
    expect(pageBackground({ ...sepia, autoDark: true }, true)).toBe("dark");
    expect(pageBackground({ ...sepia, autoDark: true }, false)).toBe("sepia");
  });

  it("gives the sepia page its own colours, not Tailwind's sepia filter", () => {
    const sepia = pageClass({ ...defaultReaderStyle, background: "sepia" }, false);
    // "sepia" is a Tailwind class that puts a yellow photo filter over the whole page;
    // index.css gives .reader-sepia the page's colours.
    expect(sepia).toBe("reader-sepia");
  });
});

describe("mark as read on scroll", () => {
  it("marks the unread articles above the first one on screen", () => {
    const article = (id: string, read: boolean) => ({ feedUrl: "f", id, title: id, link: null, html: "", imageUrl: null, author: null, publishedAt: null, fetchedAt: 0, read }) as unknown as Article;
    const list = [article("1", false), article("2", true), article("3", false), article("4", false)];
    const all = new Set(list.map(articleKey));
    expect([...scrolledPast(list, 3, all)]).toEqual([articleKey(list[0]), articleKey(list[2])]);
    expect(scrolledPast(list, 0, all).size).toBe(0);
    expect(scrolledPast(list, -1, all).size).toBe(0);
  });

  it("leaves articles that were never on screen, like new ones arriving above", () => {
    const article = (id: string) => ({ feedUrl: "f", id, title: id, link: null, html: "", imageUrl: null, author: null, publishedAt: null, fetchedAt: 0, read: false }) as unknown as Article;
    const list = [article("new"), article("1"), article("2")];
    expect([...scrolledPast(list, 2, new Set([articleKey(list[1]), articleKey(list[2])]))]).toEqual([articleKey(list[1])]);
  });
});

describe("icons", () => {
  it("parses the Android app's icon list and searches it", () => {
    const icons = parseIcons("# comment\nfolder\tfiles\tM0 0h1\nsports-soccer\tsports, activities\tM1 1|M2 2\n");
    expect(icons.get("sports-soccer")!.paths).toEqual(["M1 1", "M2 2"]);
    expect(searchIcons(icons, "soccer").map((i) => i.name)).toEqual(["sports-soccer"]);
    expect(searchIcons(icons, "").map((i) => i.name)).toEqual(["folder", "sports-soccer"]);
  });
});
