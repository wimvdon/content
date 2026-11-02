/**
 * Embedded content (video players, posts) in articles, the same as the Android app's Embeds.kt: a
 * card whose player (`player`, at `size`) the reader shows in its place straight away, or, without a
 * player, that opens the page on the service in a new tab. X and Instagram posts only open.
 */

export type EmbedKind =
  | "youtube"
  | "vimeo"
  | "spotify"
  | "soundcloud"
  | "dailymotion"
  | "appleMusic"
  | "applePodcasts"
  | "bandcamp"
  | "tiktok"
  | "bluesky"
  | "mastodon"
  | "threads"
  | "reddit"
  | "twitch"
  | "googleMaps"
  | "codepen"
  | "giphy"
  | "x"
  | "instagram"
  | "video"
  | "other";

/**
 * How big a player is: a fixed height in CSS pixels (audio players), else width ÷ height. A post
 * (Bluesky, Mastodon, …) `fitsContent`: it takes the height the post reports, `height` until then.
 */
export interface EmbedSize {
  aspect: number;
  height: number | null;
  fitsContent?: boolean;
}

export interface Embed {
  kind: EmbedKind;
  /** The page on the service, opened by the card without a player and by "Open in …". */
  url: string;
  /** A preview picture, when the service has a known one. */
  thumbnailUrl: string | null;
  /** What plays in the card's place once clicked, or null. */
  player?: string | null;
  size?: EmbedSize;
}

/** The service's own name, never translated; null for a site's video and unknown embeds. */
export const EMBED_SERVICES: Record<EmbedKind, string | null> = {
  youtube: "YouTube",
  vimeo: "Vimeo",
  spotify: "Spotify",
  soundcloud: "SoundCloud",
  dailymotion: "Dailymotion",
  appleMusic: "Apple Music",
  applePodcasts: "Apple Podcasts",
  bandcamp: "Bandcamp",
  tiktok: "TikTok",
  bluesky: "Bluesky",
  mastodon: "Mastodon",
  threads: "Threads",
  reddit: "Reddit",
  twitch: "Twitch",
  googleMaps: "Google Maps",
  codepen: "CodePen",
  giphy: "GIPHY",
  x: "X",
  instagram: "Instagram",
  video: null,
  other: null,
};

export const VIDEO_SIZE: EmbedSize = { aspect: 16 / 9, height: null };
export const VERTICAL_SIZE: EmbedSize = { aspect: 9 / 16, height: null };
const fixed = (height: number): EmbedSize => ({ aspect: 16 / 9, height });
const post = (height: number): EmbedSize => ({ aspect: 16 / 9, height, fitsContent: true });

