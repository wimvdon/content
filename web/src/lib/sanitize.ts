import DOMPurify from "dompurify";

/**
 * Article HTML made safe to show: no scripts, styles, forms or frames except video players,
 * links open in a new tab without telling the site where they came from, pictures load lazily
 * and only over https.
 */
export function sanitizeArticle(html: string, baseUrl: string): string {
  const clean = DOMPurify.sanitize(html, {
    FORBID_TAGS: ["style", "form", "input", "button", "textarea", "select", "object", "embed"],
    FORBID_ATTR: ["style", "class", "id", "width", "height"],
    ADD_TAGS: ["iframe"],
    ADD_ATTR: ["allowfullscreen"],
    RETURN_DOM_FRAGMENT: true,
  });
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
  for (const frame of Array.from(clean.querySelectorAll("iframe"))) {
    const src = absolute(frame.getAttribute("src") ?? "", baseUrl) ?? "";
    if (!/^https:\/\/(www\.)?(youtube(-nocookie)?\.com|player\.vimeo\.com)\//.test(src)) {
      frame.remove();
      continue;
    }
    frame.setAttribute("src", src.replace("://www.youtube.com/", "://www.youtube-nocookie.com/"));
    frame.setAttribute("loading", "lazy");
    frame.setAttribute("referrerpolicy", "strict-origin");
    frame.setAttribute("sandbox", "allow-scripts allow-same-origin allow-presentation allow-popups");
  }
  const holder = document.createElement("div");
  holder.appendChild(clean);
  return holder.innerHTML;
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
