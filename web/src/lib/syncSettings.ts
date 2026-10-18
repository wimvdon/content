import type { Settings } from "./app";
import { readerStyleOf } from "./readerStyle";

/**
 * The settings in the sync file: shared names with text values, the same as the Android app's
 * SyncSettings (docs/sync.md). The web app reads the names it knows and keeps the others as they
 * are, so Android-only settings (Show images, list view) survive a sync from the web.
 */

export const THEME = "theme";
export const READER_FONT = "reader.font";
export const READER_TEXT_SIZE = "reader.textSize";
export const READER_SPACING = "reader.spacing";
export const READER_BACKGROUND = "reader.background";
export const READER_AUTO_DARK = "reader.autoDark";
export const FULL_ARTICLES = "fullArticles";
export const MARK_READ_ON_SCROLL = "markReadOnScroll";

const KNOWN = new Set([
  THEME, READER_FONT, READER_TEXT_SIZE, READER_SPACING, READER_BACKGROUND, READER_AUTO_DARK,
  FULL_ARTICLES, MARK_READ_ON_SCROLL,
]);

/**
 * Names that are no longer synced and are dropped: the Instapaper sign-in (each device signs in
 * itself, and that sign-in is the account) and Unread | All (where you are in the list, not a
 * setting).
 */
const DROPPED = /^(instapaper\.|unreadOnly$)/;

/** This browser's synced settings, plus the names from base that the web app doesn't know. */
export function syncedSettings(settings: Settings, base: Record<string, string> = {}): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(base)) if (!KNOWN.has(k) && !DROPPED.test(k)) out[k] = v;
  const r = settings.readerStyle;
  Object.assign(out, {
    [THEME]: settings.theme,
    [READER_FONT]: r.font,
    [READER_TEXT_SIZE]: String(r.textSize),
    [READER_SPACING]: r.spacing,
    [READER_BACKGROUND]: r.background,
    [READER_AUTO_DARK]: String(r.autoDark),
    [FULL_ARTICLES]: String(settings.fullArticles),
    [MARK_READ_ON_SCROLL]: String(settings.markReadOnScroll),
  });
  return out;
}

/** settings with the synced values it understands; missing or unknown values change nothing. */
export function applySynced(settings: Settings, values: Record<string, string>): Settings {
  const bool = (key: string, current: boolean) => (values[key] === "true" ? true : values[key] === "false" ? false : current);
  const theme = ["system", "light", "dark"].includes(values[THEME]) ? (values[THEME] as Settings["theme"]) : settings.theme;
  const r = settings.readerStyle;
  const size = Number(values[READER_TEXT_SIZE]);
  return {
    ...settings,
    theme,
    readerStyle: readerStyleOf({
      font: values[READER_FONT] ?? r.font,
      textSize: values[READER_TEXT_SIZE] !== undefined && Number.isFinite(size) ? size : r.textSize,
      spacing: values[READER_SPACING] ?? r.spacing,
      background: values[READER_BACKGROUND] ?? r.background,
      autoDark: bool(READER_AUTO_DARK, r.autoDark),
    }, r),
    fullArticles: bool(FULL_ARTICLES, settings.fullArticles),
    markReadOnScroll: bool(MARK_READ_ON_SCROLL, settings.markReadOnScroll),
  };
}
