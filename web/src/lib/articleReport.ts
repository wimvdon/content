/**
 * Reporting an article the reader cleaned up badly (#181), as the Android app's data/ArticleReport.kt
 * does: a pre-filled "new issue" page in this repository, opened in a new tab, where the user sends
 * it with their GitHub account. It holds the article's link, what is wrong, the comment and the app;
 * never the article's text. Nothing is sent until the user submits it there.
 */

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
/** The longest comment kept, so the address stays short enough for every browser. */
export const MAX_REPORT_COMMENT = 1500;

/** The pre-filled "new issue" page for link. */
export function reportUrl(link: string, problem: ReportProblem, comment: string, appName: string, version = ""): string {
  let host = link;
  try {
    host = new URL(link).hostname.replace(/^www\./, "");
  } catch {
    // Not an address: the link itself names it.
  }
  const label = REPORT_PROBLEMS[problem];
  const title = `Article cleanup: ${label.toLowerCase()} on ${host}`;
  const lines = [`**Article:** ${link}`, `**Problem:** ${label}`, `**App:** ${`${appName} ${version}`.trim()}`];
  const text = comment.trim().slice(0, MAX_REPORT_COMMENT);
  if (text) lines.push("", text);
  const query = [`labels=${REPORT_LABEL}`, `title=${encodeURIComponent(title)}`, `body=${encodeURIComponent(lines.join("\n"))}`].join("&");
  return `${NEW_ISSUE}?${query}`;
}
