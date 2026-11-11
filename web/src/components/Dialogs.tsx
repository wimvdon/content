import { Smartphone, ArrowLeft, BookmarkPlus, BookOpenText, ChevronDown, ChevronRight, Copy, BookOpen, Mail, Palette, Plus, Rss, Tag as TagIcon, Trash2, UserRound } from "lucide-react";
import { ConfirmRemove, RemoveButton } from "./ConfirmRemove";
import { HiddenWordsRow } from "./HiddenWords";
import { insertedBefore } from "@/lib/topics";
import { CHIP_TYPE, EditChips, TopicChips, chip, chipOn } from "./TopicChips";
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
import { canCreate, ownStep } from "@/lib/newsletterSubscribe";
import { codeOfFeed, groupedAddresses, type NewsletterAddress } from "@/lib/newsletterAddresses";
import { feedsOfKind, withKindOrder, type FeedKind } from "@/lib/feedKinds";
import { SwitchRow } from "./ui/switch";
import { pushSupported } from "@/lib/notifications";

import type { ReactNode } from "react";

type Props = { open: boolean; onOpenChange: (open: boolean) => void };

/**
 * Add feed, opened with a new key each time (App), so the address starts empty. Then its topic as
 * chips (No topic, then every topic with its icon), with Edit topics at the end (Manage topics as a
 * page of this dialog, with a back arrow), as on Android.
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
        <h3 className="text-sm font-semibold">{t("topic")}</h3>
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
/** A topic's feeds (null: No topic, with those of a topic that is gone). */
function inTopic(feeds: Feed[], folders: Folder[], topicId: string | null): Feed[] {
  return feeds.filter((f) => (topicId === null ? f.folderId === null || !folders.some((x) => x.id === f.folderId) : f.folderId === topicId));
}

