import { ArrowLeft, BookmarkPlus, BookOpenText, ChevronDown, ChevronRight, LayoutList, Palette, Plus, Puzzle, Rss, Tag as TagIcon, Trash2, UserRound } from "lucide-react";
import { ConfirmRemove } from "./ConfirmRemove";
import { EditChips, TopicChips, chip, chipOn } from "./TopicChips";
import { useEffect, useMemo, useState } from "react";
import { gravatarUrl } from "@/lib/account";
import { app, useApp, type ListView, type Theme } from "@/lib/app";
import { formatRelative, plural, t, type Key } from "@/lib/i18n";
import { searchIcons } from "@/lib/icons";
import type { Feed, Folder } from "@/lib/library";
import { FeedIcon } from "./FeedIcon";
import { FolderIcon, SymbolSvg } from "./FolderIcon";
import { useIcons } from "./useIcons";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { Dialog } from "./ui/dialog";
import { Input, Label } from "./ui/input";
import { Segmented } from "./ui/segmented";
import { ReaderStyleControls } from "./ReaderStyleControls";
import { InstapaperSettings } from "./ServiceSettings";
import { SwitchRow } from "./ui/switch";
import { pushSupported } from "@/lib/notifications";

type Props = { open: boolean; onOpenChange: (open: boolean) => void };

/**
 * Add feed, opened with a new key each time (App), so the address starts empty. Then its topic as
 * chips (No topic, then every topic with its icon), with Edit topics at the end (Manage topics over
 * this dialog), as on Android.
 */
export function AddFeedDialog({ open, onOpenChange, folderId }: Props & { folderId: string | null }) {
  const { library } = useApp();
  const [input, setInput] = useState("");
  const [topicId, setTopicId] = useState<string | null>(folderId);
  const chosen = topicId && library.folders.some((f) => f.id === topicId) ? topicId : null;
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const add = async () => {
    setBusy(true);
    setError(null);
    const result = await app.addFeed(input, chosen);
    setBusy(false);
    if (result === "added") onOpenChange(false);
    else setError(t(addErrors[result]));
  };
  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("addFeed")}
      description={t("addFeedHint")}
      closeLabel={t("close")}
      cancel={t("cancel")}
      footer={
        <>
          <Button onClick={() => void add()} disabled={busy || !input.trim()}>
            {busy ? t("adding") : t("add")}
          </Button>
        </>
      }
    >
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void add();
        }}
      >
        <Input autoFocus value={input} onChange={(e) => setInput(e.target.value)} placeholder="example.com" inputMode="url" aria-label={t("address")} />
        {error ? <p className="mt-2 text-sm text-destructive" role="alert">{error}</p> : null}
      </form>
      <section className="mt-5 space-y-2">
        <h3 className="text-sm font-medium text-primary">{t("topic")}</h3>
        <TopicChips
          topics={library.folders}
          selected={(topic) => chosen === topic.id}
          // One topic: clicking the chosen one again is No topic.
          onToggle={(topic) => setTopicId(chosen === topic.id ? null : topic.id)}
          onCreated={(id) => setTopicId(id)}
          disabled={busy}
          leading={
            <button type="button" aria-pressed={chosen === null} disabled={busy} onClick={() => setTopicId(null)} className={cn(chip, chosen === null ? chipOn : "hover:bg-muted")}>
              {t("noFolder")}
            </button>
          }
        />
      </section>
    </Dialog>
  );
}

const addErrors: Record<string, Key> = {
  already: "errorAlready",
  invalid: "errorInvalid",
  none: "errorNoFeed",
  offline: "errorOffline",
  notFound: "errorNotFound",
  server: "errorServer",
  tooLarge: "errorTooLarge",
  blocked: "errorBlocked",
};

