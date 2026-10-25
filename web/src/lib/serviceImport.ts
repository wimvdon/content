/**
 * What was imported from a service when it was connected (#145): when, how many, and whether the
 * user chose to bring in everything it had or nothing (#251). As the Android app's
 * data/ServiceImport.kt, in the same text in the sync file (docs/sync.md).
 */
export interface ServiceImport {
  at: number;
  count: number;
  /** False when the user chose not to import: what the service had stays there until it changes. */
  everything: boolean;
}

export const encodeImport = (i: ServiceImport): string => JSON.stringify({ at: i.at, count: i.count, everything: i.everything });

export function decodeImport(text: string | undefined): ServiceImport | null {
  if (!text) return null;
  try {
    const m = JSON.parse(text) as Record<string, unknown>;
    if (typeof m?.at !== "number") return null;
    return { at: m.at, count: typeof m.count === "number" ? m.count : 0, everything: m.everything !== false };
  } catch {
    return null;
  }
}