export function ManageFeedsDialog({ open, onOpenChange, kind, onAddFeed, children }: Props & { kind: FeedKind; onAddFeed: () => void; children?: ReactNode }) {
  const { library, problems } = useApp();
  const [editing, setEditing] = useState<Feed | null>(null);
  const shown = useMemo(() => feedsOfKind(library.feeds, kind), [library, kind]);
  const groups = useMemo(() => {
    // Every topic, so a feed can be dragged into an empty one; none at all without feeds.
    if (shown.length === 0) return [];
    return [...library.folders.map((topic) => [topic, inTopic(shown, library.folders, topic.id)] as const), [null, inTopic(shown, library.folders, null)] as const];
  }, [library, shown]);
  // The topic's whole order, the other kind's feeds in their places.
  const fullOrder = (topicId: string | null, urls: string[], moved?: string) =>
    withKindOrder(
      inTopic(library.feeds, library.folders, topicId)
        .map((f) => f.url)
        .filter((u) => u !== moved),
      urls,
    );
  // A feed dragged from another topic into `topicId`, before the feed `before` (null: at the end).
  const dropInto = (topicId: string | null, feeds: Feed[], url: string, before: string | null) =>
    app.moveFeedInto(url, topicId, fullOrder(topicId, insertedBefore(feeds.map((f) => f.url), url, before), url));
  const newsletters = kind === "newsletter";
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={t(newsletters ? "newsletterFeeds" : "rssFeeds")} closeLabel={t("close")} done={t("done")}>
      <p className="pb-3 text-sm text-muted-foreground">{t(shown.length > 0 ? "manageFeedsHint" : newsletters ? "manageNewslettersNone" : "manageFeedsNone")}</p>
      <div className="space-y-4">
        {groups.map(([topic, feeds]) => (
          <section
            key={topic?.id ?? ""}
            className="space-y-2"
            // A feed of another topic dropped here (not on a chip): at the end of this topic.
            onDragOver={(e) => e.preventDefault()}
            onDrop={(e) => {
              e.preventDefault();
              const url = e.dataTransfer.getData(CHIP_TYPE);
              if (url && !feeds.some((f) => f.url === url)) dropInto(topic?.id ?? null, feeds as Feed[], url, null);
            }}
          >
            <h3 className="flex items-center gap-1.5 text-sm font-medium text-muted-foreground">
              {topic ? <FolderIcon name={topic.icon} className="size-4" /> : null}
              {topic?.title ?? t("noFolder")}
            </h3>
            <div className="flex flex-wrap items-center gap-1.5">
              {feeds.length === 0 ? (
                <span className="inline-flex h-8 items-center rounded-lg border border-dashed px-3 text-sm text-muted-foreground">{t("manageFeedsDropHere")}</span>
              ) : (
                <EditChips
                  items={feeds as Feed[]}
                  id={(f) => f.url}
                  label={(f) => f.title}
                  icon={(f) => <FeedIcon feed={f} className="size-4" />}
                  onReorder={(urls) => app.reorderFeedsIn(topic?.id ?? null, fullOrder(topic?.id ?? null, urls))}
                  onDropFrom={(url, before) => dropInto(topic?.id ?? null, feeds as Feed[], url, before)}
                  onEdit={setEditing}
                  editLabel={t("editFeed")}
                  error={(f) => problems.has(f.url)}
                />
              )}
            </div>
          </section>
        ))}
        <Button variant="secondary" onClick={onAddFeed}>
          <Plus />
          {t(newsletters ? "newsletterOwnNew" : "addFeed")}
        </Button>
      </div>
      {editing ? <EditFeedDialog key={editing.url} feed={editing} onClose={() => setEditing(null)} /> : null}
      {/* Add feed (New address for newsletter feeds), as a page of this dialog. */}
      {children}
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
  // A newsletter address's feed: its email address, read from the account, shown with Copy.
  const code = codeOfFeed(feed.url);
  const [email, setEmail] = useState<string | null | undefined>(undefined);
  useEffect(() => {
    if (!code) return;
    let live = true;
    void app.newsletterAddresses().then((list) => live && setEmail(list?.find((a) => a.code === code)?.address ?? null));
    return () => {
      live = false;
    };
  }, [code]);
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
      description={code ? undefined : feed.url}
      closeLabel={t("close")}
      cancel={t("cancel")}
      // Remove as a trash can at the top right, away from Cancel and Save; it asks first.
      headerAction={<RemoveButton onClick={() => setConfirming(true)} />}
      footer={<Button onClick={save}>{t("save")}</Button>}
    >
      <div className="space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="feed-title">{t("name")}</Label>
          <Input id="feed-title" value={title} onChange={(e) => setTitle(e.target.value)} onKeyDown={(e) => e.key === "Enter" && save()} />
          <p className="text-xs text-muted-foreground">{t("nameHint")}</p>
        </div>
        {problems.has(feed.url) ? <p className="text-sm text-destructive">{t("feedFailing")}</p> : null}
        {code ? (
          <section className="space-y-2">
            <h3 className="text-sm font-semibold">{t("newsletterFeedLabel")}</h3>
            {email ? <AddressRow address={email} /> : email === null ? <p className="text-sm text-muted-foreground">{t("newsletterOwnOffline")}</p> : null}
          </section>
        ) : null}
        <section className="space-y-2">
          <h3 className="text-sm font-semibold">{t("topic")}</h3>
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

/** A heading in the Add dialog: its destination's icon and name. */
function heading(Icon: typeof Rss, title: string) {
  return (
    <h3 className="flex items-center gap-1.5 text-sm font-medium text-muted-foreground">
      <Icon className="size-4" aria-hidden="true" />
      {title}
    </h3>
  );
}

/** One choice in a dialog: its icon, what it does, and a line on it. */
function choice(Icon: typeof Rss, title: string, body: string, onClick: () => void) {
  return (
    <button type="button" onClick={onClick} className="flex w-full cursor-pointer items-center gap-4 rounded-lg border bg-card p-4 text-left shadow-xs transition-colors hover:bg-accent">
      <Icon className="size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
      <span className="min-w-0">
        <span className="block font-medium">{title}</span>
        <span className="block text-sm text-muted-foreground">{body}</span>
      </span>
    </button>
  );
}

/**
 * The search bar's +, the same in Feed, Saved and the Inbox, as on Android: first what to add,
 * under two headings with their destination's icon (2026-11-11). Add feed: RSS feed opens Add feed,
 * Newsletter feed a page of this dialog that makes an email address whose newsletters arrive as a
 * feed (newsletterAddresses.ts). Save article: Save article opens the save sheet. Both then ask
 * for the address.
 */
export function AddChoiceDialog({ open, onOpenChange, onFeed, onArticle, onSignIn }: Props & { onFeed: () => void; onArticle: () => void; onSignIn: () => void }) {
  const [newsletter, setNewsletter] = useState(false);
  // However it closes, it opens again at the three choices.
  const leave = (then: () => void) => () => {
    setNewsletter(false);
    then();
  };
  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        if (!o) setNewsletter(false);
        onOpenChange(o);
      }}
      title={t("addTitle")}
      closeLabel={t("close")}
      cancel={t("cancel")}
    >
      <div className="space-y-5">
        <section className="space-y-2">
          {heading(Rss, t("addFeed"))}
          {choice(Rss, t("addChoiceFeed"), t("addChoiceFeedBody"), leave(onFeed))}
          {choice(Mail, t("addChoiceNewsletter"), t("addChoiceNewsletterBody"), () => setNewsletter(true))}
        </section>
        <section className="space-y-2">
          {heading(BookOpen, t("addChoiceArticle"))}
          {choice(BookmarkPlus, t("addChoiceArticle"), t("addChoiceArticleBody"), leave(onArticle))}
        </section>
      </div>
      <NewsletterDialog open={newsletter} onOpenChange={setNewsletter} onSignIn={leave(onSignIn)} />
    </Dialog>
  );
}

