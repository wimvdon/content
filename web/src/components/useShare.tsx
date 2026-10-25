import { useState } from "react";
import { useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import type { Item } from "@/lib/items";
import { shareLink } from "@/lib/shareLink";
import { sharedArticle } from "@/lib/sharing";
import { ShareWithUserDialog } from "./ShareWithUser";

/**
 * Share an article (#246): signed in, Send with Content with the people shared with before and
 * Other apps; signed out, straight to the browser's share menu (or the link copied). Gives the
 * function and the dialog to render.
 */
export function useShare(onMessage: (text: string) => void): [(title: string, url: string, item?: Item) => void, React.ReactNode] {
  const { account } = useApp();
  const [sharing, setSharing] = useState<{ title: string; url: string; item?: Item } | null>(null);
  const other = (title: string, url: string) =>
    void shareLink(title, url).then((result) => {
      if (result === "copied") onMessage(t("linkCopied"));
      else if (result === "failed") onMessage(url);
    });
  const share = (title: string, url: string, item?: Item) => (account ? setSharing({ title, url, item }) : other(title, url));
  const dialog = sharing ? (
    <ShareWithUserDialog
      items={[sharing.item ? sharedArticle(sharing.item) : { url: sharing.url, title: sharing.title }]}
      onClose={() => setSharing(null)}
      onOtherApps={() => other(sharing.title, sharing.url)}
    />
  ) : null;
  return [share, dialog];
}