const YOUTUBE_ID = /(?:youtube(?:-nocookie)?\.com\/(?:embed\/|watch\?v=|v\/|shorts\/|live\/)|youtu\.be\/)([A-Za-z0-9_-]{6,20})/;
const VIMEO_ID = /vimeo\.com\/(?:video\/)?(\d+)/;
const SPOTIFY = /open\.spotify\.com\/(?:embed(?:-podcast)?\/)?(?:intl-[a-z-]+\/)?(track|album|playlist|episode|show|artist)\/([A-Za-z0-9]+)/;
const DAILYMOTION = /(?:dailymotion\.com\/(?:embed\/)?video\/|dai\.ly\/|dailymotion\.com\/player\.html\?(?:.*&)?video=)([A-Za-z0-9]+)/;
const APPLE = /^https:\/\/embed\.(music|podcasts)\.apple\.com\/(.+)$/;
const TIKTOK = /tiktok\.com\/(?:embed\/(?:v2\/)?|player\/v1\/|@[^/]+\/video\/)(\d+)/;
const TWEET = /(?:platform\.twitter\.com\/embed\/Tweet\.html\?(?:.*&)?id=|(?:twitter|x)\.com\/[^/]+\/status\/)(\d+)/;
const INSTAGRAM = /instagram\.com\/(p|reel|tv)\/([A-Za-z0-9_-]+)/;
const BLUESKY = /^https:\/\/embed\.bsky\.app\/embed\/(did:[a-z0-9]+:[A-Za-z0-9._:%-]+)\/app\.bsky\.feed\.post\/([A-Za-z0-9]+)/;
const MASTODON = /^https:\/\/([a-z0-9.-]+\.[a-z]{2,})\/@([A-Za-z0-9_]+(?:@[a-z0-9.-]+)?)\/(\d+)\/embed/;
const THREADS = /^https:\/\/(?:www\.)?threads\.(?:net|com)\/@([A-Za-z0-9._]+)\/post\/([A-Za-z0-9_-]+)\/embed/;
const REDDIT = /^https:\/\/(?:embed\.reddit\.com|(?:www\.)?redditmedia\.com)\/(r\/[A-Za-z0-9_]+\/comments\/[A-Za-z0-9]+(?:\/[^?#]*)?)/;
const TWITCH = /^https:\/\/(?:player\.twitch\.tv\/\?|clips\.twitch\.tv\/embed\?)/;
const GOOGLE_MAPS = /^https:\/\/(?:www\.|maps\.)?google\.[a-z.]+\/maps\/embed/;
const CODEPEN = /^https:\/\/codepen\.io\/([A-Za-z0-9_-]+)\/embed\/(?:preview\/)?([A-Za-z0-9]+)/;
const GIPHY = /^https:\/\/giphy\.com\/embed\/([A-Za-z0-9]+)/;

/** Content's web app: Twitch only plays inside the page it is told about. */
const PLAYER_HOST = "content.wim.to";

// Posts written as a quote that the service's script turns into the post (scripts never run in
// the reader): the class and the attribute that name the post.
const POST_QUOTE = /<blockquote\b[^>]*\bclass\s*=\s*["'][^"']*\b(bluesky-embed|mastodon-embed|text-post-media|reddit-embed-bq)\b[^>]*>[\s\S]*?<\/blockquote\s*>/gi;
const BLUESKY_URI = /data-bluesky-uri\s*=\s*["']at:\/\/(did:[^/"']+)\/app\.bsky\.feed\.post\/([A-Za-z0-9]+)["']/;
const MASTODON_EMBED = /data-embed-url\s*=\s*["'](https:\/\/[^"']+\/embed)["']/;
const THREADS_PERMALINK = /data-text-post-permalink\s*=\s*["'](https:\/\/(?:www\.)?threads\.(?:net|com)\/@[A-Za-z0-9._]+\/post\/[A-Za-z0-9_-]+)/;
const REDDIT_LINK = /href\s*=\s*["']https:\/\/(?:www\.|old\.)?reddit\.com\/(r\/[A-Za-z0-9_]+\/comments\/[A-Za-z0-9]+(?:\/[^"'?#]*)?)/;

/**
 * `html` with each post written as a quote for the service's script (Bluesky, Mastodon, Threads,
 * Reddit) turned into an iframe of that post, so it shows like the iframes the reader knows. A
 * quote it can't read stays a quote. The same as Android's `Embeds.postQuotesToFrames`.
 */
export function postQuotesToFrames(html: string): string {
  return html.replace(POST_QUOTE, (quote, kind: string) => {
    let src: string | null = null;
    let m: RegExpExecArray | null;
    switch (kind.toLowerCase()) {
      case "bluesky-embed":
        m = BLUESKY_URI.exec(quote);
        if (m) src = `https://embed.bsky.app/embed/${m[1]}/app.bsky.feed.post/${m[2]}`;
        break;
      case "mastodon-embed":
        src = MASTODON_EMBED.exec(quote)?.[1] ?? null;
        break;
      case "text-post-media":
        m = THREADS_PERMALINK.exec(quote);
        if (m) src = `${m[1]}/embed`;
        break;
      default:
        m = REDDIT_LINK.exec(quote);
        if (m) src = `https://embed.reddit.com/${m[1]}`;
    }
    return src ? `<iframe src="${src.replace(/&/g, "&amp;").replace(/"/g, "&quot;")}"></iframe>` : quote;
  });
}

/**
 * The card for an iframe's address, or null when there is nothing to open. `statedHeight` is the
 * height the site gives the iframe, in pixels, when it gives one.
 */
export function embedFor(src: string | null | undefined, baseUrl: string, statedHeight: number | null = null): Embed | null {
  const url = httpsUrl(src, baseUrl);
  return url ? embedOf(url, statedHeight) : null;
}

/**
 * The height in pixels a site gives an iframe, from its height attribute ("120" or "120px") or its
 * style ("height: 120px"), or null when it gives none in pixels or one out of bounds. The same as
 * Android's `Embeds.statedHeight`.
 */
export function statedHeight(attribute: string | null | undefined, style: string | null | undefined): number | null {
  const fromStyle = style ? /(?:^|;)\s*height\s*:\s*(\d+)(?:\.\d+)?\s*px/i.exec(style)?.[1] : undefined;
  const fromAttribute = attribute?.trim().replace(/px$/i, "").trim();
  const height = Number(fromStyle ?? (fromAttribute && /^\d+$/.test(fromAttribute) ? fromAttribute : NaN));
  return Number.isInteger(height) && height >= 40 && height <= 1500 ? height : null;
}

/**
 * How high Bandcamp's player is for its address, when the site doesn't say: the small one 42, the
 * large one 120 without its big artwork (artwork=small or none), else 470 with it.
 */
function bandcampHeight(url: string): number {
  const path = url.toLowerCase();
  if (!path.includes("size=large")) return path.includes("size=small") ? 42 : 120;
  return path.includes("artwork=small") || path.includes("artwork=none") ? 120 : 470;
}

/**
 * The embed for an https address. `statedHeight` is the height the site gives the iframe, used
 * where the address doesn't say it (Bandcamp).
 */
export function embedOf(url: string, statedHeight: number | null = null): Embed {
  let m = YOUTUBE_ID.exec(url);
  if (m) {
    const id = m[1];
    return {
      kind: "youtube",
      url: `https://www.youtube.com/watch?v=${id}`,
      thumbnailUrl: `https://i.ytimg.com/vi/${id}/hqdefault.jpg`,
      player: `https://www.youtube-nocookie.com/embed/${id}?rel=0&playsinline=1`,
      size: VIDEO_SIZE,
    };
  }
  m = VIMEO_ID.exec(url);
  if (m) return { kind: "vimeo", url: `https://vimeo.com/${m[1]}`, thumbnailUrl: null, player: `https://player.vimeo.com/video/${m[1]}`, size: VIDEO_SIZE };
  m = SPOTIFY.exec(url);
  if (m) {
    const [, type, id] = m;
    // Always Spotify's full player, with its cover: the compact one (152 high) is cramped.
    return { kind: "spotify", url: `https://open.spotify.com/${type}/${id}`, thumbnailUrl: null, player: `https://open.spotify.com/embed/${type}/${id}`, size: fixed(352) };
  }
  if (url.startsWith("https://w.soundcloud.com/player/")) {
    const track = query(url, "url");
    const page = track?.startsWith("https://soundcloud.com/") ? track : "https://soundcloud.com";
    return { kind: "soundcloud", url: page, thumbnailUrl: null, player: url, size: fixed(query(url, "visual") === "true" ? 300 : 166) };
  }
  m = DAILYMOTION.exec(url);
  if (m) {
    const id = m[1];
    return {
      kind: "dailymotion",
      url: `https://www.dailymotion.com/video/${id}`,
      thumbnailUrl: `https://www.dailymotion.com/thumbnail/video/${id}`,
      player: `https://www.dailymotion.com/embed/video/${id}`,
      size: VIDEO_SIZE,
    };
  }
  m = APPLE.exec(url);
  if (m) {
    const [, service, path] = m;
    // One song or episode (?i=…) is a short player; an album, playlist or show a tall one.
    return { kind: service === "music" ? "appleMusic" : "applePodcasts", url: `https://${service}.apple.com/${path}`, thumbnailUrl: null, player: url, size: fixed(query(url, "i") !== null ? 175 : 450) };
  }
  if (url.startsWith("https://bandcamp.com/EmbeddedPlayer/")) return { kind: "bandcamp", url, thumbnailUrl: null, player: url, size: fixed(statedHeight ?? bandcampHeight(url)) };
  m = TIKTOK.exec(url);
  if (m) return { kind: "tiktok", url: `https://www.tiktok.com/embed/v2/${m[1]}`, thumbnailUrl: null, player: `https://www.tiktok.com/player/v1/${m[1]}`, size: VERTICAL_SIZE };
  m = BLUESKY.exec(url);
  if (m) return { kind: "bluesky", url: `https://bsky.app/profile/${m[1]}/post/${m[2]}`, thumbnailUrl: null, player: m[0], size: post(400) };
  m = MASTODON.exec(url);
  if (m) return { kind: "mastodon", url: `https://${m[1]}/@${m[2]}/${m[3]}`, thumbnailUrl: null, player: m[0], size: post(400) };
  m = THREADS.exec(url);
  if (m) {
    const page = `https://www.threads.net/@${m[1]}/post/${m[2]}`;
    return { kind: "threads", url: page, thumbnailUrl: null, player: `${page}/embed`, size: post(550) };
  }
  m = REDDIT.exec(url);
  if (m) {
    const path = m[1].replace(/\/+$/, "");
    return { kind: "reddit", url: `https://www.reddit.com/${path}/`, thumbnailUrl: null, player: `https://embed.reddit.com/${path}/?embed=true`, size: post(400) };
  }
  if (TWITCH.test(url)) {
    const clip = query(url, "clip");
    const video = query(url, "video");
    const channel = query(url, "channel");
    const page = clip ? `https://clips.twitch.tv/${clip}` : video ? `https://www.twitch.tv/videos/${video.replace(/^v/, "")}` : channel ? `https://www.twitch.tv/${channel}` : "https://www.twitch.tv";
    const [start, rest = ""] = url.split(/\?(.*)/s);
    const kept = rest.split("&").filter((p) => !p.startsWith("parent=") && !p.startsWith("autoplay="));
    return { kind: "twitch", url: page, thumbnailUrl: null, player: `${start}?${kept.join("&")}&parent=${PLAYER_HOST}&autoplay=false`, size: VIDEO_SIZE };
  }
  if (GOOGLE_MAPS.test(url)) return { kind: "googleMaps", url, thumbnailUrl: null, player: url, size: { aspect: 4 / 3, height: null } };
  m = CODEPEN.exec(url);
  if (m) return { kind: "codepen", url: `https://codepen.io/${m[1]}/pen/${m[2]}`, thumbnailUrl: null, player: url, size: fixed(400) };
  m = GIPHY.exec(url);
  if (m) return { kind: "giphy", url: `https://giphy.com/gifs/${m[1]}`, thumbnailUrl: null, player: `https://giphy.com/embed/${m[1]}`, size: { aspect: 4 / 3, height: null } };
  m = TWEET.exec(url);
  if (m) return { kind: "x", url: `https://x.com/i/status/${m[1]}`, thumbnailUrl: null };
  m = INSTAGRAM.exec(url);
  if (m) return { kind: "instagram", url: `https://www.instagram.com/${m[1]}/${m[2]}/`, thumbnailUrl: null };
  return { kind: "other", url, thumbnailUrl: null };
}

/** The card for a video file, with its poster as preview, or null without a source. It plays in the page's own player. */
export function videoEmbed(src: string | null | undefined, poster: string | null | undefined, baseUrl: string): Embed | null {
  const url = httpsUrl(src, baseUrl);
  return url ? { kind: "video", url, thumbnailUrl: httpsUrl(poster, baseUrl), player: url, size: VIDEO_SIZE } : null;
}

/** One query parameter of `url`, decoded, or null. */
function query(url: string, name: string): string | null {
  for (const part of (url.split("?")[1] ?? "").split("&")) {
    const [key, ...rest] = part.split("=");
    if (key !== name) continue;
    try {
      return decodeURIComponent(rest.join("="));
    } catch {
      return null;
    }
  }
  return null;
}

/** An absolute https address (http is upgraded), or null. */
function httpsUrl(href: string | null | undefined, baseUrl: string): string | null {
  if (!href?.trim()) return null;
  try {
    const url = new URL(href.trim(), baseUrl);
    if (url.protocol === "http:") url.protocol = "https:";
    return url.protocol === "https:" ? url.toString() : null;
  } catch {
    return null;
  }
}
