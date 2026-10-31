// "Save to Content": opens the Content web app with the page you're on, which saves it to Saved
// (To read) and shows the save sheet to tag it. The web app then loads the page through this
// extension, so its text is the one you see, sign-ins included.

const WEB_APP = "https://content.wim.to";

// In the browser's language, from _locales (#257).
const text = { save: chrome.i18n.getMessage("saveToContent"), none: chrome.i18n.getMessage("cannotSave") };
document.documentElement.lang = chrome.i18n.getUILanguage();
document.title = text.save;

document.getElementById("title").textContent = text.save;
const button = document.getElementById("save");
button.textContent = text.save;

/**
 * The page you're on. Some browsers (Arc, Vivaldi, Opera and others) open the popup as a window of
 * its own, so "the current window" has no page and the button stayed grey: ask for the window used
 * last first, then the current one, and take a page still loading by its pending address.
 */
async function pageTab() {
  for (const query of [{ active: true, lastFocusedWindow: true }, { active: true, currentWindow: true }]) {
    try {
      const tabs = await chrome.tabs.query(query);
      const tab = tabs.find((t) => /^https?:\/\//.test(t.url || t.pendingUrl || ""));
      if (tab) return tab;
    } catch {
      // This browser doesn't know that question: try the next one.
    }
  }
  return null;
}

pageTab().then((tab) => {
  const page = document.getElementById("page");
  const url = tab ? tab.url || tab.pendingUrl : "";
  if (!url) {
    page.textContent = text.none;
    return;
  }
  page.textContent = tab.title || url;
  button.disabled = false;
  button.addEventListener("click", () => {
    const target = `${WEB_APP}/?save=${encodeURIComponent(url)}&title=${encodeURIComponent(tab.title ?? "")}`;
    chrome.tabs.create({ url: target });
    window.close();
  });
});
