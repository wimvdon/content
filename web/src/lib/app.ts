import { useSyncExternalStore } from "react";
import { rememberRecipient } from "./recipients";
import { notificationsOn, refreshServiceWorker, turnOff as turnOffNotifications, turnOn as turnOnNotifications, type TurnOnResult } from "./notifications";
import { AccountClient, emailLinkToken, finishEmailLink, LiveUpdates, sendEmailLink, SIGNIN_PATH, SyncError, type AccountSession, type EmailLinkProblem, type SyncProblem } from "./account";
import { findFeed } from "./discovery";
import { parseFeed } from "./feedParser";
import {
  authorizeUrl,
  CALLBACK_PATH,
  exchangeCode,
  InstapaperApi,
  newState,
  parseCallback,
  type Session as InstapaperSession,
} from "./instapaper";
import { applyHighlights, notCopied, syncHighlights, type HighlightLink } from "./highlightCopy";
import { INSTAPAPER_ID, InstapaperSync } from "./instapaperSync";
import { addHighlight, changeItem, itemOf, putItem, removeHighlight, renameTag, setHighlightNote, setLiked, type Highlight, type Item } from "./items";
import { markInboxRead, removeFromInbox } from "./inbox";
import { applyChoice, saveNew, undoSave, type SaveChoice } from "./saveChoice";
import * as Saved from "./saved";
import { emptyServiceSyncState, importService, syncService, type ServiceSyncState } from "./serviceSync";
import { EVERYTHING, type ServiceImport, type ServiceScope } from "./serviceScope";
import { changesBetween, loadItemSyncState, loadLibrary, writeItemSyncState, writeLibrary } from "./libraryStore";
import { emptyItemSyncState, syncItems } from "./syncRows";
import { takeInShared, type InboxMessage, type Received, type SharedArticle } from "./sharing";
import * as L from "./library";
import { importOpml, parseOpml, writeOpml } from "./opml";
import { fetchViaProxy, FetchError } from "./proxy";
import { defaultReaderStyle, readerStyleOf, type ReaderStyle } from "./readerStyle";
import { load, remove, save } from "./storage";
import * as S from "./sync";
import * as SS from "./syncSettings";

/** Everything the web app shows, kept in one place; components subscribe with useApp(). */

export type Theme = "system" | "light" | "dark";

/** How big each article is in the list: title only, with a thumbnail, or a card with a large picture (as on Android). */
export type ListView = "small" | "medium" | "large";

export interface Settings {
  lastSyncedAt: number | null;
  unreadOnly: boolean;
  theme: Theme;
  fullArticles: boolean;
  readerStyle: ReaderStyle;
  markReadOnScroll: boolean;
  /** Show article pictures, thumbnails and embed previews (they load from the sites). */
  showImages: boolean;
  listView: ListView;
  /** What each service syncs (#145), by id; a service without one syncs everything in Saved. */
  scopes: Record<string, ServiceScope>;
  /** What was imported from each service when it was connected (#145), by id. */
  imports: Record<string, ServiceImport>;
  /** Copy highlights to Instapaper (#164); in this browser only, off by default (free accounts get five a month). */
  copyHighlights: boolean;
  /** The people shared with (#235), most recent first; synced (recipients.ts). */
  recipients: string[];
}

export type SyncStatus = { kind: "idle" } | { kind: "syncing" } | { kind: "failed"; problem: SyncProblem };

export interface State {
  loaded: boolean;
  library: L.Library;
  settings: Settings;
  selection: L.Selection;
  /** Articles opened from the Unread list stay in it until the list changes. */
  readThisVisit: Set<string>;
  refreshing: boolean;
  problems: Map<string, FetchError["kind"] | "notAFeed">;
  syncStatus: SyncStatus;
  /** The Instapaper sign-in, which is also the Reader account (sync). Kept in this browser only. */
  instapaper: InstapaperSession | null;
  /** The result of coming back from Instapaper's sign-in page, shown once. */
  instapaperNotice: "signedIn" | "failed" | null;
  /** The Content account in this browser (#166): its device key and email address. Kept in this browser only. */
  account: AccountSession | null;
  /** What other Content users just shared (#167), said once. */
  received: Received | null;
  /** The inbox is shown in place of the list (#246). */
  inboxOpen: boolean;
  /** The result of opening an emailed sign-in link, shown once. */
  accountNotice: "signedIn" | "emailAdded" | EmailLinkProblem | null;
  /** Saved's tab, Liked, tags and type (saved.ts), while Saved is shown. */
  savedFilter: Saved.SavedFilter;
  /** The save and Organise sheet while it is open (#143): Add link, just saved, or organising a saved item. */
  sheet: Sheet | null;
  /** Instapaper's sync is running (an import or a sync). */
  instapaperBusy: boolean;
  /** The highlights Instapaper refused for its limit (#164), by item key: "Not in Instapaper". */
  instapaperNotCopied: Record<string, string[]>;
}

export type Sheet = { kind: "new" } | { kind: "justSaved" | "organise"; key: string };

const INSTAPAPER_STATE_KEY = "instapaper-sign-in-state";
/** This browser's copy of what Instapaper had (serviceSync.ts), in IndexedDB. */
const INSTAPAPER_SYNC_KEY = "instapaperSync";
/** What became of the highlights copied to Instapaper (highlightCopy.ts), by item key and highlight id, in IndexedDB. */
const INSTAPAPER_HIGHLIGHTS_KEY = "instapaperHighlights";

