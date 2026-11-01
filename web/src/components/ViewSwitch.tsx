import { Check } from "lucide-react";
import { app, useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import { feedTabOf } from "@/lib/library";
import { sameTab, savedStates } from "@/lib/saved";
import { cn } from "./ui/cn";

/**
 * Which view of the place: Unread / All in Feeds, To read / All in Saved. A segmented switch, as on
 * Android and as To read / Archive in the save sheet: the same articles narrowed, not separate pages,
 * so no tabs and no swiping between them. Above the list on a phone-sized window, in the sidebar on a
 * wide one.
 */
export function ViewSwitch({ labels, selected, onSelect, className }: { labels: string[]; selected: number; onSelect: (index: number) => void; className?: string }) {
  return (
    <div role="radiogroup" aria-label={t("view")} className={cn("flex h-10 rounded-full border", className)}>
      {labels.map((label, i) => {
        const on = i === selected;
        return (
          <button
            key={label}
            type="button"
            role="radio"
            aria-checked={on}
            onClick={() => !on && onSelect(i)}
            className={cn(
              "flex min-w-0 flex-1 cursor-pointer items-center justify-center gap-1.5 px-3 text-sm font-medium",
              i > 0 && "border-l",
              i === 0 && "rounded-l-full",
              i === labels.length - 1 && "rounded-r-full",
              on ? "bg-accent text-accent-foreground" : "hover:bg-muted",
            )}
          >
            {on ? <Check className="size-4 shrink-0" aria-hidden="true" /> : null}
            <span className="truncate">{label}</span>
          </button>
        );
      })}
    </div>
  );
}

/** Feeds' switch: Unread or All. */
export function FeedViewSwitch({ className }: { className?: string }) {
  const { settings } = useApp();
  const tab = feedTabOf(settings.unreadOnly);
  return <ViewSwitch labels={[t("unread"), t("all")]} selected={tab === "unread" ? 0 : 1} onSelect={(i) => app.setUnreadOnly(i === 0)} className={className} />;
}

/** Saved's switch: To read or All; a topic or Liked stays chosen. */
export function SavedViewSwitch({ className }: { className?: string }) {
  const { savedFilter: filter } = useApp();
  const selected = Math.max(0, savedStates.findIndex((s) => sameTab(s, filter.tab)));
  return <ViewSwitch labels={[t("toRead"), t("all")]} selected={selected} onSelect={(i) => app.showSaved({ ...filter, tab: savedStates[i] })} className={className} />;
}
