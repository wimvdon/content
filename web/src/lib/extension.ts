/**
 * Content's browser extension (#168, extension/): when it is installed, it loads pages and feeds for
 * the web app from the user's own browser, with their own sign-ins, so sites that block the feed
 * loader (and paywalls they pay for, #180) load as in a browser tab. The extension marks the page
 * (data-content-extension on <html>) and answers window messages; nothing goes anywhere else.
 */

/** The window the extension talks through; tests give their own. */
export interface MessageWindow {
  postMessage(message: unknown, targetOrigin: string): void;
  addEventListener(type: "message", listener: (event: MessageEvent) => void): void;
  removeEventListener(type: "message", listener: (event: MessageEvent) => void): void;
  location: { origin: string };
}

/** What the extension sends back for one fetch. */
export interface ExtensionAnswer {
  ok: boolean;
  url?: string;
  contentType?: string;
  /** The page's bytes, base64. */
  body?: string;
  status?: number;
  problem?: string;
}

const TIMEOUT_MS = 35000;

/** Whether the extension is installed (it marks the page as soon as it loads). */
export function extensionPresent(doc: { documentElement: { dataset: DOMStringMap } } | undefined = typeof document === "undefined" ? undefined : document): boolean {
  return Boolean(doc?.documentElement.dataset.contentExtension);
}

let next = 0;

/** Asks the extension for url; rejects with its problem ("offline", "notFound", …) when it couldn't. */
export function fetchViaExtension(url: string, win: MessageWindow = window, timeout = TIMEOUT_MS): Promise<{ bytes: Uint8Array; url: string; contentType: string }> {
  const id = `f${Date.now().toString(36)}-${next++}`;
  return new Promise((resolve, reject) => {
    const done = () => {
      clearTimeout(timer);
      win.removeEventListener("message", listen);
    };
    const listen = (event: MessageEvent) => {
      if (event.origin !== win.location.origin) return;
      const data = event.data as ({ source?: string; type?: string; id?: string } & ExtensionAnswer) | null;
      if (!data || data.source !== "content-extension" || data.type !== "fetched" || data.id !== id) return;
      done();
      if (!data.ok || typeof data.body !== "string") {
        reject(new Error(data.problem ?? "server"));
        return;
      }
      resolve({ bytes: fromBase64(data.body), url: data.url ?? url, contentType: data.contentType ?? "" });
    };
    const timer = setTimeout(() => {
      done();
      reject(new Error("offline"));
    }, timeout);
    win.addEventListener("message", listen);
    win.postMessage({ source: "content-web", type: "fetch", id, url }, win.location.origin);
  });
}

function fromBase64(text: string): Uint8Array {
  const binary = atob(text);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}
