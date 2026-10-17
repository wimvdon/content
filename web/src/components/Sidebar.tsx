import { Archive, ChevronDown, ChevronRight, Folder as FolderGlyph, Heart, Inbox, Rss, Settings as SettingsIcon, SlidersHorizontal, Tag as TagGlyph } from "lucide-react";
import { useState } from "react";
import { app, useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import type { View } from "@/lib/instapaper";
import { unreadByFeed, type Selection } from "@/lib/library";
import { FeedIcon } from "./FeedIcon";
import { FolderIcon } from "./FolderIcon";
import { cn } from "./ui/cn";
import { Segmented } from "./ui/segmented";

/**
 * Feeds and, when signed in, Instapaper as two destinations (a switch at the top, like the Android
 * app's navigation bar). The list shows the chosen one's places: All articles, the folders with
 * their feeds and the other feeds; or Unread, Liked, Archive, Instapaper's folders and tags. Then
 * the way to Manage and Settings.
 */
export function Sidebar({ onNavigate, onManage, onSettings }: { onNavigate: () => void; onManage: () => void; onSettings: () => void }) {
  const { library, selection, instapaper, instapaperFolders, instapaperTags } = useApp();
  const counts = unreadByFeed(library);
  const total = library.feeds.reduce((n, f) => n + (counts.get(f.url) ?? 0), 0);
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());
  const select = (s: Selection) => {
    app.select(s);
    onNavigate();
  };
  const isSelected = (s: Selection) => JSON.stringify(s) === JSON.stringify(selection);
  const loose = library.feeds.filter((f) => !f.folderId);
  const toggle = (id: string) => setCollapsed((c) => (c.has(id) ? new Set([...c].filter((x) => x !== id)) : new Set([...c, id])));
  // The place last shown in each destination, so the switch returns to it.
  const [lastFeeds, setLastFeeds] = useState<Selection>({ kind: "all" });
  const [lastSaved, setLastSaved] = useState<View>({ kind: "home" });
  const onInstapaper = instapaper !== null && selection.kind === "instapaper";
  const showDestination = (destination: "feeds" | "instapaper") => {
    if (destination === "instapaper") {
      if (selection.kind !== "instapaper") setLastFeeds(selection);
      app.select({ kind: "instapaper", view: lastSaved });
    } else if (selection.kind === "instapaper") {
      setLastSaved(selection.view);
      app.select(lastFeeds);
    }
  };
  const instapaperRow = (view: View, icon: React.ReactNode, label: string, count?: number, key?: string) => (
    <Row
      key={key}
      selected={isSelected({ kind: "instapaper", view })}
      onClick={() => select({ kind: "instapaper", view })}
      icon={icon}
      label={label}
      count={count}
    />
  );

  return (
    <nav className="flex h-full flex-col" aria-label={t("feeds")}>
      <div className="flex items-center gap-3 px-4 py-4">
        {/* The app's logo, the same as the Android launcher icon and the favicon. */}
        <img src="/icon.svg" alt="" className="size-9 rounded-full border" />
        <span className="text-lg font-semibold">Reader</span>
      </div>
      {instapaper ? (
        <div className="px-4 pb-3">
          <Segmented
            value={onInstapaper ? "instapaper" : "feeds"}
            onChange={showDestination}
            label={t("feeds")}
            options={[
              { value: "feeds", label: t("feeds") },
              { value: "instapaper", label: t("instapaper") },
            ]}
          />
        </div>
      ) : null}
      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-2">
        {onInstapaper ? null : (
          <>
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
                      onClick={() => toggle(folder.id)}
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
          </>
        )}
        {onInstapaper ? (
          <section aria-label={t("instapaper")}>
            {instapaperRow({ kind: "home" }, <Inbox />, t("unread"))}
            {instapaperRow({ kind: "liked" }, <Heart />, t("instapaperLiked"))}
            {instapaperRow({ kind: "archive" }, <Archive />, t("instapaperArchiveList"))}
            {instapaperFolders.map((folder) => instapaperRow({ kind: "folder", id: folder.id }, <FolderGlyph />, folder.title, folder.count, `f${folder.id}`))}
            {instapaperTags.length > 0 ? (
              <div className="mt-1">
                <div className="flex items-center">
                  <button
                    type="button"
                    className="rounded-full p-1.5 text-muted-foreground hover:bg-muted"
                    aria-label={collapsed.has(TAGS) ? t("expand") : t("collapse")}
                    aria-expanded={!collapsed.has(TAGS)}
                    onClick={() => toggle(TAGS)}
                  >
                    {collapsed.has(TAGS) ? <ChevronRight className="size-4" /> : <ChevronDown className="size-4" />}
                  </button>
                  <span className="px-3 py-2 text-sm text-muted-foreground">{t("instapaperTags")}</span>
                </div>
                {collapsed.has(TAGS)
                  ? null
                  : instapaperTags.map((tag) => (
                      <Row
                        key={`t${tag.id}`}
                        className="pl-9"
                        selected={isSelected({ kind: "instapaper", view: { kind: "tag", name: tag.name } })}
                        onClick={() => select({ kind: "instapaper", view: { kind: "tag", name: tag.name } })}
                        icon={<TagGlyph />}
                        label={tag.name}
                        count={tag.count}
                      />
                    ))}
              </div>
            ) : null}
          </section>
        ) : null}
      </div>
      <div className="border-t p-2">
        <Row selected={false} onClick={onManage} icon={<SlidersHorizontal className="size-5" />} label={t("manageFeeds")} />
        <Row selected={false} onClick={onSettings} icon={<SettingsIcon className="size-5" />} label={t("settings")} />
      </div>
    </nav>
  );
}

/** The collapse key of the Instapaper tags (folder ids are UUIDs, so it can't clash). */
const TAGS = "instapaper-tags";

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
