import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ArticleList } from "./components/ArticleList";
import { ArticleView } from "./components/ArticleView";
import { NavigationBar } from "./components/NavigationBar";
import { AddChoiceDialog, AddFeedDialog, ManageFeedsDialog, NewsletterDialog, SettingsDialog } from "./components/Dialogs";
import type { FeedKind } from "./lib/feedKinds";
import { ManageTopicsDialog } from "./components/TopicChips";
import { InstapaperImportDialog } from "./components/ServiceSettings";
import { SaveSheet } from "./components/SaveSheet";
import { SearchPanel } from "./components/SearchPanel";
import { Sidebar } from "./components/Sidebar";
import { Button } from "./components/ui/button";
import { sharedByLabel } from "@/lib/sharing";
import { app, useApp } from "./lib/app";
import { plural, t } from "./lib/i18n";
import { articleKey, articlesFor, type Article } from "./lib/library";
import { useListedLibrary } from "./components/useListed";
import { websiteFor } from "./lib/articleOpening";
import { itemArticle, savedItems } from "./lib/saved";
import { closesOnMove, viewKey, type Shown } from "./lib/messages";

/** A message at the bottom, with at most one action (Undo, Open). */
type Message = { text: string; action?: { label: string; run: () => void } };

/** Three panes on wide screens (feeds, list, article); one at a time on phones. */
export default function App() {
  const state = useApp();
  const { settings, library, selection, readThisVisit, instapaperNotice, accountNotice, received, savedFilter, inboxOpen } = state;
  // The open article belongs to the section it was opened in: another section closes it.
  const [opened, setOpened] = useState<{ article: Article; selection: typeof selection } | null>(null);
  const openArticle = opened?.selection === selection ? opened.article : null;
  // The section at the time it opens: Open after saving switches to Saved first.
  const setOpenArticle = useCallback((article: Article | null) => setOpened(article ? { article, selection: app.get().selection } : null), []);
  // Search takes the list's place until it is closed; the article opened from it stays open.
  const [searching, setSearching] = useState(false);
  const [dialog, setDialog] = useState<"add" | "addChoice" | "settings" | null>(null);
  // Manage feeds for RSS feeds or newsletter feeds, and Manage tags: Settings' buttons (pages of
  // Settings when opened from there).
  const [manageFeedsKind, setManageFeedsKind] = useState<FeedKind | null>(null);
  const manageFeeds = manageFeedsKind !== null;
  const [manageTopics, setManageTopics] = useState(false);
  // Add feed (or New address, for newsletter feeds) from Manage feeds: a page of it, without a topic.
  const [addFromManage, setAddFromManage] = useState(false);
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
  // Something another Content user shared arrived (#167): the same.
  useEffect(() => {
    if (!received) return;
    const timer = setTimeout(() => app.clearReceived(), 6000);
    return () => clearTimeout(timer);
  }, [received]);
  // An emailed sign-in link opened here (#166): the same.
  useEffect(() => {
    if (!accountNotice) return;
    const timer = setTimeout(() => app.clearAccountNotice(), 6000);
    return () => clearTimeout(timer);
  }, [accountNotice]);
  const accountMessages = { signedIn: "accountLinkSignedIn", emailAdded: "accountLinkAdded", expired: "accountLinkExpired", taken: "accountLinkTaken", failed: "accountLinkFailed" } as const;
  const shown: Message | null =
    message ??
    (instapaperNotice ? { text: t(instapaperNotice === "signedIn" ? "instapaperSignedIn" : "instapaperSignInFailed") } : null) ??
    (accountNotice ? { text: t(accountMessages[accountNotice]) } : null) ??
    (received
      ? {
          text: t("shareUserReceived").replace("{names}", received.from.map(sharedByLabel).join(", ")).replace("{count}", plural("articleCount", received.count)),
          // Open goes to the inbox (#246).
          action: { label: t("open"), run: () => app.showInbox(true) },
        }
      : null);

  // A click elsewhere, or moving to another view, closes the message (2026-11-02), as on Android.
  const toast = useRef<HTMLDivElement>(null);
  const dismiss = useCallback(() => {
    setMessage(null);
    app.clearInstapaperNotice();
    app.clearAccountNotice();
    app.clearReceived();
  }, []);
  const view = viewKey(selection, inboxOpen, searching, openArticle ? articleKey(openArticle) : null);
  // What is showing, by what it came from (stable, unlike the message built from it).
  const shownFrom = message ?? instapaperNotice ?? accountNotice ?? received ?? null;
  const before = useRef<Shown | null>(null);
  useEffect(() => {
    const now = { view, message: shownFrom };
    if (closesOnMove(before.current, now)) dismiss();
    before.current = now;
  }, [view, shownFrom, dismiss]);
  const showing = shownFrom !== null;
  useEffect(() => {
    if (!showing) return;
    const onClick = (e: MouseEvent) => {
      if (!toast.current?.contains(e.target as Node)) dismiss();
    };
    // Capture: before the click does its own thing, which may show a new message.
    document.addEventListener("click", onClick, true);
    return () => document.removeEventListener("click", onClick, true);
  }, [showing, dismiss]);

  const open = useCallback((a: Article) => {
    app.open(a);
    setOpenArticle(a);
  }, [setOpenArticle]);
  // A click in a list: with Always open the website, a feed article opens on its site in a new tab
  // (marked read, as on Android); j and k still step through the reader.
  const openClicked = useCallback((a: Article) => {
    const website = websiteFor(app.get().settings.alwaysOpenWebsite, a);
    if (website) {
      app.open(a);
      window.open(website, "_blank", "noopener,noreferrer");
    } else {
      open(a);
    }
  }, [open]);

  /** Opens a saved article in Saved, as the message after saving offers. */
  const openSaved = (key: string) => {
    const item = app.get().library.items.find((i) => i.key === key);
    if (!item) return;
    app.showSaved();
    open(itemArticle(item, app.get().library));
  };

  // The feed lists without the articles the hidden words leave out (#277), for j and k.
  const listed = useListedLibrary();
  const visible = useMemo(
    () => (selection.kind === "saved" ? savedItems(library, savedFilter).map((i) => itemArticle(i, library)) : articlesFor(listed, selection, settings.unreadOnly, readThisVisit)),
    [library, listed, selection, settings.unreadOnly, readThisVisit, savedFilter],
  );

  // Keyboard: j/k next and previous, m read/unread, o open on the website, r refresh.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (dialog || manageFeeds || manageTopics || state.sheet || e.metaKey || e.ctrlKey || e.altKey || (e.target as HTMLElement).closest("input, textarea, select")) return;
      // Nothing behind an open dialog or menu (Aa, Remove, a feed's or folder's dialog).
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
  }, [dialog, manageFeeds, manageTopics, state.sheet, openArticle, visible, library.articles, open, setOpenArticle]);

  const importFile = async (file: File) => {
    const added = app.importOpml(await file.text());
    setMessage({ text: added === null ? t("importFailed") : added === 0 ? t("importNothingNew") : plural("importAdded", added) });
  };

  const undoMarkAll = (keys: Set<string>) => {
    if (keys.size === 0) return;
    setMessage({ text: plural("markedRead", keys.size), action: { label: t("undo"), run: () => app.setRead(keys, false) } });
  };


  // Manage feeds (with Add feed as its page) and Manage topics: pages of Settings when opened from
  // there, else dialogs of their own.
  const manageDialogs = (
    <>
      <ManageFeedsDialog
        open={manageFeedsKind === "rss"}
        onOpenChange={(o) => {
          if (!o) {
            setManageFeedsKind(null);
            setAddFromManage(false);
          }
        }}
        onAddFeed={() => setAddFromManage(true)}
      >
        {addFromManage ? <AddFeedDialog open onOpenChange={(o) => setAddFromManage(o)} folderId={null} /> : null}
      </ManageFeedsDialog>
      {/* Newsletter feeds: every tag's address, as in + › Newsletter feed. */}
      <NewsletterDialog
        open={manageFeedsKind === "newsletter"}
        title={t("newsletterFeeds")}
        onOpenChange={(o) => !o && setManageFeedsKind(null)}
        // Without a Content account: Settings › Account, where you sign in.
        onSignIn={() => {
          setManageFeedsKind(null);
          setDialog("settings");
        }}
      />
      <ManageTopicsDialog open={manageTopics} onOpenChange={setManageTopics} />
    </>
  );

  return (
    <div className="flex h-full">
      {/* A wide window: the places and the filters at the side (the navigation bar and the chips on a phone). */}
      <aside className="hidden w-72 shrink-0 border-r bg-muted/40 lg:block">
        <Sidebar onSettings={() => setDialog("settings")} />
      </aside>
      <main className={`min-w-0 flex-1 flex-col border-r lg:max-w-md ${openArticle ? "hidden lg:flex" : "flex"}`}>
        <div className="min-h-0 flex-1">
          {searching ? (
            <SearchPanel openKey={openArticle ? articleKey(openArticle) : null} onOpen={openClicked} onClose={() => setSearching(false)} />
          ) : (
            <ArticleList
              openKey={openArticle ? articleKey(openArticle) : null}
              onOpen={openClicked}
              onSettings={() => setDialog("settings")}
              onAddFeed={() => setDialog("add")}
              onAdd={() => setDialog("addChoice")}
              onImport={() => fileInput.current?.click()}
              onUndo={undoMarkAll}
              onSearch={() => setSearching(true)}
              onMessage={(text, action) => setMessage({ text, action })}
              onSaved={(key) => setMessage({ text: t("savedTo").replace("{place}", t("toRead")), action: { label: t("open"), run: () => openSaved(key) } })}
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

      <AddChoiceDialog
        open={dialog === "addChoice"}
        onOpenChange={(o) => setDialog(o ? "addChoice" : null)}
        onFeed={() => setDialog("add")}
        onArticle={() => {
          setDialog(null);
          app.addLink();
        }}
        // Your own newsletter address needs a Content account: Settings, where you sign in.
        onSignIn={() => setDialog("settings")}
      />
      <AddFeedDialog
        key={dialog === "add" ? "open" : "closed"}
        open={dialog === "add"}
        onOpenChange={(o) => setDialog(o ? "add" : null)}
        folderId={selection.kind === "folder" ? selection.folderId : null}
      />
      {dialog === "settings" ? null : manageDialogs}
      <SettingsDialog
        key={dialog === "settings" ? "settings" : "closed"}
        open={dialog === "settings"}
        onOpenChange={(o) => {
          setDialog(o ? "settings" : null);
          if (!o) {
            setManageFeedsKind(null);
            setManageTopics(false);
            setAddFromManage(false);
          }
        }}
        onImport={() => fileInput.current?.click()}
        onManageFeeds={setManageFeedsKind}
        onManageTopics={() => setManageTopics(true)}
      >
        {dialog === "settings" ? manageDialogs : null}
      </SettingsDialog>
      <InstapaperImportDialog />
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
        // A toast (shadcn/ui's Sonner look): bottom right on a wide window, above the bottom bars on a phone.
        <div ref={toast} role="status" className="fixed inset-x-4 bottom-20 z-50 flex items-center gap-3 rounded-lg border bg-card px-4 py-3 text-sm text-foreground shadow-lg sm:left-auto sm:w-[22rem] lg:right-6 lg:bottom-6">
          <span className="min-w-0 flex-1">{shown.text}</span>
          {shown.action ? (
            <Button
              size="sm"
              className="shrink-0"
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
