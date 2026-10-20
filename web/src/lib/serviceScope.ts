/**
 * What a service and Reader share, both ways (#145), and what was imported from it: the same as the
 * Android app's data/ServiceScope.kt, in the same text in the sync file (docs/sync.md).
 */

import type { Item } from "./items";
import { folderOf } from "./saved";

/** To read, as one of a scope's folders. */
export const TO_READ = "";

/** Everything in Saved (folders null), or a set of folders with TO_READ for To read. */
export interface ServiceScope {
  folders: string[] | null;
}

export const EVERYTHING: ServiceScope = { folders: null };

const same = (a: string, b: string) => a.trim().toLowerCase() === b.trim().toLowerCase();

/** Whether an article in `folder` (null: To read) or archived would be in the scope; only Everything covers the archive. */
export function coversPlace(scope: ServiceScope, folder: string | null, archived: boolean): boolean {
  if (scope.folders === null) return true;
  if (archived) return false;
  const name = folder?.trim() || TO_READ;
  return scope.folders.some((f) => same(f, name));
}

/** Whether an item of Saved is in the scope. */
export function covers(scope: ServiceScope, item: Item): boolean {
  if (!item.readingList) return false;
  if (scope.folders === null) return true;
  if (item.readingList.state !== "toread") return false;
  return coversPlace(scope, folderOf(item), false);
}

/** The scope with `folder` (null: To read) ticked or not. */
export function withFolder(scope: ServiceScope, folder: string | null, on: boolean): ServiceScope {
  const name = folder?.trim() || TO_READ;
  const rest = (scope.folders ?? []).filter((f) => !same(f, name));
  return { folders: on ? [...rest, name] : rest };
}

/** As stored in the sync file's settings: "*" for everything, else a JSON list. */
export const encodeScope = (scope: ServiceScope): string =>
  scope.folders === null ? "*" : JSON.stringify([...scope.folders].sort((a, b) => (a.toLowerCase() < b.toLowerCase() ? -1 : a.toLowerCase() > b.toLowerCase() ? 1 : 0)));

export function decodeScope(text: string | undefined): ServiceScope | null {
  if (text === undefined) return null;
  if (text === "*") return EVERYTHING;
  try {
    const list: unknown = JSON.parse(text);
    return Array.isArray(list) ? { folders: list.filter((x): x is string => typeof x === "string") } : null;
  } catch {
    return null;
  }
}

/** What was imported from a service when it was connected: when, how many, and from where. */
export interface ServiceImport {
  at: number;
  count: number;
  /** The folders imported (TO_READ for To read); null for everything. */
  folders: string[] | null;
  /** Whether its archive came too (always with everything). */
  archive: boolean;
}

/** Whether an article there in `folder` (null: To read) or archived is one to bring in. */
export function includes(record: Pick<ServiceImport, "folders" | "archive">, folder: string | null, archived: boolean): boolean {
  if (record.folders === null) return true;
  if (archived) return record.archive;
  const name = folder?.trim() || TO_READ;
  return record.folders.some((f) => same(f, name));
}

export const encodeImport = (i: ServiceImport): string =>
  JSON.stringify({ at: i.at, count: i.count, folders: i.folders === null ? null : [...i.folders].sort((a, b) => (a.toLowerCase() < b.toLowerCase() ? -1 : a.toLowerCase() > b.toLowerCase() ? 1 : 0)), archive: i.archive });

export function decodeImport(text: string | undefined): ServiceImport | null {
  if (!text) return null;
  try {
    const m = JSON.parse(text) as Record<string, unknown>;
    if (typeof m?.at !== "number") return null;
    const folders = Array.isArray(m.folders) ? m.folders.filter((x): x is string => typeof x === "string") : null;
    return { at: m.at, count: typeof m.count === "number" ? m.count : 0, folders, archive: m.archive !== false };
  } catch {
    return null;
  }
}
