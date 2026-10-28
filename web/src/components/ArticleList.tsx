import { Archive, ArrowLeft, Bell, BookmarkPlus, BookOpen, CheckCheck, ChevronDown, Circle, CircleCheck, Folder as FolderGlyph, Heart, Highlighter, Link2, Menu, Newspaper, Quote, RefreshCw, Rss, Search, Share2, StickyNote, Tag as TagIcon, Trash2, X, type LucideIcon } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useEscape } from "./ui/useEscape";
import { app, useApp } from "@/lib/app";
import { snippet } from "@/lib/html";
import { dayLabel, plural, t } from "@/lib/i18n";
import { articleKey, articlesFor, chipTag, feedTags, sortTime, unreadKeys, type Article, type Feed } from "@/lib/library";
import { itemArticle, noteOf, sameTab, savedItems, savedStates, showsNotes, sourceOf, tabTags, type NoteContent, type SavedFilter } from "@/lib/saved";
import { sharedByLabel } from "@/lib/sharing";
import { inboxItems, inboxUnread } from "@/lib/inbox";
import type { Item, SharedBy } from "@/lib/items";
import { NewDot } from "./NewDot";
import { Avatar } from "./ShareWithUser";
import { useShare } from "./useShare";
import { scrolledPast } from "@/lib/scrollRead";
import { FeedIcon } from "./FeedIcon";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { WithRowMenu } from "./RowMenu";
import { FilterChip } from "./ui/chip";

type ListProps = {
  openKey: string | null;
  onOpen: (a: Article) => void;
  onMenu: () => void;
  onAddFeed: () => void;
  onImport: () => void;
  onUndo: (keys: Set<string>) => void;
  /** Opens Search (the title bar's button, #183). */
  onSearch: () => void;
  /** A message at the bottom, with at most one action (Undo). */
  onMessage: (text: string, action?: { label: string; run: () => void }) => void;
};

/** The articles of the chosen feed, folder or All articles, Saved, or the inbox (#246). */
export function ArticleList(props: ListProps) {
  const { selection, inboxOpen } = useApp();
  return inboxOpen ? (
    <InboxArticles openKey={props.openKey} onOpen={props.onOpen} onMessage={props.onMessage} />
  ) : selection.kind === "saved" ? (
    <SavedArticles openKey={props.openKey} onOpen={props.onOpen} onMenu={props.onMenu} onSearch={props.onSearch} onMessage={props.onMessage} />
  ) : (
    <FeedArticles {...props} />
  );
}