export const defaultSettings: Settings = {
  lastSyncedAt: null,
  unreadOnly: true,
  theme: "system",
  fullArticles: true,
  readerStyle: defaultReaderStyle,
  markReadOnScroll: false,
  showImages: true,
  listView: "medium",
  scopes: {},
  imports: {},
  copyHighlights: false,
  recipients: [],
};

const PARALLEL = 4;

class AppStore {
  private state: State = {
    loaded: false,
    library: L.emptyLibrary(),
    settings: defaultSettings,
    selection: { kind: "all" },
    readThisVisit: new Set(),
    refreshing: false,
    problems: new Map(),
    syncStatus: { kind: "idle" },
    instapaper: null,
    instapaperNotice: null,
    account: null,
    accountNotice: null,
    received: null,
    inboxOpen: false,
    savedFilter: Saved.defaultSavedFilter,
    sheet: null,
    instapaperBusy: false,
    instapaperNotCopied: {},
  };
  private listeners = new Set<() => void>();
  private syncTimer: ReturnType<typeof setTimeout> | null = null;
  private syncing: Promise<void> | null = null;
  /** A sync was asked for while one was running: it runs once more afterwards. */
  private syncAgain = false;
  private live: LiveUpdates | null = null;
  /** The stored library, once read (init). */
  private libraryLoaded: Promise<void>;
  private resolveLibrary!: () => void;
  constructor() {
    this.libraryLoaded = new Promise((resolve) => (this.resolveLibrary = resolve));
  }

  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  get = () => this.state;

  private set(patch: Partial<State>) {
    this.state = { ...this.state, ...patch };
    this.listeners.forEach((l) => l());
  }

  private started = false;

  async init() {
    if (this.started) return;
    this.started = true;
    const [library, settings, instapaper, stored] = await Promise.all([
      loadLibrary().catch(() => undefined),
      load<Partial<Settings>>("settings").catch(() => undefined),
      load<InstapaperSession>("instapaper").catch(() => undefined),
      load<AccountSession>("account").catch(() => undefined),
    ]);
    // Before #166 the account key came with the Instapaper sign-in; the account keeps it now.
    let account = stored?.key ? { key: stored.key, email: stored.email ?? null } : null;
    if (!account && instapaper?.accountKey) {
      account = { key: instapaper.accountKey, email: null };
      await save("account", account).catch(() => undefined);
    }
    const merged = { ...defaultSettings, ...settings, readerStyle: readerStyleOf(settings?.readerStyle), scopes: settings?.scopes ?? {}, imports: settings?.imports ?? {}, recipients: Array.isArray(settings?.recipients) ? settings.recipients : [] };
    this.set({
      loaded: true,
      library: library ?? L.emptyLibrary(),
      settings: merged,
      instapaper: instapaper?.token ? { ...instapaper, accountKey: null } : null,
      account,
    });
    // Folders became tags (#187): the library read from this browser moves over once.
    this.updateLibrary((lib) => lib);
    this.resolveLibrary();
    if (location.pathname === CALLBACK_PATH) await this.finishInstapaperSignIn(location.search);
    if (location.pathname === SIGNIN_PATH) await this.finishEmailLink(location.hash);
    // A notification's click (#235, public/sw.js): ?open=shared, or a message to an open page.
    if (new URLSearchParams(location.search).get("open") === "shared") {
      history.replaceState(null, "", "/");
      this.showInbox(true);
    }
    if (typeof navigator !== "undefined" && "serviceWorker" in navigator) {
      navigator.serviceWorker.addEventListener("message", (e: MessageEvent) => {
        if ((e.data as { kind?: unknown } | null)?.kind !== "openShared") return;
        this.showInbox(true);
        void this.sync();
      });
      void refreshServiceWorker();
    }
    // "Save to Content" from the browser extension (#168): ?save=<address>&title=<title>.
    const toSave = new URLSearchParams(location.search).get("save");
    if (toSave && /^https?:\/\//.test(toSave)) {
      const title = new URLSearchParams(location.search).get("title") ?? "";
      history.replaceState(null, "", "/");
      this.saveArticle(toSave, title.trim() || toSave);
    }
    this.syncWhileVisible();
    if (this.accountKey() || this.state.instapaper) await this.sync();
    if (this.state.library.feeds.length > 0) await this.refresh(false);
  }

  /**
   * Syncs while the page is visible: when it comes back, whenever another device saved (the live
   * connection), and every minute while that connection is down (like Android).
   */
  private syncWhileVisible() {
    const visible = () => document.visibilityState === "visible";
    document.addEventListener("visibilitychange", () => {
      this.updateLive();
      if (visible()) void this.syncIfDue();
    });
    setInterval(() => visible() && !this.live?.connected && void this.syncIfDue(), S.VISIBLE_INTERVAL_MS);
    this.updateLive();
  }

  /** The live connection runs while the page is visible and an account is signed in. */
  private updateLive() {
    const key = this.accountKey();
    const want = key !== null && document.visibilityState === "visible";
    if (!want) {
      this.live?.stop();
      this.live = null;
      return;
    }
    if (this.live) return;
    this.live = new LiveUpdates(key, (versions) => {
      if (versions.document !== this.syncedVersion || (versions.items !== null && versions.items !== this.syncedItemsVersion)) void this.sync();
    });
    this.live.start();
  }

