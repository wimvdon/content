import { Bell, BookOpen, Heart, Rss, Settings as SettingsIcon } from "lucide-react";
import { app, useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import { choosesFolder, unreadByFeed } from "@/lib/library";
import { inboxUnread } from "@/lib/inbox";
import { chipTags, showsLiked, toReadCount } from "@/lib/saved";
import { topicIcons } from "@/lib/topics";
import { FeedIcon } from "./FeedIcon";
import { FolderIcon } from "./FolderIcon";
import { cn } from "./ui/cn";

/**
 * The side of a wide window, with the same levels as the Android app, laid out for a large screen:
 *
 * 1. **Where you are** (Android's navigation bar): Feeds, Saved and, signed in, the Inbox, each with
 *    what is still unread or to read.
 * 2. The tabs (Unread / All, To read / All) stay above the list, as on Android.
 * 3. **What narrows it** (Android's chips), for the place shown: in Feeds All feeds, the topics (a
 *    topic's feeds under it while you are in it) and the feeds without a topic; in Saved Liked and
 *    the topics. With this list here, the chip row is left out on wide windows.
 *
 * Settings at the bottom. On a phone-sized window there is no sidebar: the navigation bar at the
 * bottom and the chips do this.
 */
export function Sidebar({ onSettings }: { onSettings: () => void }) {
  const { library, selection, savedFilter, inboxOpen, account } = useApp();
  const counts = unreadByFeed(library);
  const total = library.feeds.reduce((n, f) => n + (counts.get(f.url) ?? 0), 0);
  const inSaved = !inboxOpen && selection.kind === "saved";
  const inFeeds = !inboxOpen && !inSaved;

  return (
    <nav className="flex h-full flex-col" aria-label={t("navigation")}>
      <div className="flex items-center gap-3 px-4 py-4">
        {/* The app's logo, the same as the Android launcher icon and the favicon. */}
        <img src="/icon.svg" alt="" className="size-8" />
        <span className="text-lg font-semibold">Content</span>
      </div>

      <div className="space-y-0.5 px-2">
        <Row selected={inFeeds} onClick={() => app.showSection(false)} icon={<Rss />} label={t("feedTab")} count={total} strong />
        <Row selected={inSaved} onClick={() => app.showSection(true)} icon={<BookOpen />} label={t("saved")} count={toReadCount(library)} strong />
        {account ? <Row selected={inboxOpen} onClick={() => app.showInbox(true)} icon={<Bell />} label={t("inboxTitle")} count={inboxUnread(library)} strong /> : null}
      </div>

      <div className="mx-4 my-3 border-t" />
      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-2">
        {inFeeds ? feedFilters() : null}
        {inSaved ? savedFilters() : null}
      </div>

      {/* At the bottom: Settings (Manage feeds and Manage topics are in it, as on Android). */}
      <div className="border-t p-2">
        <Row onClick={onSettings} icon={<SettingsIcon />} label={t("settings")} />
      </div>
    </nav>
  );

  /** Feeds' filters: All feeds, the topics with feeds (a topic's feeds while you are in it), then the feeds without a topic. */
  function feedFilters() {
    const loose = library.feeds.filter((f) => !f.folderId || !library.folders.some((x) => x.id === f.folderId));
    return (
      <div className="space-y-0.5" role="group" aria-label={t("filter")}>
        <Row selected={selection.kind === "all"} onClick={() => app.select({ kind: "all" })} icon={<Rss />} label={t("allFeeds")} count={total} />
        {library.folders
          .filter((f) => library.feeds.some((x) => x.folderId === f.id))
          .map((folder) => {
            const feeds = library.feeds.filter((f) => f.folderId === folder.id);
            const count = feeds.reduce((n, f) => n + (counts.get(f.url) ?? 0), 0);
            const open = choosesFolder(selection, folder) || (selection.kind === "feed" && feeds.some((f) => f.url === selection.feedUrl));
            return (
              <div key={folder.id}>
                <Row
                  selected={choosesFolder(selection, folder)}
                  onClick={() => app.select({ kind: "folder", folderId: folder.id })}
                  icon={<FolderIcon name={folder.icon} />}
                  label={folder.title}
                  count={count}
                />
                {open ? (
                  <div className="space-y-0.5 pl-6">
                    {feeds.map((feed) => (
                      <Row
                        key={feed.url}
                        selected={selection.kind === "feed" && selection.feedUrl === feed.url}
                        onClick={() => app.select({ kind: "feed", feedUrl: feed.url })}
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
            selected={selection.kind === "feed" && selection.feedUrl === feed.url}
            onClick={() => app.select({ kind: "feed", feedUrl: feed.url })}
            icon={<FeedIcon feed={feed} />}
            label={feed.title}
            count={counts.get(feed.url) ?? 0}
          />
        ))}
      </div>
    );
  }

  /** Saved's filters, as its chips: everything, Liked (when something liked is in the tab), then the topics. One at a time. */
  function savedFilters() {
    const filter = savedFilter;
    const tags = chipTags(library, filter);
    const icons = topicIcons(library);
    const plain = !filter.likedOnly && filter.tags.length === 0;
    return (
      <div className="space-y-0.5" role="group" aria-label={t("filter")}>
        <Row selected={plain} onClick={() => app.showSaved({ ...filter, likedOnly: false, tags: [] })} icon={<BookOpen />} label={t("showAll")} />
        {showsLiked(library, filter) ? (
          <Row selected={filter.likedOnly} onClick={() => app.showSaved({ ...filter, likedOnly: !filter.likedOnly, tags: [] })} icon={<Heart />} label={t("liked")} />
        ) : null}
        {tags.map((tag) => {
          const on = filter.tags[0]?.toLowerCase() === tag.toLowerCase();
          const icon = icons.get(tag.trim().toLowerCase());
          return (
            <Row
              key={tag}
              selected={on}
              onClick={() => app.showSaved({ ...filter, likedOnly: false, tags: on ? [] : [tag] })}
              icon={icon ? <FolderIcon name={icon} /> : <FolderIcon name="label" />}
              label={tag}
            />
          );
        })}
      </div>
    );
  }
}

function Row({
  selected,
  onClick,
  icon,
  label,
  count,
  strong,
}: {
  selected?: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  label: string;
  count?: number;
  /** The places (Feeds, Saved, Inbox): a little larger than the filters under them. */
  strong?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-current={selected ? "page" : undefined}
      className={cn(
        "flex w-full min-w-0 cursor-pointer items-center gap-3 rounded-full px-3 text-left text-sm",
        strong ? "h-11 font-medium" : "h-9",
        selected ? "bg-accent font-medium text-accent-foreground" : "hover:bg-muted",
      )}
    >
      <span className="shrink-0 text-muted-foreground [&>*]:size-5">{icon}</span>
      <span className="min-w-0 flex-1 truncate">{label}</span>
      {count !== undefined && count > 0 ? <span className="text-xs text-muted-foreground tabular-nums">{count}</span> : null}
    </button>
  );
}
