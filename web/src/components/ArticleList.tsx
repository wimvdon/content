import { Archive, Bookmark, BookOpen, CheckCheck, Circle, CircleCheck, Folder as FolderGlyph, Heart, Highlighter, Link2, ListFilter, Menu, Newspaper, Quote, RefreshCw, Rss, Search, type LucideIcon } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { app, useApp } from "@/lib/app";
import { snippet } from "@/lib/html";
import { dayLabel, plural, t } from "@/lib/i18n";
import type { Item } from "@/lib/items";
import { articleKey, articlesFor, sortTime, unreadKeys, type Article, type Feed } from "@/lib/library";
import { cardBody, hostOf, itemArticle, sameTab, savedFolders, savedItems, sourceOf, tabTags, type SavedFilter, type SavedTab, type SavedType } from "@/lib/saved";
import { scrolledPast } from "@/lib/scrollRead";
import { FeedIcon } from "./FeedIcon";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { FilterChip } from "./ui/chip";

type ListProps = {
  openKey: string | null;
  onOpen: (a: Article) => void;
  onMenu: () => void;
  onAddFeed: () => void;
  onImport: () => void;
  onUndo: (keys: Set<string>) => void;
  onSearch: () => void;
};

/** The articles of the chosen feed, folder or All articles, or Saved. */
export function ArticleList(props: ListProps) {
  const { selection } = useApp();
  return selection.kind === "saved" ? (
    <SavedArticles openKey={props.openKey} onOpen={props.onOpen} onMenu={props.onMenu} onSearch={props.onSearch} />
  ) : (
    <FeedArticles {...props} />
  );
}

function FeedArticles({ openKey, onOpen, onMenu, onAddFeed, onImport, onUndo, onSearch }: ListProps) {
  const { library, selection, settings, readThisVisit, refreshing, loaded, problems } = useApp();
  const articles = useMemo(
    () => articlesFor(library, selection, settings.unreadOnly, readThisVisit),
    [library, selection, settings.unreadOnly, readThisVisit],
  );
  const feeds = useMemo(() => new Map(library.feeds.map((f) => [f.url, f])), [library.feeds]);
  const unread = unreadKeys(library, selection).size;
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
      <header className="flex items-center gap-1 px-2 pt-2 pb-1">
        <Button variant="ghost" size="icon" className="lg:hidden" onClick={onMenu} aria-label={t("openFeeds")}>
          <Menu />
        </Button>
        <div className="min-w-0 flex-1 px-2">
          <h1 className="truncate text-xl font-semibold">{title}</h1>
          {library.feeds.length > 0 ? (
            <p className="text-xs text-muted-foreground">{plural(settings.unreadOnly ? "unreadCount" : "unreadCountAll", unread)}</p>
          ) : null}
        </div>
        <SearchButton onClick={onSearch} />
        {library.feeds.length > 0 ? (
          <>
            <Button variant="ghost" size="icon" onClick={() => void app.refresh()} disabled={refreshing} aria-label={t("refresh")} title={t("refresh") + " (r)"}>
              <RefreshCw className={cn(refreshing && "animate-spin")} />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              disabled={unread === 0}
              onClick={() => onUndo(app.markAllRead())}
              aria-label={t("markAllRead")}
              title={t("markAllRead")}
            >
              <CheckCheck />
            </Button>
          </>
        ) : null}
      </header>
      {library.feeds.length > 0 ? (
        <div className="flex items-center gap-2 px-4 pt-2 pb-3">
          {/* Unread or All: two filter chips, one chosen, in a row above the list (as on Android). */}
          <div role="radiogroup" aria-label={t("filter")} className="flex gap-2">
            <FilterChip role="radio" selected={settings.unreadOnly} onClick={() => app.setUnreadOnly(true)}>
              {t("unread")}
            </FilterChip>
            <FilterChip role="radio" selected={!settings.unreadOnly} onClick={() => app.setUnreadOnly(false)}>
              {t("all")}
            </FilterChip>
          </div>
          {problems.size > 0 ? <span className="text-xs text-destructive">{plural("feedsFailed", problems.size)}</span> : null}
        </div>
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
            text={settings.unreadOnly ? t("caughtUpText") : t("nothingHere")}
          >
            {settings.unreadOnly ? (
              <Button onClick={() => app.setUnreadOnly(false)}>
                {t("showAll")}
              </Button>
            ) : null}
          </Empty>
        ) : (
          days.map(([day, list]) => (
            <div key={day}>
              <h2 className="sticky top-0 z-10 bg-background px-3 pt-3 pb-1 text-sm font-medium text-primary">{dayLabel(day)}</h2>
              <ul className="space-y-1">
                {list.map((a) => (
                  <li key={articleKey(a)}>
                    <Row article={a} feed={showFeedName ? feeds.get(a.feedUrl) : undefined} open={openKey === articleKey(a)} onOpen={() => onOpen(a)} onToggleRead={() => app.toggleRead(a)} />
                  </li>
                ))}
              </ul>
            </div>
          ))
        )}
      </div>
    </section>
  );
}