  /** The account's versions (document and items) this browser last saved or read. */
  private syncedVersion: number | null = null;
  private syncedItemsVersion: number | null = null;

  private accountKey(): string | null {
    return this.state.account?.key ?? null;
  }

  /** Syncs unless the last sync was only seconds ago. */
  syncIfDue(): Promise<void> {
    return S.syncDue(this.state.settings.lastSyncedAt, Date.now()) ? this.sync() : Promise.resolve();
  }

  // Library changes: stored at once, synced a few seconds later.
  private updateLibrary(change: (lib: L.Library) => L.Library, syncSoon = true) {
    const before = this.state.library;
    // A folder (from an older app, another device or filed here) becomes a tag (#187).
    const library = L.feedFoldersToTags(Saved.foldersToTags(change(before)));
    if (library === before) return;
    // When each saved item last changed here, for "the later change wins" with a service (#145).
    if (before.items !== library.items) {
      const old = new Map(before.items.map((i) => [i.key, i]));
      const now = Date.now();
      for (const i of library.items) if (old.get(i.key) !== i) this.editedAt[i.key] = now;
    }
    // Saved shows the items, so a change from anywhere (this page, another device, the service) shows at once.
    this.set({ library, selection: L.selectionExists(library, this.state.selection), savedFilter: Saved.shownFilter(library, this.state.savedFilter) });
    void writeLibrary(changesBetween(before, library)).catch(() => undefined);
    if (syncSoon) this.scheduleSync(before, library);
  }

  /** A change to the document's part, the items or read marks is synced soon. */
  private scheduleSync(before: L.Library, after: L.Library) {
    const changed =
      S.encode(S.documentOf(before)) !== S.encode(S.documentOf(after)) ||
      before.items !== after.items ||
      before.articles.length !== after.articles.length ||
      before.articles.some((a, i) => a.read !== after.articles[i].read);
    if (changed) this.syncSoon();
  }

  /** Syncs a moment after the last of a row of changes. */
  private syncSoon() {
    if (!this.accountKey() && !this.state.instapaper) return;
    if (this.syncTimer) clearTimeout(this.syncTimer);
    this.syncTimer = setTimeout(() => void this.sync(), S.CHANGE_DELAY_MS);
  }

  updateSettings(patch: Partial<Settings>, syncSoon = true) {
    const before = this.state.settings;
    const settings = { ...before, ...patch };
    this.set({ settings });
    void save("settings", settings).catch(() => undefined);
    if (syncSoon && !sameValues(SS.syncedSettings(before), SS.syncedSettings(settings))) this.syncSoon();
  }

  /** Where Feeds and Saved were last, so switching between them (the navigation bar) goes back there. */
  private lastFeeds: L.Selection = { kind: "all" };

  select(selection: L.Selection) {
    // Another feed or folder starts afresh; Saved and back, or the same one again, keeps the
    // articles read this visit in the Unread list (#157).
    const fresh = selection.kind !== "saved" && !L.sameSelection(selection, this.lastFeeds);
    if (selection.kind !== "saved") this.lastFeeds = selection;
    this.set(fresh ? { selection, readThisVisit: new Set(), inboxOpen: false } : { selection, inboxOpen: false });
  }

  /** Saved, on a tab and with filters (the sidebar, the tabs and the chips). */
  showSaved(filter: Saved.SavedFilter = this.state.savedFilter) {
    this.set({ savedFilter: Saved.shownFilter(this.state.library, filter) });
    if (this.state.selection.kind !== "saved") this.select({ kind: "saved" });
  }

  /** Feeds or Saved, where it was last (the navigation bar). */
  showSection(saved: boolean) {
    if (this.state.inboxOpen) this.set({ inboxOpen: false });
    if ((this.state.selection.kind === "saved") === saved) return;
    if (saved) this.showSaved();
    else this.select(L.selectionExists(this.state.library, this.lastFeeds));
  }

  setUnreadOnly(unreadOnly: boolean) {
    if (unreadOnly === this.state.settings.unreadOnly) return;
    this.updateSettings({ unreadOnly });
    this.set({ readThisVisit: new Set() });
  }

  private fetch = (url: string) => fetchViaProxy(url);

  /** Checks every feed. Only a refresh you ask for starts the Unread list afresh (#157). */
  async refresh(byYou = true) {
    if (this.state.refreshing) return;
    this.set(byYou ? { refreshing: true, readThisVisit: new Set() } : { refreshing: true });
    // Read marks and feeds from other devices first, so they apply to the new articles.
    await this.sync();
    const feeds = [...this.state.library.feeds];
    const results = new Map<string, ReturnType<typeof parseFeed>>();
    const problems = new Map<string, FetchError["kind"] | "notAFeed">();
    let next = 0;
    const worker = async () => {
      while (next < feeds.length) {
        const feed = feeds[next++];
        try {
          const fetched = await this.fetch(feed.url);
          results.set(feed.url, parseFeed(fetched.text, fetched.url));
        } catch (e) {
          problems.set(feed.url, e instanceof FetchError ? e.kind : "notAFeed");
        }
      }
    };
    await Promise.all(Array.from({ length: PARALLEL }, worker));
    const now = Date.now();
    this.updateLibrary((lib) => {
      let out = lib;
      for (const [url, parsed] of results) out = L.mergeFeed(out, url, parsed, now);
      return out;
    });
    this.set({ refreshing: false, problems });
    void this.findIcons();
  }

