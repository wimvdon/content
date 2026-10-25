/**
 * Reporting an article the reader cleaned up badly (#181), as the Android app's data/ArticleReport.kt
 * does: a pre-filled "new issue" page in this repository, opened in a new tab, where the user sends
 * it with their GitHub account. It holds the article's link, what is wrong, the comment, the app and
 * the text Content shows (#251), so it can be compared with the page itself. A browser takes only so
 * long an address: a long text is cut short there (`cut`) and the app copies all of it to paste.
 * Nothing is sent until the user submits it there.
 */

import { toPlainText } from "./html";

export type ReportProblem = "textMissing" | "junkLeft" | "picturesWrong" | "other";

/** What is wrong, as the issue says it (in English, for the repository), in the order the dialog shows. */
export const REPORT_PROBLEMS: Record<ReportProblem, string> = {
  textMissing: "Text missing",
  junkLeft: "Junk left in",
  picturesWrong: "Pictures wrong",
  other: "Other",
};

export const NEW_ISSUE = "https://github.com/wimvdon/content/issues/new";
/** The label the issue gets, so reports can be found together. */
export const REPORT_LABEL = "parser";
/** The longest comment kept. */
export const MAX_REPORT_COMMENT = 1500;
/** The longest address made: GitHub and browsers refuse much longer ones. */
export const MAX_REPORT_URL = 7500;
/** Said where the text is cut short; the whole text is on the clipboard then. */
export const CUT_NOTE = "… (cut short here: the whole text is on the clipboard, paste it below)";

/** The pre-filled "new issue" page for link, with text (the article as Content shows it), and whether the text was cut short. */
export function report(link: string, problem: ReportProblem, comment: string, appName: string, version = "", text = ""): { url: string; cut: boolean } {
  let host = link;
  try {
    host = new URL(link).hostname.replace(/^www\./, "");
  } catch {
    // Not an address: the link itself names it.
  }
  const label = REPORT_PROBLEMS[problem];
  const title = `Article cleanup: ${label.toLowerCase()} on ${host}`;
  const lines = [`**Article:** ${link}`, `**Problem:** ${label}`, `**App:** ${`${appName} ${version}`.trim()}`];
  const note = comment.trim().slice(0, MAX_REPORT_COMMENT);
  if (note) lines.push("", note);
  const head = lines.join("\n");
  const url = (body: string) => `${NEW_ISSUE}?${[`labels=${REPORT_LABEL}`, `title=${encodeURIComponent(title)}`, `body=${encodeURIComponent(body)}`].join("&")}`;
  const withText = (shown: string) => `${head}\n\n**Text in Content:**\n\n\`\`\`\`\n${shown}\n\`\`\`\``;
  const whole = text.trim();
  if (!whole) return { url: url(head), cut: false };
  const full = url(withText(whole));
  if (full.length <= MAX_REPORT_URL) return { url: full, cut: false };
  // As much of the text as fits, cut at a word.
  let low = 0;
  let high = whole.length;
  while (low < high) {
    const mid = Math.ceil((low + high) / 2);
    if (url(withText(whole.slice(0, mid) + CUT_NOTE)).length <= MAX_REPORT_URL) low = mid;
    else high = mid - 1;
  }
  let start = whole.slice(0, low);
  const space = start.lastIndexOf(" ");
  if (space > start.length / 2) start = start.slice(0, space);
  return { url: url(withText(start.trimEnd() + CUT_NOTE)), cut: true };
}

/** The pre-filled "new issue" page without the article's text. */
export const reportUrl = (link: string, problem: ReportProblem, comment: string, appName: string, version = "") => report(link, problem, comment, appName, version).url;

/** The article's text from the HTML the reader shows: one paragraph per block, blank lines between. */
export function textOf(html: string): string {
  return html
    .split(/<\/(?:p|h[1-6]|li|blockquote|pre|figcaption|dd|dt)>|<br\s*\/?>|<hr\s*\/?>/i)
    .map(toPlainText)
    .filter((p) => p.length > 0)
    .join("\n\n");
}