function FeedArticles({ openKey, onOpen, onMenu, onAddFeed, onImport, onUndo, onSearch, onMessage }: ListProps) {
  const { library, selection, settings, readThisVisit, refreshing, loaded, problems } = useApp();
  const [share, shareDialog] = useShare((text) => onMessage(text));
  const articles = useMemo(
    () => articlesFor(library, selection, settings.unreadOnly, readThisVisit),
    [library, selection, settings.unreadOnly, readThisVisit],
  );
  const feeds = useMemo(() => new Map(library.feeds.map((f) => [f.url, f])), [library.feeds]);
  const unread = unreadKeys(library, selection).size;
  const tags = useMemo(() => feedTags(library), [library]);
  const oneFeed = selection.kind === "feed" ? (feeds.get(selection.feedUrl)?.title ?? null) : null;
  const title =
    selection.kind === "folder"
      ? (library.folders.find((f) => f.id === selection.folderId)?.title ?? "")
      : selection.kind === "feed"
        ? (feeds.get(selection.feedUrl)?.title ?? "")
        : t("allArticles");
  const days = useMemo(() => groupByDay(articles), [articles]);
  const showFeedName = selection.kind !== "feed";

  // Mark as read on scroll: once per frame, the unread articles that scrolled off the top, of
  // those that were on screen (#157), so new ones arriving above are never marked unseen.
  const frame = useRef(0);
  const listRef = useRef<HTMLDivElement>(null);
  const seen = useRef(new Set<string>());
  const rowsOf = (list: HTMLElement) => Array.from(list.querySelectorAll<HTMLElement>("[data-key]"));
  const noteSeen = (list: HTMLElement) => {
    const box = list.getBoundingClientRect();
    for (const row of rowsOf(list)) {
      const r = row.getBoundingClientRect();
      if (r.bottom > box.top && r.top < box.bottom) seen.current.add(row.dataset.key!);
    }
  };
  useEffect(() => {
    if (settings.markReadOnScroll && listRef.current) noteSeen(listRef.current);
  }, [articles, settings.markReadOnScroll]);
  const onScroll = (e: React.UIEvent<HTMLDivElement>) => {
    if (!settings.markReadOnScroll || frame.current) return;
    const list = e.currentTarget;
    frame.current = requestAnimationFrame(() => {
      frame.current = 0;
      noteSeen(list);
      const top = list.getBoundingClientRect().top;
      const rows = rowsOf(list);
      const first = rows.findIndex((row) => row.getBoundingClientRect().bottom > top);
      app.markScrolledPast(scrolledPast(articles, first === -1 ? rows.length : first, seen.current));
    });
  };

  return (
    <section className="flex h-full min-w-0 flex-col" aria-label={title}>
      <SearchBar onMenu={onMenu} onSearch={onSearch}>
        {library.feeds.length > 0 ? (
          <>
            <Button variant="ghost" size="icon" onClick={() => void app.refresh()} disabled={refreshing} aria-label={t("refresh")} title={t("refresh") + " (r)"}>
              <RefreshCw className={cn(refreshing && "animate-spin")} />
            </Button>
            <Button variant="ghost" size="icon" disabled={unread === 0} onClick={() => onUndo(app.markAllRead())} aria-label={t("markAllRead")} title={t("markAllRead")}>
              <CheckCheck />
            </Button>
          </>
        ) : null}
      </SearchBar>
      {library.feeds.length > 0 ? (
        <>
          <TagChips
            state={<StateChip options={[t("unread"), t("all")]} selected={settings.unreadOnly ? 0 : 1} onSelect={(i) => app.setUnreadOnly(i === 0)} filtering={settings.unreadOnly} />}
            // One feed chosen by its name (#185): its chip comes first, chosen.
            tags={oneFeed ? [oneFeed, ...tags] : tags}
            // A folder picked in the menu lights up its chip: the folder's name is its feeds' tag.
            selected={oneFeed ?? chipTag(library, selection)}
            onSelect={(tag) => app.select(tag === null || tag === oneFeed ? { kind: "all" } : { kind: "tag", tag })}
            // A feed or folder chosen elsewhere is a filter too: the X goes back to every feed.
            filtered={selection.kind !== "all"}
            onClear={() => app.select({ kind: "all" })}
          />
          {problems.size > 0 ? <p className="px-4 pb-1 text-xs text-destructive">{plural("feedsFailed", problems.size)}</p> : null}
        </>
      ) : null}
      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-6" role="feed" aria-busy={refreshing} onScroll={onScroll} ref={listRef}>
        {!loaded || (refreshing && library.articles.length === 0) ? (
          <Skeletons />
        ) : library.feeds.length === 0 ? (
          <Empty icon={Rss} title={t("noFeedsTitle")} text={t("noFeedsText")}>
            <Button onClick={onAddFeed}>{t("addFeed")}</Button>
            <Button variant="outline" onClick={onImport}>
              {t("importOpml")}
            </Button>
          </Empty>
        ) : articles.length === 0 ? (
          <Empty
            icon={settings.unreadOnly ? CheckCheck : Newspaper}
            title={settings.unreadOnly ? t("caughtUpTitle") : t("noArticlesTitle")}
          >
            {settings.unreadOnly ? (
              <Button onClick={() => app.setUnreadOnly(false)}>
                {t("showAll")}
              </Button>
            ) : null}
            {/* Look for new articles (#251), as on Android. */}
            <Button variant="outline" onClick={() => void app.refresh()} disabled={refreshing}>
              {t("refresh")}
            </Button>
          </Empty>
        ) : (
          days.map(([day, list]) => (
            <div key={day}>
              <h2 className="sticky top-0 z-10 bg-background px-3 pt-3 pb-1 text-sm font-medium text-primary">{dayLabel(day)}</h2>
              <ul className="space-y-1">
                {list.map((a) => (
                  <li key={articleKey(a)}>
                    {/* Right-click or press and hold (#221): read or unread, Save, Share. */}
                    <WithRowMenu
                      items={[
                        { icon: a.read ? <Circle /> : <CircleCheck />, label: a.read ? t("markUnread") : t("markRead"), onClick: () => app.toggleRead(a) },
                        ...(a.link
                          ? [
                              { icon: <BookmarkPlus />, label: t("save"), onClick: () => app.saveArticle(a.link!, a.title) },
                              { icon: <Share2 />, label: t("share"), onClick: () => share(a.title, a.link!) },
                            ]
                          : []),
                      ]}
                    >
                      <Row
                        article={a}
                        feed={showFeedName ? feeds.get(a.feedUrl) : undefined}
                        open={openKey === articleKey(a)}
                        onOpen={() => onOpen(a)}
                        onToggleRead={() => app.toggleRead(a)}
                        onFeedClick={() => app.select({ kind: "feed", feedUrl: a.feedUrl })}
                      />
                    </WithRowMenu>
                  </li>
                ))}
              </ul>
            </div>
          ))
        )}
      </div>
      {shareDialog}
    </section>
  );
}