  /** Website icons: the icon the home page names, else /favicon.ico (loaded by the browser). */
  private async findIcons() {
    for (const feed of this.state.library.feeds.filter((f) => !f.iconUrl && f.siteUrl)) {
      try {
        const page = await this.fetch(feed.siteUrl!);
        const doc = new DOMParser().parseFromString(page.text, "text/html");
        const links = Array.from(doc.querySelectorAll<HTMLLinkElement>("link[rel~='icon'][href], link[rel='apple-touch-icon'][href]"));
        const best = links
          .filter((l) => !(l.getAttribute("href") ?? "").startsWith("data:") && !/\.svg(\?|$)/i.test(l.getAttribute("href") ?? ""))
          .sort((a, b) => size(b) - size(a))[0];
        const icon = new URL(best?.getAttribute("href") ?? "/favicon.ico", page.url);
        icon.protocol = "https:";
        this.updateLibrary((lib) => L.setFeedIcon(lib, feed.url, icon.toString()));
      } catch {
        // No icon; the first letter is shown.
      }
    }
  }

  async addFeed(input: string, folderId: string | null): Promise<"added" | "already" | "invalid" | "none" | FetchError["kind"]> {
    const result = await findFeed(input, this.fetch);
    if (result.kind === "invalid" || result.kind === "none") return result.kind;
    if (result.kind === "failed") return result.error.kind;
    if (this.state.library.feeds.some((f) => f.url === result.url)) return "already";
    const now = Date.now();
    this.updateLibrary((lib) =>
      L.mergeFeed(
        L.addFeed(lib, { ...L.newFeed(result.url, result.feed.title), siteUrl: result.feed.siteUrl, folderId }),
        result.url,
        result.feed,
        now,
      ),
    );
    void this.findIcons();
    return "added";
  }

  removeFeed = (url: string) => this.updateLibrary((lib) => L.removeFeed(lib, url));
  renameFeed = (url: string, title: string) => this.updateLibrary((lib) => L.renameFeed(lib, url, title));
  moveFeed = (url: string, folderId: string | null) => this.updateLibrary((lib) => L.moveFeed(lib, url, folderId));
  addFolder = (title: string, icon: string) => {
    const id = crypto.randomUUID();
    this.updateLibrary((lib) => L.addFolder(lib, { id, title, icon }));
    return id;
  };
  updateFolder = (id: string, title: string, icon: string) => this.updateLibrary((lib) => L.updateFolder(lib, id, title, icon));
  removeFolder = (id: string) => this.updateLibrary((lib) => L.removeFolder(lib, id));
  /** Up or down within its folder, as Manage feeds lists it. */
  moveFeedBy = (url: string, delta: number) => this.updateLibrary((lib) => L.moveWithinFolder(lib, url, delta));
  moveFolderBy = (id: string, delta: number) =>
    this.updateLibrary((lib) => ({ ...lib, folders: L.reorder(lib.folders, lib.folders.findIndex((f) => f.id === id), delta) }));

  setRead(keys: Set<string>, read: boolean) {
    this.updateLibrary((lib) => L.setRead(lib, keys, read));
  }

  /**
   * Opening an article marks it read; it stays in the Unread list until the list changes. A saved
   * item has no read state (archiving is an action of its own), but the feed article it was saved
   * from counts as read, like on Android (#157).
   */
  open(article: L.Article) {
    const itemKey = Saved.itemKeyOf(article);
    if (itemKey !== null) {
      const item = this.state.library.items.find((i) => i.key === itemKey);
      const fromFeed = item && Saved.feedArticleOf(item, this.state.library);
      if (fromFeed && !fromFeed.read) this.setRead(new Set([L.articleKey(fromFeed)]), true);
      return;
    }
    const key = L.articleKey(article);
    this.set({ readThisVisit: new Set([...this.state.readThisVisit, key]) });
    if (!article.read) this.setRead(new Set([key]), true);
  }

  /** Read ⇄ unread, by hand (the row's button, ⋮, m): one marked read stays in the Unread list, like opened ones. */
  toggleRead(article: L.Article) {
    const key = L.articleKey(article);
    const current = this.state.library.articles.find((a) => L.articleKey(a) === key);
    if (!current) return;
    if (!current.read) this.set({ readThisVisit: new Set([...this.state.readThisVisit, key]) });
    this.setRead(new Set([key]), !current.read);
  }

  /** Mark as read on scroll: the articles stay in the Unread list until the list changes. */
  markScrolledPast(keys: Set<string>) {
    if (keys.size === 0) return;
    this.set({ readThisVisit: new Set([...this.state.readThisVisit, ...keys]) });
    this.setRead(keys, true);
  }

  /** Done with the section: everything unread in it is read, and nothing read stays in the Unread list (#157). */
  markAllRead(): Set<string> {
    const keys = L.unreadKeys(this.state.library, this.state.selection);
    this.set({ readThisVisit: new Set() });
    this.setRead(keys, true);
    return keys;
  }

  importOpml(text: string): number | null {
    const entries = parseOpml(text);
    if (entries.length === 0) return null;
    let added = 0;
    this.updateLibrary((lib) => {
      const [out, count] = importOpml(lib, entries, () => crypto.randomUUID());
      added = count;
      return out;
    });
    if (added > 0) void this.refresh(false);
    return added;
  }

