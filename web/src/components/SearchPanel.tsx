import { Check, ChevronDown, Search, X } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useApp } from "@/lib/app";
import { plural, t } from "@/lib/i18n";
import { articleKey, type Article } from "@/lib/library";
import { articleNote, itemArticle, noteOf, sourceOf } from "@/lib/saved";
import { topicIcons } from "@/lib/topics";
import { indexArticles, menuFeeds, narrowed, noFilters, searchResults, searchTopics, showsNotes, toggleFeed, withScope, withTopic, type SearchFilters } from "@/lib/search";
import { Empty, NoteCard, Row } from "./ArticleList";
import { FeedIcon } from "./FeedIcon";
import { FolderIcon } from "./FolderIcon";
import { useEscape } from "./ui/useEscape";
import { Button } from "./ui/button";
import { FilterChip } from "./ui/chip";
import { cn } from "./ui/cn";

/**
 * The Search tab (#171), in place of the list: a field, one row of chips (Feeds with its menu,
 * Saved, the topics), and below the matches, mixed and best first, each labelled Feed or Saved under All (like the Android app's
 * Search tab). With nothing typed it lists everything the filters let through, newest first. The
 * query and filters stay while an article is read, so the results are still there.
 */
export function SearchPanel({ openKey, onOpen, onClose }: { openKey: string | null; onOpen: (a: Article) => void; onClose: () => void }) {
  const { library } = useApp();
  const [query, setQuery] = useState(() => sessionQuery);
  const [filters, setFilters] = useState<SearchFilters>(() => sessionFilters);
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => input.current?.focus(), []);
  useEffect(() => {
    sessionQuery = query;
    sessionFilters = filters;
  }, [query, filters]);

  const index = useMemo(() => indexArticles(library), [library]);
  const feeds = useMemo(() => new Map(library.feeds.map((f) => [f.url, f])), [library.feeds]);
  const hits = useMemo(() => searchResults(query, library, index, filters), [index, library, query, filters]);
  const blank = query.trim() === "" && !narrowed(filters);
  const all = filters.scope === "all";

  return (
    <section className="flex h-full min-w-0 flex-col" aria-label={t("search")}>
      <header className="flex items-center gap-1 px-2 pt-2 pb-1">
        <Search className="mx-2 size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
        <input
          ref={input}
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => e.key === "Escape" && (query ? setQuery("") : onClose())}
          placeholder={t("searchHint")}
          aria-label={t("searchHint")}
          className="h-10 min-w-0 flex-1 bg-transparent px-2 text-base outline-none placeholder:text-muted-foreground [&::-webkit-search-cancel-button]:hidden"
        />
        {query ? (
          <Button
            variant="ghost"
            size="icon"
            onClick={() => {
              setQuery("");
              input.current?.focus();
            }}
            aria-label={t("clear")}
            title={t("clear")}
          >
            <X />
          </Button>
        ) : null}
      </header>
      <FilterRow filters={filters} onChange={setFilters} />
      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-6">
        {blank && hits.length === 0 ? (
          <Empty icon={Search} title={t("searchStartTitle")} text={t("searchStartBody")} />
        ) : hits.length === 0 ? (
          <Empty icon={Search} title={t("searchNoneTitle")} text={t("searchNoneBody")} />
        ) : showsNotes(filters.scope) ? (
          // All and Saved as notes, like Saved's folders; Feeds as rows, like the feeds.
          <ul className="columns-[11rem] gap-2 px-1 pt-1">
            {hits.map((hit) => {
              const article = hit.kind === "feed" ? hit.article : itemArticle(hit.item, library);
              return (
                <li key={articleKey(article)} className="mb-2 break-inside-avoid">
                  <NoteCard
                    note={hit.kind === "feed" ? articleNote(hit.article) : noteOf(hit.item)}
                    feed={hit.kind === "feed" ? feeds.get(hit.article.feedUrl) : sourceOf(hit.item, library)}
                    open={openKey === articleKey(article)}
                    onOpen={() => onOpen(article)}
                    link={article.link}
                    label={all ? (hit.kind === "feed" ? t("searchLabelFeed") : t("saved")) : undefined}
                  />
                </li>
              );
            })}
          </ul>
        ) : (
          <ul className="space-y-1">
            {hits.map((hit) => {
              if (hit.kind === "feed") {
                const a = hit.article;
                return (
                  <li key={articleKey(a)}>
                    <Row article={a} feed={feeds.get(a.feedUrl)} open={openKey === articleKey(a)} onOpen={() => onOpen(a)} label={all ? t("searchLabelFeed") : undefined} />
                  </li>
                );
              }
              const article = itemArticle(hit.item, library);
              return (
                <li key={articleKey(article)}>
                  <Row
                    article={article}
                    feed={sourceOf(hit.item, library)}
                    open={openKey === articleKey(article)}
                    onOpen={() => onOpen(article)}
                    noReadState
                    label={all ? t("saved") : undefined}
                  />
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </section>
  );
}

/**
 * The one chip row above the results, as on Android: Feeds (a menu: All feeds, or some feeds with
 * check boxes, and Clear), Saved (on or off), a divider, then the topics, one at a time.
 */
function FilterRow({ filters, onChange }: { filters: SearchFilters; onChange: (f: SearchFilters) => void }) {
  const { library } = useApp();
  const topics = useMemo(() => searchTopics(library, filters.scope, filters.topic), [library, filters.scope, filters.topic]);
  const icons = useMemo(() => topicIcons(library), [library]);
  const hasSaved = library.items.some((i) => i.readingList !== null);
  if (library.feeds.length === 0 && !hasSaved) return null;
  const saved = filters.scope === "saved";
  return (
    <div role="group" aria-label={t("filter")} className="flex shrink-0 items-center gap-2 overflow-x-auto px-4 pt-1 pb-3 [scrollbar-width:none]">
      {library.feeds.length > 0 ? <FeedsMenu filters={filters} onChange={onChange} /> : null}
      {hasSaved ? (
        <FilterChip selected={saved} onClick={() => onChange(withScope(filters, saved ? "all" : "saved"))}>
          {t("saved")}
        </FilterChip>
      ) : null}
      {topics.length > 0 ? <span className="h-6 w-px shrink-0 bg-border" aria-hidden="true" /> : null}
      {topics.map((topic) => {
        const on = filters.topic !== null && topic.toLowerCase() === filters.topic.toLowerCase();
        const icon = icons.get(topic.trim().toLowerCase());
        return (
          <FilterChip key={topic} selected={on} check={false} onClick={() => onChange(withTopic(filters, on ? null : topic, library))}>
            {icon ? <FolderIcon name={icon} className="size-4" /> : null}
            {topic}
          </FilterChip>
        );
      })}
    </div>
  );
}

/**
 * Feeds as a chip with a menu: All feeds, then every feed (of the chosen topic) with a check box, so
 * several can be picked; the menu stays open while ticking them. Clear shows both kinds again. The
 * chip says what is picked: Feeds, the one feed's name, or how many.
 */
function FeedsMenu({ filters, onChange }: { filters: SearchFilters; onChange: (f: SearchFilters) => void }) {
  const { library } = useApp();
  const [at, setAt] = useState<{ top: number; left: number } | null>(null);
  useEscape(at !== null, () => setAt(null));
  const on = filters.scope === "feeds";
  const picked = filters.feedUrls;
  const feeds = menuFeeds(library, filters.topic);
  const label =
    picked.length === 0
      ? t("feeds")
      : picked.length === 1
        ? (library.feeds.find((f) => f.url === picked[0])?.title ?? t("feeds"))
        : plural("feedsCount", picked.length);
  const item = "flex w-full cursor-pointer items-center gap-3 px-3 py-2 text-left text-sm hover:bg-muted";
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
        className={cn("inline-flex h-8 shrink-0 cursor-pointer items-center gap-1 rounded-lg border pr-2 pl-3 text-sm", on ? "border-transparent bg-accent font-medium text-accent-foreground" : "hover:bg-muted")}
      >
        {on ? <Check className="size-4" aria-hidden="true" /> : null}
        <span className="max-w-40 truncate">{label}</span>
        <ChevronDown className="size-4" aria-hidden="true" />
      </button>
      {at ? (
        <>
          <div className="fixed inset-0 z-20" onClick={() => setAt(null)} aria-hidden="true" />
          <div role="menu" aria-label={t("feeds")} style={{ top: at.top, left: at.left }} className="fixed z-30 max-h-[60vh] w-72 overflow-y-auto rounded-xl border bg-card py-1 shadow-lg">
            <button
              type="button"
              role="menuitemradio"
              aria-checked={on && picked.length === 0}
              onClick={() => {
                setAt(null);
                onChange(withScope(filters, "feeds"));
              }}
              className={item}
            >
              <span className={cn("size-4 shrink-0 rounded-full border-2", on && picked.length === 0 ? "border-primary bg-primary shadow-[inset_0_0_0_2px_var(--color-card)]" : "border-muted-foreground")} />
              {t("allFeeds")}
            </button>
            <div className="my-1 h-px bg-border" />
            {feeds.map((feed) => {
              const ticked = picked.includes(feed.url);
              return (
                <button key={feed.url} type="button" role="menuitemcheckbox" aria-checked={ticked} onClick={() => onChange(toggleFeed(filters, feed.url))} className={item}>
                  <span className={cn("flex size-4 shrink-0 items-center justify-center rounded-sm border-2", ticked ? "border-primary bg-primary text-primary-foreground" : "border-muted-foreground")}>
                    {ticked ? <Check className="size-3" aria-hidden="true" /> : null}
                  </span>
                  <span className="min-w-0 flex-1 truncate">{feed.title}</span>
                  <FeedIcon feed={feed} className="size-4" />
                </button>
              );
            })}
            {on ? (
              <>
                <div className="my-1 h-px bg-border" />
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    setAt(null);
                    onChange(withScope(filters, "all"));
                  }}
                  className={item}
                >
                  <X className="size-4" aria-hidden="true" />
                  {t("clear")}
                </button>
              </>
            ) : null}
          </div>
        </>
      ) : null}
    </>
  );
}

/** What was typed and the filters, kept while Search is closed for this visit (like the Android app). */
let sessionQuery = "";
let sessionFilters: SearchFilters = noFilters;