/**
 * Saved (#119, #147), as on Android: To read or the Archive (the home tabs, #183), then the
 * tab's tags as chips, and the type filter. To read is a list like the feeds; a folder and the
 * Archive are collections, so their items are cards with your note (or first highlight, or the
 * description), the site, tags and highlights.
 */
function SavedArticles({
  openKey,
  onOpen,
  onMenu,
  onSearch,
  onMessage,
}: {
  openKey: string | null;
  onOpen: (a: Article) => void;
  onMenu: () => void;
  onSearch: () => void;
  onMessage: ListProps["onMessage"];
}) {
  const { library, savedFilter: filter, loaded } = useApp();
  const [share, shareDialog] = useShare((text) => onMessage(text));
  // Right-click or press and hold on a saved item (#221, #263), cards and rows alike.
  const menuOf = (item: Item) => [
    { icon: <TagIcon />, label: t("organiseTitle"), onClick: () => app.openOrganise(item.key) },
    { icon: <Share2 />, label: t("share"), onClick: () => share(item.title, item.url, item) },
    {
      icon: <Trash2 />,
      label: t("savedRemove"),
      onClick: () => {
        app.removeItem(item.key);
        onMessage(t("savedRemoved"), { label: t("undo"), run: () => app.restoreItem(item) });
      },
    },
  ];
  const items = useMemo(() => savedItems(library, filter), [library, filter]);
  // Every tag in Saved, whichever state is shown: a tag stays chosen when the state changes.
  const tags = useMemo(() => tabTags(library, { kind: "all" }), [library]);
  const set = (change: Partial<SavedFilter>) => app.showSaved({ ...filter, ...change });
  const plain = !filter.likedOnly && filter.tags.length === 0;
  return (
    <section className="flex h-full min-w-0 flex-col" aria-label={t("saved")}>
      <SearchBar onMenu={onMenu} onSearch={onSearch}>
        <Button variant="ghost" size="icon" onClick={app.addLink} aria-label={t("addLink")} title={t("addLink")}>
          <Link2 />
        </Button>
      </SearchBar>
      <TagChips
        state={
          <StateChip
            options={[t("toRead"), t("archiveTab"), t("all")]}
            selected={Math.max(0, savedStates.findIndex((s) => sameTab(s, filter.tab)))}
            onSelect={(i) => set({ tab: savedStates[i] })}
            filtering={filter.tab.kind !== "all"}
          />
        }
        // Liked is a group like a tag (#206): one at a time, before the tags.
        liked={
          <button
            type="button"
            aria-pressed={filter.likedOnly}
            onClick={() => set({ likedOnly: !filter.likedOnly, tags: [] })}
            className={cn(
              "inline-flex h-8 shrink-0 cursor-pointer items-center gap-1.5 rounded-lg border px-3 text-sm",
              filter.likedOnly ? "border-transparent bg-accent text-accent-foreground" : "hover:bg-muted",
            )}
          >
            <Heart className={cn("size-4", filter.likedOnly && "fill-current")} aria-hidden="true" />
            {t("liked")}
          </button>
        }
        tags={tags}
        selected={filter.tags[0] ?? null}
        onSelect={(tag) => set({ tags: tag === null ? [] : [tag], likedOnly: false })}
        filtered={!plain}
        onClear={() => set({ likedOnly: false, tags: [] })}
      />
      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-6" role="feed">
        {!loaded ? (
          <Skeletons />
        ) : items.length === 0 ? (
          <Empty
            icon={filter.tab.kind === "archive" ? Archive : filter.tab.kind === "folder" ? FolderGlyph : BookOpen}
            title={plain && filter.tab.kind === "toread" ? t("nothingToRead") : plain && filter.tab.kind === "archive" ? t("nothingArchived") : t("savedNoneTitle")}
            text={plain && filter.tab.kind === "toread" ? t("savedEmptyBody") : undefined}
          >
            {/* Filtered to nothing: one click back to the whole tab; an empty To read offers Add link. */}
            {!plain ? (
              <Button onClick={() => set({ likedOnly: false, tags: [] })}>{t("showAll")}</Button>
            ) : filter.tab.kind === "toread" ? (
              <Button variant="tonal" onClick={app.addLink}>
                <Link2 />
                {t("addLink")}
              </Button>
            ) : null}
          </Empty>
        ) : (
          <>
            <p className="px-3 pt-3 pb-2 text-xs text-muted-foreground">{plural("savedCount", items.length)}</p>
            {showsNotes(filter.tab) ? (
              <ul className="columns-[11rem] gap-2 px-1">
                {items.map((item) => {
                  const article = itemArticle(item, library);
                  return (
                    <li key={item.key} className="mb-2 break-inside-avoid">
                      {/* Right-click or press and hold (#221, #263): Organise, Share and Remove. */}
                      <WithRowMenu items={menuOf(item)}>
                        <NoteCard note={noteOf(item)} feed={sourceOf(item, library)} open={openKey === articleKey(article)} onOpen={() => onOpen(article)} />
                      </WithRowMenu>
                    </li>
                  );
                })}
              </ul>
            ) : (
              <ul className="space-y-1">
                {items.map((item) => {
                  const article = itemArticle(item, library);
                  return (
                    <li key={item.key}>
                      <WithRowMenu items={menuOf(item)}>
                        <Row article={article} feed={sourceOf(item, library)} open={openKey === articleKey(article)} onOpen={() => onOpen(article)} noReadState />
                      </WithRowMenu>
                    </li>
                  );
                })}
              </ul>
            )}
          </>
        )}
      </div>
      {shareDialog}
    </section>
  );
}

