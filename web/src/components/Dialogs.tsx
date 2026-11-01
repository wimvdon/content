import { ArrowDown, ArrowLeft, ArrowUp, ChevronDown, EllipsisVertical, Merge, Pencil, Plus, Trash2 } from "lucide-react";
import { useEscape } from "./ui/useEscape";
import { ConfirmRemove } from "./ConfirmRemove";
import { TopicChips, chip, chipOn } from "./TopicChips";
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
import { Input, Label, Select } from "./ui/input";
import { Segmented } from "./ui/segmented";
import { ReaderStyleControls } from "./ReaderStyleControls";
import { InstapaperSettings } from "./ServiceSettings";
import { topicCounts } from "@/lib/topics";
import { SwitchRow } from "./ui/switch";
import { pushSupported } from "@/lib/notifications";

type Props = { open: boolean; onOpenChange: (open: boolean) => void };

/**
 * Add feed, opened with a new key each time (App), so the address starts empty. Then its topic as
 * chips (No topic, then every topic with its icon), with New topic and Edit at the end to make,
 * rename, reorder or remove topics right there (#314), as on Android.
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
 * Manage topics, as on Android: the topics with what they hold (feeds and saved articles, and a
 * feed that fails), in the user's order; then the feeds without a topic, loose; Add feed and New
 * topic. Clicking a topic opens its page in the same dialog, for managing it (reading is in Feeds
 * and Saved): its feeds (reorder, edit, add one to it), how many saved articles have it, Edit, and
 * Merge with… and Remove in the ⋮ menu (rarely needed, so they stay out of the way).
 */
