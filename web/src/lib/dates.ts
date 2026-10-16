/**
 * Reads feed dates like the Android app (feed/FeedDates.kt): RFC 822 in RSS
 * ("Tue, 3 Jun 2008 11:05:30 GMT") and ISO 8601 in Atom ("2008-06-03T11:05:30Z").
 * Returns epoch milliseconds, or null.
 */
const RFC822 = /^(?:[A-Za-z]+,?\s*)?(\d{1,2})[\s-]+([A-Za-z]{3})[A-Za-z]*\.?[\s-]+(\d{2,4})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?\s*(.*)$/;
const ISO = /^(\d{4})-(\d{2})-(\d{2})(?:[T\s](\d{2}):(\d{2})(?::(\d{2})(?:[.,](\d+))?)?)?\s*(Z|z|UTC|GMT|[+-]\d{2}(?::?\d{2})?)?$/;
const MONTHS = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"];
const ZONES: Record<string, number> = {
  "": 0, GMT: 0, UT: 0, UTC: 0, Z: 0,
  EST: -5, EDT: -4, CST: -6, CDT: -5, MST: -7, MDT: -6, PST: -8, PDT: -7,
  CET: 1, CEST: 2, BST: 1,
};

export function parseFeedDate(raw: string | null | undefined): number | null {
  const text = raw?.trim().replace(/\s+/g, " ");
  if (!text) return null;
  return parseIso(text) ?? parseRfc822(text);
}

function zoneOffset(zone: string): number | null {
  const named = ZONES[zone.toUpperCase()];
  if (named !== undefined) return named * 3600;
  const m = /^([+-])(\d{2}):?(\d{2})?$/.exec(zone);
  if (!m) return null;
  const hours = Number(m[2]);
  const minutes = Number(m[3] ?? 0);
  if (hours > 18 || minutes > 59) return null;
  return (m[1] === "-" ? -1 : 1) * (hours * 3600 + minutes * 60);
}

function toMillis(y: number, mo: number, d: number, h: number, mi: number, s: number, ms: number, offset: number): number | null {
  if (mo < 1 || mo > 12 || d < 1 || d > 31 || h > 23 || mi > 59 || s > 60) return null;
  const date = new Date(Date.UTC(y, mo - 1, d, h, mi, Math.min(s, 59), ms));
  if (date.getUTCDate() !== d) return null;
  return date.getTime() - offset * 1000;
}

function parseRfc822(text: string): number | null {
  const m = RFC822.exec(text);
  if (!m) return null;
  const month = MONTHS.indexOf(m[2].toLowerCase()) + 1;
  if (month === 0) return null;
  let year = Number(m[3]);
  if (m[3].length <= 2) year += year < 50 ? 2000 : 1900;
  const offset = zoneOffset(m[7].trim());
  if (offset === null) return null;
  return toMillis(year, month, Number(m[1]), Number(m[4]), Number(m[5]), Number(m[6] ?? 0), 0, offset);
}

function parseIso(text: string): number | null {
  const m = ISO.exec(text);
  if (!m) return null;
  const offset = zoneOffset(m[8] ?? "");
  if (offset === null) return null;
  const ms = m[7] ? Number(m[7].padEnd(3, "0").slice(0, 3)) : 0;
  return toMillis(Number(m[1]), Number(m[2]), Number(m[3]), Number(m[4] ?? 0), Number(m[5] ?? 0), Number(m[6] ?? 0), ms, offset);
}