/**
 * The inbox (#246), as on Android: what other Content users shared with you, newest first, like a
 * feed's articles with who sent it in place of the feed. Unread ones are bold with a dot; opening one
 * reads it. Right-click or press and hold: read or unread, Save, Share, Remove from inbox.
 */
function InboxArticles({ openKey, onOpen, onMessage }: { openKey: string | null; onOpen: (a: Article) => void; onMessage: ListProps["onMessage"] }) {
  const { library, settings, loaded } = useApp();
  const items = useMemo(() => inboxItems(library), [library]);
  const [share, shareDialog] = useShare((text) => onMessage(text));
  return (
    <section className="flex h-full min-w-0 flex-col" aria-label={t("inboxTitle")}>
      <header className="flex shrink-0 items-center gap-1 px-3 pt-3 pb-1">
        <Button variant="ghost" size="icon" onClick={() => app.showInbox(false)} aria-label={t("back")}>
          <ArrowLeft />
        </Button>
        <h1 className="text-lg font-medium">{t("inboxTitle")}</h1>
      </header>
      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-6" role="feed">
        {!loaded ? (
          <Skeletons />
        ) : items.length === 0 ? (
          <Empty icon={Bell} title={t("inboxEmptyTitle")} text={t("inboxEmptyBody")} />
        ) : (
          <ul className="space-y-1">
            {items.map((item) => {
              const by = item.sharedBy!;
              const article = { ...itemArticle(item, library), read: by.read === true, publishedAt: by.at ?? null, fetchedAt: by.at ?? 0 };
              const toggle = () => app.markInboxRead(item.key, by.read !== true);
              return (
                <li key={item.key}>
                  <WithRowMenu
                    items={[
                      { icon: by.read ? <Circle /> : <CircleCheck />, label: by.read ? t("markUnread") : t("markRead"), onClick: toggle },
                      // Already in Saved: nothing to save.
                      ...(item.readingList ? [] : [{ icon: <BookmarkPlus />, label: t("save"), onClick: () => app.saveArticle(item.url, item.title) }]),
                      { icon: <Share2 />, label: t("share"), onClick: () => share(item.title, item.url, item) },
                      {
                        icon: <Trash2 />,
                        label: t("inboxRemove"),
                        onClick: () => {
                          const before = app.removeFromInbox(item.key);
                          if (before) onMessage(t("inboxRemoved"), { label: t("undo"), run: () => app.restoreInbox(before) });
                        },
                      },
                    ]}
                  >
                    <Row
                      article={article}
                      feed={undefined}
                      source={<Sender by={by} images={settings.showImages} />}
                      open={openKey === articleKey(article)}
                      onOpen={() => {
                        app.markInboxRead(item.key);
                        onOpen(article);
                      }}
                      onToggleRead={toggle}
                    />
                  </WithRowMenu>
                </li>
              );
            })}
          </ul>
        )}
      </div>
      {shareDialog}
    </section>
  );
}

