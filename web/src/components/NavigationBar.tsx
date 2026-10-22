import { List, Rss } from "lucide-react";
import { app, useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import { cn } from "./ui/cn";

/**
 * Feed | Saved at the bottom (#189), like the Android app's navigation bar: each goes back to the
 * list last shown there. Search opens from the search bar at the top.
 */
export function NavigationBar({ searching, onNavigate }: { searching: boolean; onNavigate: () => void }) {
  const { selection } = useApp();
  const inSaved = selection.kind === "saved";
  const item = (selected: boolean, saved: boolean, Icon: typeof Rss, label: string) => (
    <button
      type="button"
      aria-current={selected ? "page" : undefined}
      onClick={() => {
        app.showSection(saved);
        onNavigate();
      }}
      className="flex flex-1 cursor-pointer flex-col items-center gap-1 py-2 text-xs font-medium"
    >
      <span className={cn("flex h-8 w-16 items-center justify-center rounded-full", selected ? "bg-accent text-accent-foreground" : "text-muted-foreground")}>
        <Icon className="size-5" />
      </span>
      <span className={selected ? "text-foreground" : "text-muted-foreground"}>{label}</span>
    </button>
  );
  return (
    <nav className="flex border-t bg-muted/40" aria-label={t("navigation")}>
      {item(!searching && !inSaved, false, Rss, t("feedTab"))}
      {item(!searching && inSaved, true, List, t("saved"))}
    </nav>
  );
}
