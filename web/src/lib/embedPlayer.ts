import { t } from "./i18n";

/**
 * Shows an embed card's player (the card made by `sanitizeArticle`): the service's own player, or
 * the site's video in the page's player, takes the card's place at the size the service asks for,
 * with an "Open in …" link under it. It plays once clicked. False for a card without a player,
 * which stays a link.
 */
export function playEmbed(card: HTMLAnchorElement): boolean {
  const player = card.dataset.player;
  if (!player || !/^https:\/\//i.test(player)) return false;
  const doc = card.ownerDocument;
  const box = doc.createElement("div");
  box.className = "embed-player";
  let media: HTMLElement;
  if (card.dataset.kind === "video") {
    const video = doc.createElement("video");
    video.src = player;
    video.controls = true;
    video.playsInline = true;
    media = video;
  } else {
    const frame = doc.createElement("iframe");
    // Bluesky's post reports its height to the page with the id it was given.
    frame.src = card.dataset.kind === "bluesky" ? `${player}?id=${Math.random().toString(36).slice(2)}` : player;
    frame.setAttribute("allow", "autoplay; encrypted-media; fullscreen; picture-in-picture; clipboard-write");
    frame.setAttribute("allowfullscreen", "");
    frame.setAttribute("referrerpolicy", "strict-origin-when-cross-origin");
    // The player runs in its own sandbox: its scripts, but never the top page or forms.
    frame.setAttribute("sandbox", "allow-scripts allow-same-origin allow-presentation allow-popups allow-popups-to-escape-sandbox");
    frame.title = card.dataset.service ?? t("embedOther");
    media = frame;
  }
  const height = Number(card.dataset.height);
  const aspect = Number(card.dataset.aspect);
  if (height > 0) media.style.height = `${height}px`;
  if (card.dataset.fits === "true" && media instanceof HTMLIFrameElement) fitToContent(media);
  else if (aspect > 0) {
    media.style.aspectRatio = String(aspect);
    if (aspect < 1) media.style.maxWidth = "360px";
  }
  box.appendChild(media);
  const service = card.dataset.service;
  if (service) {
    const open = doc.createElement("a");
    open.className = "embed-open";
    open.href = card.href;
    open.target = "_blank";
    open.rel = "noopener noreferrer";
    open.textContent = t("embedOpenIn").replace("{service}", service);
    box.appendChild(open);
  }
  card.replaceWith(box);
  return true;
}

/** Shows the player of every embed card in [root] straight away (see `playEmbed`). */
export function playAll(root: ParentNode): void {
  for (const card of Array.from(root.querySelectorAll<HTMLAnchorElement>("a.embed[data-player]"))) playEmbed(card);
}

/** The bounds of the height a post may take, in CSS pixels. */
const MIN_POST_HEIGHT = 100;
const MAX_POST_HEIGHT = 3000;

/**
 * The height a post's frame asks for in a message it sends the page, or null for any other
 * message: Bluesky's `{id, height}`, Mastodon's `{type: "setHeight", height}`, Reddit's
 * `{"type": "resize.embed", "data": height}` and Threads' `{"type": "MEASURE", "details": {height}}`
 * (both also as JSON text). Kept within bounds.
 */
export function heightFromMessage(data: unknown): number | null {
  let message = data;
  if (typeof message === "string") {
    try {
      message = JSON.parse(message);
    } catch {
      return null;
    }
  }
  if (!message || typeof message !== "object") return null;
  const m = message as { type?: unknown; height?: unknown; data?: unknown; details?: { height?: unknown } };
  let height: unknown = null;
  if (m.type === "resize.embed") height = m.data;
  else if (m.type === "MEASURE") height = m.details?.height;
  else height = m.height;
  if (typeof height !== "number" || !Number.isFinite(height) || height < MIN_POST_HEIGHT) return null;
  return Math.min(Math.ceil(height), MAX_POST_HEIGHT);
}

/**
 * Lets a post's frame take the height it reports (until then it keeps its own). Mastodon only
 * reports it when asked, once the frame has loaded.
 */
function fitToContent(frame: HTMLIFrameElement) {
  const view = frame.ownerDocument.defaultView;
  if (!view) return;
  const onMessage = (event: MessageEvent) => {
    if (!frame.isConnected) return view.removeEventListener("message", onMessage);
    if (event.source !== frame.contentWindow) return;
    const height = heightFromMessage(event.data);
    if (height !== null) frame.style.height = `${height}px`;
  };
  view.addEventListener("message", onMessage);
  frame.addEventListener("load", () => frame.contentWindow?.postMessage({ type: "setHeight", id: 1 }, "*"));
}