/** Who shared it: their Gravatar picture (else their first letter) and name (else their address). */
function Sender({ by, images }: { by: SharedBy; images: boolean }) {
  const label = sharedByLabel(by);
  return (
    <>
      <Avatar email={by.email} label={label} images={images} className="size-4 text-[0.6rem]" />
      <span className="truncate">{label}</span>
    </>
  );
}

/** The inbox's bell (#246), with a dot while something shared with you is unread; only while signed in. */
function InboxBell() {
  const { account, library } = useApp();
  if (!account) return null;
  const unread = inboxUnread(library) > 0;
  return (
    <Button variant="ghost" size="icon" className="relative" onClick={() => app.showInbox(true)} aria-label={t("inboxTitle")} title={t("inboxTitle")}>
      <Bell />
      {unread ? <NewDot className="absolute top-2 right-2" /> : null}
    </Button>
  );
}

/** A tab's name: To read, All, Archive or the folder's own. */
/**
 * The title bar as a large search bar (#189), as on Android: ☰ at its start, then "Search articles",
 * which opens Search, and the list's own buttons at its end.
 */
function SearchBar({ onMenu, onSearch, children }: { onMenu: () => void; onSearch: () => void; children?: React.ReactNode }) {
  return (
    <header className="shrink-0 px-3 pt-3 pb-1">
      <div className="flex h-14 items-center gap-1 rounded-full bg-muted px-1">
        <Button variant="ghost" size="icon" className="lg:hidden" onClick={onMenu} aria-label={t("openMenu")}>
          <Menu />
        </Button>
        <button type="button" onClick={onSearch} title={t("search") + " (/)"} className="min-w-0 flex-1 cursor-pointer truncate px-2 text-left text-muted-foreground">
          {t("searchHint")}
        </button>
        {children}
        <InboxBell />
        <Button variant="ghost" size="icon" onClick={onSearch} aria-label={t("search")}>
          <Search />
        </Button>
      </div>
    </header>
  );
}

/**
 * The state chip at the start of the chip row (#205): what of the place is shown, Unread or All in
 * Feeds, To read, Archive or All in Saved. A menu picks another; it is lit while it leaves things out.
 */
