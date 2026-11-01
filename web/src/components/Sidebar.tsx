import { Archive, BookOpen, ChevronDown, ChevronRight, Rss, Settings as SettingsIcon } from "lucide-react";
import { useState } from "react";
import { app, useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import { choosesFolder, unreadByFeed } from "@/lib/library";
import { toReadCount } from "@/lib/saved";
import { FeedIcon } from "./FeedIcon";
import { FolderIcon } from "./FolderIcon";
import { cn } from "./ui/cn";

const FEEDS = "section-feeds";
const SAVED = "section-saved";

/**
 * The ☰ menu: Feeds and Saved as two sections that stay as you fold them, then Settings (Manage
 * topics is in it, #314). A feed folder shows its feeds only while you are in it (#251).
 */
export function Sidebar({
  onNavigate,
  onSettings,
}: {
  onNavigate?: () => void;
  onSettings: () => void;
}) {
  const { library, selection, savedFilter } = useApp();
  const counts = unreadByFeed(library);
  const total = library.feeds.reduce((n, f) => n + (counts.get(f.url) ?? 0), 0);
  const inSaved = selection.kind === "saved";

  // Both sections start open and stay as you fold them, wherever you go (#251).
  const [openSections, setOpenSections] = useState<Record<string, boolean>>({ [FEEDS]: true, [SAVED]: true });

  const toggleSection = (id: string) => {
    setOpenSections((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  const toRead = toReadCount(library);
  const loose = library.feeds.filter((f) => !f.folderId);

  return (
    <nav className="flex h-full flex-col" aria-label={t("navigation")}>
      <div className="flex items-center gap-3 px-4 py-4">
        {/* The app's logo, the same as the Android launcher icon and the favicon. */}
        <img src="/icon.svg" alt="" className="size-8" />
        <span className="text-lg font-semibold">Content</span>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-2">
        {/* Feeds section: its places (All and the topics with feeds, as the chips); a topic's feeds
            only while you are in it; then the feeds without a topic. */}
        <Section
          id={FEEDS}
          title={t("feeds")}
          count={openSections[FEEDS] ? undefined : total}
          open={openSections[FEEDS]}
          onToggle={toggleSection}
        >
          <Row
            selected={!inSaved && selection.kind === "all"}
            onClick={() => {
              app.select({ kind: "all" });
              onNavigate?.();
            }}
            icon={<Rss className="size-5" />}
            label={t("feedTab")}
            count={total}
          />
          {library.folders.filter((f) => library.feeds.some((x) => x.folderId === f.id)).map((folder) => {
            const feeds = library.feeds.filter((f) => f.folderId === folder.id);
            const count = feeds.reduce((n, f) => n + (counts.get(f.url) ?? 0), 0);
            const open = !inSaved && (choosesFolder(selection, folder) || (selection.kind === "feed" && feeds.some((f) => f.url === selection.feedUrl)));
            return (
              <div key={folder.id} className="mt-1">
                <div className="flex items-center">
                  <Row
                    selected={!inSaved && choosesFolder(selection, folder)}
                    onClick={() => {
                      app.select({ kind: "folder", folderId: folder.id });
                      onNavigate?.();
                    }}
                    icon={<FolderIcon name={folder.icon} />}
                    label={folder.title}
                    count={count}
                  />
                </div>
                {open ? (
                  <div className="pl-6">
                    {feeds.map((feed) => (
                      <Row
                        key={feed.url}
                        selected={!inSaved && selection.kind === "feed" && selection.feedUrl === feed.url}
                        onClick={() => {
                          app.select({ kind: "feed", feedUrl: feed.url });
                          onNavigate?.();
                        }}
                        icon={<FeedIcon feed={feed} />}
                        label={feed.title}
                        count={counts.get(feed.url) ?? 0}
                      />
                    ))}
                  </div>
                ) : null}
              </div>
            );
          })}
          {loose.map((feed) => (
            <Row
              key={feed.url}
              selected={!inSaved && selection.kind === "feed" && selection.feedUrl === feed.url}
              onClick={() => {
                app.select({ kind: "feed", feedUrl: feed.url });
                onNavigate?.();
              }}
              icon={<FeedIcon feed={feed} />}
              label={feed.title}
              count={counts.get(feed.url) ?? 0}
            />
          ))}
        </Section>

        {/* Saved section: its places, as on the state chip: To read (with what is still to read) and
            the Archive (no count: it is not a to-do). Liked and the topics are filters: chips only. */}
        <Section
          id={SAVED}
          title={t("saved")}
          count={openSections[SAVED] ? undefined : toRead}
          open={openSections[SAVED]}
          onToggle={toggleSection}
        >
          <Row
            selected={inSaved && savedFilter.tab.kind === "toread"}
            onClick={() => {
              app.showSaved({ ...savedFilter, tab: { kind: "toread" } });
              onNavigate?.();
            }}
            icon={<BookOpen className="size-5" />}
            label={t("toRead")}
            count={toRead}
          />
          <Row
            selected={inSaved && savedFilter.tab.kind === "archive"}
            onClick={() => {
              app.showSaved({ ...savedFilter, tab: { kind: "archive" } });
              onNavigate?.();
            }}
            icon={<Archive className="size-5" />}
            label={t("archive")}
          />
        </Section>
      </div>

      {/* At the bottom: Settings (Manage topics is in it, as on Android, #314). */}
      <div className="border-t p-2">
        <Row onClick={onSettings} icon={<SettingsIcon className="size-5" />} label={t("settings")} />
      </div>
    </nav>
  );
}

/** A section of the navigation with a header that folds it open and shut. */
function Section({
  id,
  title,
  count,
  open,
  onToggle,
  children,
}: {
  id: string;
  title: string;
  count?: number;
  open: boolean;
  onToggle: (id: string) => void;
  children: React.ReactNode;
}) {
  return (
    <section className="mb-2" aria-label={title}>
      <button
        type="button"
        className="flex w-full cursor-pointer items-center gap-2 rounded-full px-3 py-2 text-left text-sm font-medium text-primary hover:bg-muted"
        aria-expanded={open}
        onClick={() => onToggle(id)}
      >
        <span className="flex-1">{title}</span>
        {count ? <span className="text-xs text-muted-foreground tabular-nums">{count}</span> : null}
        {open ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
      </button>
      {open ? children : null}
    </section>
  );
}


function Row({
  selected,
  onClick,
  icon,
  label,
  count,
  className,
}: {
  selected?: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  label: string;
  count?: number;
  className?: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-current={selected ? "page" : undefined}
      className={cn(
        "flex w-full min-w-0 cursor-pointer items-center gap-3 rounded-full px-3 py-2 text-left text-sm",
        selected ? "bg-accent font-medium text-accent-foreground" : "hover:bg-muted",
        className,
      )}
    >
      <span className="shrink-0 text-muted-foreground [&>*]:size-5">{icon}</span>
      <span className="min-w-0 flex-1 truncate">{label}</span>
      {count !== undefined && count > 0 ? <span className="text-xs text-muted-foreground tabular-nums">{count}</span> : null}
    </button>
  );
}
