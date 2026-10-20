import { List, Rss } from "lucide-react";
import { app, useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import { cn } from "./ui/cn";

/**
 * Feeds | Saved at the bottom on narrow screens, like the Android app's navigation bar: each goes
 * back to the feed, folder or saved list last shown there. Wide screens have the sidebar instead.
 */
export function NavigationBar({ onNavigate }: { onNavigate: () => void }) {
  const { selection } = useApp();
  const inSaved = selection.kind === "saved";
  const item = (saved: boolean, Icon: typeof Rss, label: string) => {
    const selected = saved === inSaved;
    return (
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
  };
  return (
    <nav className="flex border-t bg-muted/40 lg:hidden" aria-label={t("navigation")}>
      {item(false, Rss, t("feeds"))}
      {/* Saved is a list (#142); the open book is To read, one of its tabs. */}
      {item(true, List, t("saved"))}
    </nav>
  );
}
