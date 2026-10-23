// "Save to Content": opens the Content web app with the page you're on, which saves it to Saved
// (To read) and shows the save sheet to tag it. The web app then loads the page through this
// extension, so its text is the one you see, sign-ins included.

const WEB_APP = "https://rss-reader-web.wim-d57.workers.dev";

const dutch = navigator.language.toLowerCase().startsWith("nl");
const text = dutch ? { save: "Bewaren in Content", none: "Deze pagina kan niet bewaard worden." } : { save: "Save to Content", none: "This page can't be saved." };

document.getElementById("title").textContent = text.save;
const button = document.getElementById("save");
button.textContent = text.save;

chrome.tabs.query({ active: true, currentWindow: true }, ([tab]) => {
  const page = document.getElementById("page");
  const url = tab?.url ?? "";
  if (!/^https?:\/\//.test(url)) {
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