/**
 * Newsletter feed: an address of your own (Content's feed loader). From Settings' Newsletter feeds
 * it opens at New address (`startCreating`), as a page of Manage feeds.
 */
export function NewsletterDialog({
  open,
  onOpenChange,
  onSignIn,
  startCreating = false,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSignIn: () => void;
  startCreating?: boolean;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={t("addChoiceNewsletter")} closeLabel={t("close")} backLabel={t("back")}>
      {open ? <OwnAddresses onSignIn={onSignIn} onDone={() => onOpenChange(false)} startCreating={startCreating} /> : null}
    </Dialog>
  );
}

/**
 * Your own address (newsletterAddresses.ts), like Add feed: first the account's addresses by topic,
 * then New address: a name for its feed and a topic (No topic, every topic), then Create address;
 * once made, the address with Copy. Without a Content account, Sign in first. The addresses' feeds
 * are in Settings' Newsletter feeds afterwards, each with its address.
 */
function OwnAddresses({ onSignIn, onDone, startCreating }: { onSignIn: () => void; onDone: () => void; startCreating: boolean }) {
  const { account, library } = useApp();
  const [name, setName] = useState("");
  const [topicId, setTopicId] = useState<string | null>(null);
  const chosen = topicId && library.folders.some((f) => f.id === topicId) ? topicId : null;
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  const [made, setMade] = useState<NewsletterAddress | null>(null);
  // The account's addresses first, if there are any (undefined: still loading); New address opens the form.
  const [existing, setExisting] = useState<NewsletterAddress[] | undefined>(undefined);
  const [creating, setCreating] = useState(startCreating);
  const signedIn = ownStep(!!account) === "addresses";
  useEffect(() => {
    if (!signedIn) return;
    let live = true;
    void app.newsletterAddresses().then((list) => live && setExisting(list ?? []));
    return () => {
      live = false;
    };
  }, [signedIn]);
  const create = async () => {
    setBusy(true);
    const result = await app.createNewsletterAddress(name.trim(), chosen);
    setBusy(false);
    if (result) setMade(result);
    else setFailed(true);
  };
  if (signedIn && existing === undefined) return <p className="text-sm">{t("newsletterOwnExplained")}</p>;
  const groups = groupedAddresses(existing ?? [], library.feeds, library.folders);
  if (signedIn && !made && !creating && groups.length > 0) {
    return (
      <div className="space-y-4">
        {/* Your addresses, by topic (as in Feeds), each with Copy; then New address. */}
        {groups.map((g) => (
          <section key={g.topic?.id ?? ""} className="space-y-2">
            <h3 className="text-sm font-semibold">{g.topic?.title ?? t("noFolder")}</h3>
            {g.addresses.map(({ name, address }) => (
              <AddressRow key={address.code} address={address.address} name={name} />
            ))}
          </section>
        ))}
        <div className="flex justify-end">
          <Button onClick={() => setCreating(true)}>
            <Plus />
            {t("newsletterOwnNew")}
          </Button>
        </div>
      </div>
    );
  }
  if (!signedIn) {
    return (
      <div className="space-y-3">
        <p className="text-sm">{t("newsletterOwnExplained")}</p>
        <p className="text-sm text-muted-foreground">{t("newsletterOwnSignInFirst")}</p>
        <Button onClick={onSignIn}>{t("newsletterOwnSignIn")}</Button>
      </div>
    );
  }
  if (made) {
    return (
      <div className="space-y-3">
        <p className="text-sm">{t("newsletterOwnMade")}</p>
        <AddressRow address={made.address} />
        <div className="flex justify-end">
          <Button onClick={onDone}>{t("done")}</Button>
        </div>
      </div>
    );
  }
  return (
    <div className="space-y-4">
      <p className="text-sm">{t("newsletterOwnExplained")}</p>
      <div className="space-y-1.5">
        <Label htmlFor="newsletter-name">{t("name")}</Label>
        <Input
          id="newsletter-name"
          autoFocus
          value={name}
          disabled={busy}
          onChange={(e) => {
            setName(e.target.value.slice(0, 100));
            setFailed(false);
          }}
          onKeyDown={(e) => {
            if (e.key === "Enter" && canCreate(name, busy)) void create();
          }}
        />
        {failed ? <p className="text-sm text-destructive">{t("newsletterOwnFailed")}</p> : null}
      </div>
      <section className="space-y-2">
        <h3 className="text-sm font-semibold">{t("topic")}</h3>
        <TopicChips
          topics={library.folders}
          selected={(topic) => chosen === topic.id}
          onToggle={(topic) => setTopicId(chosen === topic.id ? null : topic.id)}
          leading={
            <button type="button" aria-pressed={chosen === null} onClick={() => setTopicId(null)} className={cn(chip, chosen === null ? chipOn : "hover:bg-muted")}>
              {t("noFolder")}
            </button>
          }
        />
      </section>
      <div className="flex justify-end">
        <Button disabled={!canCreate(name, busy)} onClick={() => void create()}>
          {t("newsletterOwnCreate")}
        </Button>
      </div>
    </div>
  );
}

