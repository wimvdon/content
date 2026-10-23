import { ArrowDown, ArrowUp, Pencil, Plus, Trash2 } from "lucide-react";
import { ConfirmRemove } from "./ConfirmRemove";
import { useEffect, useState } from "react";
import { gravatarUrl } from "@/lib/account";
import { app, useApp, type ListView, type Theme } from "@/lib/app";
import { formatRelative, t, type Key } from "@/lib/i18n";
import { searchIcons } from "@/lib/icons";
import type { Feed, Folder } from "@/lib/library";
import { FeedIcon } from "./FeedIcon";
import { FolderIcon, SymbolSvg } from "./FolderIcon";
import { useIcons } from "./useIcons";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { Dialog } from "./ui/dialog";
import { Input, Label, Select } from "./ui/input";
import { Segmented } from "./ui/segmented";
import { ReaderStyleControls } from "./ReaderStyleControls";
import { InstapaperSettings } from "./ServiceSettings";
import { SwitchRow } from "./ui/switch";

type Props = { open: boolean; onOpenChange: (open: boolean) => void };

/** Opened with a new key each time (App), so the address starts empty. */
export function AddFeedDialog({ open, onOpenChange, folderId }: Props & { folderId: string | null }) {
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const add = async () => {
    setBusy(true);
    setError(null);
    const result = await app.addFeed(input, folderId);
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
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            {t("cancel")}
          </Button>
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

export function ManageDialog({ open, onOpenChange, onAddFeed }: Props & { onAddFeed: () => void }) {
  const { library } = useApp();
  const [editFeed, setEditFeed] = useState<Feed | null>(null);
  const [editFolder, setEditFolder] = useState<Folder | "new" | null>(null);
  const groups: [Folder | null, Feed[]][] = [
    ...library.folders.map((f) => [f, library.feeds.filter((x) => x.folderId === f.id)] as [Folder, Feed[]]),
    [null, library.feeds.filter((x) => !x.folderId)],
  ];
  return (
    <>
      <Dialog
        open={open}
        onOpenChange={onOpenChange}
        title={t("feedsAndFolders")}
        closeLabel={t("close")}
        wide
        footer={
          <>
            <Button variant="tonal" onClick={() => setEditFolder("new")}>
              <Plus />
              {t("newFolder")}
            </Button>
            <Button onClick={onAddFeed}>
              <Plus />
              {t("addFeed")}
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          {groups.map(([folder, feeds], gi) => (
            <section key={folder?.id ?? "none"} className="rounded-xl bg-muted/60 p-2">
              <div className="flex items-center gap-2 px-2 py-1">
                <span className="inline-flex size-8 items-center justify-center rounded-full bg-accent text-accent-foreground">
                  {folder ? <FolderIcon name={folder.icon} className="size-4" /> : <FolderIcon name="folder-off" className="size-4" />}
                </span>
                <h3 className="flex-1 truncate font-medium">{folder?.title ?? t("noFolder")}</h3>
                {folder ? (
                  <>
                    <Button variant="ghost" size="iconSm" disabled={gi === 0} onClick={() => app.moveFolderBy(folder.id, -1)} aria-label={t("moveUp")}>
                      <ArrowUp />
                    </Button>
                    <Button variant="ghost" size="iconSm" disabled={gi >= library.folders.length - 1} onClick={() => app.moveFolderBy(folder.id, 1)} aria-label={t("moveDown")}>
                      <ArrowDown />
                    </Button>
                    <Button variant="ghost" size="iconSm" onClick={() => setEditFolder(folder)} aria-label={t("edit")}>
                      <Pencil />
                    </Button>
                  </>
                ) : null}
              </div>
              {feeds.length === 0 ? <p className="px-3 py-2 text-sm text-muted-foreground">{folder ? t("folderEmpty") : t("noFolderEmpty")}</p> : null}
              <ul>
                {feeds.map((feed, index) => {
                  return (
                    <li key={feed.url} className="flex items-center gap-2 rounded-lg px-2 py-1.5 hover:bg-card">
                      <FeedIcon feed={feed} />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm">{feed.title}</span>
                        <span className="block truncate text-xs text-muted-foreground">{feed.url}</span>
                      </span>
                      <Button variant="ghost" size="iconSm" disabled={index === 0} onClick={() => app.moveFeedBy(feed.url, -1)} aria-label={t("moveUp")}>
                        <ArrowUp />
                      </Button>
                      <Button variant="ghost" size="iconSm" disabled={index === feeds.length - 1} onClick={() => app.moveFeedBy(feed.url, 1)} aria-label={t("moveDown")}>
                        <ArrowDown />
                      </Button>
                      <Button variant="ghost" size="iconSm" onClick={() => setEditFeed(feed)} aria-label={t("edit")}>
                        <Pencil />
                      </Button>
                    </li>
                  );
                })}
              </ul>
            </section>
          ))}
        </div>
      </Dialog>
      <EditFeedDialog key={`feed:${editFeed?.url ?? ""}`} feed={editFeed} folders={library.folders} onClose={() => setEditFeed(null)} />
      <FolderDialog key={`folder:${editFolder === "new" ? "new" : (editFolder?.id ?? "")}`} folder={editFolder} onClose={() => setEditFolder(null)} />
    </>
  );
}

function EditFeedDialog({ feed, folders, onClose }: { feed: Feed | null; folders: Folder[]; onClose: () => void }) {
  const [title, setTitle] = useState(feed?.title ?? "");
  const [folderId, setFolderId] = useState(feed?.folderId ?? "");
  const [confirming, setConfirming] = useState(false);
  if (!feed) return null;
  const save = () => {
    if (title.trim() !== feed.title) app.renameFeed(feed.url, title);
    if ((folderId || null) !== feed.folderId) app.moveFeed(feed.url, folderId || null);
    onClose();
  };
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={t("editFeed")}
      description={feed.url}
      closeLabel={t("close")}
      footer={
        <>
          <Button
            variant="destructive"
            className="mr-auto"
            onClick={() => setConfirming(true)}
          >
            <Trash2 />
            {t("remove")}
          </Button>
          <Button variant="ghost" onClick={onClose}>
            {t("cancel")}
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
        <div className="space-y-1.5">
          <Label htmlFor="feed-folder">{t("folder")}</Label>
          <Select id="feed-folder" value={folderId} onChange={(e) => setFolderId(e.currentTarget.value)}>
            <option value="">{t("noFolder")}</option>
            {folders.map((f) => (
              <option key={f.id} value={f.id}>
                {f.title}
              </option>
            ))}
          </Select>
        </div>
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

function FolderDialog({ folder, onClose }: { folder: Folder | "new" | null; onClose: () => void }) {
  const icons = useIcons();
  const [title, setTitle] = useState(folder && folder !== "new" ? folder.title : "");
  const [icon, setIcon] = useState(folder && folder !== "new" ? folder.icon : "folder");
  const [query, setQuery] = useState("");
  const [confirming, setConfirming] = useState(false);
  if (!folder) return null;
  const save = () => {
    if (folder === "new") app.addFolder(title, icon);
    else app.updateFolder(folder.id, title, icon);
    onClose();
  };
  const found = icons ? searchIcons(icons, query) : [];
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={folder === "new" ? t("newFolder") : t("editFolder")}
      closeLabel={t("close")}
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
          <Button variant="ghost" onClick={onClose}>
            {t("cancel")}
          </Button>
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
            onClose();
          }}
          onClose={() => setConfirming(false)}
        />
      ) : null}
    </Dialog>
  );
}

