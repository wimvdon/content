import { Bookmark, CheckCheck, Menu, Newspaper, RefreshCw, Rss, type LucideIcon } from "lucide-react";
import { useMemo, useRef } from "react";
import { app, useApp } from "@/lib/app";
import { snippet } from "@/lib/html";
import { dayLabel, plural, t } from "@/lib/i18n";
import type { View } from "@/lib/instapaper";
import { bookmarkArticle, hostOf, viewTitle } from "@/lib/instapaperLibrary";
import { articleKey, articlesFor, sortTime, unreadKeys, type Article, type Feed } from "@/lib/library";
import { scrolledPast } from "@/lib/scrollRead";
import { FeedIcon } from "./FeedIcon";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { Segmented } from "./ui/segmented";

type ListProps = {
  openKey: string | null;
  onOpen: (a: Article) => void;
  onMenu: () => void;
  onAddFeed: () => void;
  onImport: () => void;
  onUndo: (keys: Set<string>) => void;
};

/** The articles of the chosen feed, folder or All articles, or of an Instapaper list. */
export function ArticleList(props: ListProps) {
  const { selection } = useApp();
  return selection.kind === "instapaper" ? <InstapaperArticles view={selection.view} openKey={props.openKey} onOpen={props.onOpen} onMenu={props.onMenu} /> : <FeedArticles {...props} />;
}

