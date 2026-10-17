import { ALargeSmall, ArrowLeft, BookmarkPlus, Circle, CircleCheck, ExternalLink, FileText, Share2 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { app, useApp } from "@/lib/app";
import { extractArticle, needsFullText } from "@/lib/fullArticle";
import { cached, cachedNow, keep } from "@/lib/fullArticleStore";
import { formatDateTime, t } from "@/lib/i18n";
import { problemText } from "@/lib/instapaperText";
import { articleKey, sortTime, type Article } from "@/lib/library";
import { fetchViaProxy } from "@/lib/proxy";
import { sanitizeArticle } from "@/lib/sanitize";
import { FeedIcon } from "./FeedIcon";
import { pageClass, textStyle, useAppDark } from "./readerPage";
import { ReaderStyleControls } from "./ReaderStyleControls";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { Dialog } from "./ui/dialog";

/** The full article for one address: loaded, or failed (no answer yet means it is loading). */
type Full = { link: string; html: string | null };

/** The parent gives each article its own key, so this state starts fresh for every article. */
export function ArticleView({ article, onBack, onMessage }: { article: Article; onBack: () => void; onMessage: (text: string) => void }) {
  const { library, settings, instapaper } = useApp();
  const [saving, setSaving] = useState(false);
  const feed = library.feeds.find((f) => f.url === article.feedUrl);
  const current = library.articles.find((a) => articleKey(a) === articleKey(article)) ?? article;
  const link = article.link;
  const canLoad = !!link;
  const [useFull, setUseFull] = useState(() => settings.fullArticles && canLoad && needsFullText(article.html, link));
  const [full, setFull] = useState<Full | null>(() => {
    const known = link ? cachedNow(link) : null;
    return link && known ? { link, html: known } : null;
  });
  const [textOpen, setTextOpen] = useState(false);
  const appDark = useAppDark(settings.theme);
  const style = settings.readerStyle;

  const loaded = full && full.link === link ? full : null;
  useEffect(() => {
    if (!useFull || !link || loaded) return;
    let cancelled = false;
    (async () => {
      let html = await cached(link);
      if (!html) {
        const page = await fetchViaProxy(link);
        html = extractArticle(page.text, page.url);
        if (html) void keep(link, html);
      }
      if (!cancelled) setFull({ link, html });
    })().catch(() => !cancelled && setFull({ link, html: null }));
    return () => {
      cancelled = true;
    };
  }, [useFull, link, loaded]);
  const fullHtml = useFull ? loaded?.html : undefined;

  const base = article.link ?? feed?.siteUrl ?? article.feedUrl;
  const html = useMemo(
    () => sanitizeArticle(fullHtml ?? article.html, base),
    [fullHtml, article.html, base],
  );
  const showHero = article.imageUrl && !html.includes(article.imageUrl);

  const share = async () => {
    if (!article.link) return;
    if (navigator.share) {
      await navigator.share({ title: article.title, url: article.link }).catch(() => undefined);
    } else {
      await navigator.clipboard.writeText(article.link);
      onMessage(t("linkCopied"));
    }
  };

  const saveToInstapaper = async () => {
    const address = article.link;
    if (!address || saving) return;
    setSaving(true);
    try {
      await app.withInstapaper((api) => api.save(address, article.title));
      onMessage(t("instapaperSaved"));
    } catch (e) {
      onMessage(problemText(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <article className="flex h-full min-w-0 flex-col" aria-label={article.title}>
      <div className="flex items-center gap-1 border-b px-2 py-2">
        <Button variant="ghost" size="icon" onClick={onBack} aria-label={t("back")} className="lg:hidden">
          <ArrowLeft />
        </Button>
        <div className="flex-1" />
        <Button variant="ghost" size="icon" onClick={() => setTextOpen(true)} aria-label={t("textSettings")} title={t("textSettings")}>
          <ALargeSmall />
        </Button>
        {canLoad ? (
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
        <Button
          variant="ghost"
          size="icon"
          onClick={() => app.setRead(new Set([articleKey(current)]), !current.read)}
          aria-label={current.read ? t("markUnread") : t("markRead")}
          title={(current.read ? t("markUnread") : t("markRead")) + " (m)"}
        >
          {current.read ? <Circle /> : <CircleCheck />}
        </Button>
        {article.link ? (
          <>
            {instapaper ? (
              <Button variant="ghost" size="icon" onClick={() => void saveToInstapaper()} disabled={saving} aria-label={t("saveToInstapaper")} title={t("saveToInstapaper")}>
                <BookmarkPlus />
              </Button>
            ) : null}
            <Button variant="ghost" size="icon" onClick={() => void share()} aria-label={t("share")} title={t("share")}>
              <Share2 />
            </Button>
            <Button variant="ghost" size="icon" asChild>
              <a href={article.link} target="_blank" rel="noopener noreferrer" aria-label={t("openWebsite")} title={t("openWebsite") + " (o)"}>
                <ExternalLink />
              </a>
            </Button>
          </>
        ) : null}
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
          {showHero ? <img src={article.imageUrl!} alt="" referrerPolicy="no-referrer" className="mt-6 w-full rounded-xl bg-muted object-cover" onError={(e) => (e.currentTarget.style.display = "none")} /> : null}
          {useFull && !loaded ? <p className="mt-6 text-sm text-muted-foreground">{t("loadingFull")}</p> : null}
          {useFull && loaded && !loaded.html ? <p className="mt-6 text-sm text-muted-foreground">{t("fullFailed")}</p> : null}
          <div className="article mt-6" style={textStyle(style)} dangerouslySetInnerHTML={{ __html: html }} />
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
    </article>
  );
}
