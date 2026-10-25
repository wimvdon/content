// Notifications when something is shared with you (#235): the feed loader pokes this browser with an
// empty Web Push message (worker/src/push.ts) and this shows it. The page registers it with the
// notification's text in the address (?title=…&body=…, from src/lib/i18n.ts), so the wording stays
// in one place and follows the language. Clicking it opens Saved › Shared with me.

const params = new URL(self.location.href).searchParams;
const TITLE = params.get("title") || "Content";
const BODY = params.get("body") || "";

self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", (event) => event.waitUntil(self.clients.claim()));

self.addEventListener("push", (event) => {
  event.waitUntil(self.registration.showNotification(TITLE, { body: BODY, icon: "/icon-192.png", tag: "shared-with-me" }));
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  event.waitUntil(
    (async () => {
      const windows = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
      const open = windows.find((w) => new URL(w.url).origin === self.location.origin);
      if (open) {
        await open.focus();
        open.postMessage({ kind: "openShared" });
      } else {
        await self.clients.openWindow("/?open=shared");
      }
    })(),
  );
});
