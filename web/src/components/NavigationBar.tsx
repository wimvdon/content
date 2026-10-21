import { List, Rss, Search } from "lucide-react";
import { app, useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import { cn } from "./ui/cn";

/**
 * Feeds | Saved | Search at the bottom on narrow screens, like the Android app's navigation bar:
 * Feeds and Saved go back to the feed, folder or saved list last shown there; Search is its own tab
 * (#171). Wide screens have the sidebar instead.
 */
export function NavigationBar({ searching, onSearch, onNavigate }: { searching: boolean; onSearch: () => void; onNavigate: () => void }) {
  const { selection } = useApp();
  const inSaved = selection.kind === "saved";
  const item = (selected: boolean, onClick: () => void, Icon: typeof Rss, label: string) => (
    <button type="button" aria-current={selected ? "page" : undefined} onClick={onClick} className="flex flex-1 cursor-pointer flex-col items-center gap-1 py-2 text-xs font-medium">
      <span className={cn("flex h-8 w-16 items-center justify-center rounded-full", selected ? "bg-accent text-accent-foreground" : "text-muted-foreground")}>
        <Icon className="size-5" />
      </span>
      <span className={selected ? "text-foreground" : "text-muted-foreground"}>{label}</span>
    </button>
  );
  const section = (saved: boolean) => () => {
    app.showSection(saved);
    onNavigate();
  };
  return (
    <nav className="flex border-t bg-muted/40 lg:hidden" aria-label={t("navigation")}>
      {item(!searching && !inSaved, section(false), Rss, t("feeds"))}
      {/* Saved is a list (#142); the open book is To read, one of its tabs. */}
      {item(!searching && inSaved, section(true), List, t("saved"))}
      {item(searching, onSearch, Search, t("search"))}
    </nav>
  );
}