function FeedArticles({ openKey, onOpen, onMenu, onAddFeed, onImport, onUndo }: ListProps) {
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

  // Mark as read on scroll: once per frame, the unread articles that scrolled off the top.
  const frame = useRef(0);
  const onScroll = (e: React.UIEvent<HTMLDivElement>) => {
    if (!settings.markReadOnScroll || frame.current) return;
    const list = e.currentTarget;
    frame.current = requestAnimationFrame(() => {
      frame.current = 0;
      const top = list.getBoundingClientRect().top;
      const rows = Array.from(list.querySelectorAll<HTMLElement>("[data-key]"));
      const first = rows.findIndex((row) => row.getBoundingClientRect().bottom > top);
      app.markScrolledPast(scrolledPast(articles, first === -1 ? rows.length : first));
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
          <Segmented
            label={t("filter")}
            value={settings.unreadOnly ? "unread" : "all"}
            onChange={(v) => app.setUnreadOnly(v === "unread")}
            options={[
              { value: "unread", label: t("unread") },
              { value: "all", label: t("all") },
            ]}
          />
          {problems.size > 0 ? <span className="text-xs text-destructive">{plural("feedsFailed", problems.size)}</span> : null}
        </div>
      ) : null}
      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-6" role="feed" aria-busy={refreshing} onScroll={onScroll}>
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
                    <Row article={a} feed={showFeedName ? feeds.get(a.feedUrl) : undefined} open={openKey === articleKey(a)} onOpen={() => onOpen(a)} />
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
 * An Instapaper list: the saved articles with the same rows as feed articles, without read state
 * (archiving is an action in the reader), newest saved first, a page at a time.
 */
function InstapaperArticles({ view, openKey, onOpen, onMenu }: { view: View; openKey: string | null; onOpen: (a: Article) => void; onMenu: () => void }) {
  const { instapaperList: list, instapaperFolders } = useApp();
  const title = viewTitle(view, instapaperFolders, { home: t("unread"), liked: t("instapaperLiked"), archive: t("instapaperArchiveList") });
  const items = list.items;
  return (
    <section className="flex h-full min-w-0 flex-col" aria-label={title}>
      <header className="flex items-center gap-1 px-2 pt-2 pb-1">
        <Button variant="ghost" size="icon" className="lg:hidden" onClick={onMenu} aria-label={t("openFeeds")}>
          <Menu />
        </Button>
        <div className="min-w-0 flex-1 px-2">
          <h1 className="truncate text-xl font-semibold">{title}</h1>
          <p className="text-xs text-muted-foreground">{t("instapaper")}</p>
        </div>
        <Button variant="ghost" size="icon" onClick={() => void app.refresh()} disabled={list.loading} aria-label={t("refresh")} title={t("refresh") + " (r)"}>
          <RefreshCw className={cn(list.loading && "animate-spin")} />
        </Button>
      </header>
      <div className="min-h-0 flex-1 overflow-y-auto px-2 pt-2 pb-6" role="feed" aria-busy={list.loading}>
        {list.problem ? (
          <p role="alert" className="px-3 pb-2 text-sm text-destructive">
            {list.problem}
          </p>
        ) : null}
        {list.loading && items.length === 0 ? (
          <Skeletons />
        ) : items.length === 0 && !list.problem ? (
          <Empty icon={Bookmark} title={title} text={t("instapaperEmpty")} />
        ) : (
          <ul className="space-y-1">
            {items.map((b) => {
              const article = bookmarkArticle(b);
              const host = hostOf(b.url);
              return (
                <li key={b.id}>
                  <Row article={article} feed={host ? siteOf(host) : undefined} open={openKey === articleKey(article)} onOpen={() => onOpen(article)} noReadState />
                </li>
              );
            })}
          </ul>
        )}
        {items.length > 0 && items.length < list.total ? (
          <div className="flex justify-center pt-3">
            <Button variant="tonal" onClick={() => void app.loadInstapaperList(view, true)} disabled={list.loading}>
              {t("instapaperLoadMore")}
            </Button>
          </div>
        ) : null}
      </div>
    </section>
  );
}

/** The website of a saved article, shown where a feed's name goes (with its first letter as icon). */
const siteOf = (host: string): Feed => ({ url: host, title: host, siteUrl: null, folderId: null, iconUrl: null, renamed: false });

function Row({ article, feed, open, onOpen, noReadState }: { article: Article; feed: Feed | undefined; open: boolean; onOpen: () => void; noReadState?: boolean }) {
  const unread = !article.read && !noReadState;
  const text = useMemo(() => snippet(article.html, 160), [article.html]);
  return (
    <button
      type="button"
      onClick={onOpen}
      data-key={articleKey(article)}
      aria-current={open ? "true" : undefined}
      className={cn(
        "flex w-full cursor-pointer gap-3 rounded-xl p-3 text-left transition-colors",
        open ? "bg-accent" : unread || noReadState ? "bg-card hover:bg-muted" : "hover:bg-muted",
      )}
    >
      <div className="min-w-0 flex-1">
        {feed ? (
          <div className="mb-1 flex items-center gap-1.5 text-xs font-medium text-primary">
            <FeedIcon feed={feed} className="size-4" />
            <span className="truncate">{feed.title}</span>
          </div>
        ) : null}
        <div className="flex items-start gap-2">
          {unread ? <span className="mt-2 size-2 shrink-0 rounded-full bg-unread" aria-label={t("unread")} /> : null}
          <h3 className={cn("line-clamp-3 text-[0.95rem] leading-snug", unread || noReadState ? "font-semibold" : "text-muted-foreground")}>{article.title}</h3>
        </div>
        {text ? <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">{text}</p> : null}
      </div>
      {article.imageUrl ? (
        <img src={article.imageUrl} alt="" loading="lazy" referrerPolicy="no-referrer" className="size-18 shrink-0 rounded-lg bg-muted object-cover" onError={(e) => (e.currentTarget.style.display = "none")} />
      ) : null}
    </button>
  );
}

/** Same layout as the Android app's EmptyState: icon in a circle, title, one sentence, buttons. */
function Empty({
  icon: Icon,
  title,
  text,
  children,
}: {
  icon: LucideIcon;
  title: string;
  text: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="mx-auto flex max-w-sm flex-col items-center gap-3 px-6 py-16 text-center">
      <div className="mb-1 flex size-18 items-center justify-center rounded-full bg-accent text-accent-foreground">
        <Icon className="size-9" aria-hidden="true" />
      </div>
      <h2 className="text-lg font-semibold">{title}</h2>
      <p className="text-sm text-muted-foreground">{text}</p>
      <div className="flex flex-wrap justify-center gap-2 pt-2">{children}</div>
    </div>
  );
}

function Skeletons() {
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
