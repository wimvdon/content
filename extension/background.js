// Content's browser extension (#168), the part that fetches. It only answers the Content web app
// (bridge.js runs on its address only) and only fetches what the web app asks for: one https page or
// feed at a time, with this browser's own cookies, so sites that block the feed loader, and paywalls
// you pay for, load as they do in your browser. It never fetches anything on its own, keeps nothing
// and doesn't look at your browsing. It also holds the right-click menu: Save to Content on a page or
// a link (#257).

const MAX_BYTES = 10 * 1024 * 1024;
const TIMEOUT_MS = 30000;

/** True for an address the extension may fetch: https only. */
function allowed(url) {
  try {
    return new URL(url).protocol === "https:";
  } catch {
    return false;
  }
}

async function load(url) {
  if (!allowed(url)) return { ok: false, problem: "notAllowed" };
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const response = await fetch(url, { credentials: "include", redirect: "follow", signal: controller.signal });
    if (!response.ok) return { ok: false, problem: response.status === 404 || response.status === 410 ? "notFound" : "server" };
    const buffer = await response.arrayBuffer();
    if (buffer.byteLength > MAX_BYTES) return { ok: false, problem: "tooLarge" };
    // Bytes as base64, so the web app decodes the text with the page's own charset.
    let binary = "";
    const bytes = new Uint8Array(buffer);
    for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
    return { ok: true, url: response.url || url, contentType: response.headers.get("Content-Type") || "", body: btoa(binary) };
  } catch {
    return { ok: false, problem: "offline" };
  } finally {
    clearTimeout(timer);
  }
}

chrome.runtime.onMessage.addListener((message, sender, reply) => {
  // Only the bridge on the web app's own pages may ask (the manifest runs it nowhere else).
  if (!sender.tab || message?.type !== "fetch" || typeof message.url !== "string") return false;
  load(message.url).then(reply);
  return true;
});

// Right-click menu (#257): Save to Content on the page, Save link to Content on a link. Like the
// toolbar button, it opens the web app with the address, which saves it and shows Save and share.
const WEB_APP = "https://content.wim.to";

chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.removeAll(() => {
    chrome.contextMenus.create({ id: "save-page", title: chrome.i18n.getMessage("saveToContent"), contexts: ["page"], documentUrlPatterns: ["https://*/*", "http://*/*"] });
    chrome.contextMenus.create({ id: "save-link", title: chrome.i18n.getMessage("saveLinkToContent"), contexts: ["link"], targetUrlPatterns: ["https://*/*", "http://*/*"] });
  });
});

chrome.contextMenus.onClicked.addListener((info, tab) => {
  // A link's text only in Firefox; without one the web app reads the page's own title.
  const [url, title] = info.menuItemId === "save-link" ? [info.linkUrl, info.linkText ?? ""] : [info.pageUrl ?? tab?.url, tab?.title ?? ""];
  if (!url || !/^https?:\/\//.test(url)) return;
  chrome.tabs.create({ url: `${WEB_APP}/?save=${encodeURIComponent(url)}&title=${encodeURIComponent(title)}`, index: tab ? tab.index + 1 : undefined });
});
