import { ALargeSmall, ArrowLeft, Circle, CircleCheck, EllipsisVertical, ExternalLink, FileText, Trash2 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { app, useApp } from "@/lib/app";
import { extractArticle, needsFullText, wantsFullText } from "@/lib/fullArticle";
import { cached, cachedNow, keep } from "@/lib/fullArticleStore";
import { formatDateTime, t } from "@/lib/i18n";
import { itemForArticle, type Highlight } from "@/lib/items";
import { articleKey, sortTime, type Article } from "@/lib/library";
import { hostOf, isItemArticle, siteOf, sourceOf } from "@/lib/saved";
import { FetchError, fetchViaProxy } from "@/lib/proxy";
import { resolveText } from "@/lib/sharedText";
import { sanitizeArticle } from "@/lib/sanitize";
import { FeedIcon } from "./FeedIcon";
import { HighlightDialog, HighlightedText, HighlightsDialog } from "./Highlights";
import { useAnchors } from "./useAnchors";
import { LibraryBar, RemoveDialog } from "./SavedActions";
import { pageClass, textStyle, useAppDark } from "./readerPage";
import { ReaderStyleControls } from "./ReaderStyleControls";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { Dialog } from "./ui/dialog";
import { useEscape } from "./ui/useEscape";

/** The full article for one address: loaded, or failed and why (no answer yet means it is loading). */
type Full = { link: string; html: string | null; blocked?: boolean };

/** The parent gives each article its own key, so this state starts fresh for every article. */
/** Kept the same between renders, so the page isn't drawn again for nothing. */
const NO_HIGHLIGHTS: Highlight[] = [];

export function ArticleView({
  article,
  onBack,
  onMessage,
}: {
  article: Article;
  onBack: () => void;
  onMessage: (text: string, action?: { label: string; run: () => void }) => void;
}) {
  const { library, settings, instapaperNotCopied } = useApp();
  // A saved article (also a feed article that was saved): the feed it came from (or its website)
  // above it, and Saved's actions instead of read state and Save.
  const fromSaved = isItemArticle(article);
  const found = fromSaved ? library.items.find((i) => i.key === article.id) : itemForArticle(library, article);
  const saved = found?.readingList ? found : null;
  const site = hostOf(article.link);
  const feed = fromSaved ? (saved ? sourceOf(saved, library) : site ? siteOf(site) : undefined) : library.feeds.find((f) => f.url === article.feedUrl);
  const current = library.articles.find((a) => articleKey(a) === articleKey(article)) ?? article;
  const link = article.link;
  const canLoad = !!link;
  const [useFull, setUseFull] = useState(() => canLoad && wantsFullText(settings.fullArticles, !fromSaved, needsFullText(article.html, link), !!saved));
  const [full, setFull] = useState<Full | null>(() => {
    const known = link ? cachedNow(link) : null;
    return link && known ? { link, html: known } : null;
  });
  const [textOpen, setTextOpen] = useState(false);
  const appDark = useAppDark(settings.theme);
  const style = settings.readerStyle;

  const loaded = full && full.link === link ? full : null;
  // A saved article shows the one text every device shows, stored in the account (sharedText.ts).
  const savedKey = saved?.key ?? null;
  useEffect(() => {
    if (!useFull || !link || loaded) return;
    let cancelled = false;
    (async () => {
      const html = await resolveText(
        savedKey !== null,
        () => (savedKey ? app.storedText(savedKey) : Promise.resolve(null)),
        async () => {
          const known = await cached(link);
          if (known) return known;
          const page = await fetchViaProxy(link, true);
          return extractArticle(page.text, page.url);
        },
        (own) => (savedKey ? app.storeText(savedKey, own) : Promise.resolve(null)),
      );
      if (html) void keep(link, html);
      if (!cancelled) setFull({ link, html });
    })().catch((e) => !cancelled && setFull({ link, html: null, blocked: e instanceof FetchError && e.kind === "blocked" }));
    return () => {
      cancelled = true;
    };
  }, [useFull, link, loaded, savedKey]);
  const fullHtml = useFull ? loaded?.html : undefined;

  const siteUrl = feed?.siteUrl ?? null;
  const base = article.link ?? siteUrl ?? article.feedUrl;
  const feedHtml = article.html;
  const showImages = settings.showImages;
  const html = useSanitized(fullHtml ?? feedHtml, base, showImages);
  const showHero = settings.showImages && article.imageUrl && !html.includes(article.imageUrl);

  const share = async () => {
    if (!article.link) return;
    if (navigator.share) {
      await navigator.share({ title: article.title, url: article.link }).catch(() => undefined);
    } else {
      // Copying can be refused (no permission): then the message shows the link to copy by hand.
      const copied = await navigator.clipboard.writeText(article.link).then(
        () => true,
        () => false,
      );
      onMessage(copied ? t("linkCopied") : article.link);
    }
  };

  // The dialog to file it (folder and tags) says it was saved.
  const saveArticle = () => article.link && app.saveArticle(article.link, article.title);
  const [menuOpen, setMenuOpen] = useState(false);
  useEscape(menuOpen, () => setMenuOpen(false));
  const [removeOpen, setRemoveOpen] = useState(false);
  // Highlights (#102): those of the article's item; one open, a new one waiting for its note, the list.
  const highlights = found?.highlights ?? NO_HIGHLIGHTS;
  // A saved article's highlights made in Instapaper's own apps come in when it opens (#164).
  const lookKey = found?.readingList ? found.key : null;
  useEffect(() => {
    if (lookKey) app.lookAtHighlights(lookKey);
  }, [lookKey]);
  const anchors = useAnchors(html, highlights);
  const [openHighlight, setOpenHighlight] = useState<string | null>(null);
  const [draft, setDraft] = useState<Highlight | null>(null);
  const [listOpen, setListOpen] = useState(false);
  const opened = openHighlight ? highlights.find((h) => h.id === openHighlight) : undefined;

  return (
    <article className="flex h-full min-w-0 flex-col" aria-label={article.title}>
      <div className="flex items-center gap-1 border-b px-2 py-2">
        <Button variant="ghost" size="icon" onClick={onBack} aria-label={t("back")} className="lg:hidden">
          <ArrowLeft />
        </Button>
        <div className="flex-1" />
        {/* This reading (#139): the full article, Aa and ⋮; the article in your library, and Share, are in the bar below. */}
        {/* Not for a saved article: it always shows the whole article (#208). */}
        {canLoad && !saved ? (
          <Button
            variant={useFull ? "tonal" : "ghost"}
            size="icon"
            onClick={() => setUseFull(!useFull)}
            aria-pressed={useFull}
            aria-label={useFull ? t("showFeedText") : t("showFull")}
            title={useFull ? t("showFeedText") : t("showFull")}
          >
            <FileText />
          </Button>
        ) : null}
        <Button variant="ghost" size="icon" onClick={() => setTextOpen(true)} aria-label={t("textSettings")} title={t("textSettings")}>
          <ALargeSmall />
        </Button>
        <div className="relative">
          <Button variant="ghost" size="icon" onClick={() => setMenuOpen(!menuOpen)} aria-expanded={menuOpen} aria-label={t("moreOptions")} title={t("moreOptions")}>
            <EllipsisVertical />
          </Button>
          {menuOpen ? (
            <>
              <div className="fixed inset-0 z-20" onClick={() => setMenuOpen(false)} aria-hidden="true" />
              <div role="menu" className="absolute right-0 z-30 mt-1 w-60 rounded-xl border bg-card py-1 shadow-lg">
                {article.link ? (
                  <a
                    role="menuitem"
                    href={article.link}
                    target="_blank"
                    rel="noopener noreferrer"
                    onClick={() => setMenuOpen(false)}
                    className="flex w-full items-center gap-3 px-3 py-2 text-sm hover:bg-muted [&_svg]:size-4 [&_svg]:text-muted-foreground"
                  >
                    <ExternalLink />
                    {t("openWebsite")}
                  </a>
                ) : null}
                {!fromSaved ? (
                  <MenuItem
                    icon={current.read ? <Circle /> : <CircleCheck />}
                    label={current.read ? t("markUnread") : t("markRead")}
                    onClick={() => app.toggleRead(current)}
                    close={() => setMenuOpen(false)}
                  />
                ) : null}
                {saved ? <MenuItem icon={<Trash2 />} label={t("savedRemove")} onClick={() => setRemoveOpen(true)} close={() => setMenuOpen(false)} /> : null}
              </div>
            </>
          ) : null}
        </div>
      </div>
      <Dialog open={textOpen} onOpenChange={setTextOpen} title={t("textSettings")} closeLabel={t("close")}>
        <ReaderStyleControls />
      </Dialog>
      <div className={cn("min-h-0 flex-1 overflow-y-auto bg-background text-foreground", pageClass(style, appDark))}>
        <div className="mx-auto max-w-2xl px-5 py-8">
          <div className="mb-3 flex items-center gap-2 text-sm text-muted-foreground">
            <FeedIcon feed={feed} className="size-5" />
            <span className="truncate font-medium text-primary">{feed?.title}</span>
            <span aria-hidden="true">·</span>
            <time dateTime={new Date(sortTime(article)).toISOString()}>{formatDateTime(sortTime(article))}</time>
          </div>
          <h1 className={cn("text-3xl leading-tight font-bold", style.font === "serif" ? "font-serif" : "font-sans")}>{article.title}</h1>
          {article.author ? <p className="mt-2 text-sm text-muted-foreground">{article.author}</p> : null}
          {saved && saved.tags.length > 0 ? (
            <ul className="mt-3 flex flex-wrap gap-1.5" aria-label={t("tags")}>
              {saved.tags.map((tag) => (
                <li key={tag} className="rounded-full bg-muted px-2.5 py-0.5 text-xs text-muted-foreground">
                  {tag}
                </li>
              ))}
            </ul>
          ) : null}
          {saved?.note ? <p className="mt-4 rounded-xl bg-muted px-4 py-3 text-sm whitespace-pre-line">{saved.note}</p> : null}
          {showHero ? <img src={article.imageUrl!} alt="" referrerPolicy="no-referrer" className="mt-6 w-full rounded-xl bg-muted object-cover" onError={(e) => (e.currentTarget.style.display = "none")} /> : null}
          {useFull && !loaded ? <p className="mt-6 text-sm text-muted-foreground">{t("loadingFull")}</p> : null}
          {useFull && loaded && !loaded.html ? (
            // Why the full article isn't here, with the way to read it all the same.
            <div className="mt-6 flex flex-wrap items-center gap-3 rounded-xl bg-muted px-4 py-3">
              <p className="min-w-0 flex-1 text-sm text-muted-foreground">{t(loaded.blocked ? "fullBlocked" : "fullFailed")}</p>
              {article.link ? (
                <Button variant="tonal" size="sm" asChild>
                  <a href={article.link} target="_blank" rel="noopener noreferrer">
                    <ExternalLink />
                    {t("readOnWebsite")}
                  </a>
                </Button>
              ) : null}
            </div>
          ) : null}
          <HighlightedText
            html={html}
            className="article mt-6"
            style={textStyle(style)}
            highlights={link ? highlights : null}
            onAdd={(h, withNote) => (withNote ? setDraft(h) : link && app.addHighlight(link, article.title, h))}
            onOpen={setOpenHighlight}
          />
          {article.link ? (
            <div className="mt-10 border-t pt-6">
              <Button variant="tonal" asChild>
                <a href={article.link} target="_blank" rel="noopener noreferrer">
                  <ExternalLink />
                  {t("readOnWebsite")}
                </a>
              </Button>
            </div>
          ) : null}
        </div>
      </div>
      <LibraryBar
        item={saved}
        onSave={article.link && !fromSaved ? saveArticle : null}
        onShare={article.link ? () => void share() : null}
        highlightCount={highlights.length}
        onHighlights={() => setListOpen(true)}
        onArchived={(undo) => {
          // Done with it, as on Android: back to the list, with Undo.
          onBack();
          onMessage(t("archived"), { label: t("undo"), run: undo });
        }}
      />
      {opened && found ? (
        <HighlightDialog
          key={opened.id}
          highlight={opened}
          anchor={anchors.get(opened.id)}
          onSave={(note) => note.trim() !== opened.note && app.setHighlightNote(found.key, opened.id, note)}
          onRemove={() => app.removeHighlight(found.key, opened.id)}
          onClose={() => setOpenHighlight(null)}
        />
      ) : null}
      {draft && link ? (
        <HighlightDialog highlight={draft} anchor={undefined} onSave={(note) => app.addHighlight(link, article.title, { ...draft, note: note.trim() })} onRemove={null} onClose={() => setDraft(null)} />
      ) : null}
      {listOpen ? (
        <HighlightsDialog
          highlights={highlights}
          anchors={anchors}
          notCopied={found ? instapaperNotCopied[found.key] : undefined}
          onOpen={(id) => {
            setListOpen(false);
            setOpenHighlight(id);
          }}
          onClose={() => setListOpen(false)}
        />
      ) : null}
      {saved ? <RemoveDialog item={saved} open={removeOpen} onClose={() => setRemoveOpen(false)} onRemoved={() => fromSaved && onBack()} /> : null}
    </article>
  );
}

function MenuItem({ icon, label, onClick, close }: { icon: React.ReactNode; label: string; onClick: () => void; close: () => void }) {
  return (
    <button
      type="button"
      role="menuitem"
      onClick={() => {
        close();
        onClick();
      }}
      className="flex w-full cursor-pointer items-center gap-3 px-3 py-2 text-left text-sm hover:bg-muted [&_svg]:size-4 [&_svg]:text-muted-foreground"
    >
      {icon}
      {label}
    </button>
  );
}

/** The article's HTML made safe to show, worked out again only when it changes. */
function useSanitized(html: string, base: string, showImages: boolean): string {
  return useMemo(() => sanitizeArticle(html, base, showImages), [html, base, showImages]);
}
