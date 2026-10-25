import type { Key } from "./i18n";
import type { SaveChoice } from "./saveChoice";

/**
 * Sending an article with Content from the Save and share sheet (#256), besides saving it or instead:
 * the people it offers, picking them, what its button does, and how the sending went, in one message.
 * Like the Android app's SendTo.
 */

/** How one send went (App.shareWithUser). */
export type ShareStatus = "sent" | "noAccount" | "signedOut" | "offline" | "failed";

const PROBLEMS: Record<string, Key> = {
  noAccount: "shareUserNoAccount",
  signedOut: "shareUserSignedOut",
  offline: "syncOffline",
  failed: "syncServer",
};

/** What a send that didn't get through says. */
export const problemKey = (status: string): Key => PROBLEMS[status] ?? "syncServer";

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Looks like an email address (the account says whether it has a Content account). */
export const isEmail = (text: string) => EMAIL.test(text.trim());

const same = (a: string, b: string) => a.trim().toLowerCase() === b.trim().toLowerCase();

/** The people offered: those shared with before, then the ones picked here (an address added in the sheet), each once. */
export function people(recent: string[], picked: string[]): string[] {
  const out: string[] = [];
  for (const email of [...recent, ...picked]) if (!out.some((e) => same(e, email))) out.push(email);
  return out;
}

export const has = (picked: string[], email: string) => picked.some((e) => same(e, email));

/** Picks someone, or unpicks them. */
export const toggle = (picked: string[], email: string) => (has(picked, email) ? picked.filter((e) => !same(e, email)) : [...picked, email.trim()]);

/** What the sheet's button does, and says. */
export type SendAction = "save" | "saveAndSend" | "send";

export const actionOf = (choice: SaveChoice): SendAction => (!choice.keep ? "send" : choice.sendTo.length === 0 ? "save" : "saveAndSend");

/** The button can be clicked: a new save needs its link, and Don't save needs someone to send it to. */
export const canConfirm = (choice: SaveChoice, hasLink: boolean) => hasLink && (choice.keep || choice.sendTo.length > 0);

/** How sending went: to everyone, or the first one it didn't reach and why. */
export type SendOutcome = { sent: string[] } | { failed: string; status: ShareStatus };

/** One outcome for the message after sending; null when nothing was sent. */
export function outcomeOf(results: [string, ShareStatus][]): SendOutcome | null {
  if (results.length === 0) return null;
  const failed = results.find(([, status]) => status !== "sent");
  return failed ? { failed: failed[0], status: failed[1] } : { sent: results.map(([email]) => email) };
}
