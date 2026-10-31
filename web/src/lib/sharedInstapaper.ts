import type { Session } from "./instapaper";

/**
 * The Instapaper sign-in, kept in the Content account's synced settings (syncSettings.ts,
 * INSTAPAPER_TOKEN) so Instapaper is connected once for every device of the account, the Android
 * app's included: the same rules as its SharedInstapaper.kt (docs/instapaper.md). The value is a
 * JSON object with the token, the Instapaper user's id and name; "" means it was disconnected (on
 * any device, or Instapaper refused the token), which disconnects every device.
 */

export type SharedAction = { kind: "none" } | { kind: "signOut" } | { kind: "signIn"; session: Session };

export function encodeShared(session: Session): string {
  return JSON.stringify(session.userId === null ? { token: session.token, username: session.username } : { token: session.token, username: session.username, userId: session.userId });
}

/** The session in text, or null when it is empty (signed out) or can't be read. */
export function decodeShared(text: string | undefined): Session | null {
  if (!text) return null;
  try {
    const value = JSON.parse(text) as { token?: unknown; userId?: unknown; username?: unknown } | null;
    if (!value || typeof value.token !== "string" || !value.token) return null;
    return { token: value.token, userId: typeof value.userId === "number" ? value.userId : null, username: typeof value.username === "string" ? value.username : "" };
  } catch {
    return null;
  }
}

/**
 * What this browser puts in the file: its sign-in; "" when it was signed in at the last sync and
 * isn't now (it disconnected); undefined (the file's stays) when it never had one, so a browser
 * that simply isn't connected yet doesn't disconnect the others.
 */
export function localShared(session: Session | null, base: string | undefined): string | undefined {
  if (session) return encodeShared(session);
  return base ? "" : base;
}

/** What to do with the merged value, given this browser's session. */
export function sharedAction(session: Session | null, merged: string | undefined): SharedAction {
  if (merged === undefined) return { kind: "none" };
  const shared = decodeShared(merged);
  if (!shared) return merged === "" && session ? { kind: "signOut" } : { kind: "none" };
  if (session?.token === shared.token) return { kind: "none" };
  return { kind: "signIn", session: shared };
}

/** Whether a and b are the same Instapaper user (known ids that match), so a new token keeps what was synced. */
export function sameUser(a: Session | null, b: Session | null): boolean {
  return a?.userId != null && a.userId === b?.userId;
}
