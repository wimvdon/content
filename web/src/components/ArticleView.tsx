import { ArrowLeft, Circle, CircleCheck, ExternalLink, FileText, Share2 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { app, useApp } from "@/lib/app";
import { extractArticle, isSummary } from "@/lib/fullArticle";
import { formatDateTime, t } from "@/lib/i18n";
import { articleKey, sortTime, type Article } from "@/lib/library";
import { fetchViaProxy } from "@/lib/proxy";
import { sanitizeArticle } from "@/lib/sanitize";
import { FeedIcon } from "./FeedIcon";
import { Button } from "./ui/button";

type Full = { state: "idle" } | { state: "loading" } | { state: "done"; html: string } | { state: "failed" };

/** Full texts loaded in this session, so going back to an article doesn't load it again. */
const fullCache = new Map<string, string>();

export function ArticleView({ article, onBack, onMessage }: { article: Article; onBack: () => void; onMessage: (text: string) => void }) {
  const { library, settings } = useApp();
  const feed = library.feeds.find((f) => f.url === article.feedUrl);
  const current = library.articles.find((a) => articleKey(a) === articleKey(article)) ?? article;
  const wantsFull = settings.fullArticles && !!article.link && isSummary(article.html);
  const [useFull, setUseFull] = useState(wantsFull);
  const [full, setFull] = useState<Full>({ state: "idle" });

  useEffect(() => {
    setUseFull(wantsFull);
    setFull(article.link && fullCache.has(article.link) ? { state: "done", html: fullCache.get(article.link)! } : { state: "idle" });
  }, [article, wantsFull]);

  useEffect(() => {
    if (!useFull || full.state !== "idle" || !article.link) return;
    const link = article.link;
    let cancelled = false;
    setFull({ state: "loading" });
    fetchViaProxy(settings.proxyUrl, link)
      .then((page) => {
        const html = extractArticle(page.text, page.url);
        if (cancelled) return;
        if (html) {
          fullCache.set(link, html);
          setFull({ state: "done", html });
        } else setFull({ state: "failed" });
      })
      .catch(() => !cancelled && setFull({ state: "failed" }));
    return () => {
      cancelled = true;
    };
  }, [useFull, full.state, article.link, settings.proxyUrl]);

  const base = article.link ?? feed?.siteUrl ?? article.feedUrl;
  const html = useMemo(
    () => sanitizeArticle(useFull && full.state === "done" ? full.html : article.html, base),
    [useFull, full, article.html, base],
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

  return (
    <article className="flex h-full min-w-0 flex-col" aria-label={article.title}>
      <div className="flex items-center gap-1 border-b px-2 py-2">
        <Button variant="ghost" size="icon" onClick={onBack} aria-label={t("back")} className="lg:hidden">
          <ArrowLeft />
        </Button>
        <div className="flex-1" />
        {article.link && settings.proxyUrl ? (
          <Button variant={useFull ? "tonal" : "ghost"} size="icon" onClick={() => setUseFull(!useFull)} aria-pressed={useFull} aria-label={t("fullArticle")} title={t("fullArticle")}>
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
      <div className="min-h-0 flex-1 overflow-y-auto">
        <div className="mx-auto max-w-2xl px-5 py-8">
          <div className="mb-3 flex items-center gap-2 text-sm text-muted-foreground">
            <FeedIcon feed={feed} className="size-5" />
            <span className="truncate font-medium text-primary">{feed?.title}</span>
            <span aria-hidden="true">·</span>
            <time dateTime={new Date(sortTime(article)).toISOString()}>{formatDateTime(sortTime(article))}</time>
          </div>
          <h1 className="font-serif text-3xl leading-tight font-bold">{article.title}</h1>
          {article.author ? <p className="mt-2 text-sm text-muted-foreground">{article.author}</p> : null}
          {showHero ? <img src={article.imageUrl!} alt="" referrerPolicy="no-referrer" className="mt-6 w-full rounded-xl bg-muted object-cover" onError={(e) => (e.currentTarget.style.display = "none")} /> : null}
          {useFull && full.state === "loading" ? <p className="mt-6 text-sm text-muted-foreground">{t("loadingFull")}</p> : null}
          {useFull && full.state === "failed" ? <p className="mt-6 text-sm text-muted-foreground">{t("fullFailed")}</p> : null}
          <div className="article mt-6" dangerouslySetInnerHTML={{ __html: html }} />
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