/** Downloads the feed list as an OPML file. */
function exportOpml() {
  const blob = new Blob([app.exportOpml()], { type: "text/x-opml" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = "content-feeds.opml";
  a.click();
  URL.revokeObjectURL(a.href);
}

/**
 * Manage feeds, as on Android's sheet: the feeds as chips, grouped by topic (in the topics' order,
 * then No topic). Drag a chip (or focus it and press ← →) to reorder it within its topic; click it
 * to edit it (name, topic, Remove). A feed that fails to download has its name in red. Add feed at
 * the end (over this dialog). Every change is stored at once, so it has **Done**.
 */
export function ManageFeedsDialog({ open, onOpenChange, onAddFeed }: Props & { onAddFeed: () => void }) {
  const { library, problems } = useApp();
  const [editing, setEditing] = useState<Feed | null>(null);
  const groups = useMemo(() => {
    const inTopics = library.folders.map((topic) => [topic, library.feeds.filter((f) => f.folderId === topic.id)] as const);
    const loose = library.feeds.filter((f) => f.folderId === null || !library.folders.some((x) => x.id === f.folderId));
    return [...inTopics, [null, loose] as const].filter(([, feeds]) => feeds.length > 0);
  }, [library]);
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={t("manageFeedsTitle")} closeLabel={t("close")} cancel={t("done")}>
      <p className="pb-3 text-sm text-muted-foreground">{t(library.feeds.length === 0 ? "manageFeedsNone" : "manageFeedsHint")}</p>
      <div className="space-y-4">
        {groups.map(([topic, feeds]) => (
          <section key={topic?.id ?? ""} className="space-y-2">
            <h3 className="flex items-center gap-1.5 text-sm font-medium text-muted-foreground">
              {topic ? <FolderIcon name={topic.icon} className="size-4" /> : null}
              {topic?.title ?? t("noFolder")}
            </h3>
            <div className="flex flex-wrap items-center gap-1.5">
              <EditChips
                items={feeds as Feed[]}
                id={(f) => f.url}
                label={(f) => f.title}
                icon={(f) => <FeedIcon feed={f} className="size-4" />}
                onReorder={(urls) => app.reorderFeedsIn(topic?.id ?? null, urls)}
                onEdit={setEditing}
                editLabel={t("editFeed")}
                error={(f) => problems.has(f.url)}
              />
            </div>
          </section>
        ))}
        <Button variant="tonal" onClick={onAddFeed}>
          <Plus />
          {t("addFeed")}
        </Button>
      </div>
      {editing ? <EditFeedDialog key={editing.url} feed={editing} onClose={() => setEditing(null)} /> : null}
    </Dialog>
  );
}

/**
 * Edit a feed, as on Android's sheet: its name (empty brings back the feed's own), why it fails if
 * it does, its topic as chips (No topic, then every topic, Edit topics), Remove (asks first) and Save.
 */
function EditFeedDialog({ feed, onClose }: { feed: Feed; onClose: () => void }) {
  const { library, problems } = useApp();
  const [title, setTitle] = useState(feed.title);
  const [topicId, setTopicId] = useState<string | null>(feed.folderId);
  const chosen = topicId && library.folders.some((f) => f.id === topicId) ? topicId : null;
  const [confirming, setConfirming] = useState(false);
  const save = () => {
    if (title.trim() !== feed.title) app.renameFeed(feed.url, title);
    if (chosen !== feed.folderId) app.moveFeed(feed.url, chosen);
    onClose();
  };
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={t("editFeed")}
      description={feed.url}
      closeLabel={t("close")}
      cancel={t("cancel")}
      footer={
        <>
          <Button variant="destructive" className="mr-auto" onClick={() => setConfirming(true)}>
            <Trash2 />
            {t("remove")}
          </Button>
          <Button onClick={save}>{t("save")}</Button>
        </>
      }
    >
      <div className="space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="feed-title">{t("name")}</Label>
          <Input id="feed-title" value={title} onChange={(e) => setTitle(e.target.value)} onKeyDown={(e) => e.key === "Enter" && save()} />
          <p className="text-xs text-muted-foreground">{t("nameHint")}</p>
        </div>
        {problems.has(feed.url) ? <p className="text-sm text-destructive">{t("feedFailing")}</p> : null}
        <section className="space-y-2">
          <h3 className="text-sm font-medium text-primary">{t("topic")}</h3>
          <TopicChips
            topics={library.folders}
            selected={(topic) => chosen === topic.id}
            // One topic: clicking the chosen one again is No topic.
            onToggle={(topic) => setTopicId(chosen === topic.id ? null : topic.id)}
            leading={
              <button type="button" aria-pressed={chosen === null} onClick={() => setTopicId(null)} className={cn(chip, chosen === null ? chipOn : "hover:bg-muted")}>
                {t("noFolder")}
              </button>
            }
          />
        </section>
      </div>
      <ConfirmRemove
        open={confirming}
        title={t("removeFeedTitle")}
        body={t("removeFeedBody").replace("{name}", feed.title)}
        onConfirm={() => {
          app.removeFeed(feed.url);
          onClose();
        }}
        onClose={() => setConfirming(false)}
      />
    </Dialog>
  );
}

