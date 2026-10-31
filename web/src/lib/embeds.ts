/**
 * Embedded content (video players, posts) in articles, the same as the Android app's Embeds.kt: a
 * card that, once clicked, loads the service's own player in its place (`player`, at `size`), or,
 * without a player, opens the page on the service in a new tab. X and Instagram posts need their
 * sites' scripts, so they only open.
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
  | "x"
  | "instagram"
  | "video"
  | "other";

/** How big a player is: a fixed height in CSS pixels (audio players), else width ÷ height. */
export interface EmbedSize {
  aspect: number;
  height: number | null;
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
  x: "X",
  instagram: "Instagram",
  video: null,
  other: null,
};

export const VIDEO_SIZE: EmbedSize = { aspect: 16 / 9, height: null };
export const VERTICAL_SIZE: EmbedSize = { aspect: 9 / 16, height: null };
const fixed = (height: number): EmbedSize => ({ aspect: 16 / 9, height });

const YOUTUBE_ID = /(?:youtube(?:-nocookie)?\.com\/(?:embed\/|watch\?v=|v\/|shorts\/|live\/)|youtu\.be\/)([A-Za-z0-9_-]{6,20})/;
const VIMEO_ID = /vimeo\.com\/(?:video\/)?(\d+)/;
const SPOTIFY = /open\.spotify\.com\/(?:embed(?:-podcast)?\/)?(?:intl-[a-z-]+\/)?(track|album|playlist|episode|show|artist)\/([A-Za-z0-9]+)/;
const DAILYMOTION = /(?:dailymotion\.com\/(?:embed\/)?video\/|dai\.ly\/|dailymotion\.com\/player\.html\?(?:.*&)?video=)([A-Za-z0-9]+)/;
const APPLE = /^https:\/\/embed\.(music|podcasts)\.apple\.com\/(.+)$/;
const TIKTOK = /tiktok\.com\/(?:embed\/(?:v2\/)?|player\/v1\/|@[^/]+\/video\/)(\d+)/;
const TWEET = /(?:platform\.twitter\.com\/embed\/Tweet\.html\?(?:.*&)?id=|(?:twitter|x)\.com\/[^/]+\/status\/)(\d+)/;
const INSTAGRAM = /instagram\.com\/(p|reel|tv)\/([A-Za-z0-9_-]+)/;

/** The card for an iframe's address, or null when there is nothing to open. */
export function embedFor(src: string | null | undefined, baseUrl: string): Embed | null {
  const url = httpsUrl(src, baseUrl);
  return url ? embedOf(url) : null;
}

/** The embed for an https address. */
export function embedOf(url: string): Embed {
  let m = YOUTUBE_ID.exec(url);
  if (m) {
    const id = m[1];
    return {
      kind: "youtube",
      url: `https://www.youtube.com/watch?v=${id}`,
      thumbnailUrl: `https://i.ytimg.com/vi/${id}/hqdefault.jpg`,
      player: `https://www.youtube-nocookie.com/embed/${id}?autoplay=1&rel=0&playsinline=1`,
      size: VIDEO_SIZE,
    };
  }
  m = VIMEO_ID.exec(url);
  if (m) return { kind: "vimeo", url: `https://vimeo.com/${m[1]}`, thumbnailUrl: null, player: `https://player.vimeo.com/video/${m[1]}?autoplay=1`, size: VIDEO_SIZE };
  m = SPOTIFY.exec(url);
  if (m) {
    const [, type, id] = m;
    const short = type === "track" || type === "episode";
    return { kind: "spotify", url: `https://open.spotify.com/${type}/${id}`, thumbnailUrl: null, player: `https://open.spotify.com/embed/${type}/${id}`, size: fixed(short ? 152 : 352) };
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
      player: `https://www.dailymotion.com/embed/video/${id}?autoplay=1`,
      size: VIDEO_SIZE,
    };
  }
  m = APPLE.exec(url);
  if (m) {
    const [, service, path] = m;
    // One song or episode (?i=…) is a short player; an album, playlist or show a tall one.
    return { kind: service === "music" ? "appleMusic" : "applePodcasts", url: `https://${service}.apple.com/${path}`, thumbnailUrl: null, player: url, size: fixed(query(url, "i") !== null ? 175 : 450) };
  }
  if (url.startsWith("https://bandcamp.com/EmbeddedPlayer/")) return { kind: "bandcamp", url, thumbnailUrl: null, player: url, size: fixed(url.includes("size=large") ? 470 : 120) };
  m = TIKTOK.exec(url);
  if (m) return { kind: "tiktok", url: `https://www.tiktok.com/embed/v2/${m[1]}`, thumbnailUrl: null, player: `https://www.tiktok.com/player/v1/${m[1]}`, size: VERTICAL_SIZE };
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
