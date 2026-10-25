import type { Settings } from "./app";
import { decodeRecipients, encodeRecipients } from "./recipients";
import { decodeImport, encodeImport, type ServiceImport } from "./serviceImport";

/**
 * The settings in the sync file: shared names with text values, the same as the Android app's
 * SyncSettings (docs/sync.md). The web app reads the names it knows and keeps the others as they
 * are, so settings only the other app has survive a sync from the web.
 *
 * Only settings about what the app does are synced (Load full articles, Mark as read on scroll, what
 * was imported from each connected service, #145, and the people shared with, #235).
 * How it looks stays in each browser and on each device (#96): the theme, the reader's font, size,
 * spacing and page colour, Show images and the list view.
 */

export const FULL_ARTICLES = "fullArticles";
export const MARK_READ_ON_SCROLL = "markReadOnScroll";
/** The people shared with (#235), a JSON list of addresses, most recent first. */
export const RECIPIENTS = "sharing.recipients";

/** The services whose import record travels with the account (#145). */
export const SERVICES = ["instapaper"];
export const importName = (service: string) => `services.${service}.imported`;

const KNOWN = new Set([FULL_ARTICLES, MARK_READ_ON_SCROLL, RECIPIENTS]);

/** Names earlier versions wrote; dropped from the file when this browser saves it. */
const dropped = (name: string) => name.endsWith(".scope") || name.startsWith("services.raindrop.");

/** This browser's synced settings, plus the names from base that the web app doesn't know. */
export function syncedSettings(settings: Settings, base: Record<string, string> = {}): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(base)) if (!KNOWN.has(k) && !dropped(k)) out[k] = v;
  Object.assign(out, {
    [FULL_ARTICLES]: String(settings.fullArticles),
    [MARK_READ_ON_SCROLL]: String(settings.markReadOnScroll),
    [RECIPIENTS]: encodeRecipients(settings.recipients),
  });
  for (const id of SERVICES) {
    // An import this browser doesn't know of stays as the file has it.
    const record = settings.imports[id];
    if (record) out[importName(id)] = encodeImport(record);
  }
  return out;
}

/** settings with the synced values it understands; missing or unknown values change nothing. */
export function applySynced(settings: Settings, values: Record<string, string>): Settings {
  const bool = (key: string, current: boolean) => (values[key] === "true" ? true : values[key] === "false" ? false : current);
  const imports: Record<string, ServiceImport> = { ...settings.imports };
  for (const id of SERVICES) {
    const record = decodeImport(values[importName(id)]);
    if (record) imports[id] = record;
  }
  return {
    ...settings,
    fullArticles: bool(FULL_ARTICLES, settings.fullArticles),
    markReadOnScroll: bool(MARK_READ_ON_SCROLL, settings.markReadOnScroll),
    imports,
    recipients: decodeRecipients(values[RECIPIENTS]) ?? settings.recipients,
  };
}