/**
 * Saved (#119, #147), as on Android: To read, All, each folder and Archive as tabs, then Liked and
 * the tab's tags as chips, and the type filter next to search. To read, All and Archive are lists
 * like the feeds; a folder is a collection, so its articles are cards with your note (or first
 * highlight, or the description), the site, tags and highlights.
 */
function SavedArticles({ openKey, onOpen, onMenu, onSearch }: { openKey: string | null; onOpen: (a: Article) => void; onMenu: () => void; onSearch: () => void }) {
  const { library, savedFilter: filter, loaded } = useApp();
  const items = useMemo(() => savedItems(library, filter), [library, filter]);
  const folders = useMemo(() => savedFolders(library), [library]);
  const tags = useMemo(() => tabTags(library, filter.tab), [library, filter.tab]);
  const set = (change: Partial<SavedFilter>) => app.showSaved({ ...filter, ...change });
  const plain = !filter.likedOnly && filter.tags.length === 0 && filter.type === null;
  const tabs: SavedTab[] = [{ kind: "toread" }, { kind: "all" }, ...folders.map((name): SavedTab => ({ kind: "folder", name })), { kind: "archive" }];
  return (
    <section className="flex h-full min-w-0 flex-col" aria-label={t("saved")}>
      <header className="flex items-center gap-1 px-2 pt-2 pb-1">
        <Button variant="ghost" size="icon" className="lg:hidden" onClick={onMenu} aria-label={t("openFeeds")}>
          <Menu />
        </Button>
        <div className="min-w-0 flex-1 px-2">
          <h1 className="truncate text-xl font-semibold">{t("saved")}</h1>
        </div>
        <Button variant="ghost" size="icon" onClick={app.addLink} aria-label={t("addLink")} title={t("addLink")}>
          <Link2 />
        </Button>
        <TypeFilter type={filter.type} onChange={(type) => set({ type })} />
        <SearchButton onClick={onSearch} />
      </header>
      <nav aria-label={t("saved")} className="flex shrink-0 overflow-x-auto border-b px-2 [scrollbar-width:none]">
        {tabs.map((tab) => {
          const selected = sameTab(tab, filter.tab);
          const Icon = tab.kind === "toread" ? BookOpen : tab.kind === "all" ? Bookmark : tab.kind === "archive" ? Archive : FolderGlyph;
          return (
            <button
              key={tab.kind === "folder" ? `f:${tab.name}` : tab.kind}
              type="button"
              aria-current={selected ? "page" : undefined}
              onClick={() => !selected && set({ tab, tags: [] })}
              className={cn(
                "flex shrink-0 cursor-pointer items-center gap-1.5 border-b-[3px] px-3 py-3 text-sm font-medium whitespace-nowrap",
                selected ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground",
              )}
            >
              <Icon className="size-4" aria-hidden="true" />
              <span className="max-w-40 truncate">{tabName(tab)}</span>
            </button>
          );
        })}
      </nav>
      <div role="group" aria-label={t("filter")} className="flex shrink-0 items-center gap-2 overflow-x-auto px-4 py-2 [scrollbar-width:none]">
        <button
          type="button"
          aria-pressed={filter.likedOnly}
          onClick={() => set({ likedOnly: !filter.likedOnly })}
          className={cn(
            "inline-flex h-8 shrink-0 cursor-pointer items-center gap-1.5 rounded-lg border px-3 text-sm",
            filter.likedOnly ? "border-transparent bg-accent font-medium text-accent-foreground" : "hover:bg-muted",
          )}
        >
          <Heart className={cn("size-4", filter.likedOnly && "fill-current")} aria-hidden="true" />
          {t("liked")}
        </button>
        {tags.length > 0 ? <span className="h-6 w-px shrink-0 bg-border" aria-hidden="true" /> : null}
        {tags.map((tag) => {
          const selected = filter.tags.some((x) => x.toLowerCase() === tag.toLowerCase());
          const others = filter.tags.filter((x) => x.toLowerCase() !== tag.toLowerCase());
          return (
            <FilterChip key={tag} selected={selected} onClick={() => set({ tags: selected ? others : [...others, tag] })}>
              {tag}
            </FilterChip>
          );
        })}
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-6" role="feed">
        {!loaded ? (
          <Skeletons />
        ) : items.length === 0 ? (
          <Empty
            icon={filter.tab.kind === "archive" ? Archive : BookOpen}
            title={plain && filter.tab.kind === "toread" ? t("nothingToRead") : plain && filter.tab.kind === "archive" ? t("nothingArchived") : t("savedNoneTitle")}
            text={plain && filter.tab.kind === "toread" ? t("savedEmptyBody") : undefined}
          />
        ) : (
          <>
            <p className="px-3 pt-3 pb-2 text-xs text-muted-foreground">{plural("savedCount", items.length)}</p>
            {filter.tab.kind === "folder" ? (
              <ul className="columns-[11rem] gap-2 px-1">
                {items.map((item) => {
                  const article = itemArticle(item, library);
                  return (
                    <li key={item.key} className="mb-2 break-inside-avoid">
                      <NoteCard item={item} feed={sourceOf(item, library)} open={openKey === articleKey(article)} onOpen={() => onOpen(article)} />
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
                      <Row article={article} feed={sourceOf(item, library)} open={openKey === articleKey(article)} onOpen={() => onOpen(article)} noReadState />
                    </li>
                  );
                })}
              </ul>
            )}
          </>
        )}
      </div>
    </section>
  );
}