export function ManageDialog({
  open,
  onOpenChange,
  onAddFeed,
  topic: initialTopic,
}: Props & { onAddFeed: (topicId: string | null) => void; topic?: string }) {
  const { library, problems } = useApp();
  const counts = useMemo(() => topicCounts(library), [library]);
  // undefined: the list; an id: that topic's page.
  const [shown, setShown] = useState<string | undefined>(initialTopic);
  const [editFeed, setEditFeed] = useState<Feed | null>(null);
  const [editFolder, setEditFolder] = useState<Folder | "new" | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [merging, setMerging] = useState(false);
  const [confirmRemove, setConfirmRemove] = useState(false);
  useEscape(menuOpen, () => setMenuOpen(false));
  const loose = library.feeds.filter((f) => !f.folderId || !library.folders.some((x) => x.id === f.folderId));
  const topic = shown ? library.folders.find((f) => f.id === shown) : undefined;
  const feeds = topic ? library.feeds.filter((f) => f.folderId === topic.id) : [];
  const failing = (list: Feed[]) => list.filter((f) => problems.has(f.url)).length;
  const detail = (feedsIn: number, saved: number, fails: number) =>
    [plural("feedsCount", feedsIn), plural("topicSavedCount", saved), ...(fails > 0 ? [plural("topicFailingCount", fails)] : [])].join(" · ");
  const count = topic ? counts.get(topic.id) : undefined;
  return (
    <>
      <Dialog
        open={open}
        onOpenChange={onOpenChange}
        title={topic ? topic.title : t("manageTitle")}
        closeLabel={t("close")}
        wide
        footer={
          topic ? (
            <>
              <div className="relative mr-auto">
                <Button variant="ghost" size="icon" onClick={() => setMenuOpen(!menuOpen)} aria-expanded={menuOpen} aria-label={t("moreOptions")} title={t("moreOptions")}>
                  <EllipsisVertical />
                </Button>
                {menuOpen ? (
                  <>
                    <div className="fixed inset-0 z-20" onClick={() => setMenuOpen(false)} aria-hidden="true" />
                    <div role="menu" className="absolute bottom-full left-0 z-30 mb-1 w-56 rounded-xl border bg-card py-1 shadow-lg">
                      {library.folders.length > 1 ? (
                        <MenuButton
                          icon={<Merge />}
                          label={t("topicMerge")}
                          onClick={() => {
                            setMenuOpen(false);
                            setMerging(true);
                          }}
                        />
                      ) : null}
                      <MenuButton
                        icon={<Trash2 />}
                        label={t("topicRemove")}
                        destructive
                        onClick={() => {
                          setMenuOpen(false);
                          setConfirmRemove(true);
                        }}
                      />
                    </div>
                  </>
                ) : null}
              </div>
              <Button variant="outline" onClick={() => setEditFolder(topic)}>
                <Pencil />
                {t("editFolder")}
              </Button>
            </>
          ) : (
            // Two actions: Add feed (the more frequent) filled, New topic quieter.
            <>
              <Button variant="tonal" onClick={() => setEditFolder("new")}>
                <Plus />
                {t("newFolder")}
              </Button>
              <Button onClick={() => onAddFeed(null)}>
                <Plus />
                {t("addFeed")}
              </Button>
            </>
          )
        }
      >
        {topic ? (
          <div className="space-y-5">
            <Button variant="ghost" size="sm" onClick={() => setShown(undefined)}>
              <ArrowLeft />
              {t("manageTitle")}
            </Button>
            <section className="space-y-1">
              <h3 className="text-sm font-medium text-primary">{t("feeds")}</h3>
              <FeedRows feeds={feeds} onEdit={setEditFeed} />
              <Button variant="ghost" onClick={() => onAddFeed(topic.id)}>
                <Plus />
                {t("topicAddFeed").replace("{name}", topic.title)}
              </Button>
            </section>
            <section className="space-y-1">
              <h3 className="text-sm font-medium text-primary">{t("saved")}</h3>
              <p className="text-sm">
                {plural("topicSavedCount", count?.saved ?? 0)} · {plural("topicToReadCount", count?.toRead ?? 0)}
              </p>
            </section>
          </div>
        ) : (
          <div className="space-y-4">
            {library.folders.length === 0 ? <p className="text-sm text-muted-foreground">{t("foldersExplanation")}</p> : null}
            <ul className="space-y-1">
              {library.folders.map((f, i) => {
                const inTopic = library.feeds.filter((x) => x.folderId === f.id);
                const fails = failing(inTopic);
                return (
                  <li key={f.id} className="flex items-center gap-2 rounded-xl bg-muted/60 p-2">
                    <button type="button" onClick={() => setShown(f.id)} className="flex min-w-0 flex-1 cursor-pointer items-center gap-3 text-left">
                      <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-full bg-accent text-accent-foreground">
                        <FolderIcon name={f.icon} className="size-4" />
                      </span>
                      <span className="min-w-0">
                        <span className="block truncate font-medium">{f.title}</span>
                        <span className={cn("block truncate text-xs", fails > 0 ? "text-destructive" : "text-muted-foreground")}>
                          {detail(inTopic.length, counts.get(f.id)?.saved ?? 0, fails)}
                        </span>
                      </span>
                    </button>
                    <Button variant="ghost" size="iconSm" disabled={i === 0} onClick={() => app.moveFolderBy(f.id, -1)} aria-label={t("moveUp")}>
                      <ArrowUp />
                    </Button>
                    <Button variant="ghost" size="iconSm" disabled={i === library.folders.length - 1} onClick={() => app.moveFolderBy(f.id, 1)} aria-label={t("moveDown")}>
                      <ArrowDown />
                    </Button>
                  </li>
                );
              })}
            </ul>
            {/* The feeds without a topic, loose under the topics (there is no "No topic" page). */}
            {loose.length > 0 ? <FeedRows feeds={loose} onEdit={setEditFeed} /> : null}
          </div>
        )}
      </Dialog>
      <EditFeedDialog key={`feed:${editFeed?.url ?? ""}`} feed={editFeed} folders={library.folders} onClose={() => setEditFeed(null)} />
      <FolderDialog
        key={`folder:${editFolder === "new" ? "new" : (editFolder?.id ?? "")}`}
        folder={editFolder}
        onClose={() => setEditFolder(null)}
        onCreated={(id) => setShown(id)}
      />
      {topic ? (
        <>
          <MergeDialog
            key={`merge:${topic.id}:${String(merging)}`}
            open={merging}
            topic={topic}
            others={library.folders.filter((f) => f.id !== topic.id)}
            onMerge={(into) => {
              app.mergeTopic(topic.id, into);
              setMerging(false);
              setShown(into);
            }}
            onClose={() => setMerging(false)}
          />
          <ConfirmRemove
            open={confirmRemove}
            title={t("removeFolderTitle")}
            body={t("removeFolderBody").replace("{name}", topic.title)}
            onConfirm={() => {
              app.removeFolder(topic.id);
              setConfirmRemove(false);
              setShown(undefined);
            }}
            onClose={() => setConfirmRemove(false)}
          />
        </>
      ) : null}
    </>
  );
}