/** Settings in the order DESIGN.md gives: the account first, then the app's own, then appearance. */
export function SettingsDialog({ open, onOpenChange, onImport }: Props & { onImport: () => void }) {
  const { settings, library } = useApp();
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={t("settings")} closeLabel={t("close")} wide>
      <div className="space-y-6">
        <AccountSection />
        <InstapaperSettings />
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
        </section>
        <section className="space-y-2">
          <h3 className="text-sm font-medium text-primary">{t("articleList")}</h3>
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
        <section className="space-y-2">
          <h3 className="text-sm font-medium text-primary">{t("reader")}</h3>
          <SwitchRow title={t("fullArticles")} summary={t("fullArticlesSummary")} checked={settings.fullArticles} onChange={(v) => app.updateSettings({ fullArticles: v })} />
          <SwitchRow title={t("showImages")} summary={t("showImagesSummary")} checked={settings.showImages} onChange={(v) => app.updateSettings({ showImages: v })} />
          <div className="pt-2">
            <ReaderStyleControls preview />
          </div>
        </section>
        <section className="space-y-2">
          <h3 className="text-sm font-medium text-primary">{t("appearance")}</h3>
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
        </section>
        <p className="text-xs text-muted-foreground">
          Reader ·{" "}
          <a className="underline" href="https://github.com/wimvdon/reader/blob/main/PRIVACY.md" target="_blank" rel="noopener noreferrer">
            {t("privacy")}
          </a>
        </p>
      </div>
    </Dialog>
  );
}

/**
 * The Content account (#166, docs/account.md): sign in with an emailed link (or with Instapaper,
 * which opens one too). Signed in: who (the address and its Gravatar picture), how sync is doing,
 * Sync now, Add email address (an account opened with Instapaper), Sign out and Delete synced data.
 */
function AccountSection() {
  const { settings, syncStatus, account } = useApp();
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleteFailed, setDeleteFailed] = useState(false);
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
      <h3 className="text-sm font-medium text-primary">{t("account")}</h3>
      {account ? (
        <>
          <div className="flex items-center gap-3">
            {account.email && settings.showImages ? <Gravatar key={account.email} email={account.email} /> : null}
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">{account.email ?? t("accountTitle")}</p>
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
          <Button variant="ghost" onClick={() => app.connectInstapaper()}>
            {t("accountSignInInstapaper")}
          </Button>
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
function EmailLinkForm() {
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
        <Label htmlFor="account-email">{t("accountEmail")}</Label>
        <Input
          id="account-email"
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