  exportOpml = () => writeOpml("Reader", this.state.library);

  // Instapaper (docs/instapaper.md). Sign-in happens on Instapaper's own page; the token stays in this
  // browser. The sign-in is also the Reader account that syncs the library (docs/account.md).

  /** From a click: leaves for Instapaper's sign-in page, which sends the user back to CALLBACK_PATH. */
  connectInstapaper() {
    const state = newState();
    sessionStorage.setItem(INSTAPAPER_STATE_KEY, state);
    location.assign(authorizeUrl(state));
  }

  private async finishInstapaperSignIn(search: string) {
    const answer = parseCallback(search);
    const expected = sessionStorage.getItem(INSTAPAPER_STATE_KEY);
    sessionStorage.removeItem(INSTAPAPER_STATE_KEY);
    history.replaceState(null, "", "/");
    if (answer.kind !== "code" || !expected || answer.state !== expected) {
      this.set({ instapaperNotice: "failed" });
      return;
    }
    try {
      const session = await exchangeCode(answer.code);
      await remove(INSTAPAPER_SYNC_KEY).catch(() => undefined);
      await remove(INSTAPAPER_HIGHLIGHTS_KEY).catch(() => undefined);
      // Instapaper is only a connected service (#215): the account it would open isn't kept.
      await this.setInstapaper({ ...session, accountKey: null });
      this.set({ instapaperNotice: "signedIn" });
      this.updateLive();
      void this.sync();
    } catch {
      this.set({ instapaperNotice: "failed" });
    }
  }

  clearInstapaperNotice() {
    this.set({ instapaperNotice: null });
  }

  // The Content account (#166, docs/account.md): an emailed sign-in link, or opened by Instapaper.

  /** Emails a sign-in link to email (signed in: a link that adds it to this account). False when it couldn't be sent. */
  sendEmailLink = async (email: string): Promise<boolean> => {
    try {
      await sendEmailLink(email, this.accountKey());
      return true;
    } catch {
      return false;
    }
  };

  private async finishEmailLink(hash: string) {
    history.replaceState(null, "", "/");
    const token = emailLinkToken(hash);
    const result = token ? await finishEmailLink(token) : "failed";
    if (typeof result === "string") {
      this.set({ accountNotice: result });
      return;
    }
    if (result.key) {
      // Another account than the one signed in here: start from this browser's library again.
      const before = this.accountKey();
      if (before && before.split(".")[0] !== result.key.split(".")[0]) await this.forgetSync();
      await this.setAccount({ key: result.key, email: result.email });
      this.set({ accountNotice: "signedIn" });
      this.updateLive();
      void this.sync();
    } else {
      if (this.state.account) await this.setAccount({ ...this.state.account, email: result.email });
      this.set({ accountNotice: "emailAdded" });
    }
  }

  /** What other Content users shared (#167): into the app's inbox (#246), then out of the account's; says what arrived. */
  private async takeInShared(client: AccountClient) {
    const inbox = await client.inbox().catch(() => [] as InboxMessage[]);
    if (inbox.length === 0) return;
    let received: Received | null = null;
    this.updateLibrary((lib) => {
      const result = takeInShared(lib, inbox, Date.now());
      received = result.received;
      return result.library;
    });
    await client.inboxDone(inbox.map((m) => m.id)).catch(() => undefined);
    if (received && (received as Received).count > 0) this.set({ received });
  }

  clearReceived() {
    this.set({ received: null });
  }

  /** Notifications in this browser (#235, notifications.ts): on, or off and why it can't be on. */
  notificationsOn = () => notificationsOn();

  turnOnNotifications = async (): Promise<TurnOnResult | "signedOut"> => {
    const account = this.state.account;
    if (!account) return "signedOut";
    try {
      return await turnOnNotifications(new AccountClient(account.key));
    } catch {
      return "failed";
    }
  };

  turnOffNotifications = () => turnOffNotifications(this.state.account ? new AccountClient(this.state.account.key) : null);

  /** Whether the account can share (#167): signed in, with an email address so they see who it's from. */
  canShare = () => Boolean(this.state.account?.email);

  /** The name on email's Gravatar profile (#234): "" when it has none, null when it couldn't be asked. Asked once per address on this page. */
  profileName = async (email: string): Promise<string | null> => {
    const clean = email.trim().toLowerCase();
    const known = this.names.get(clean);
    if (known !== undefined) return known;
    const account = this.state.account;
    if (!account) return null;
    try {
      const name = await new AccountClient(account.key).profileName(clean);
      this.names.set(clean, name);
      return name;
    } catch {
      return null;
    }
  };

  private names = new Map<string, string>();

  /** Sends articles to a Content user (#167); `tag` when they go as a tag. */
  shareWithUser = async (to: string, items: SharedArticle[], tag: string | null): Promise<"sent" | "noAccount" | "noEmail" | "signedOut" | "offline" | "failed"> => {
    const account = this.state.account;
    if (!account) return "signedOut";
    if (!account.email) return "noEmail";
    try {
      if (!(await new AccountClient(account.key).share(to, { email: account.email, name: "" }, items, tag))) return "noAccount";
      // Offered first next time, on every device (#235).
      this.updateSettings({ recipients: rememberRecipient(this.state.settings.recipients, to) });
      return "sent";
    } catch (e) {
      return e instanceof SyncError && e.problem === "offline" ? "offline" : "failed";
    }
  };

