import DOMPurify from "dompurify";
import { embedFor, videoEmbed, type Embed, type EmbedKind } from "./embeds";
import { t, type Key } from "./i18n";

/**
 * Article HTML made safe to show: no scripts, styles, forms or frames; video players and other
 * embeds become a card that opens them in a new tab (nothing plays inside the page); links open
 * in a new tab without telling the site where they came from; pictures load lazily and only over
 * https.
 */
export function sanitizeArticle(html: string, baseUrl: string): string {
  // An iframe's fallback content never shows, and with markup in it DOMPurify drops the whole
  // iframe (and so its card).
  const clean = DOMPurify.sanitize(html.replace(/(<iframe\b[^>]*>)[\s\S]*?<\/iframe\s*>/gi, "$1</iframe>"), {
    FORBID_TAGS: ["style", "form", "input", "button", "textarea", "select", "object", "embed"],
    FORBID_ATTR: ["style", "class", "id", "width", "height"],
    ADD_TAGS: ["iframe"],
    RETURN_DOM_FRAGMENT: true,
  });
  for (const frame of Array.from(clean.querySelectorAll("iframe"))) replaceWithCard(frame, embedFor(frame.getAttribute("src"), baseUrl));
  for (const video of Array.from(clean.querySelectorAll("video"))) {
    const src = video.getAttribute("src") ?? video.querySelector("source[src]")?.getAttribute("src");
    replaceWithCard(video, videoEmbed(src, video.getAttribute("poster"), baseUrl));
  }
  for (const a of Array.from(clean.querySelectorAll("a[href]"))) {
    a.setAttribute("href", absolute(a.getAttribute("href")!, baseUrl) ?? "#");
    a.setAttribute("target", "_blank");
    a.setAttribute("rel", "noopener noreferrer");
  }
  for (const img of Array.from(clean.querySelectorAll("img"))) {
    const src = img.getAttribute("src") ?? img.getAttribute("data-src") ?? "";
    const url = absolute(src, baseUrl);
    if (!url || !url.startsWith("https:")) {
      img.remove();
      continue;
    }
    img.setAttribute("src", url);
    img.removeAttribute("srcset");
    img.setAttribute("loading", "lazy");
    img.setAttribute("referrerpolicy", "no-referrer");
  }
  const holder = document.createElement("div");
  holder.appendChild(clean);
  return holder.innerHTML;
}

const LABELS: Record<EmbedKind, Key> = { youtube: "embedYoutube", vimeo: "embedVimeo", video: "embedVideo", other: "embedOther" };

/** Puts an embed card (preview picture and label) where the player was, or removes it. */
function replaceWithCard(element: Element, embed: Embed | null) {
  if (!embed) {
    element.remove();
    return;
  }
  const doc = element.ownerDocument;
  const card = doc.createElement("a");
  card.className = "embed";
  card.setAttribute("href", embed.url);
  if (embed.thumbnailUrl) {
    const img = doc.createElement("img");
    img.setAttribute("src", embed.thumbnailUrl);
    img.setAttribute("alt", "");
    card.appendChild(img);
  }
  const label = doc.createElement("span");
  label.className = embed.kind === "other" ? "embed-label" : "embed-label embed-play";
  label.textContent = t(LABELS[embed.kind]);
  card.appendChild(label);
  element.replaceWith(card);
}

function absolute(href: string, base: string): string | null {
  try {
    const url = new URL(href, base);
    if (url.protocol === "http:") url.protocol = "https:";
    return ["https:", "mailto:"].includes(url.protocol) ? url.toString() : null;
  } catch {
    return null;
  }
}
