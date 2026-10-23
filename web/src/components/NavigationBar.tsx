import { List, Rss } from "lucide-react";
import { app, useApp } from "@/lib/app";
import { sharedWaiting } from "@/lib/saved";
import { NewDot } from "./NewDot";
import { t } from "@/lib/i18n";
import { cn } from "./ui/cn";

/**
 * Feed | Saved at the bottom (#189), like the Android app's navigation bar: each goes back to the
 * list last shown there. Search opens from the search bar at the top. Not in wide windows: the menu
 * at the left has Feeds and Saved there.
 */
export function NavigationBar({ searching, onNavigate }: { searching: boolean; onNavigate: () => void }) {
  const { selection, library } = useApp();
  // Something shared with you waits (#167): a dot on Saved.
  const waiting = sharedWaiting(library) > 0;
  const inSaved = selection.kind === "saved";
  const item = (selected: boolean, saved: boolean, Icon: typeof Rss, label: string, dot = false) => (
    <button
      type="button"
      aria-current={selected ? "page" : undefined}
      onClick={() => {
        app.showSection(saved);
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
      {item(!searching && !inSaved, false, Rss, t("feedTab"))}
      {item(!searching && inSaved, true, List, t("saved"), waiting)}
    </nav>
  );
}
