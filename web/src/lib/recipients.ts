/**
 * The people you shared with (#235), most recent first, offered as one-tap choices when sending to a
 * Content user. Addresses in lower case, at most MAX_RECIPIENTS. Synced with the account as the
 * setting "sharing.recipients" (a JSON list), like the Android app's data/Recipients.kt.
 */

export const MAX_RECIPIENTS = 12;

/** The list with email first: it was just shared with. */
export function rememberRecipient(list: string[], email: string): string[] {
  const clean = email.trim().toLowerCase();
  if (!clean) return list;
  return [clean, ...list.filter((e) => e !== clean)].slice(0, MAX_RECIPIENTS);
}

/** The list without email: forgotten, so it is no longer offered (hold a person, then ×). */
export function forgetRecipient(list: string[], email: string): string[] {
  const clean = email.trim().toLowerCase();
  return list.filter((e) => e !== clean);
}

export const encodeRecipients = (list: string[]) => JSON.stringify(list);

/** The list in text, or null when it isn't one. */
export function decodeRecipients(text: string | undefined | null): string[] | null {
  if (!text) return null;
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return null;
  }
  if (!Array.isArray(parsed)) return null;
  const clean = parsed.filter((e): e is string => typeof e === "string").map((e) => e.trim().toLowerCase()).filter((e) => e.includes("@"));
  return [...new Set(clean)].slice(0, MAX_RECIPIENTS);
}