function StateChip({ options, selected, onSelect, filtering }: { options: string[]; selected: number; onSelect: (index: number) => void; filtering: boolean }) {
  const [at, setAt] = useState<{ top: number; left: number } | null>(null);
  useEscape(at !== null, () => setAt(null));
  return (
    <>
      <button
        type="button"
        aria-haspopup="true"
        aria-expanded={at !== null}
        onClick={(e) => {
          // The chip row scrolls sideways, so the menu is placed on the page, under the chip.
          const r = e.currentTarget.getBoundingClientRect();
          setAt({ top: r.bottom + 4, left: r.left });
        }}
        className={cn(
          "inline-flex h-8 shrink-0 cursor-pointer items-center gap-1 rounded-lg border pr-2 pl-3 text-sm",
          filtering ? "border-transparent bg-accent text-accent-foreground" : "hover:bg-muted",
        )}
      >
        {options[selected]}
        <ChevronDown className="size-4" />
      </button>
      {at ? (
        <>
          <div className="fixed inset-0 z-20" onClick={() => setAt(null)} aria-hidden="true" />
          <div role="radiogroup" aria-label={t("filter")} style={{ top: at.top, left: at.left }} className="fixed z-30 w-44 rounded-xl border bg-card py-1 shadow-lg">
            {options.map((label, i) => (
              <button
                key={label}
                type="button"
                role="radio"
                aria-checked={i === selected}
                onClick={() => {
                  setAt(null);
                  if (i !== selected) onSelect(i);
                }}
                className="flex w-full cursor-pointer items-center gap-3 px-3 py-2 text-left text-sm hover:bg-muted"
              >
                <span className={cn("size-4 rounded-full border-2", i === selected ? "border-primary bg-primary shadow-[inset_0_0_0_2px_var(--color-card)]" : "border-muted-foreground")} />
                <span className="flex-1">{label}</span>
              </button>
            ))}
          </div>
        </>
      ) : null}
    </>
  );
}

/**
 * The chip row under the search bar: the state chip, then the groups as chips, one at a time
 * (folders in Feeds, tags in Saved). While a group or another filter is on, an X at the end clears it.
 */
function TagChips({
  state,
  liked,
  tags,
  selected,
  onSelect,
  filtered,
  onClear,
}: {
  state: React.ReactNode;
  /** Saved's Liked chip (#206), the first group. */
  liked?: React.ReactNode;
  tags: string[];
  selected: string | null;
  onSelect: (tag: string | null) => void;
  filtered: boolean;
  onClear: () => void;
}) {
  return (
    <div role="group" aria-label={t("filter")} className="flex shrink-0 items-center gap-2 overflow-x-auto px-4 py-2 [scrollbar-width:none]">
      {state}
      {tags.length > 0 || liked ? <span className="h-6 w-px shrink-0 bg-border" aria-hidden="true" /> : null}
      {liked}
      {tags.map((tag) => {
        const on = selected !== null && tag.toLowerCase() === selected.toLowerCase();
        return (
          <FilterChip key={tag} selected={on} check={false} onClick={() => onSelect(on ? null : tag)}>
            {tag}
          </FilterChip>
        );
      })}
      {filtered ? (
        <Button variant="ghost" size="icon" onClick={onClear} aria-label={t("clear")} title={t("clear")}>
          <X />
        </Button>
      ) : null}
    </div>
  );
}

/**
 * A note card (Noter's cards): the picture on top, the title, for a saved item your note (else the
 * first highlight, quoted, else the description), for a feed article the start of its text, then
 * the site, tags, highlights and a heart when liked. `label` says what kind it is in Search.
 */
export function NoteCard({ note, feed, open, onOpen, label }: { note: NoteContent; feed: Feed | undefined; open: boolean; onOpen: () => void; label?: string }) {
  const { settings } = useApp();
  const body = note.body;
  const image = settings.showImages ? note.imageUrl : null;
  const highlights = note.highlights;
  return (
    <button
      type="button"
      onClick={onOpen}
      aria-current={open ? "true" : undefined}
      className={cn("flex w-full cursor-pointer flex-col overflow-hidden rounded-xl border text-left", open ? "bg-accent" : "bg-background hover:bg-muted")}
    >
      {image ? <img src={image} alt="" loading="lazy" referrerPolicy="no-referrer" className="h-24 w-full bg-muted object-cover" onError={(e) => (e.currentTarget.style.display = "none")} /> : null}
      <div className="flex flex-col gap-2 px-4 py-3">
        {label ? <span className="text-xs font-medium text-primary">{label}</span> : null}
        <h3 className="line-clamp-3 font-serif leading-snug font-medium">{note.title}</h3>
        {/* The text, kept short: the first highlight, quoted, or the site's description. */}
        {body?.kind === "quote" ? (
          <p className="flex gap-2 text-sm">
            <Quote className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
            <span className="line-clamp-3 font-serif">{body.text}</span>
          </p>
        ) : body ? (
          <p className="line-clamp-3 text-sm text-muted-foreground">{body.text}</p>
        ) : null}
        {/* Your note, small: a glimpse, the whole of it is in the article. */}
        {note.note ? (
          <p className="flex gap-1.5 text-xs text-muted-foreground italic" aria-label={`${t("note")}: ${note.note}`}>
            <StickyNote className="mt-px size-3.5 shrink-0" aria-hidden="true" />
            <span className="line-clamp-2">{note.note}</span>
          </p>
        ) : null}
        {/* The tags as small chips (#208), not as #words. */}
        {note.tags.length > 0 ? (
          <div className="flex flex-wrap gap-1">
            {note.tags.map((tag) => (
              <span key={tag} className="max-w-full truncate rounded-md bg-accent px-2 py-0.5 text-xs text-accent-foreground">
                {tag}
              </span>
            ))}
          </div>
        ) : null}
        <div className="flex items-center gap-1.5 text-xs">
          <span className="flex min-w-0 items-center gap-1 text-muted-foreground">
            {feed ? <FeedIcon feed={feed} className="size-4" /> : null}
            <span className="truncate">{note.host}</span>
          </span>
          {/* How many highlights (your markings and their notes). */}
          {highlights > 0 ? (
            <span className="flex shrink-0 items-center gap-0.5 text-muted-foreground" aria-label={plural("highlightsCount", highlights)}>
              <Highlighter className="size-3.5" aria-hidden="true" />
              {highlights}
            </span>
          ) : null}
          {note.liked ? <Heart className="size-3.5 shrink-0 fill-current text-primary" aria-label={t("liked")} /> : null}
        </div>
      </div>
    </button>
  );
}