/**
 * The search bar's +, the same in Feeds, Saved and the Inbox, as on Android: first what to add.
 * Subscribe to feed opens Add feed, Save article the save sheet; both then ask for the address.
 */
export function AddChoiceDialog({ open, onOpenChange, onFeed, onArticle }: Props & { onFeed: () => void; onArticle: () => void }) {
  const choice = (Icon: typeof Rss, title: string, body: string, onClick: () => void) => (
    <button type="button" onClick={onClick} className="flex w-full cursor-pointer items-center gap-4 rounded-xl bg-muted p-4 text-left hover:bg-accent/60">
      <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-accent text-accent-foreground">
        <Icon className="size-5" aria-hidden="true" />
      </span>
      <span className="min-w-0">
        <span className="block font-medium">{title}</span>
        <span className="block text-sm text-muted-foreground">{body}</span>
      </span>
    </button>
  );
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={t("addTitle")} closeLabel={t("close")} cancel={t("cancel")}>
      <div className="space-y-3">
        {choice(Rss, t("addChoiceFeed"), t("addChoiceFeedBody"), onFeed)}
        {choice(BookmarkPlus, t("addChoiceArticle"), t("addChoiceArticleBody"), onArticle)}
      </div>
    </Dialog>
  );
}

export function FolderDialog({
  folder,
  onClose,
  onCreated,
  onRenamed,
  onRemoved,
}: {
  folder: Folder | "new" | null;
  onClose: () => void;
  onCreated?: (id: string) => void;
  /** An existing topic saved with this name (#314: the Organise sheet's choice follows). */
  onRenamed?: (from: string, to: string) => void;
  onRemoved?: (title: string) => void;
}) {
  const icons = useIcons();
  const [title, setTitle] = useState(folder && folder !== "new" ? folder.title : "");
  const [icon, setIcon] = useState(folder && folder !== "new" ? folder.icon : "folder");
  const [query, setQuery] = useState("");
  const [confirming, setConfirming] = useState(false);
  if (!folder) return null;
  const save = () => {
    if (folder === "new") onCreated?.(app.addFolder(title, icon));
    else {
      app.updateFolder(folder.id, title, icon);
      onRenamed?.(folder.title, title);
    }
    onClose();
  };
  const found = icons ? searchIcons(icons, query) : [];
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={folder === "new" ? t("newFolder") : t("editFolder")}
      closeLabel={t("close")}
      cancel={t("cancel")}
      footer={
        <>
          {folder !== "new" ? (
            <Button
              variant="destructive"
              className="mr-auto"
              onClick={() => setConfirming(true)}
            >
              <Trash2 />
              {t("remove")}
            </Button>
          ) : null}
          <Button onClick={save} disabled={!title.trim()}>
            {t("save")}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="folder-title">{t("title")}</Label>
          <Input id="folder-title" autoFocus value={title} onChange={(e) => setTitle(e.target.value)} onKeyDown={(e) => e.key === "Enter" && title.trim() && save()} />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="icon-search">{t("icon")}</Label>
          <Input id="icon-search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder={t("searchIcons")} />
          <div className="grid grid-cols-8 gap-1 pt-2" role="radiogroup" aria-label={t("icon")}>
            {found.map((i) => (
              <button
                key={i.name}
                type="button"
                role="radio"
                aria-checked={icon === i.name}
                aria-label={i.name}
                title={i.name}
                onClick={() => setIcon(i.name)}
                className={cn("flex aspect-square cursor-pointer items-center justify-center rounded-lg hover:bg-muted", icon === i.name && "bg-primary text-primary-foreground hover:bg-primary")}
              >
                <SymbolSvg icon={i} />
              </button>
            ))}
          </div>
        </div>
      </div>
      {folder && folder !== "new" ? (
        <ConfirmRemove
          open={confirming}
          title={t("removeFolderTitle")}
          body={t("removeFolderBody").replace("{name}", folder.title)}
          onConfirm={() => {
            app.removeFolder(folder.id);
            onRemoved?.(folder.title);
            onClose();
          }}
          onClose={() => setConfirming(false)}
        />
      ) : null}
    </Dialog>
  );
}

