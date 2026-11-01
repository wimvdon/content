import { Bell, BookOpen, Rss } from "lucide-react";
import { app, useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import { inboxUnread } from "@/lib/inbox";
import { NewDot } from "./NewDot";
import { cn } from "./ui/cn";

/**
 * Feeds | Saved | Inbox at the bottom (#189, #246), like the Android app's navigation bar: each goes
 * back to the list last shown there; the Inbox only while signed in, with a dot while something
 * shared with you is unread. Search opens from the search bar at the top. Not in wide windows: the
 * sidebar has the same places at its top.
 */
export function NavigationBar({ searching, onNavigate }: { searching: boolean; onNavigate: () => void }) {
  const { selection, inboxOpen, account, library } = useApp();
  const inSaved = selection.kind === "saved";
  const item = (selected: boolean, go: () => void, Icon: typeof Rss, label: string, dot = false) => (
    <button
      type="button"
      aria-current={selected ? "page" : undefined}
      onClick={() => {
        go();
        onNavigate();
      }}
      className="flex flex-1 cursor-pointer flex-col items-center gap-1 py-2 text-xs font-medium"
    >
      <span className={cn("relative flex h-8 w-16 items-center justify-center rounded-full", selected ? "bg-accent text-accent-foreground" : "text-muted-foreground")}>
        <Icon className="size-5" />
        {dot ? <NewDot className="absolute top-1 right-4" /> : null}
      </span>
      <span className={selected ? "text-foreground" : "text-muted-foreground"}>{label}</span>
    </button>
  );
  return (
    <nav className="flex border-t bg-muted/40 lg:hidden" aria-label={t("navigation")}>
      {item(!searching && !inboxOpen && !inSaved, () => app.showSection(false), Rss, t("feedTab"))}
      {item(!searching && !inboxOpen && inSaved, () => app.showSection(true), BookOpen, t("saved"))}
      {account ? item(!searching && inboxOpen, () => app.showInbox(true), Bell, t("inboxTitle"), inboxUnread(library) > 0) : null}
    </nav>
  );
}
