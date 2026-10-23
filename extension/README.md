# Content browser extension (#168)

A small extension for Chrome, Edge and Firefox that helps the Content web app:

- **Loads pages for the web app from your own browser.** The web app can't download other
  websites itself, so it uses the feed loader on Cloudflare, which many news sites block. With the
  extension installed, the web app asks the extension instead: the page then comes from your own
  connection, with your own sign-ins, so sites you pay for (#180) load in full too. Feeds still go
  through the feed loader, and through the extension only when the feed loader is refused.
- **Saves the page you're on:** the toolbar button **Save to Content** opens the web app with the
  page, which saves it to Saved (To read) and shows the save sheet to tag it.

It only answers the Content web app's own address (`bridge.js` runs nowhere else), fetches only
what the web app asks for (https only, up to 10 MB), keeps nothing, and doesn't look at your
browsing. It needs access to every site because the articles you read can be on any site; it uses
that access only when the web app asks for one page.

## Installing it yourself (for now)

**Chrome or Edge**

1. Download this repository (Code › Download ZIP) and unzip it, or use your local copy.
2. Open `chrome://extensions` (Edge: `edge://extensions`) and turn on **Developer mode**.
3. Click **Load unpacked** and pick the `extension` folder.
4. Open the Content web app (or reload it). Articles from sites that block the feed loader now open.

**Firefox**

1. Open `about:debugging#/runtime/this-firefox`.
2. Click **Load Temporary Add-on…** and pick `extension/manifest.json`. (Firefox forgets it when it
   closes; a signed version comes with publishing.)

After changing a file here, click the reload arrow on the extension's card.

## Files

| File | What it does |
|---|---|
| `manifest.json` | Manifest V3: the toolbar button, access to sites, and `bridge.js` on the web app's address only |
| `background.js` | Fetches one https address with the browser's cookies when the web app asks, and sends the bytes back |
| `bridge.js` | On the web app's pages: says the extension is there and passes the page's requests on |
| `popup.html`, `popup.js` | Save to Content |
| `icon.png` | The app's icon |

The web app's side is `web/src/lib/extension.ts` (tested in `web/test/extension.test.ts`), used by
`web/src/lib/proxy.ts`.

## Publishing (later)

Chrome Web Store: a one-time $5 developer fee; zip the folder's contents and upload. Firefox
Add-ons: free; it signs the extension. Both ask for a privacy statement: see `PRIVACY.md`.