type SettingsPage = "account" | "integrations" | "theme" | "list" | "reader";

/**
 * Settings, as on Android: two large buttons at the top for Manage feeds and Manage topics (dialogs
 * of their own, so they open from other places too), then one row per page (Account, Integrations,
 * Theme, Article list, Article view), each with its icon, what it holds and a chevron. A page opens
 * in the same dialog, with ← Settings to go back.
 */
export function SettingsDialog({
  open,
  onOpenChange,
  onImport,
  onManageFeeds,
  onManageTopics,
}: Props & { onImport: () => void; onManageFeeds: () => void; onManageTopics: () => void }) {
  const { settings, library, account } = useApp();
  // App opens it with a new key each time, so it starts on the main page.
  const [page, setPage] = useState<SettingsPage | null>(null);
  const titles: Record<SettingsPage, string> = {
    account: t("account"),
    integrations: t("integrations"),
    theme: t("theme"),
    list: t("articleList"),
    reader: t("articleView"),
  };
  const themeNames: Record<Theme, string> = { system: t("themeSystem"), light: t("themeLight"), dark: t("themeDark") };
  const big = (Icon: typeof Rss, title: string, detail: string, onClick: () => void) => (
    <button type="button" onClick={onClick} className="flex cursor-pointer flex-col items-start gap-3 rounded-xl bg-muted p-4 text-left hover:bg-accent/60">
      <span className="flex size-10 items-center justify-center rounded-full bg-accent text-accent-foreground">
        <Icon className="size-5" aria-hidden="true" />
      </span>
      <span>
        <span className="block font-medium">{title}</span>
        <span className="block text-sm text-muted-foreground">{detail}</span>
      </span>
    </button>
  );
  const row = (to: SettingsPage, Icon: typeof Rss, summary: string) => (
    <button type="button" onClick={() => setPage(to)} className="flex w-full cursor-pointer items-center gap-4 rounded-lg px-2 py-3 text-left hover:bg-muted">
      <Icon className="size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-medium">{titles[to]}</span>
        <span className="block truncate text-sm text-muted-foreground">{summary}</span>
      </span>
      <ChevronRight className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
    </button>
  );
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={page ? titles[page] : t("settings")} closeLabel={t("close")} wide>
      {page === null ? (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            {big(Rss, t("manageFeedsTitle"), plural("feedsCount", library.feeds.length), onManageFeeds)}
            {big(TagIcon, t("manageTitle"), plural("topicsCount", library.folders.length), onManageTopics)}
          </div>
          <nav aria-label={t("settings")}>
            {row("account", UserRound, account?.email ?? t("accountSignedOut"))}
            {row("integrations", Puzzle, t("integrationsSummary"))}
            {row("theme", Palette, themeNames[settings.theme])}
            {row("list", LayoutList, t("listSummary"))}
            {row("reader", BookOpenText, t("articleViewSummary"))}
          </nav>
          <p className="text-xs text-muted-foreground">
            Content ·{" "}
            <a className="underline" href="https://github.com/wimvdon/content/blob/main/PRIVACY.md" target="_blank" rel="noopener noreferrer">
              {t("privacy")}
            </a>
          </p>
        </div>
      ) : (
        <div className="space-y-6">
          <Button variant="ghost" size="sm" className="-ml-3" onClick={() => setPage(null)}>
            <ArrowLeft />
            {t("settings")}
          </Button>
          {page === "account" ? <AccountSection /> : null}
          {page === "integrations" ? (
            <>
              {/* Moving to or from another reader: the feeds in and out. */}
              <section className="space-y-2">
                <h3 className="text-sm font-medium text-primary">{t("opmlTitle")}</h3>
                <div className="flex flex-wrap gap-2">
                  <Button variant="outline" onClick={onImport}>
                    {t("importOpml")}
                  </Button>
                  <Button variant="outline" onClick={exportOpml} disabled={library.feeds.length === 0}>
                    {t("exportOpml")}
                  </Button>
                </div>
                <p className="text-xs text-muted-foreground">{t("opmlHint")}</p>
                {/* How to get the file out of the readers people come from, and Substack, which has no file. */}
                <ImportGuide title={t("importFeedlyTitle")} steps={[t("importFeedlyStep1"), t("importWebStep")]} note={t("importFeedlyNote")} />
                <ImportGuide title={t("importInoreaderTitle")} steps={[t("importInoreaderStep1"), t("importInoreaderStep2"), t("importWebStep")]} />
                <ImportGuide title={t("importSubstackTitle")} steps={[t("substackWebStep1"), t("substackWebStep2")]} />
              </section>
              {/* The service Saved is also kept in. */}
              <InstapaperSettings />
            </>
          ) : null}
          {page === "theme" ? (
            <Segmented<Theme>
              label={t("theme")}
              value={settings.theme}
              onChange={(theme) => app.updateSettings({ theme })}
              options={[
                { value: "system", label: t("themeSystem") },
                { value: "light", label: t("themeLight") },
                { value: "dark", label: t("themeDark") },
              ]}
            />
          ) : null}
          {page === "list" ? (
            <section className="space-y-2">
              <div className="flex flex-wrap items-center justify-between gap-2 py-1">
                <span className="text-sm">{t("listView")}</span>
                <Segmented<ListView>
                  label={t("listView")}
                  value={settings.listView}
                  onChange={(listView) => app.updateSettings({ listView })}
                  options={[
                    { value: "small", label: t("listSmall") },
                    { value: "medium", label: t("listMedium") },
                    { value: "large", label: t("listLarge") },
                  ]}
                />
              </div>
              <SwitchRow title={t("markReadOnScroll")} checked={settings.markReadOnScroll} onChange={(v) => app.updateSettings({ markReadOnScroll: v })} />
            </section>
          ) : null}
          {page === "reader" ? (
            <>
              <section className="space-y-2">
                <SwitchRow title={t("fullArticles")} summary={t("fullArticlesSummary")} checked={settings.fullArticles} onChange={(v) => app.updateSettings({ fullArticles: v })} />
                <SwitchRow title={t("showImages")} summary={t("showImagesSummary")} checked={settings.showImages} onChange={(v) => app.updateSettings({ showImages: v })} />
              </section>
              <section className="space-y-2">
                <h3 className="text-sm font-medium text-primary">{t("textSettings")}</h3>
                <ReaderStyleControls preview />
              </section>
            </>
          ) : null}
        </div>
      )}
    </Dialog>
  );
}