/** A tab's name: To read, All, Archive or the folder's own. */
function tabName(tab: SavedTab): string {
  switch (tab.kind) {
    case "toread":
      return t("toRead");
    case "all":
      return t("all");
    case "archive":
      return t("archiveTab");
    case "folder":
      return tab.name;
  }
}

/** The type filter: a filter button next to search, with All types, Articles, Videos and Audio. */
function TypeFilter({ type, onChange }: { type: SavedType | null; onChange: (type: SavedType | null) => void }) {
  const [open, setOpen] = useState(false);
  const options: [SavedType | null, string][] = [
    [null, t("typeAll")],
    ["article", t("typeArticles")],
    ["video", t("typeVideos")],
    ["audio", t("typeAudio")],
  ];
  return (
    <div className="relative">
      <Button
        variant="ghost"
        size="icon"
        onClick={() => setOpen(!open)}
        aria-expanded={open}
        aria-label={t("typeFilter")}
        title={t("typeFilter")}
        className={cn(type !== null && "text-primary")}
      >
        <ListFilter />
      </Button>
      {open ? (
        <>
          <div className="fixed inset-0 z-20" onClick={() => setOpen(false)} aria-hidden="true" />
          <div role="radiogroup" aria-label={t("typeFilter")} className="absolute right-0 z-30 mt-1 w-44 rounded-xl border bg-card py-1 shadow-lg">
            {options.map(([value, label]) => (
              <button
                key={value ?? "all"}
                type="button"
                role="radio"
                aria-checked={type === value}
                onClick={() => {
                  setOpen(false);
                  onChange(value);
                }}
                className="flex w-full cursor-pointer items-center gap-3 px-3 py-2 text-left text-sm hover:bg-muted"
              >
                <span className={cn("size-4 rounded-full border-2", type === value ? "border-primary bg-primary shadow-[inset_0_0_0_2px_var(--color-card)]" : "border-muted-foreground")} />
                {label}
              </button>
            ))}
          </div>
        </>
      ) : null}
    </div>
  );
}