/** Feeds as rows, on a topic's page and (those without a topic) in Manage topics: reorder and edit. */
function FeedRows({ feeds, onEdit }: { feeds: Feed[]; onEdit: (feed: Feed) => void }) {
  const { problems } = useApp();
  return (
    <ul>
      {feeds.map((feed, index) => (
        <li key={feed.url} className="flex items-center gap-2 rounded-lg px-2 py-1.5 hover:bg-muted">
          <FeedIcon feed={feed} />
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm">{feed.title}</span>
            <span className={cn("block truncate text-xs", problems.has(feed.url) ? "text-destructive" : "text-muted-foreground")}>
              {problems.has(feed.url) ? t("feedFailing") : feed.url}
            </span>
          </span>
          <Button variant="ghost" size="iconSm" disabled={index === 0} onClick={() => app.moveFeedBy(feed.url, -1)} aria-label={t("moveUp")}>
            <ArrowUp />
          </Button>
          <Button variant="ghost" size="iconSm" disabled={index === feeds.length - 1} onClick={() => app.moveFeedBy(feed.url, 1)} aria-label={t("moveDown")}>
            <ArrowDown />
          </Button>
          <Button variant="ghost" size="iconSm" onClick={() => onEdit(feed)} aria-label={t("edit")}>
            <Pencil />
          </Button>
        </li>
      ))}
    </ul>
  );
}

function MenuButton({ icon, label, onClick, destructive }: { icon: React.ReactNode; label: string; onClick: () => void; destructive?: boolean }) {
  return (
    <button
      type="button"
      role="menuitem"
      onClick={onClick}
      className={cn(
        "flex w-full cursor-pointer items-center gap-3 px-3 py-2 text-left text-sm hover:bg-muted [&_svg]:size-4",
        destructive ? "text-destructive [&_svg]:text-destructive" : "[&_svg]:text-muted-foreground",
      )}
    >
      {icon}
      {label}
    </button>
  );
}

/** Merge with…: pick the topic that stays; it gets this one's feeds and saved articles. */
function MergeDialog({ open, topic, others, onMerge, onClose }: { open: boolean; topic: Folder; others: Folder[]; onMerge: (into: string) => void; onClose: () => void }) {
  const [into, setInto] = useState("");
  return (
    <Dialog
      open={open}
      onOpenChange={(o) => !o && onClose()}
      title={`${topic.title}: ${t("topicMerge")}`}
      description={t("topicMergeBody")}
      closeLabel={t("close")}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t("cancel")}
          </Button>
          <Button disabled={!into} onClick={() => onMerge(into)}>
            {t("topicMergeAction")}
          </Button>
        </>
      }
    >
      <Select aria-label={t("topicMerge")} value={into} onChange={(e) => setInto(e.currentTarget.value)} className="w-full">
        <option value="">…</option>
        {others.map((f) => (
          <option key={f.id} value={f.id}>
            {f.title}
          </option>
        ))}
      </Select>
    </Dialog>
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
            onRemoved?.(folder.title);
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
        {/* Connection first: the account, then the service Saved is also kept in. */}
        <InstapaperSettings />
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
        {/* Moving to or from another reader: data in and out, before Appearance (DESIGN.md's order). */}
        <section className="space-y-2 pt-2">
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
          Content ·{" "}
          <a className="underline" href="https://github.com/wimvdon/content/blob/main/PRIVACY.md" target="_blank" rel="noopener noreferrer">
            {t("privacy")}
          </a>
        </p>
      </div>
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
      <h3 className="text-sm font-medium text-primary">{t("account")}</h3>
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