/**
 * The Content account (#166, docs/account.md): sign in with an emailed link. Instapaper is only a
 * connected service (#215). Signed in: who (the address with its Gravatar picture and name, #234), how sync is doing,
 * Sync now, Add email address (an account opened with Instapaper), Sign out and Delete synced data.
 */
function AccountSection() {
  const { settings, syncStatus, account } = useApp();
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleteFailed, setDeleteFailed] = useState(false);
  // The name on the address's Gravatar profile (#234), asked once per address.
  const [name, setName] = useState<{ email: string; name: string } | null>(null);
  const email = account?.email ?? null;
  useEffect(() => {
    if (!email) return;
    let current = true;
    void app.profileName(email).then((found) => current && found !== null && setName({ email, name: found }));
    return () => {
      current = false;
    };
  }, [email]);
  const shownName = name && name.email === email ? name.name : "";
  const statusText =
    syncStatus.kind === "syncing"
      ? t("syncing")
      : syncStatus.kind === "failed"
        ? t(syncProblems[syncStatus.problem])
        : settings.lastSyncedAt
          ? t("lastSynced").replace("{when}", formatRelative(settings.lastSyncedAt))
          : t("notSyncedYet");
  const failed = syncStatus.kind === "failed";
  return (
    <section className="space-y-2">
      <h3 className="text-sm font-medium text-primary">{t("accountTitle")}</h3>
      {account ? (
        <>
          <div className="flex items-center gap-3">
            {account.email && settings.showImages ? <Gravatar key={account.email} email={account.email} /> : null}
            <div className="min-w-0">
              {shownName ? <p className="truncate text-sm font-medium">{shownName}</p> : null}
              <p className={cn("truncate text-sm", shownName ? "text-muted-foreground" : "font-medium")}>{account.email ?? t("accountTitle")}</p>
              <p className={cn("text-sm", failed ? "text-destructive" : "text-muted-foreground")} role={failed ? "alert" : undefined}>
                {statusText}
              </p>
            </div>
          </div>
          <div className="flex flex-wrap gap-2 pt-1">
            {!(syncStatus.kind === "failed" && syncStatus.problem === "signedOut") ? (
              <Button variant="tonal" onClick={() => void app.sync()} disabled={syncStatus.kind === "syncing"}>
                {t("syncNow")}
              </Button>
            ) : null}
            <Button variant="ghost" onClick={() => void app.signOutAccount()}>
              {t("accountSignOut")}
            </Button>
            <Button variant="ghost" className="text-destructive" onClick={() => setConfirmDelete(true)}>
              {t("deleteAccountData")}
            </Button>
          </div>
          {deleteFailed ? <p className="text-sm text-destructive" role="alert">{t("syncOffline")}</p> : null}
          <NotificationsSwitch />
          {account.email ? null : (
            <div className="space-y-2 pt-2">
              <p className="text-sm font-medium">{t("accountAddEmail")}</p>
              <p className="text-sm text-muted-foreground">{t("accountAddEmailSummary")}</p>
              <EmailLinkForm />
            </div>
          )}
        </>
      ) : (
        <>
          <p className="text-sm text-muted-foreground">{t("accountSummary")}</p>
          <EmailLinkForm />
        </>
      )}
      <Dialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title={t("deleteAccountData")}
        closeLabel={t("close")}
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmDelete(false)}>
              {t("cancel")}
            </Button>
            <Button
              variant="destructive"
              onClick={() => {
                setConfirmDelete(false);
                void app.deleteAccountData().then((ok) => setDeleteFailed(!ok));
              }}
            >
              <Trash2 />
              {t("delete")}
            </Button>
          </>
        }
      >
        <p className="text-sm">{t("deleteAccountConfirm")}</p>
      </Dialog>
    </section>
  );
}

