import type { ListView, Settings, Theme } from "./app";
import { MAX_TEXT_SIZE, MIN_TEXT_SIZE, type ReaderBackground, type ReaderFont, type ReaderSpacing } from "./readerStyle";
import { decodeRecipients, encodeRecipients } from "./recipients";
import { decodeImport, encodeImport, type ServiceImport } from "./serviceImport";

/**
 * The settings in the sync file: names with text values (docs/sync.md). The web app reads the names
 * it knows and keeps the others as they are, so the Android app's settings survive a sync from the web.
 *
 * How things look is the web app's own, under "web." (the Android app's under "android."): the same
 * in every browser, never mixed with the phone's (the theme, the reader's font, size, spacing and
 * page colour, Show images, the list view).
 *
 * What the app does is shared with the Android app (the owner's choice, 2026-11-02), under
 * "articles.": Load full articles, Mark as read on scroll, Always open the website and Hide articles
 * with words. Until a file has those, each is read from the web's own name of before ("web.…"),
 * which is then dropped.
 *
 * Also shared by both apps: what was imported from each connected service (#145), the people shared with
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
export const SHOW_IMAGES = `${PREFIX}showImages`;
export const LIST_VIEW = `${PREFIX}listView`;

/** What the app does, shared with the Android app. */
export const SHARED = "articles.";
export const FULL_ARTICLES = `${SHARED}fullArticles`;
export const MARK_READ_ON_SCROLL = `${SHARED}markReadOnScroll`;
export const ALWAYS_OPEN_WEBSITE = `${SHARED}alwaysOpenWebsite`;
export const HIDDEN_WORDS = `${SHARED}hiddenWords`;

/** The web's own names for them before they were shared: read while the shared one is missing, never written. */
export const OWN_FULL_ARTICLES = `${PREFIX}fullArticles`;
export const OWN_MARK_READ_ON_SCROLL = `${PREFIX}markReadOnScroll`;
export const OWN_HIDDEN_WORDS = `${PREFIX}hiddenWords`;
const OWN_BEFORE = new Set([OWN_FULL_ARTICLES, OWN_MARK_READ_ON_SCROLL, OWN_HIDDEN_WORDS]);

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

const KNOWN = new Set([THEME, READER_FONT, READER_TEXT_SIZE, READER_SPACING, READER_BACKGROUND, READER_AUTO_DARK, FULL_ARTICLES, SHOW_IMAGES, MARK_READ_ON_SCROLL, ALWAYS_OPEN_WEBSITE, LIST_VIEW, HIDDEN_WORDS, RECIPIENTS]);

/** Names earlier versions wrote; dropped from the file when this browser saves it. */
const dropped = (name: string) =>
  name.endsWith(".scope") || name.startsWith("services.raindrop.") || name === LEGACY_FULL_ARTICLES || name === LEGACY_MARK_READ_ON_SCROLL || OWN_BEFORE.has(name);

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
    [ALWAYS_OPEN_WEBSITE]: String(settings.alwaysOpenWebsite),
    [LIST_VIEW]: settings.listView,
    [HIDDEN_WORDS]: settings.hiddenWords,
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
    fullArticles: bool(FULL_ARTICLES, bool(OWN_FULL_ARTICLES, bool(LEGACY_FULL_ARTICLES, settings.fullArticles))),
    showImages: bool(SHOW_IMAGES, settings.showImages),
    markReadOnScroll: bool(MARK_READ_ON_SCROLL, bool(OWN_MARK_READ_ON_SCROLL, bool(LEGACY_MARK_READ_ON_SCROLL, settings.markReadOnScroll))),
    alwaysOpenWebsite: bool(ALWAYS_OPEN_WEBSITE, settings.alwaysOpenWebsite),
    listView: pick(LIST_VIEW, LIST_VIEWS, settings.listView),
    hiddenWords: typeof values[HIDDEN_WORDS] === "string" ? values[HIDDEN_WORDS] : typeof values[OWN_HIDDEN_WORDS] === "string" ? values[OWN_HIDDEN_WORDS] : settings.hiddenWords,
    imports,
    recipients: decodeRecipients(values[RECIPIENTS]) ?? settings.recipients,
  };
}
