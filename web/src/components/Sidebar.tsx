import { Bell, BookOpen, Heart, Mail, Rss, Settings as SettingsIcon } from "lucide-react";
import { app, useApp } from "@/lib/app";
import { useListedLibrary } from "./useListed";
import { t } from "@/lib/i18n";
import { choosesFolder, feedTabOf, unreadByFeed } from "@/lib/library";
import { inboxUnread } from "@/lib/inbox";
import { hasUnreadNewsletter } from "@/lib/newsletters";
import { chipTags, isPlainFilter, showsLiked, showsNewsletters, toReadCount, withoutChips } from "@/lib/saved";
import { sidebarFeeds, topicIcons } from "@/lib/topics";
import { FeedIcon } from "./FeedIcon";
import { FolderIcon } from "./FolderIcon";
import { NewDot } from "./NewDot";
import { cn } from "./ui/cn";
import { FeedViewSwitch, SavedViewSwitch } from "./ViewSwitch";

/**
 * The side of a wide window, with the same levels as the Android app, laid out for a large screen:
 *
 * 1. **Where you are** (Android's navigation bar): Feeds, Saved and, signed in, the Inbox, each with
 *    what is still unread or to read.
 * 2. **Which view**: the switch Unread / All in Feeds, To read / All in Saved (on a phone-sized
 *    window it sits above the list, as on Android).
 * 3. **What narrows it** (Android's chips), for the place shown: in Feeds All feeds, the topics (a
 *    topic's feeds under it while you are in it) and the feeds without a topic; in Saved Liked and
 *    the topics. With this list here, the chip row is left out on wide windows.
 *
 * Settings at the bottom. On a phone-sized window there is no sidebar: the navigation bar at the
 * bottom and the chips do this.
 */
export function Sidebar({ onSettings }: { onSettings: () => void }) {
  const { selection, savedFilter, inboxOpen, account, settings } = useApp();
  // Counts and chips without the articles the hidden words leave out.
  const library = useListedLibrary();
  const counts = unreadByFeed(library);
  const total = library.feeds.reduce((n, f) => n + (counts.get(f.url) ?? 0), 0);
  const inSaved = !inboxOpen && selection.kind === "saved";
  const inFeeds = !inboxOpen && !inSaved;
  // The newsletters' dot (with the setting on): on Saved and its Newsletters row while one waits unread.
  const newsletterDot = settings.newsletterDot && hasUnreadNewsletter(library);

  return (
    <nav className="flex h-full flex-col" aria-label={t("navigation")}>
      <div className="flex items-center gap-3 px-4 py-4">
        {/* The app's logo, the same as the Android launcher icon and the favicon. */}
        <img src="/icon.svg" alt="" className="size-8" />
        <span className="text-lg font-semibold">Content</span>
      </div>

      <div className="space-y-0.5 px-2">
        <Row selected={inFeeds} onClick={() => app.showSection(false)} icon={<Rss />} label={t("feedTab")} count={total} strong />
        <Row selected={inSaved} onClick={() => app.showSection(true)} icon={<BookOpen />} label={t("saved")} count={toReadCount(library)} strong dot={newsletterDot} />
        {account ? <Row selected={inboxOpen} onClick={() => app.showInbox(true)} icon={<Bell />} label={t("inboxTitle")} count={inboxUnread(library)} strong /> : null}
      </div>

      <div className="mx-4 my-3 border-t" />
      {inFeeds && library.feeds.length > 0 ? <FeedViewSwitch className="mx-4 mb-3" /> : null}
      {inSaved ? <SavedViewSwitch className="mx-4 mb-3" /> : null}
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

  /** Feeds' filters: All feeds, the topics with something in the view shown (a topic's feeds while you are in it), then the feeds without a topic, as on Android. */
  function feedFilters() {
    const { topics, loose } = sidebarFeeds(library, feedTabOf(settings.unreadOnly), selection);
    return (
      <div className="space-y-0.5" role="group" aria-label={t("filter")}>
        <Row selected={selection.kind === "all"} onClick={() => app.select({ kind: "all" })} icon={<Rss />} label={t("allFeeds")} count={total} />
        {topics.map(({ folder, feeds }) => {
            const count = library.feeds.filter((f) => f.folderId === folder.id).reduce((n, f) => n + (counts.get(f.url) ?? 0), 0);
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
    const plain = isPlainFilter(filter);
    return (
      <div className="space-y-0.5" role="group" aria-label={t("filter")}>
        <Row selected={plain} onClick={() => app.showSaved(withoutChips(filter))} icon={<BookOpen />} label={t("allArticles")} />
        {showsLiked(library, filter) ? (
          <Row selected={filter.likedOnly} onClick={() => app.showSaved({ ...withoutChips(filter), likedOnly: !filter.likedOnly })} icon={<Heart />} label={t("liked")} />
        ) : null}
        {showsNewsletters(library, filter) ? (
          <Row
            selected={filter.newslettersOnly === true}
            onClick={() => app.showSaved({ ...withoutChips(filter), newslettersOnly: !filter.newslettersOnly })}
            icon={<Mail />}
            label={t("newsletters")}
            dot={newsletterDot}
          />
        ) : null}
        {tags.map((tag) => {
          const on = filter.tags[0]?.toLowerCase() === tag.toLowerCase();
          const icon = icons.get(tag.trim().toLowerCase());
          return (
            <Row
              key={tag}
              selected={on}
              onClick={() => app.showSaved({ ...withoutChips(filter), tags: on ? [] : [tag] })}
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
  dot,
}: {
  selected?: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  label: string;
  count?: number;
  /** The places (Feeds, Saved, Inbox): a little larger than the filters under them. */
  strong?: boolean;
  /** The newsletters' dot (Saved and its Newsletters row, with the setting on). */
  dot?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-current={selected ? "page" : undefined}
      className={cn(
        "flex w-full min-w-0 cursor-pointer items-center gap-2 rounded-md px-2 text-left text-sm transition-colors",
        strong ? "h-9 font-medium" : "h-8",
        selected ? "bg-accent font-medium text-accent-foreground" : "hover:bg-accent hover:text-accent-foreground",
      )}
    >
      <span className="shrink-0 text-muted-foreground [&>*]:size-4">{icon}</span>
      <span className="min-w-0 flex-1 truncate">{label}</span>
      {dot ? <NewDot label={t("newslettersUnread")} /> : null}
      {count !== undefined && count > 0 ? <span className="text-xs text-muted-foreground tabular-nums">{count}</span> : null}
    </button>
  );
}