const syncProblems: Record<string, Key> = {
  offline: "syncOffline",
  signedOut: "syncSignedOut",
  server: "syncServer",
  unreadableFile: "syncUnreadable",
  notSetUp: "syncNotSetUp",
};

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** An email address and a button that emails a link to it; then says to open the email. */
export function EmailLinkForm({ id = "account-email" }: { id?: string } = {}) {
  const [email, setEmail] = useState("");
  const [sending, setSending] = useState(false);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);
  const valid = EMAIL.test(email.trim());
  const send = () => {
    if (!valid || sending) return;
    const to = email.trim();
    setSending(true);
    setFailed(false);
    void app.sendEmailLink(to).then((ok) => {
      setSending(false);
      if (ok) setSentTo(to);
      else setFailed(true);
    });
  };
  return (
    <form
      className="space-y-2"
      onSubmit={(e) => {
        e.preventDefault();
        send();
      }}
    >
      <div className="space-y-1">
        <Label htmlFor={id}>{t("accountEmail")}</Label>
        <Input
          id={id}
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => {
            setEmail(e.target.value);
            setFailed(false);
          }}
        />
      </div>
      <Button type="submit" disabled={!valid || sending}>
        {t("accountEmailSend")}
      </Button>
      {sentTo ? <p className="text-sm">{t("accountEmailSent").replace("{email}", sentTo)}</p> : null}
      {failed ? (
        <p className="text-sm text-destructive" role="alert">
          {t("accountEmailFailed")}
        </p>
      ) : null}
    </form>
  );
}

