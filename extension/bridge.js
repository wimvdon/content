// Runs only on the Content web app's own pages (manifest.json). It tells the page the extension is
// there and passes its fetch requests to background.js, which loads them with this browser's own
// cookies. Messages carry {source: "content-web" | "content-extension"} so nothing else is answered.

const VERSION = "1.1.0";

function hello() {
  window.postMessage({ source: "content-extension", type: "hello", version: VERSION }, window.location.origin);
}

window.addEventListener("message", (event) => {
  if (event.source !== window || event.origin !== window.location.origin) return;
  const message = event.data;
  if (!message || message.source !== "content-web") return;
  if (message.type === "ping") {
    hello();
    return;
  }
  if (message.type !== "fetch" || typeof message.id !== "string" || typeof message.url !== "string") return;
  chrome.runtime.sendMessage({ type: "fetch", url: message.url }, (answer) => {
    const result = answer ?? { ok: false, problem: "offline" };
    window.postMessage({ source: "content-extension", type: "fetched", id: message.id, ...result }, window.location.origin);
  });
});

document.documentElement.dataset.contentExtension = VERSION;
hello();
