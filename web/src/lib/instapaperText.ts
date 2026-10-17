import { t, type Key } from "./i18n";
import { InstapaperError, type Problem } from "./instapaper";

const keys: Record<Problem, Key> = {
  auth: "instapaperAuth",
  notApproved: "instapaperNotApproved",
  rateLimit: "instapaperRate",
  offline: "errorOffline",
  server: "errorServer",
  invalid: "instapaperInvalid",
};

/** What to tell the user about a failed Instapaper call. */
export function problemText(error: unknown): string {
  return t(keys[error instanceof InstapaperError ? error.problem : "server"]);
}