/** The Gravatar picture of the address, or nothing when it has none. */
function Gravatar({ email }: { email: string }) {
  const [src, setSrc] = useState<string | null>(null);
  const [missing, setMissing] = useState(false);
  useEffect(() => {
    let current = true;
    void gravatarUrl(email).then((url) => current && setSrc(url));
    return () => {
      current = false;
    };
  }, [email]);
  if (!src || missing) return null;
  return <img src={src} alt="" className="size-10 shrink-0 rounded-full" onError={() => setMissing(true)} />;
}

/**
 * Notify me when something is shared with me (#235): Web Push in this browser. Hidden where the
 * browser can't; says so when the browser's permission was refused.
 */
function NotificationsSwitch() {
  const [on, setOn] = useState<boolean | null>(null);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<"denied" | "unavailable" | "failed" | null>(null);
  useEffect(() => {
    let current = true;
    void app.notificationsOn().then((value) => current && setOn(value));
    return () => {
      current = false;
    };
  }, []);
  if (!pushSupported() || on === null || problem === "unavailable") return null;
  const change = async (value: boolean) => {
    setBusy(true);
    setProblem(null);
    if (value) {
      const result = await app.turnOnNotifications();
      if (result === "on") setOn(true);
      else setProblem(result === "signedOut" ? "failed" : result);
    } else {
      await app.turnOffNotifications();
      setOn(false);
    }
    setBusy(false);
  };
  return (
    <div className="pt-1">
      <SwitchRow title={t("pushSetting")} summary={t("pushSettingSummary")} checked={on} disabled={busy} onChange={(v) => void change(v)} />
      {problem ? (
        <p className="text-sm text-destructive" role="alert">
          {t(problem === "denied" ? "pushDenied" : "syncServer")}
        </p>
      ) : null}
    </div>
  );
}

/** One reader's way over (Feedly, Inoreader, Substack): a row that opens its numbered steps, as on Android. */
function ImportGuide({ title, steps, note }: { title: string; steps: string[]; note?: string }) {
  return (
    <details className="group rounded-md border">
      <summary className="flex cursor-pointer list-none items-center justify-between px-3 py-2 text-sm font-medium">
        {title}
        <ChevronDown className="size-4 text-muted-foreground transition-transform group-open:rotate-180" aria-hidden="true" />
      </summary>
      <div className="space-y-3 px-3 pb-3">
        <ol className="space-y-3">
          {steps.map((step, i) => (
            <li key={i} className="flex items-center gap-3 text-sm">
              <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-primary text-xs font-medium text-primary-foreground" aria-hidden="true">
                {i + 1}
              </span>
              <span>{step}</span>
            </li>
          ))}
        </ol>
        {note ? <p className="text-xs text-muted-foreground">{note}</p> : null}
      </div>
    </details>
  );
}