  clearAccountNotice() {
    this.set({ accountNotice: null });
  }

  private async setAccount(account: AccountSession | null) {
    if (account) await save("account", account).catch(() => undefined);
    else await remove("account").catch(() => undefined);
    this.set({ account });
  }

  /** Signs out of the Content account in this browser; the library stays here, and Instapaper stays connected. */
  async signOutAccount() {
    const key = this.accountKey();
    if (key) void new AccountClient(key).signOut().catch(() => undefined);
    // The feed loader forgets this browser's push address with its key; the browser drops it too (#235).
    void turnOffNotifications(null);
    await this.forgetAccount();
  }

  private async forgetAccount() {
    await this.forgetSync();
    await this.setAccount(null);
    this.updateLive();
    this.set({ syncStatus: { kind: "idle" } });
  }

  /** The text stored in the account for saved item `key` (sharedText.ts); null without an account. */
  storedText = async (key: string): Promise<string | null> => {
    const account = this.accountKey();
    return account ? new AccountClient(account).text(key) : null;
  };

  /** Stores `html` as saved item `key`'s text unless one is there; gives back the one that is stored. */
  storeText = async (key: string, html: string): Promise<string | null> => {
    const account = this.accountKey();
    return account ? new AccountClient(account).storeText(key, html) : null;
  };

  /** Disconnects Instapaper in this browser; the Content account stays signed in (#166). */
  async disconnectInstapaper() {
    await this.signOutHere();
  }

  /**
   * Deletes everything the account keeps (for every device) and signs out. The library stays in
   * this browser. Returns false when the server couldn't be reached.
   */
  async deleteAccountData(): Promise<boolean> {
    const key = this.accountKey();
    if (key) {
      try {
        await new AccountClient(key).deleteAll();
      } catch {
        return false;
      }
    }
    await this.forgetAccount();
    return true;
  }

  private async signOutHere() {
    // Disconnected: what was synced with Instapaper, and its import, are forgotten; Saved stays.
    await remove(INSTAPAPER_SYNC_KEY).catch(() => undefined);
    await remove(INSTAPAPER_HIGHLIGHTS_KEY).catch(() => undefined);
    this.set({ instapaperNotCopied: {} });
    const imports = { ...this.state.settings.imports };
    delete imports[INSTAPAPER_ID];
    this.updateSettings({ imports });
    await this.setInstapaper(null);
  }

  /** Forgets what was synced, so the next account starts by combining this browser's library with its own. */
  private async forgetSync() {
    if (this.syncTimer) clearTimeout(this.syncTimer);
    this.syncedVersion = null;
    this.syncedItemsVersion = null;
    await remove("syncBase").catch(() => undefined);
    await writeItemSyncState(emptyItemSyncState(), null).catch(() => undefined);
    this.updateSettings({ lastSyncedAt: null }, false);
  }

  private async setInstapaper(session: InstapaperSession | null) {
    if (session) await save("instapaper", session).catch(() => undefined);
    else await remove("instapaper").catch(() => undefined);
    // Saved stays as it is: it is always the library's items.
    this.set({ instapaper: session });
  }

  // Saved (saved.ts): always the library's items; a connected service syncs them (syncInstapaper).

  /** Saves an article to To read and opens the sheet to file, like, tag and note it at once (#143). */
  saveArticle = (url: string, title: string) => {
    let key: string | null = null;
    this.updateLibrary((lib) => {
      const [out, item] = Saved.saveLink(lib, url, title, Date.now());
      key = item?.key ?? null;
      this.savedBefore = (item && lib.items.find((i) => i.key === item.key)) ?? null;
      return out;
    });
    this.set({ sheet: key ? { kind: "justSaved", key } : null });
  };

  /** The item as it was before the last saveArticle (null: not saved then), for Remove on the sheet. */
  private savedBefore: Item | null = null;

  /** Remove on the sheet right after saving: takes the save back, as if it was never saved. */
  undoSave = (key: string) => this.updateLibrary((lib) => undoSave(lib, key, this.savedBefore));

  /** Add link: the sheet asks for the address; nothing is saved until Save. */
  addLink = () => this.set({ sheet: { kind: "new" } });

  /** Organise a saved item: its folder, tags with Like, and note. */
  openOrganise = (key: string) => this.set({ sheet: { kind: "organise", key } });

  closeSheet = () => this.set({ sheet: null });

  /** Saves a new link where the sheet says; its key, or null when it isn't a web address. */
  saveNew = (url: string, choice: SaveChoice): string | null => {
    let key: string | null = null;
    this.updateLibrary((lib) => {
      const [out, item] = saveNew(lib, url, null, Date.now(), choice);
      key = item?.key ?? null;
      return out;
    });
    return key;
  };

  /** Writes what the sheet says about a saved item. */
  organise = (key: string, choice: SaveChoice) => this.updateLibrary((lib) => applyChoice(lib, key, choice));

  /** A highlight on the article of url (#102); a feed article not saved yet goes to To read with it. */
  addHighlight = (url: string, title: string, highlight: Highlight) => this.updateLibrary((lib) => addHighlight(lib, url, title, highlight, Date.now()));
  setHighlightNote = (key: string, id: string, note: string) => this.updateLibrary((lib) => setHighlightNote(lib, key, id, note));
  removeHighlight = (key: string, id: string) => this.updateLibrary((lib) => removeHighlight(lib, key, id));