/** A newsletter address with Copy. */
function AddressRow({ address, name }: { address: string; name?: string }) {
  return (
    <div className="flex items-center gap-2 rounded-lg border p-3">
      <div className="min-w-0 flex-1">
        {name ? <p className="truncate text-sm font-medium">{name}</p> : null}
        <p className="truncate text-sm text-muted-foreground">{address}</p>
      </div>
      <Button variant="ghost" size="icon" aria-label={t("newsletterOwnCopy")} title={t("newsletterOwnCopy")} onClick={() => void navigator.clipboard?.writeText(address).catch(() => undefined)}>
        <Copy />
      </Button>
    </div>
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
      headerAction={folder !== "new" ? <RemoveButton onClick={() => setConfirming(true)} /> : undefined}
      footer={
        <Button onClick={save} disabled={!title.trim()}>
          {t("save")}
        </Button>
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

/** Settings' pages, one per part of the app (2026-11-11): Feed, Saved, the article, and how everything looks. */
type SettingsPage = "account" | "feed" | "saved" | "reader" | "appearance";

/**
 * Settings, as on Android (2026-11-11): two large buttons side by side for the two kinds of feed,
 * RSS feeds and Newsletter feeds (Manage feeds with that kind), and a wide one under them for
 * Manage tags (dialogs of their own, so they open from other places too); then one row per page
 * (Account, Feed, Saved, Article view, Appearance), each with its icon, what it holds and a chevron.
 * A page opens in the same dialog, with ← Settings to go back.
 */
export function SettingsDialog({
  open,
  onOpenChange,
  onImport,
  onManageFeeds,
  onManageTopics,
  children,
}: Props & { onImport: () => void; onManageFeeds: (kind: FeedKind) => void; onManageTopics: () => void; children?: ReactNode }) {
  const { settings, library, account } = useApp();
  // App opens it with a new key each time, so it starts on the main page.
  const [page, setPage] = useState<SettingsPage | null>(null);
  const titles: Record<SettingsPage, string> = {
    account: t("account"),
    feed: t("feedTab"),
    saved: t("saved"),
    reader: t("articleView"),
    appearance: t("appearance"),
  };
  const themeNames: Record<Theme, string> = { system: t("themeSystem"), light: t("themeLight"), dark: t("themeDark") };
  const viewNames: Record<ListView, string> = { small: t("listSmall"), medium: t("listMedium"), large: t("listLarge") };
  const rssCount = feedsOfKind(library.feeds, "rss").length;
  const newsletterCount = library.feeds.length - rssCount;
  // Side by side: the icon above the text; wide (Manage tags, under them): the icon before it.
  const big = (Icon: typeof Rss, title: string, detail: string, onClick: () => void, wide = false) => (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex cursor-pointer gap-3 rounded-lg border bg-card p-4 text-left shadow-xs transition-colors hover:bg-accent",
        wide ? "col-span-2 items-center gap-4" : "flex-col items-start",
      )}
    >
      <Icon className="size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
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
            {big(Rss, t("rssFeeds"), plural("feedsCount", rssCount), () => onManageFeeds("rss"))}
            {big(Mail, t("newsletterFeeds"), plural("feedsCount", newsletterCount), () => onManageFeeds("newsletter"))}
            {big(TagIcon, t("manageTitle"), plural("topicsCount", library.folders.length), onManageTopics, true)}
          </div>
          <nav aria-label={t("settings")}>
            {row("account", UserRound, account?.email ?? t("accountSignedOut"))}
            {row("feed", Rss, t("feedSettingsSummary"))}
            {row("saved", BookOpen, t("savedSettingsSummary"))}
            {row("reader", BookOpenText, t("articleViewSummary"))}
            {row("appearance", Palette, t("appearanceSummary").replace("{theme}", themeNames[settings.theme]).replace("{view}", viewNames[settings.listView]))}
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
          {page === "feed" ? (
            <>
              {/* What only Feed's articles do (the list view is Appearance's: every list has it). */}
              <section className="space-y-2">
                <SwitchRow title={t("markReadOnScroll")} checked={settings.markReadOnScroll} onChange={(v) => app.updateSettings({ markReadOnScroll: v })} />
                <HiddenWordsRow />
              </section>
              {/* Moving to or from another reader: the feeds in and out. */}
              <section className="space-y-2">
                <h3 className="text-sm font-semibold">{t("opmlTitle")}</h3>
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
            </>
          ) : null}
          {/* The service Saved is also kept in. */}
          {page === "saved" ? <InstapaperSettings /> : null}
          {page === "appearance" ? (
            <section className="space-y-4">
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
              {/* The articles' rows, which Feed, Saved, the Inbox and Search all use. */}
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
            </section>
          ) : null}
          {page === "reader" ? (
            <>
              <section className="space-y-2">
                <SwitchRow title={t("alwaysWebsite")} summary={t("alwaysWebsiteSummary")} checked={settings.alwaysOpenWebsite} onChange={(v) => app.updateSettings({ alwaysOpenWebsite: v })} />
                <SwitchRow title={t("fullArticles")} summary={t("fullArticlesSummary")} checked={settings.fullArticles} onChange={(v) => app.updateSettings({ fullArticles: v })} />
                <SwitchRow title={t("showImages")} summary={t("showImagesSummary")} checked={settings.showImages} onChange={(v) => app.updateSettings({ showImages: v })} />
              </section>
              <section className="space-y-2">
                <h3 className="text-sm font-semibold">{t("textSettings")}</h3>
                <ReaderStyleControls preview />
              </section>
            </>
          ) : null}
        </div>
      )}
      {/* Manage feeds and Manage topics, opened from the two buttons: pages of this dialog. */}
      {children}
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
      <h3 className="text-sm font-semibold">{t("accountTitle")}</h3>
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
              <Button variant="secondary" onClick={() => void app.sync()} disabled={syncStatus.kind === "syncing"}>
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
      {/* The Android app, with the same account (its Account section points here, to content.wim.to). No link until it is in the Play Store. */}
      <div className="flex items-start gap-3 pt-3">
        <Smartphone className="mt-0.5 size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
        <div className="min-w-0">
          <p className="text-sm font-medium">{t("androidAppTitle")}</p>
          <p className="text-sm text-muted-foreground">{t("androidAppSummary")}</p>
        </div>
      </div>
      <Dialog
        popup
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title={t("deleteAccountData")}
        closeLabel={t("close")}
        footer={
          <>
            <Button variant="outline" onClick={() => setConfirmDelete(false)}>
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
              <span className="flex size-7 shrink-0 items-center justify-center rounded-full border bg-muted text-xs font-medium text-foreground" aria-hidden="true">
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

