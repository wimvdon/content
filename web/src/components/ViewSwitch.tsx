import { app, useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import { feedTabOf } from "@/lib/library";
import { hasUnreadNewsletter } from "@/lib/newsletters";
import { sameTab, savedStates } from "@/lib/saved";
import { NewDot } from "./NewDot";
import { cn } from "./ui/cn";

/**
 * Which view of the place: Unread / All in Feeds, To read / All in Saved: the same articles
 * narrowed. Drawn as shadcn/ui's tabs list (the chosen one raised), the web's own look. Above the
 * list on a phone-sized window, in the sidebar on a wide one.
 */
export function ViewSwitch({
  labels,
  selected,
  onSelect,
  className,
  dots = [],
}: {
  labels: string[];
  selected: number;
  onSelect: (index: number) => void;
  className?: string;
  /** The views with a dot (Saved's To read while a newsletter waits, with the setting on). */
  dots?: number[];
}) {
  return (
    <div role="radiogroup" aria-label={t("view")} className={cn("flex h-9 items-center rounded-lg bg-muted p-[3px] text-muted-foreground", className)}>
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
              "flex h-full min-w-0 flex-1 cursor-pointer items-center justify-center rounded-md px-3 text-sm font-medium transition-colors",
              on ? "bg-background text-foreground shadow-sm" : "hover:text-foreground",
            )}
          >
            <span className="truncate">{label}</span>
            {dots.includes(i) ? <NewDot className="ml-1.5 shrink-0" label={t("newslettersUnread")} /> : null}
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
  const { savedFilter: filter, settings, library } = useApp();
  const selected = Math.max(0, savedStates.findIndex((s) => sameTab(s, filter.tab)));
  // The newsletters' dot: unread ones are always to read.
  const dots = settings.newsletterDot && hasUnreadNewsletter(library) ? [savedStates.findIndex((s) => s.kind === "toread")] : [];
  return <ViewSwitch labels={[t("toRead"), t("all")]} selected={selected} onSelect={(i) => app.showSaved({ ...filter, tab: savedStates[i] })} className={className} dots={dots} />;
}
