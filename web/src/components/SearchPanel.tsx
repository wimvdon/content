import { ChevronDown, Search, X } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import { articleKey, type Article } from "@/lib/library";
import { articleNote, itemArticle, noteOf, sourceOf, tabTags } from "@/lib/saved";
import { indexArticles, narrowed, noFilters, searchResults, showsNotes, type SearchFilter, type SearchFilters } from "@/lib/search";
import { Empty, NoteCard, Row } from "./ArticleList";
import { Button } from "./ui/button";
import { FilterChip } from "./ui/chip";
import { cn } from "./ui/cn";

/**
 * The Search tab (#171), in place of the list: a field, chips for All, Feeds or Saved and that
 * kind's filters (feeds: a folder or one feed; Saved: a folder, a tag and a type), and below the
 * matches, mixed and best first, each labelled Feed or Saved under All (like the Android app's
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
      <div role="radiogroup" aria-label={t("filter")} className="flex flex-wrap gap-2 px-4 pt-1 pb-3">
        {(
          [
            ["all", t("all")],
            ["feeds", t("feeds")],
            ["saved", t("saved")],
          ] as const
        ).map(([value, label]) => (
          <FilterChip key={value} role="radio" selected={filters.scope === value} onClick={() => setFilters({ ...noFilters, scope: value as SearchFilter })}>
            {label}
          </FilterChip>
        ))}
      </div>
      <Narrow filters={filters} onChange={setFilters} />
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

/** The chosen kind's filters, as chips that open a menu; a chosen one shows its value. */
function Narrow({ filters, onChange }: { filters: SearchFilters; onChange: (f: SearchFilters) => void }) {
  const { library } = useApp();
  const tags = useMemo(() => tabTags(library, { kind: "all" }), [library]);
  if (filters.scope === "all") return null;
  const chips =
    filters.scope === "feeds"
      ? [
          library.folders.length > 0 ? (
            <MenuChip
              key="folder"
              label={t("folder")}
              value={filters.feedFolderId}
              options={[[null, t("searchAnyFolder")], ...library.folders.map((f): [string, string] => [f.id, f.title])]}
              onPick={(feedFolderId) => onChange({ ...filters, feedFolderId, feedUrl: null })}
            />
          ) : null,
          <MenuChip
            key="feed"
            label={t("searchLabelFeed")}
            value={filters.feedUrl}
            options={[
              [null, t("searchAnyFeed")],
              ...library.feeds.filter((f) => filters.feedFolderId === null || f.folderId === filters.feedFolderId).map((f): [string, string] => [f.url, f.title]),
            ]}
            onPick={(feedUrl) => onChange({ ...filters, feedUrl })}
          />,
        ]
      : [
          tags.length > 0 ? (
            <MenuChip
              key="tag"
              label={t("folder")}
              value={filters.tag}
              options={[[null, t("searchAnyFolder")], ...tags.map((x): [string, string] => [x, x])]}
              onPick={(tag) => onChange({ ...filters, tag })}
            />
          ) : null,
        ];
  return <div className="flex flex-wrap gap-2 px-4 pb-3">{chips}</div>;
}

/**
 * A filter chip that opens a menu of `options` (value and name; the first is "any"). It is the
 * browser's own select, styled as a chip, so it works with the keyboard and screen readers.
 */
function MenuChip({ label, value, options, onPick }: { label: string; value: string | null; options: [string | null, string][]; onPick: (v: string | null) => void }) {
  const chosen = options.find(([v]) => v !== null && v === value)?.[1];
  return (
    <label
      className={cn(
        "relative inline-flex h-8 cursor-pointer items-center gap-1 rounded-lg border px-3 text-sm",
        chosen ? "border-transparent bg-accent font-medium text-accent-foreground" : "hover:bg-muted",
      )}
    >
      <span className="max-w-40 truncate">{chosen ?? label}</span>
      <ChevronDown className="size-4" aria-hidden="true" />
      <select
        aria-label={label}
        value={value ?? ""}
        onChange={(e) => onPick(e.target.value === "" ? null : e.target.value)}
        className="absolute inset-0 cursor-pointer opacity-0"
      >
        {options.map(([v, name]) => (
          <option key={v ?? ""} value={v ?? ""}>
            {name}
          </option>
        ))}
      </select>
    </label>
  );
}

/** What was typed and the filters, kept while Search is closed for this visit (like the Android app). */
let sessionQuery = "";
let sessionFilters: SearchFilters = noFilters;
