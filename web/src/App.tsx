import * as D from "@radix-ui/react-dialog";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ArticleList } from "./components/ArticleList";
import { ArticleView } from "./components/ArticleView";
import { NavigationBar } from "./components/NavigationBar";
import { AddFeedDialog, ManageDialog, SettingsDialog } from "./components/Dialogs";
import { SaveSheet } from "./components/SaveSheet";
import { SavedManageDialog } from "./components/SavedManage";
import { SearchPanel } from "./components/SearchPanel";
import { Sidebar } from "./components/Sidebar";
import { Button } from "./components/ui/button";
import { app, useApp } from "./lib/app";
import { plural, t } from "./lib/i18n";
import { articleKey, articlesFor, type Article } from "./lib/library";
import { itemArticle, savedItems } from "./lib/saved";

/** A message at the bottom, with at most one action (Undo, Open). */
type Message = { text: string; action?: { label: string; run: () => void } };

/** Three panes on wide screens (feeds, list, article); one at a time on phones. */
export default function App() {
  const state = useApp();
  const { settings, library, selection, readThisVisit, instapaperNotice, savedFilter } = state;
  // The open article belongs to the section it was opened in: another section closes it.
  const [opened, setOpened] = useState<{ article: Article; selection: typeof selection } | null>(null);
  const openArticle = opened?.selection === selection ? opened.article : null;
  // The section at the time it opens: Open after saving switches to Saved first.
  const setOpenArticle = useCallback((article: Article | null) => setOpened(article ? { article, selection: app.get().selection } : null), []);
  const [drawer, setDrawer] = useState(false);
  // Search takes the list's place until it is closed; the article opened from it stays open.
  const [searching, setSearching] = useState(false);
  const [dialog, setDialog] = useState<"add" | "manage" | "manageSaved" | "settings" | null>(null);
  const [message, setMessage] = useState<Message | null>(null);
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

  // Coming back from Instapaper's sign-in page: say how it went, then let it fade like the other messages.
  useEffect(() => {
    if (!instapaperNotice) return;
    const timer = setTimeout(() => app.clearInstapaperNotice(), 6000);
    return () => clearTimeout(timer);
  }, [instapaperNotice]);
  const shown: Message | null =
    message ??
    (instapaperNotice ? { text: t(instapaperNotice === "signedIn" ? "instapaperSignedIn" : "instapaperSignInFailed") } : null);

  const open = useCallback((a: Article) => {
    app.open(a);
    setOpenArticle(a);
  }, [setOpenArticle]);

  /** Opens a saved article in Saved, as the message after saving offers. */
  const openSaved = (key: string) => {
    const item = app.get().library.items.find((i) => i.key === key);
    if (!item) return;
    app.showSaved();
    open(itemArticle(item, app.get().library));
  };

  const visible = useMemo(
    () => (selection.kind === "saved" ? savedItems(library, savedFilter).map((i) => itemArticle(i, library)) : articlesFor(library, selection, settings.unreadOnly, readThisVisit)),
    [library, selection, settings.unreadOnly, readThisVisit, savedFilter],
  );

  // Keyboard: j/k next and previous, m read/unread, o open on the website, r refresh.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (dialog || state.sheet || e.metaKey || e.ctrlKey || e.altKey || (e.target as HTMLElement).closest("input, textarea, select")) return;
      // Nothing behind an open dialog or menu (the drawer, Aa, Remove, a feed's or folder's dialog).
      if (document.querySelector('[role="dialog"], [role="menu"]')) return;
      const index = openArticle ? visible.findIndex((a) => articleKey(a) === articleKey(openArticle)) : -1;
      if (e.key === "j" || e.key === "k") {
        const next = visible[e.key === "j" ? index + 1 : Math.max(index - 1, 0)];
        if (next) {
          open(next);
          document.querySelector(`[data-key="${CSS.escape(articleKey(next))}"]`)?.scrollIntoView({ block: "nearest" });
        }
      } else if (e.key === "m" && openArticle) {
        app.toggleRead(openArticle);
      } else if (e.key === "o" && openArticle?.link) {
        window.open(openArticle.link, "_blank", "noopener,noreferrer");
      } else if (e.key === "r") {
        void app.refresh();
      } else if (e.key === "/") {
        setSearching(true);
      } else if (e.key === "Escape" && openArticle) {
        // Closes the article, as Back does on a phone.
        setOpenArticle(null);
      } else return;
      e.preventDefault();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [dialog, state.sheet, openArticle, visible, library.articles, open, setOpenArticle]);

  const importFile = async (file: File) => {
    const added = app.importOpml(await file.text());
    setMessage({ text: added === null ? t("importFailed") : added === 0 ? t("importNothingNew") : plural("importAdded", added) });
  };

  const undoMarkAll = (keys: Set<string>) => {
    if (keys.size === 0) return;
    setMessage({ text: plural("markedRead", keys.size), action: { label: t("undo"), run: () => app.setRead(keys, false) } });
  };

  const sidebar = (
    <Sidebar
      onNavigate={() => setDrawer(false)}
      onManage={() => {
        setDrawer(false);
        setDialog("manage");
      }}
      onManageSaved={() => {
        setDrawer(false);
        setDialog("manageSaved");
      }}
      onSettings={() => {
        setDrawer(false);
        setDialog("settings");
      }}
    />
  );

  return (
    <div className="flex h-full">
      <D.Root open={drawer} onOpenChange={setDrawer}>
        <D.Portal>
          <D.Overlay className="fixed inset-0 z-40 bg-black/40" />
          <D.Content className="fixed inset-y-0 left-0 z-50 w-80 max-w-[85vw] bg-background shadow-xl" aria-describedby={undefined}>
            <D.Title className="sr-only">{t("navigation")}</D.Title>
            {sidebar}
          </D.Content>
        </D.Portal>
      </D.Root>
      <main className={`min-w-0 flex-1 flex-col border-r lg:max-w-md ${openArticle ? "hidden lg:flex" : "flex"}`}>
        <div className="min-h-0 flex-1">
          {searching ? (
            <SearchPanel openKey={openArticle ? articleKey(openArticle) : null} onOpen={open} onClose={() => setSearching(false)} />
          ) : (
            <ArticleList
              openKey={openArticle ? articleKey(openArticle) : null}
              onOpen={open}
              onMenu={() => setDrawer(true)}
              onAddFeed={() => setDialog("add")}
              onImport={() => fileInput.current?.click()}
              onUndo={undoMarkAll}
              onSearch={() => setSearching(true)}
            />
          )}
        </div>
        <NavigationBar searching={searching} onNavigate={() => setSearching(false)} />
      </main>
      <div className={`min-w-0 flex-1 ${openArticle ? "block" : "hidden lg:block"}`}>
        {openArticle ? (
          <ArticleView key={articleKey(openArticle)} article={openArticle} onBack={() => setOpenArticle(null)} onMessage={(text, action) => setMessage({ text, action })} />
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
      <SavedManageDialog open={dialog === "manageSaved"} onOpenChange={(o) => setDialog(o ? "manageSaved" : null)} />
      <SettingsDialog open={dialog === "settings"} onOpenChange={(o) => setDialog(o ? "settings" : null)} />
      <SaveSheet
        onMessage={(text, openKey) =>
          // Not in the article yet: Open goes there, in Saved (#142).
          setMessage({ text, action: openKey ? { label: t("open"), run: () => openSaved(openKey) } : undefined })
        }
      />
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
      {shown ? (
        // Above the bottom bars on a phone (the article's library bar, Feeds | Saved).
        <div role="status" className="fixed bottom-20 left-1/2 z-50 flex max-w-[calc(100vw-2rem)] -translate-x-1/2 items-center gap-3 rounded-xl bg-foreground px-4 py-3 text-sm text-background shadow-lg lg:bottom-4">
          <span>{shown.text}</span>
          {shown.action ? (
            <Button
              size="sm"
              variant="ghost"
              className="text-primary hover:bg-white/10"
              onClick={() => {
                shown.action?.run();
                setMessage(null);
              }}
            >
              {shown.action.label}
            </Button>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