  likeItem = (key: string, liked: boolean) => this.updateLibrary((lib) => setLiked(lib, key, liked));
  archiveItem = (key: string, archived: boolean) => this.updateLibrary((lib) => Saved.archiveItem(lib, key, archived));
  /** Into a folder, or To read with null. */
  /** The inbox (#246) in place of the list, or the list again. */
  showInbox = (open: boolean) => this.set({ inboxOpen: open });
  markInboxRead = (key: string, read = true) => this.updateLibrary((lib) => markInboxRead(lib, key, read));
  /** Out of the inbox; the item as it was, for Undo. */
  removeFromInbox = (key: string): Item | undefined => {
    const before = itemOf(this.state.library, key);
    this.updateLibrary((lib) => removeFromInbox(lib, key));
    return before;
  };
  /** Undo for removeFromInbox: back in the inbox as it was. */
  restoreInbox = (item: Item) => this.updateLibrary((lib) => putItem(lib, { ...(itemOf(lib, item.key) ?? item), sharedBy: item.sharedBy }));
  removeItem = (key: string) => this.updateLibrary((lib) => Saved.removeSaved(lib, key));

  /** Folders and tags (Folders and tags). Renaming to another's name joins the two. */
  renameSavedFolder = (from: string, to: string) => {
    this.updateLibrary((lib) => Saved.renameFolder(lib, from, to));
    const tab = this.state.savedFilter.tab;
    if (tab.kind === "folder" && tab.name.toLowerCase() === from.toLowerCase()) this.set({ savedFilter: { ...this.state.savedFilter, tab: { kind: "folder", name: to.trim() } } });
  };
  /** Its articles go to the Archive. */
  removeSavedFolder = (name: string) => this.updateLibrary((lib) => Saved.removeFolder(lib, name));
  renameSavedTag = (from: string, to: string) => this.updateLibrary((lib) => renameTag(lib, from, to));
  removeSavedTag = (name: string) => this.updateLibrary((lib) => Saved.removeTag(lib, name));

  private instapaperSyncing: Promise<void> | null = null;

  /**
   * Syncs Saved with Instapaper when signed in (serviceSync.ts), as the Android app does: what changed
   * there comes in, what changed here goes there. Runs after each sync with the Reader account. A
   * token Instapaper no longer accepts signs the user out, so they can sign in again.
   */
  private syncInstapaper(): Promise<void> {
    if (this.instapaperSyncing) return this.instapaperSyncing;
    this.instapaperSyncing = (async () => {
      const session = this.state.instapaper;
      if (!session) return;
      await this.libraryLoaded;
      let state = await this.instapaperState();
      this.set({ instapaperBusy: true });
      const service = new InstapaperSync(new InstapaperApi(session.token));
      // Just connected: everything Instapaper has comes in first, once; there is nothing to choose
      // (everything syncs). Connected before #145: already in step, no import.
      if (!this.state.settings.imports[INSTAPAPER_ID] && Object.keys(state.rows).length === 0) {
        const imported = await importService(service, state, { folders: null, archive: true }, (change) => {
          this.updateLibrary(change);
          return this.state.library;
        }, Date.now());
        if (imported.problem || this.state.instapaper?.token !== session.token) {
          this.set({ instapaperBusy: false });
          if (imported.problem === "auth") await this.disconnectInstapaper();
          return;
        }
        await save(INSTAPAPER_SYNC_KEY, imported.state).catch(() => undefined);
        this.updateSettings({ imports: { ...this.state.settings.imports, [INSTAPAPER_ID]: { folders: null, archive: true, at: Date.now(), count: imported.imported ?? 0 } } });
        state = imported.state;
      }
      const result = await syncService(
        service,
        state,
        (change) => {
          this.updateLibrary(change);
          return this.state.library;
        },
        Date.now(),
        // Folders became tags (#187), so a scope of folders means nothing any more: everything in Saved syncs.
        EVERYTHING,
        { ...this.editedAt },
      )
        .then(async (result) => {
          if (!result.problem && this.state.settings.copyHighlights && this.state.instapaper?.token === session.token) {
            await this.copyHighlights(service, result.state).catch(() => undefined);
          }
          return result;
        })
        .finally(() => this.set({ instapaperBusy: false }));
      // Signed out meanwhile: what was synced belongs to the old sign-in.
      if (this.state.instapaper?.token !== session.token) return;
      await save(INSTAPAPER_SYNC_KEY, result.state).catch(() => undefined);
      if (result.problem === "auth") await this.disconnectInstapaper();
    })().finally(() => (this.instapaperSyncing = null));
    return this.instapaperSyncing;
  }

