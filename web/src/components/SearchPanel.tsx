import { ArrowLeft, Search, X } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import { articleKey, type Article } from "@/lib/library";
import { itemArticle, sourceOf } from "@/lib/saved";
import { indexArticles, mix, searchArticles, searchItems, type SearchFilter } from "@/lib/search";
import { Empty, Row } from "./ArticleList";
import { Button } from "./ui/button";
import { FilterChip } from "./ui/chip";

/**
 * Search, in place of the list: a field, chips for All, Feeds or Saved, and the matches below, mixed
 * and best first, each labelled Feed or Saved (like the Android app's search screen). The query
 * stays while an article is read, so the results are still there.
 */
export function SearchPanel({ openKey, onOpen, onClose }: { openKey: string | null; onOpen: (a: Article) => void; onClose: () => void }) {
  const { library } = useApp();
  const [query, setQuery] = useState(() => sessionQuery);
  const [filter, setFilter] = useState<SearchFilter>("all");
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => input.current?.focus(), []);
  useEffect(() => {
    sessionQuery = query;
  }, [query]);

  const index = useMemo(() => indexArticles(library), [library]);
  const feeds = useMemo(() => new Map(library.feeds.map((f) => [f.url, f])), [library.feeds]);
  const hits = useMemo(
    () => mix(query, searchArticles(index, query), searchItems(library.items, query), filter),
    [index, library.items, query, filter],
  );
  const blank = query.trim() === "";

  return (
    <section className="flex h-full min-w-0 flex-col" aria-label={t("search")}>
      <header className="flex items-center gap-1 px-2 pt-2 pb-1">
        <Button variant="ghost" size="icon" onClick={onClose} aria-label={t("back")} title={t("back")}>
          <ArrowLeft />
        </Button>
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
      <div role="radiogroup" aria-label={t("filter")} className="flex flex-wrap gap-2 px-4 pt-1 pb-3">
        {(
          [
            ["all", t("all")],
            ["feeds", t("feeds")],
            ["saved", t("saved")],
          ] as const
        ).map(([value, label]) => (
          <FilterChip key={value} role="radio" selected={filter === value} onClick={() => setFilter(value)}>
            {label}
          </FilterChip>
        ))}
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-6">
        {blank ? (
          <Empty icon={Search} title={t("searchStartTitle")} text={t("searchStartBody")} />
        ) : hits.length === 0 ? (
          <Empty icon={Search} title={t("searchNoneTitle")} text={t("searchNoneBody")} />
        ) : (
          <ul className="space-y-1">
            {hits.map((hit) => {
              if (hit.kind === "feed") {
                const a = hit.article;
                return (
                  <li key={articleKey(a)}>
                    <Row article={a} feed={feeds.get(a.feedUrl)} open={openKey === articleKey(a)} onOpen={() => onOpen(a)} label={t("searchLabelFeed")} />
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
                    label={t("saved")}
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

/** What was typed, kept while Search is closed for this visit (like the Android app). */
let sessionQuery = "";