/**
 * A saved article in a folder, as a note (Noter's cards): the picture on top, the title, your note
 * (else the first highlight, quoted, else the description), then the site, tags, highlights and a
 * heart when liked.
 */
function NoteCard({ item, feed, open, onOpen }: { item: Item; feed: Feed | undefined; open: boolean; onOpen: () => void }) {
  const { settings } = useApp();
  const body = cardBody(item);
  const image = settings.showImages ? item.imageUrl : null;
  const highlights = item.highlights.length;
  return (
    <button
      type="button"
      onClick={onOpen}
      aria-current={open ? "true" : undefined}
      className={cn("flex w-full cursor-pointer flex-col overflow-hidden rounded-xl border text-left", open ? "bg-accent" : "bg-background hover:bg-muted")}
    >
      {image ? <img src={image} alt="" loading="lazy" referrerPolicy="no-referrer" className="h-24 w-full bg-muted object-cover" onError={(e) => (e.currentTarget.style.display = "none")} /> : null}
      <div className="flex flex-col gap-2 px-4 py-3">
        <h3 className="line-clamp-3 font-serif leading-snug font-medium">{item.title}</h3>
        {body?.kind === "quote" ? (
          <p className="flex gap-2 text-sm">
            <Quote className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
            <span className="line-clamp-4 font-serif">{body.text}</span>
          </p>
        ) : body ? (
          <p className="line-clamp-[10] text-sm whitespace-pre-line text-muted-foreground">{body.text}</p>
        ) : null}
        <div className="flex flex-wrap items-center gap-x-1.5 gap-y-1 text-xs">
          <span className="flex min-w-0 items-center gap-1 text-muted-foreground">
            {feed ? <FeedIcon feed={feed} className="size-4" /> : null}
            <span className="truncate">{hostOf(item.url)}</span>
          </span>
          {item.tags.map((tag) => (
            <span key={tag} className="font-medium text-primary">
              #{tag}
            </span>
          ))}
          {highlights > 0 ? (
            <span className="flex items-center gap-0.5 text-muted-foreground" aria-label={plural("highlightsCount", highlights)}>
              <Highlighter className="size-3.5" aria-hidden="true" />
              {highlights}
            </span>
          ) : null}
          {item.liked ? <Heart className="size-3.5 fill-current text-primary" aria-label={t("liked")} /> : null}
        </div>
      </div>
    </button>
  );
}

/** Search everything (feeds and saved), at the top of both lists like on Android. */
function SearchButton({ onClick }: { onClick: () => void }) {
  return (
    <Button variant="ghost" size="icon" onClick={onClick} aria-label={t("search")} title={t("search") + " (/)"}>
      <Search />
    </Button>
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
}: {
  article: Article;
  feed: Feed | undefined;
  open: boolean;
  onOpen: () => void;
  noReadState?: boolean;
  label?: string;
  onToggleRead?: () => void;
}) {
  const row = <RowButton article={article} feed={feed} open={open} onOpen={onOpen} noReadState={noReadState} label={label} />;
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
        className="absolute top-1.5 right-1.5 flex size-8 cursor-pointer items-center justify-center rounded-full bg-card text-muted-foreground opacity-0 shadow-sm transition-opacity group-hover:opacity-100 hover:text-foreground focus-visible:opacity-100 [&_svg]:size-4"
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
}: {
  article: Article;
  feed: Feed | undefined;
  open: boolean;
  onOpen: () => void;
  noReadState?: boolean;
  label?: string;
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
        {feed || label ? (
          <div className="mb-1 flex items-center gap-1.5 text-xs font-medium text-primary">
            {feed ? (
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