/**
 * One article in a list. `label` says what kind it is (Search: Feed or Saved). With
 * `onToggleRead` (the feed list), a button on hover marks it read or unread, like the swipe on
 * Android (#157).
 */
export function Row({
  article,
  feed,
  open,
  onOpen,
  noReadState,
  label,
  onToggleRead,
  onFeedClick,
  source,
}: {
  article: Article;
  feed: Feed | undefined;
  open: boolean;
  onOpen: () => void;
  noReadState?: boolean;
  label?: string;
  onToggleRead?: () => void;
  /** Clicking the feed's name shows only that feed (#185). */
  onFeedClick?: () => void;
  /** Shown where the feed's name goes: who shared it, in the inbox (#246). */
  source?: React.ReactNode;
}) {
  const row = <RowButton article={article} feed={feed} open={open} onOpen={onOpen} noReadState={noReadState} label={label} onFeedClick={onFeedClick} source={source} />;
  if (!onToggleRead) return row;
  const toggle = article.read ? t("markUnread") : t("markRead");
  return (
    <div className="group relative">
      {row}
      <button
        type="button"
        onClick={onToggleRead}
        aria-label={toggle}
        title={toggle + " (m)"}
        className="absolute top-1.5 right-1.5 flex size-8 cursor-pointer items-center justify-center rounded-full bg-card text-muted-foreground opacity-0 shadow-sm transition-opacity group-hover:opacity-100 hover:text-foreground focus-visible:opacity-100 [@media(hover:none)]:opacity-100 [&_svg]:size-4"
      >
        {article.read ? <Circle /> : <CircleCheck />}
      </button>
    </div>
  );
}

