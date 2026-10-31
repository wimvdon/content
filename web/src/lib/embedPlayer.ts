import { t } from "./i18n";

/**
 * Plays an embed card (made by `sanitizeArticle`): the service's own player, or the site's video in
 * the page's player, takes the card's place at the size the service asks for, with an "Open in …"
 * link under it. Nothing loads from the service before this click. False for a card without a
 * player, which then opens as a link.
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
    video.autoplay = true;
    video.playsInline = true;
    media = video;
  } else {
    const frame = doc.createElement("iframe");
    frame.src = player;
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
