/**
 * One text per saved article, shared by every device (docs/account.md, "Stored texts"), the same
 * as the Android app's data/SharedText.kt: highlights then find their passage in the same text on
 * the phone, on the web and later elsewhere. The first device to extract the article stores it in
 * the Reader account; every other one takes that, and when two extract it at the same moment, the
 * one stored first wins for both.
 */

/**
 * The text to show for an article: for a saved one, the account's (`stored`) when it has one, else
 * this device's extraction (`extract`), stored for the others (`store` gives back the one that won);
 * otherwise just this device's. Null when there is none at all. A failed account call (offline,
 * signed out) counts as no stored text: the device's own is shown.
 */
export async function resolveText(
  saved: boolean,
  stored: () => Promise<string | null>,
  extract: () => Promise<string | null>,
  store: (html: string) => Promise<string | null>,
): Promise<string | null> {
  if (saved) {
    const known = await stored().catch(() => null);
    if (known) return known;
  }
  const own = await extract();
  if (own === null || !saved) return own;
  return (await store(own).catch(() => null)) ?? own;
}
