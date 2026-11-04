import { Search, X } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import { articleKey, type Article } from "@/lib/library";
import { articleNote, itemArticle, noteOf, sourceOf } from "@/lib/saved";
import { topicIcons } from "@/lib/topics";
import { indexArticles, menuFeeds, narrowed, noFilters, searchResults, searchTopics, showsNotes, toggleFeed, withScope, withTopic, type SearchFilters } from "@/lib/search";
import { Empty, NoteCard, Row } from "./ArticleList";
import { FeedIcon } from "./FeedIcon";
import { FolderIcon } from "./FolderIcon";
import { Button } from "./ui/button";
import { FilterChip } from "./ui/chip";

/**
 * The Search tab (#171), in place of the list: a field, chips (Feeds, Saved, the tags; with Feeds
 * on, a second row of the feeds), and below the matches, mixed and best first, each labelled Feed or Saved under All (like the Android app's
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
 * The chips above the results, as on Android: Feeds and Saved (on or off), a divider, then the tags,
 * one at a time. With Feeds on, a second row has every feed (of the chosen tag) as a chip with its
 * icon, instead of a menu: clicking some searches only those, none means every feed.
 */
function FilterRow({ filters, onChange }: { filters: SearchFilters; onChange: (f: SearchFilters) => void }) {
  const { library } = useApp();
  const topics = useMemo(() => searchTopics(library, filters.scope, filters.topic), [library, filters.scope, filters.topic]);
  const icons = useMemo(() => topicIcons(library), [library]);
  const hasSaved = library.items.some((i) => i.readingList !== null);
  if (library.feeds.length === 0 && !hasSaved) return null;
  const saved = filters.scope === "saved";
  const feedsOn = filters.scope === "feeds";
  const feeds = feedsOn ? menuFeeds(library, filters.topic) : [];
  const row = "flex shrink-0 items-center gap-2 overflow-x-auto px-4 [scrollbar-width:none]";
  return (
    <div role="group" aria-label={t("filter")} className="space-y-2 pt-1 pb-3">
      <div className={row}>
        {library.feeds.length > 0 ? (
          <FilterChip selected={feedsOn} onClick={() => onChange(withScope(filters, feedsOn ? "all" : "feeds"))}>
            {t("feeds")}
          </FilterChip>
        ) : null}
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
            <FilterChip key={topic} selected={on} onClick={() => onChange(withTopic(filters, on ? null : topic, library))}>
              {icon ? <FolderIcon name={icon} className="size-4" /> : null}
              {topic}
            </FilterChip>
          );
        })}
      </div>
      {feeds.length > 0 ? (
        <div role="group" aria-label={t("feeds")} className={row}>
          {feeds.map((feed) => (
            <FilterChip key={feed.url} selected={filters.feedUrls.includes(feed.url)} onClick={() => onChange(toggleFeed(filters, feed.url))}>
              <FeedIcon feed={feed} className="size-4" />
              <span className="max-w-40 truncate">{feed.title}</span>
            </FilterChip>
          ))}
        </div>
      ) : null}
    </div>
  );
}

/** What was typed and the filters, kept while Search is closed for this visit (like the Android app). */
let sessionQuery = "";
let sessionFilters: SearchFilters = noFilters;
