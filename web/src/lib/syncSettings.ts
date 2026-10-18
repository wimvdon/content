import type { Settings } from "./app";

/**
 * The settings in the sync file: shared names with text values, the same as the Android app's
 * SyncSettings (docs/sync.md). The web app reads the names it knows and keeps the others as they
 * are, so settings only the other app has survive a sync from the web.
 *
 * Only settings about what the app does are synced (Load full articles, Mark as read on scroll).
 * How it looks stays in each browser and on each device (#96): the theme, the reader's font, size,
 * spacing and page colour, Show images and the list view.
 */

export const FULL_ARTICLES = "fullArticles";
export const MARK_READ_ON_SCROLL = "markReadOnScroll";

const KNOWN = new Set([FULL_ARTICLES, MARK_READ_ON_SCROLL]);

/** View settings that older versions synced; each device keeps its own now (#96). */
export const DROPPED_VIEW = new Set([
  "theme", "dynamicColor", "reader.font", "reader.textSize", "reader.spacing", "reader.background",
  "reader.autoDark", "showImages", "listView",
]);

/**
 * Names that are no longer synced and are dropped: the view settings (above), the Instapaper
 * sign-in (each device signs in itself, and that sign-in is the account) and Unread | All (where
 * you are in the list, not a setting).
 */
const DROPPED = /^(instapaper\.|unreadOnly$)/;

const dropped = (name: string) => DROPPED.test(name) || DROPPED_VIEW.has(name);

/** This browser's synced settings, plus the names from base that the web app doesn't know. */
export function syncedSettings(settings: Settings, base: Record<string, string> = {}): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(base)) if (!KNOWN.has(k) && !dropped(k)) out[k] = v;
  Object.assign(out, {
    [FULL_ARTICLES]: String(settings.fullArticles),
    [MARK_READ_ON_SCROLL]: String(settings.markReadOnScroll),
  });
  return out;
}

/** settings with the synced values it understands; missing or unknown values change nothing. */
export function applySynced(settings: Settings, values: Record<string, string>): Settings {
  const bool = (key: string, current: boolean) => (values[key] === "true" ? true : values[key] === "false" ? false : current);
  return {
    ...settings,
    fullArticles: bool(FULL_ARTICLES, settings.fullArticles),
    markReadOnScroll: bool(MARK_READ_ON_SCROLL, settings.markReadOnScroll),
  };
}
