import type { ListView, Settings, Theme } from "./app";
import { MAX_TEXT_SIZE, MIN_TEXT_SIZE, type ReaderBackground, type ReaderFont, type ReaderSpacing } from "./readerStyle";
import { decodeRecipients, encodeRecipients } from "./recipients";
import { decodeImport, encodeImport, type ServiceImport } from "./serviceImport";

/**
 * The settings in the sync file: names with text values (docs/sync.md). The web app reads the names
 * it knows and keeps the others as they are, so the Android app's settings survive a sync from the web.
 *
 * The web app's own settings go under "web." (the Android app's under "android."), so they are the
 * same in every browser and the Android app's are the same on every phone, but the two never mix
 * (the owner's choice): a computer and a phone often want something different. That is how it
 * looks (the theme, the reader's font, size, spacing and page colour, Show images, the list view)
 * and what it does (Load full articles, Mark as read on scroll).
 *
 * Shared by both apps: what was imported from each connected service (#145), the people shared with
 * (#235) and the Instapaper sign-in (INSTAPAPER_TOKEN, sharedInstapaper.ts: connect once, on every
 * device). Not synced: Unread | All (it is where you are in the list, not a setting).
 */

export const PREFIX = "web.";
export const THEME = `${PREFIX}theme`;
export const READER_FONT = `${PREFIX}reader.font`;
export const READER_TEXT_SIZE = `${PREFIX}reader.textSize`;
export const READER_SPACING = `${PREFIX}reader.spacing`;
export const READER_BACKGROUND = `${PREFIX}reader.background`;
export const READER_AUTO_DARK = `${PREFIX}reader.autoDark`;
export const FULL_ARTICLES = `${PREFIX}fullArticles`;
export const SHOW_IMAGES = `${PREFIX}showImages`;
export const MARK_READ_ON_SCROLL = `${PREFIX}markReadOnScroll`;
export const LIST_VIEW = `${PREFIX}listView`;

/** Load full articles and Mark as read on scroll were shared by both apps before; read once, never written. */
export const LEGACY_FULL_ARTICLES = "fullArticles";
export const LEGACY_MARK_READ_ON_SCROLL = "markReadOnScroll";

/** The people shared with (#235), a JSON list of addresses, most recent first. */
export const RECIPIENTS = "sharing.recipients";

/** The Instapaper sign-in, shared by every device of the account; "" once disconnected. */
export const INSTAPAPER_TOKEN = "services.instapaper.token";

/** The services whose import record travels with the account (#145). */
export const SERVICES = ["instapaper"];
export const importName = (service: string) => `services.${service}.imported`;

const KNOWN = new Set([THEME, READER_FONT, READER_TEXT_SIZE, READER_SPACING, READER_BACKGROUND, READER_AUTO_DARK, FULL_ARTICLES, SHOW_IMAGES, MARK_READ_ON_SCROLL, LIST_VIEW, RECIPIENTS]);

/** Names earlier versions wrote; dropped from the file when this browser saves it. */
const dropped = (name: string) =>
  name.endsWith(".scope") || name.startsWith("services.raindrop.") || name === LEGACY_FULL_ARTICLES || name === LEGACY_MARK_READ_ON_SCROLL;

const THEMES: Theme[] = ["system", "light", "dark"];
const FONTS: ReaderFont[] = ["sans", "serif"];
const SPACINGS: ReaderSpacing[] = ["compact", "normal", "relaxed"];
const BACKGROUNDS: ReaderBackground[] = ["default", "sepia", "dark"];
const LIST_VIEWS: ListView[] = ["small", "medium", "large"];

/**
 * This browser's synced settings, plus the names from base that the web app doesn't know.
 * instapaperToken is this browser's Instapaper sign-in for the file: undefined leaves the file's as it is.
 */
export function syncedSettings(settings: Settings, base: Record<string, string> = {}, instapaperToken?: string): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(base)) if (!KNOWN.has(k) && !dropped(k)) out[k] = v;
  const reader = settings.readerStyle;
  Object.assign(out, {
    [THEME]: settings.theme,
    [READER_FONT]: reader.font,
    [READER_TEXT_SIZE]: String(reader.textSize),
    [READER_SPACING]: reader.spacing,
    [READER_BACKGROUND]: reader.background,
    [READER_AUTO_DARK]: String(reader.autoDark),
    [FULL_ARTICLES]: String(settings.fullArticles),
    [SHOW_IMAGES]: String(settings.showImages),
    [MARK_READ_ON_SCROLL]: String(settings.markReadOnScroll),
    [LIST_VIEW]: settings.listView,
    [RECIPIENTS]: encodeRecipients(settings.recipients),
  });
  for (const id of SERVICES) {
    // An import this browser doesn't know of stays as the file has it.
    const record = settings.imports[id];
    if (record) out[importName(id)] = encodeImport(record);
  }
  if (instapaperToken !== undefined) out[INSTAPAPER_TOKEN] = instapaperToken;
  return out;
}

/** settings with the synced values it understands; missing or unknown values change nothing. */
export function applySynced(settings: Settings, values: Record<string, string>): Settings {
  const bool = (key: string, current: boolean) => (values[key] === "true" ? true : values[key] === "false" ? false : current);
  const pick = <T extends string>(key: string, allowed: T[], current: T): T => (allowed as string[]).includes(values[key]) ? (values[key] as T) : current;
  const imports: Record<string, ServiceImport> = { ...settings.imports };
  for (const id of SERVICES) {
    const record = decodeImport(values[importName(id)]);
    if (record) imports[id] = record;
  }
  const reader = settings.readerStyle;
  const size = Number(values[READER_TEXT_SIZE]);
  return {
    ...settings,
    theme: pick(THEME, THEMES, settings.theme),
    readerStyle: {
      font: pick(READER_FONT, FONTS, reader.font),
      textSize: Number.isInteger(size) && size >= MIN_TEXT_SIZE && size <= MAX_TEXT_SIZE ? size : reader.textSize,
      spacing: pick(READER_SPACING, SPACINGS, reader.spacing),
      background: pick(READER_BACKGROUND, BACKGROUNDS, reader.background),
      autoDark: bool(READER_AUTO_DARK, reader.autoDark),
    },
    fullArticles: bool(FULL_ARTICLES, bool(LEGACY_FULL_ARTICLES, settings.fullArticles)),
    showImages: bool(SHOW_IMAGES, settings.showImages),
    markReadOnScroll: bool(MARK_READ_ON_SCROLL, bool(LEGACY_MARK_READ_ON_SCROLL, settings.markReadOnScroll)),
    listView: pick(LIST_VIEW, LIST_VIEWS, settings.listView),
    imports,
    recipients: decodeRecipients(values[RECIPIENTS]) ?? settings.recipients,
  };
}