function RowButton({
  article,
  feed,
  open,
  onOpen,
  noReadState,
  label,
  onFeedClick,
  source,
}: {
  article: Article;
  feed: Feed | undefined;
  open: boolean;
  onOpen: () => void;
  noReadState?: boolean;
  label?: string;
  onFeedClick?: () => void;
  source?: React.ReactNode;
}) {
  const { settings } = useApp();
  const view = settings.listView;
  const unread = !article.read && !noReadState;
  const text = useMemo(() => snippet(article.html, 160), [article.html]);
  const image = settings.showImages && view !== "small" ? article.imageUrl : null;
  const hide = (e: React.SyntheticEvent<HTMLImageElement>) => (e.currentTarget.style.display = "none");
  return (
    <button
      type="button"
      onClick={onOpen}
      data-key={articleKey(article)}
      aria-current={open ? "true" : undefined}
      className={cn(
        "flex w-full cursor-pointer gap-3 rounded-xl text-left transition-colors",
        view === "large" ? "flex-col overflow-hidden" : "p-3",
        open ? "bg-accent" : unread || noReadState ? "bg-card hover:bg-muted" : "hover:bg-muted",
      )}
    >
      {view === "large" && image ? <img src={image} alt="" loading="lazy" referrerPolicy="no-referrer" className="aspect-video w-full bg-muted object-cover" onError={hide} /> : null}
      <div className={cn("min-w-0 flex-1", view === "large" && "px-3 pb-3", view === "large" && !image && "pt-3")}>
        {feed || label || source ? (
          <div className="mb-1 flex items-center gap-1.5 text-xs font-medium text-primary">
            {source ? (
              <span className="flex min-w-0 items-center gap-1.5">{source}</span>
            ) : feed && onFeedClick ? (
              // Inside the row's button, so a span that acts as a button (#185).
              <span
                role="button"
                tabIndex={0}
                title={feed.title}
                onClick={(e) => {
                  e.stopPropagation();
                  onFeedClick();
                }}
                onKeyDown={(e) => {
                  if (e.key !== "Enter" && e.key !== " ") return;
                  e.preventDefault();
                  e.stopPropagation();
                  onFeedClick();
                }}
                className="flex min-w-0 items-center gap-1.5 rounded-full hover:underline"
              >
                <FeedIcon feed={feed} className="size-4" />
                <span className="truncate">{feed.title}</span>
              </span>
            ) : feed ? (
              <>
                <FeedIcon feed={feed} className="size-4" />
                <span className="truncate">{feed.title}</span>
              </>
            ) : null}
            {label ? <span className="ml-auto shrink-0 rounded-md bg-accent px-1.5 py-0.5 text-[0.7rem] text-accent-foreground">{label}</span> : null}
          </div>
        ) : null}
        <div className="flex items-start gap-2">
          {unread ? <span className="mt-2 size-2 shrink-0 rounded-full bg-unread" aria-label={t("unread")} /> : null}
          <h3
            className={cn(
              "leading-snug",
              view === "small" ? "line-clamp-2 text-sm" : view === "large" ? "line-clamp-3 text-lg" : "line-clamp-3 text-[0.95rem]",
              unread || noReadState ? "font-semibold" : "text-muted-foreground",
            )}
          >
            {article.title}
          </h3>
        </div>
        {text && view !== "small" ? <p className={cn("mt-1 text-sm text-muted-foreground", view === "large" ? "line-clamp-3" : "line-clamp-2")}>{text}</p> : null}
      </div>
      {view === "medium" && image ? (
        <img src={image} alt="" loading="lazy" referrerPolicy="no-referrer" className="size-18 shrink-0 rounded-lg bg-muted object-cover" onError={hide} />
      ) : null}
    </button>
  );
}

/** Same layout as the Android app's EmptyState: icon in a circle, title, one sentence, buttons. */
export function Empty({
  icon: Icon,
  title,
  text,
  children,
}: {
  icon: LucideIcon;
  title: string;
  text?: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="mx-auto flex max-w-sm flex-col items-center gap-3 px-6 py-16 text-center">
      <div className="mb-1 flex size-18 items-center justify-center rounded-full bg-accent text-accent-foreground">
        <Icon className="size-9" aria-hidden="true" />
      </div>
      <h2 className="text-lg font-semibold">{title}</h2>
      {text ? <p className="text-sm text-muted-foreground">{text}</p> : null}
      <div className="flex flex-wrap justify-center gap-2 pt-2">{children}</div>
    </div>
  );
}

export function Skeletons() {
  return (
    <div className="space-y-2 p-2" aria-hidden="true">
      {Array.from({ length: 6 }, (_, i) => (
        <div key={i} className="animate-pulse rounded-xl bg-card p-3">
          <div className="h-3 w-24 rounded bg-muted" />
          <div className="mt-2 h-4 w-11/12 rounded bg-muted" />
          <div className="mt-1.5 h-4 w-2/3 rounded bg-muted" />
        </div>
      ))}
    </div>
  );
}

/** Articles under their day (local time), newest first. */
function groupByDay(articles: Article[]): [string, Article[]][] {
  const groups = new Map<string, Article[]>();
  for (const a of articles) {
    const d = new Date(sortTime(a));
    const day = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
    const list = groups.get(day);
    if (list) list.push(a);
    else groups.set(day, [a]);
  }
  return [...groups];
}
