import * as D from "@radix-ui/react-dialog";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ArticleList } from "./components/ArticleList";
import { ArticleView } from "./components/ArticleView";
import { AddFeedDialog, ManageDialog, SettingsDialog } from "./components/Dialogs";
import { Sidebar } from "./components/Sidebar";
import { Button } from "./components/ui/button";
import { app, useApp } from "./lib/app";
import { plural, t } from "./lib/i18n";
import { articleKey, articlesFor, type Article } from "./lib/library";

/** Three panes on wide screens (feeds, list, article); one at a time on phones. */
export default function App() {
  const state = useApp();
  const { settings, library, selection, readThisVisit } = state;
  // The open article belongs to the section it was opened in: another section closes it.
  const [opened, setOpened] = useState<{ article: Article; selection: typeof selection } | null>(null);
  const openArticle = opened?.selection === selection ? opened.article : null;
  const setOpenArticle = useCallback((article: Article | null) => setOpened(article ? { article, selection } : null), [selection]);
  const [drawer, setDrawer] = useState(false);
  const [dialog, setDialog] = useState<"add" | "manage" | "settings" | null>(null);
  const [message, setMessage] = useState<{ text: string; undo?: () => void } | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  useEffect(() => {
    void app.init();
  }, []);

  // Theme: the device's, or the one chosen.
  useEffect(() => {
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const apply = () => document.documentElement.classList.toggle("dark", settings.theme === "dark" || (settings.theme === "system" && media.matches));
    apply();
    media.addEventListener("change", apply);
    return () => media.removeEventListener("change", apply);
  }, [settings.theme]);

  useEffect(() => {
    if (!message) return;
    const timer = setTimeout(() => setMessage(null), 6000);
    return () => clearTimeout(timer);
  }, [message]);

  const open = useCallback((a: Article) => {
    app.open(a);
    setOpenArticle(a);
  }, [setOpenArticle]);

  const visible = useMemo(() => articlesFor(library, selection, settings.unreadOnly, readThisVisit), [library, selection, settings.unreadOnly, readThisVisit]);

  // Keyboard: j/k next and previous, m read/unread, o open on the website, r refresh.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (dialog || e.metaKey || e.ctrlKey || e.altKey || (e.target as HTMLElement).closest("input, textarea, select")) return;
      const index = openArticle ? visible.findIndex((a) => articleKey(a) === articleKey(openArticle)) : -1;
      if (e.key === "j" || e.key === "k") {
        const next = visible[e.key === "j" ? index + 1 : Math.max(index - 1, 0)];
        if (next) {
          open(next);
          document.querySelector(`[data-key="${CSS.escape(articleKey(next))}"]`)?.scrollIntoView({ block: "nearest" });
        }
      } else if (e.key === "m" && openArticle) {
        const current = library.articles.find((a) => articleKey(a) === articleKey(openArticle));
        if (current) app.setRead(new Set([articleKey(current)]), !current.read);
      } else if (e.key === "o" && openArticle?.link) {
        window.open(openArticle.link, "_blank", "noopener,noreferrer");
      } else if (e.key === "r") {
        void app.refresh();
      } else return;
      e.preventDefault();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [dialog, openArticle, visible, library.articles, open]);

  const importFile = async (file: File) => {
    const added = app.importOpml(await file.text());
    setMessage({ text: added === null ? t("importFailed") : added === 0 ? t("importNothingNew") : plural("importAdded", added) });
  };

  const undoMarkAll = (keys: Set<string>) => {
    if (keys.size === 0) return;
    setMessage({ text: plural("markedRead", keys.size), undo: () => app.setRead(keys, false) });
  };

  const sidebar = (
    <Sidebar
      onNavigate={() => setDrawer(false)}
      onManage={() => {
        setDrawer(false);
        setDialog("manage");
      }}
      onSettings={() => {
        setDrawer(false);
        setDialog("settings");
      }}
    />
  );

  return (
    <div className="flex h-full">
      <aside className="hidden w-72 shrink-0 border-r bg-muted/40 lg:block">{sidebar}</aside>
      <D.Root open={drawer} onOpenChange={setDrawer}>
        <D.Portal>
          <D.Overlay className="fixed inset-0 z-40 bg-black/40 lg:hidden" />
          <D.Content className="fixed inset-y-0 left-0 z-50 w-80 max-w-[85vw] bg-background shadow-xl lg:hidden" aria-describedby={undefined}>
            <D.Title className="sr-only">{t("feeds")}</D.Title>
            {sidebar}
          </D.Content>
        </D.Portal>
      </D.Root>
      <main className={`min-w-0 flex-1 border-r lg:max-w-md ${openArticle ? "hidden lg:block" : "block"}`}>
        <ArticleList
          openKey={openArticle ? articleKey(openArticle) : null}
          onOpen={open}
          onMenu={() => setDrawer(true)}
          onAddFeed={() => setDialog("add")}
          onImport={() => fileInput.current?.click()}
          onUndo={undoMarkAll}
        />
      </main>
      <div className={`min-w-0 flex-1 ${openArticle ? "block" : "hidden lg:block"}`}>
        {openArticle ? (
          <ArticleView key={articleKey(openArticle)} article={openArticle} onBack={() => setOpenArticle(null)} onMessage={(text) => setMessage({ text })} />
        ) : (
          <div className="flex h-full items-center justify-center p-8 text-center text-sm text-muted-foreground">{t("pickArticle")}</div>
        )}
      </div>

      <AddFeedDialog key={dialog === "add" ? "open" : "closed"} open={dialog === "add"} onOpenChange={(o) => setDialog(o ? "add" : null)} folderId={selection.kind === "folder" ? selection.folderId : null} />
      <ManageDialog
        open={dialog === "manage"}
        onOpenChange={(o) => setDialog(o ? "manage" : null)}
        onAddFeed={() => setDialog("add")}
        onImport={() => fileInput.current?.click()}
      />
      <SettingsDialog open={dialog === "settings"} onOpenChange={(o) => setDialog(o ? "settings" : null)} />
      <input
        ref={fileInput}
        type="file"
        accept=".opml,.xml,text/xml,application/xml,text/x-opml"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) void importFile(file);
          e.target.value = "";
        }}
      />
      {message ? (
        <div role="status" className="fixed bottom-4 left-1/2 z-50 flex -translate-x-1/2 items-center gap-3 rounded-xl bg-foreground px-4 py-3 text-sm text-background shadow-lg">
          <span>{message.text}</span>
          {message.undo ? (
            <Button
              size="sm"
              variant="ghost"
              className="text-primary hover:bg-white/10"
              onClick={() => {
                message.undo?.();
                setMessage(null);
              }}
            >
              {t("undo")}
            </Button>
          ) : null}
        </div>
      ) : null}
      {!settings.proxyUrl && state.loaded ? (
        <div className="fixed right-4 bottom-4 z-40 max-w-xs rounded-xl border bg-card p-4 text-sm shadow-lg">
          <p>{t("needsProxy")}</p>
          <Button size="sm" className="mt-2" onClick={() => setDialog("settings")}>
            {t("settings")}
          </Button>
        </div>
      ) : null}
    </div>
  );
}