  /**
   * Copies the highlights of each article in step with Instapaper there (#164), as the Android app
   * does. Only articles with something to do cost a call; it stops at the first problem and goes on next time.
   */
  private async copyHighlights(service: InstapaperSync, state: ServiceSyncState): Promise<void> {
    const stored = (await load<Record<string, Record<string, HighlightLink>>>(INSTAPAPER_HIGHLIGHTS_KEY).catch(() => undefined)) ?? {};
    const links: Record<string, Record<string, HighlightLink>> = {};
    for (const [key, byId] of Object.entries(stored)) if (state.rows[key] && state.rows[key].linked !== false) links[key] = byId;
    const now = Date.now();
    const items = new Map(this.state.library.items.map((i) => [i.key, i]));
    const look = new Set(this.lookAt);
    this.lookAt.clear();
    try {
      for (const [key, row] of Object.entries(state.rows)) {
        if (row.linked === false) continue;
        const item = items.get(key);
        if (!item?.readingList) continue;
        const synced = await syncHighlights(service, INSTAPAPER_ID, row.remoteId, item.highlights, links[key] ?? {}, now, look.has(key));
        links[key] = synced.links;
        // Highlights made, deleted or changed in Instapaper's own apps come in.
        if (synced.highlights !== item.highlights) {
          this.updateLibrary((lib) => changeItem(lib, key, (i) => ({ ...i, highlights: applyHighlights(i.highlights, item.highlights, synced.highlights) })));
        }
        if (synced.problem) break;
      }
    } finally {
      await save(INSTAPAPER_HIGHLIGHTS_KEY, links).catch(() => undefined);
      this.showNotCopied(links);
    }
  }

  private showNotCopied(links: Record<string, Record<string, HighlightLink>>) {
    const out: Record<string, string[]> = {};
    for (const [key, byId] of Object.entries(links)) {
      const refused = [...notCopied(byId)];
      if (refused.length > 0) out[key] = refused;
    }
    this.set({ instapaperNotCopied: out });
  }

  /** Saved items whose highlights are looked up at Instapaper on the next sync (opened in the reader). */
  private lookAt = new Set<string>();

  /** A saved article opened (#164): its highlights in Instapaper are looked up now, so ones made there come in. */
  lookAtHighlights = (key: string) => {
    if (!this.state.instapaper || !this.state.settings.copyHighlights) return;
    this.lookAt.add(key);
    void this.syncInstapaper().catch(() => undefined);
  };

  /** Copying highlights to Instapaper on or off (#164); on, it copies at once. */
  setCopyHighlights = (on: boolean) => {
    this.updateSettings({ copyHighlights: on });
    if (on) void this.syncInstapaper().catch(() => undefined);
    else this.set({ instapaperNotCopied: {} });
  };

  /** When each saved item last changed here, by key (kept for this visit). */
  private editedAt: Record<string, number> = {};

  private async instapaperState(): Promise<ServiceSyncState> {
    const state = (await load<ServiceSyncState>(INSTAPAPER_SYNC_KEY).catch(() => undefined)) ?? emptyServiceSyncState;
    return { cursor: state.cursor ?? null, rows: state.rows ?? {} };
  }

  // Sync with the Reader account, the same way as the Android app (docs/sync.md).

  /** Syncs now; asked while a sync runs, it runs once more right after, so no change waits. */
  sync(): Promise<void> {
    if (this.syncing) {
      this.syncAgain = true;
      return this.syncing;
    }
    this.syncing = (async () => {
      do {
        this.syncAgain = false;
        await this.syncOnce();
        await this.syncInstapaper().catch(() => undefined);
      } while (this.syncAgain);
    })().finally(() => (this.syncing = null));
    return this.syncing;
  }

  private async syncOnce() {
    const key = this.accountKey();
    if (!key) return;
    this.set({ syncStatus: { kind: "syncing" } });
    try {
      const saved = await load<S.SyncBase>("syncBase").catch(() => undefined);
      let newFeeds = false;
      const next = await S.syncRound(new AccountClient(key), typeof saved?.text === "string" ? saved : null, (base, remote) => {
        // Merged with the library as it is right now, so changes made while the document loaded count.
        const current = this.state.library;
        const local = { ...S.localDocument(current), settings: SS.syncedSettings(this.state.settings, base?.settings) };
        const merged = remote ? S.merge(base, local, remote) : local;
        newFeeds ||= S.hasNewFeeds(current, merged);
        this.updateLibrary(() => S.apply(this.state.library, merged), false);
        this.updateSettings(SS.applySynced(this.state.settings, merged.settings), false);
        return merged;
      });
      if (this.accountKey() !== key) return;
      await save("syncBase", next);
      this.syncedVersion = next.version;
      // Then the reading list, Bookmarks and read marks, one row per item (#106).
      const items = await loadItemSyncState();
      const nextItems = await syncItems(new AccountClient(key), items, Date.now(), (change) => {
        this.updateLibrary(change, false);
        return this.state.library;
      });
      if (this.accountKey() !== key) return;
      await writeItemSyncState(items, nextItems);
      this.syncedItemsVersion = nextItems.version;
      await this.takeInShared(new AccountClient(key));
      this.updateSettings({ lastSyncedAt: Date.now() }, false);
      this.set({ syncStatus: { kind: "idle" } });
      if (newFeeds) void this.refresh(false);
    } catch (e) {
      const problem = e instanceof SyncError ? e.problem : "server";
      this.set({ syncStatus: { kind: "failed", problem } });
    }
  }
}

function sameValues(a: Record<string, string>, b: Record<string, string>): boolean {
  const keys = Object.keys(a);
  return keys.length === Object.keys(b).length && keys.every((k) => a[k] === b[k]);
}

function size(link: HTMLLinkElement): number {
  return Number((link.getAttribute("sizes") ?? "").split("x")[0]) || (link.rel.includes("apple-touch-icon") ? 180 : 16);
}

export const app = new AppStore();

export function useApp(): State {
  return useSyncExternalStore(app.subscribe, app.get);
}
