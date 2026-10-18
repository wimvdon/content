import { Archive, ChevronDown, ChevronRight, Folder as FolderGlyph, Heart, Inbox, Rss, Settings as SettingsIcon, Tag as TagGlyph } from "lucide-react";
import { useState } from "react";
import { app, useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import type { View } from "@/lib/instapaper";
import { unreadByFeed, type Selection } from "@/lib/library";
import { FeedIcon } from "./FeedIcon";
import { FolderIcon } from "./FolderIcon";
import { ManageFeedsIcon, ManageInstapaperIcon } from "./ManageIcons";
import { cn } from "./ui/cn";

/**
 * The app's navigation: Feeds and, when signed in, Instapaper as two sections that fold open and
 * shut (remembered in this browser). Feeds: All articles, the folders with their feeds, the other
 * feeds. Instapaper: Unread, Liked, Archive, its folders and tags. Then the ways to manage each,
 * and Settings. The same everywhere in the app, like the Android drawer.
 */
export function Sidebar({
  onNavigate,
  onManage,
  onManageInstapaper,
  onSettings,
}: {
  onNavigate: () => void;
  onManage: () => void;
  onManageInstapaper: () => void;
  onSettings: () => void;
}) {
  const { library, selection, instapaper, instapaperFolders, instapaperTags } = useApp();
  const counts = unreadByFeed(library);
  const total = library.feeds.reduce((n, f) => n + (counts.get(f.url) ?? 0), 0);
  const [collapsed, setCollapsed] = useState<Set<string>>(loadCollapsed);
  const select = (s: Selection) => {
    app.select(s);
    onNavigate();
  };
  const isSelected = (s: Selection) => JSON.stringify(s) === JSON.stringify(selection);
  const loose = library.feeds.filter((f) => !f.folderId);
  const toggle = (id: string) =>
    setCollapsed((c) => {
      const next = c.has(id) ? new Set([...c].filter((x) => x !== id)) : new Set([...c, id]);
      saveCollapsed(next);
      return next;
    });
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
    <nav className="flex h-full flex-col" aria-label={t("navigation")}>
      <div className="flex items-center gap-3 px-4 py-4">
        {/* The app's logo, the same as the Android launcher icon and the favicon. */}
        <img src="/icon.svg" alt="" className="size-9 rounded-full border" />
        <span className="text-lg font-semibold">Reader</span>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-2">
        <Section id={FEEDS} title={t("feeds")} count={collapsed.has(FEEDS) ? total : undefined} open={!collapsed.has(FEEDS)} onToggle={toggle}>
          <Row selected={isSelected({ kind: "all" })} onClick={() => select({ kind: "all" })} icon={<Rss className="size-5" />} label={t("allArticles")} count={total} />
          {library.folders.map((folder) => {
            const feeds = library.feeds.filter((f) => f.folderId === folder.id);
            const count = feeds.reduce((n, f) => n + (counts.get(f.url) ?? 0), 0);
            const open = !collapsed.has(folder.id);
            return (
              <div key={folder.id} className="mt-1">
                <div className="flex items-center">
                  <Chevron open={open} onClick={() => toggle(folder.id)} />
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
        </Section>
        {instapaper ? (
          <Section id={INSTAPAPER} title={t("instapaper")} open={!collapsed.has(INSTAPAPER)} onToggle={toggle}>
            {instapaperRow({ kind: "home" }, <Inbox />, t("unread"))}
            {instapaperRow({ kind: "liked" }, <Heart />, t("instapaperLiked"))}
            {instapaperRow({ kind: "archive" }, <Archive />, t("instapaperArchiveList"))}
            {instapaperFolders.map((folder) => instapaperRow({ kind: "folder", id: folder.id }, <FolderGlyph />, folder.title, folder.count, `f${folder.id}`))}
            {instapaperTags.length > 0 ? (
              <div className="mt-1">
                <div className="flex items-center">
                  <Chevron open={!collapsed.has(TAGS)} onClick={() => toggle(TAGS)} />
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
          </Section>
        ) : null}
      </div>
      <div className="border-t p-2">
        <Row selected={false} onClick={onManage} icon={<ManageFeedsIcon />} label={t("manageFeeds")} />
        {instapaper ? <Row selected={false} onClick={onManageInstapaper} icon={<ManageInstapaperIcon />} label={t("manageInstapaper")} /> : null}
        <Row selected={false} onClick={onSettings} icon={<SettingsIcon className="size-5" />} label={t("settings")} />
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

function Chevron({ open, onClick }: { open: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      className="rounded-full p-1.5 text-muted-foreground hover:bg-muted"
      aria-label={open ? t("collapse") : t("expand")}
      aria-expanded={open}
      onClick={onClick}
    >
      {open ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
    </button>
  );
}

/** The collapse keys of the two sections (folder ids are UUIDs, so they can't clash). */
const FEEDS = "section-feeds";
const INSTAPAPER = "section-instapaper";
const COLLAPSED_KEY = "reader-sidebar-collapsed";

/** What was folded shut, kept in this browser; empty when storage is blocked. */
function loadCollapsed(): Set<string> {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(COLLAPSED_KEY) ?? "[]");
    return new Set(Array.isArray(value) ? value.filter((x): x is string => typeof x === "string") : []);
  } catch {
    return new Set();
  }
}

function saveCollapsed(keys: Set<string>) {
  try {
    localStorage.setItem(COLLAPSED_KEY, JSON.stringify([...keys]));
  } catch {
    // Storage blocked: it is forgotten on reload.
  }
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
