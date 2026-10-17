import { Bookmark, ChevronDown, ChevronRight, Rss, Settings as SettingsIcon, SlidersHorizontal } from "lucide-react";
import { useState } from "react";
import { app, useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import { unreadByFeed, type Selection } from "@/lib/library";
import { FeedIcon } from "./FeedIcon";
import { FolderIcon } from "./FolderIcon";
import { cn } from "./ui/cn";

/** All articles, the folders with their feeds, the other feeds, and the way to Manage and Settings. */
export function Sidebar({ onNavigate, onManage, onSettings, onInstapaper }: { onNavigate: () => void; onManage: () => void; onSettings: () => void; onInstapaper: () => void }) {
  const { library, selection, instapaper } = useApp();
  const counts = unreadByFeed(library);
  const total = library.feeds.reduce((n, f) => n + (counts.get(f.url) ?? 0), 0);
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());
  const select = (s: Selection) => {
    app.select(s);
    onNavigate();
  };
  const isSelected = (s: Selection) => JSON.stringify(s) === JSON.stringify(selection);
  const loose = library.feeds.filter((f) => !f.folderId);

  return (
    <nav className="flex h-full flex-col" aria-label={t("feeds")}>
      <div className="flex items-center gap-3 px-4 py-4">
        <span className="inline-flex size-9 items-center justify-center rounded-full bg-accent text-primary">
          <Rss className="size-5" />
        </span>
        <span className="text-lg font-semibold">Reader</span>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-2">
        <Row selected={isSelected({ kind: "all" })} onClick={() => select({ kind: "all" })} icon={<Rss className="size-5" />} label={t("allArticles")} count={total} />
        {library.folders.map((folder) => {
          const feeds = library.feeds.filter((f) => f.folderId === folder.id);
          const count = feeds.reduce((n, f) => n + (counts.get(f.url) ?? 0), 0);
          const open = !collapsed.has(folder.id);
          return (
            <div key={folder.id} className="mt-1">
              <div className="flex items-center">
                <button
                  type="button"
                  className="rounded-full p-1.5 text-muted-foreground hover:bg-muted"
                  aria-label={open ? t("collapse") : t("expand")}
                  aria-expanded={open}
                  onClick={() => setCollapsed((c) => (c.has(folder.id) ? new Set([...c].filter((x) => x !== folder.id)) : new Set([...c, folder.id])))}
                >
                  {open ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
                </button>
                <Row
                  className="flex-1"
                  selected={isSelected({ kind: "folder", folderId: folder.id })}
                  onClick={() => select({ kind: "folder", folderId: folder.id })}
                  icon={<FolderIcon name={folder.icon} />}
                  label={folder.title}
                  count={count}
                />
              </div>
              {open &&
                feeds.map((feed) => (
                  <Row
                    key={feed.url}
                    className="pl-9"
                    selected={isSelected({ kind: "feed", feedUrl: feed.url })}
                    onClick={() => select({ kind: "feed", feedUrl: feed.url })}
                    icon={<FeedIcon feed={feed} />}
                    label={feed.title}
                    count={counts.get(feed.url) ?? 0}
                  />
                ))}
            </div>
          );
        })}
        {loose.length > 0 && library.folders.length > 0 ? <div className="mx-3 my-2 h-px bg-border" /> : null}
        {loose.map((feed) => (
          <Row
            key={feed.url}
            selected={isSelected({ kind: "feed", feedUrl: feed.url })}
            onClick={() => select({ kind: "feed", feedUrl: feed.url })}
            icon={<FeedIcon feed={feed} />}
            label={feed.title}
            count={counts.get(feed.url) ?? 0}
          />
        ))}
      </div>
      <div className="border-t p-2">
        {instapaper ? <Row selected={false} onClick={onInstapaper} icon={<Bookmark className="size-5" />} label={t("instapaper")} /> : null}
        <Row selected={false} onClick={onManage} icon={<SlidersHorizontal className="size-5" />} label={t("manageFeeds")} />
        <Row selected={false} onClick={onSettings} icon={<SettingsIcon className="size-5" />} label={t("settings")} />
      </div>
    </nav>
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
  selected: boolean;
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
      {count ? <span className="text-xs text-muted-foreground tabular-nums">{count}</span> : null}
    </button>
  );
}
