/** What a browser offers for sharing; tests give their own. */
export interface ShareTarget {
  share?: (data: { title: string; url: string }) => Promise<void>;
  clipboard?: { writeText(text: string): Promise<void> };
}

/**
 * Shares a link (#221): the system's share sheet where there is one, else the link is copied.
 * Gives back what happened: "shared", "copied", or "failed" (then show the link to copy by hand).
 */
export async function shareLink(title: string, url: string, nav: ShareTarget = navigator): Promise<"shared" | "copied" | "failed"> {
  if (nav.share) {
    await nav.share({ title, url }).catch(() => undefined);
    return "shared";
  }
  const copied = await (nav.clipboard?.writeText(url) ?? Promise.reject(new Error("no clipboard"))).then(
    () => true,
    () => false,
  );
  return copied ? "copied" : "failed";
}
