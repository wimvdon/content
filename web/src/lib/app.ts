import { useSyncExternalStore } from "react";
import { AccountClient, LiveUpdates, SyncError, type SyncProblem } from "./account";
import { findFeed } from "./discovery";
import { parseFeed } from "./feedParser";
import {
  authorizeUrl,
  CALLBACK_PATH,
  exchangeCode,
  InstapaperApi,
  InstapaperError,
  newState,
  parseCallback,
  reorderedPositions,
  type Bookmark,
  type Folder as InstapaperFolder,
  type Session as InstapaperSession,
  type Tag as InstapaperTag,
  type View as InstapaperView,
} from "./instapaper";
import { applyChange, patchBookmark, sameView, type Change } from "./instapaperLibrary";
import { problemText } from "./instapaperText";
import { LocalSaved, type SavedService } from "./localSaved";
import { changesBetween, loadItemSyncState, loadLibrary, writeItemSyncState, writeLibrary } from "./libraryStore";
import { emptyItemSyncState, syncItems } from "./syncRows";
import { applySaved, savedData } from "./savedItems";
import * as L from "./library";
import { loadSaved, savedViews, type SavedArticles } from "./search";
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
  /** How many articles saved in this browser were just copied to the account, shown once; 0 when none. */
  savedCopied: number;
  /** Instapaper's folders and tags, for the sidebar and Manage; read again after every change. */
  instapaperFolders: InstapaperFolder[];
  instapaperTags: InstapaperTag[];
  /** The Instapaper list shown when an Instapaper section is selected. */
  instapaperList: InstapaperList;
  /** The saved article open in the reader; it stays there when a change takes it out of the list. */
  instapaperOpen: Bookmark | null;
  /** The article just saved from a feed, while the dialog to file it (folder and tags) is open. */
  instapaperJustSaved: Bookmark | null;
}

export interface InstapaperList {
  view: InstapaperView | null;
  items: Bookmark[];
  total: number;
  loading: boolean;
  problem: string | null;
}

const emptyInstapaperList: InstapaperList = { view: null, items: [], total: 0, loading: false, problem: null };
const INSTAPAPER_PAGE = 25;

const INSTAPAPER_STATE_KEY = "instapaper-sign-in-state";

export const defaultSettings: Settings = {
  lastSyncedAt: null,
  unreadOnly: true,
  theme: "system",
  fullArticles: true,
  readerStyle: defaultReaderStyle,
  markReadOnScroll: false,
  showImages: true,
  listView: "medium",
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
    savedCopied: 0,
    instapaperFolders: [],
    instapaperTags: [],
    instapaperList: emptyInstapaperList,
    instapaperOpen: null,
    instapaperJustSaved: null,
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
  /**
   * Saved without an account: the library's reading list (#106, savedItems.ts), as LocalSaved's
   * data; its changes become changes to the items.
   */
  private localSaved = new LocalSaved({
    read: async () => {
      await this.libraryLoaded;
      return savedData(this.state.library);
    },
    write: async (data) => {
      await this.libraryLoaded;
      this.updateLibrary((lib) => applySaved(lib, savedData(lib), data, Date.now()), false);
    },
  });
  /** The copy of `localSaved` to the account that is running, so no article is sent twice. */
  private copying: Promise<void> | null = null;

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
    const [library, settings, instapaper] = await Promise.all([
      loadLibrary().catch(() => undefined),
      load<Partial<Settings>>("settings").catch(() => undefined),
      load<InstapaperSession>("instapaper").catch(() => undefined),
    ]);
    const merged = { ...defaultSettings, ...settings, readerStyle: readerStyleOf(settings?.readerStyle) };
    this.set({
      loaded: true,
      library: library ?? L.emptyLibrary(),
      settings: merged,
      instapaper: instapaper?.token ? { ...instapaper, accountKey: instapaper.accountKey ?? null } : null,
    });
    this.resolveLibrary();
    void this.loadInstapaperSides();
    if (location.pathname === CALLBACK_PATH) await this.finishInstapaperSignIn(location.search);
    else void this.copyLocalSaved();
    this.syncWhileVisible();
    if (this.accountKey()) await this.sync();
    if (this.state.library.feeds.length > 0) await this.refresh();
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
    return this.state.instapaper?.accountKey ?? null;
  }

  /** Syncs unless the last sync was only seconds ago. */
  syncIfDue(): Promise<void> {
    return S.syncDue(this.state.settings.lastSyncedAt, Date.now()) ? this.sync() : Promise.resolve();
  }

  // Library changes: stored at once, synced a few seconds later.
  private updateLibrary(change: (lib: L.Library) => L.Library, syncSoon = true) {
    const before = this.state.library;
    const library = change(before);
    if (library === before) return;
    this.set({ library, selection: L.selectionExists(library, this.state.selection) });
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
    if (!this.accountKey()) return;
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
  private lastSaved: L.Selection = { kind: "instapaper", view: { kind: "home" } };

  select(selection: L.Selection) {
    if (selection.kind === "instapaper") this.lastSaved = selection;
    else this.lastFeeds = selection;
    this.set({ selection, readThisVisit: new Set() });
    if (selection.kind === "instapaper") void this.loadInstapaperList(selection.view);
  }

  /** Feeds or Saved, where it was last (the navigation bar). */
  showSection(saved: boolean) {
    if ((this.state.selection.kind === "instapaper") === saved) return;
    this.select(saved ? this.lastSaved : L.selectionExists(this.state.library, this.lastFeeds));
  }

  setUnreadOnly(unreadOnly: boolean) {
    this.updateSettings({ unreadOnly });
    this.set({ readThisVisit: new Set() });
  }

  private fetch = (url: string) => fetchViaProxy(url);

  async refresh() {
    const selection = this.state.selection;
    if (selection.kind === "instapaper") {
      void this.loadInstapaperSides();
      void this.loadInstapaperList(selection.view);
    }
    if (this.state.refreshing) return;
    this.set({ refreshing: true, readThisVisit: new Set() });
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
  moveFeedBy = (url: string, delta: number) =>
    this.updateLibrary((lib) => ({ ...lib, feeds: L.reorder(lib.feeds, lib.feeds.findIndex((f) => f.url === url), delta) }));
  moveFolderBy = (id: string, delta: number) =>
    this.updateLibrary((lib) => ({ ...lib, folders: L.reorder(lib.folders, lib.folders.findIndex((f) => f.id === id), delta) }));

  setRead(keys: Set<string>, read: boolean) {
    this.updateLibrary((lib) => L.setRead(lib, keys, read));
  }

  /** Opening an article marks it read; it stays in the Unread list until the list changes. */
  open(article: L.Article) {
    if (article.feedUrl === "instapaper") {
      // Saved articles have no read state; the reader keeps this one even if it leaves the list.
      // One opened from Search may not be in the list shown: openSaved has already set it.
      const open = this.state.instapaperOpen;
      this.set({ instapaperOpen: this.state.instapaperList.items.find((b) => String(b.id) === article.id) ?? (open && String(open.id) === article.id ? open : null) });
      return;
    }
    const key = L.articleKey(article);
    this.set({ readThisVisit: new Set([...this.state.readThisVisit, key]) });
    if (!article.read) this.setRead(new Set([key]), true);
  }

  /** Mark as read on scroll: the articles stay in the Unread list until the list changes. */
  markScrolledPast(keys: Set<string>) {
    if (keys.size === 0) return;
    this.set({ readThisVisit: new Set([...this.state.readThisVisit, ...keys]) });
    this.setRead(keys, true);
  }

  markAllRead(): Set<string> {
    const keys = L.unreadKeys(this.state.library, this.state.selection);
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
    if (added > 0) void this.refresh();
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
      await this.forgetSync();
      await this.setInstapaper(session);
      this.set({ instapaperNotice: "signedIn" });
      this.updateLive();
      void this.copyLocalSaved();
      void this.sync();
    } catch {
      this.set({ instapaperNotice: "failed" });
    }
  }

  clearInstapaperNotice() {
    this.set({ instapaperNotice: null });
  }

  /**
   * Signed in, copies the articles saved in this browser to the account and shows them there. What
   * didn't make it (offline, too many requests) is tried again the next time the app opens.
   */
  private copyLocalSaved(): Promise<void> {
    const session = this.state.instapaper;
    if (!session || this.copying) return this.copying ?? Promise.resolve();
    this.copying = (async () => {
      const upload = await this.localSaved.uploadTo(new InstapaperApi(session.token));
      if (upload.problem === "auth") await this.disconnectInstapaper();
      if (upload.copied === 0 || this.state.instapaper?.token !== session.token) return;
      this.set({ savedCopied: upload.copied });
      void this.loadInstapaperSides();
      const s = this.state.selection;
      if (s.kind === "instapaper") void this.loadInstapaperList(s.view);
    })().finally(() => (this.copying = null));
    return this.copying;
  }

  clearSavedCopied() {
    this.set({ savedCopied: 0 });
  }

  /** Signs out of Instapaper and the account in this browser; the library stays here. */
  async disconnectInstapaper() {
    const key = this.accountKey();
    if (key) void new AccountClient(key).signOut().catch(() => undefined);
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
    await this.signOutHere();
    return true;
  }

  private async signOutHere() {
    await this.forgetSync();
    await this.setInstapaper(null);
    this.updateLive();
    this.set({ syncStatus: { kind: "idle" } });
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
    if (session) {
      this.set({ instapaper: session });
      void this.loadInstapaperSides();
      return;
    }
    // Back to the articles saved in this browser, which have no folders.
    this.set({ instapaper: null, instapaperFolders: [], instapaperTags: [], instapaperList: emptyInstapaperList, instapaperOpen: null, instapaperJustSaved: null });
    void this.loadInstapaperSides();
    if (this.state.selection.kind === "instapaper") this.select({ kind: "instapaper", view: { kind: "home" } });
  }

  // Instapaper's lists, folders and tags, shown like the feeds (instapaperLibrary.ts).

  /** Reads the folders and tags again (their counts change with every move). */
  async loadInstapaperSides() {
    try {
      const [folders, tags] = await this.withInstapaper(async (api) => [await api.folders(), await api.tags()] as const);
      this.set({ instapaperFolders: folders, instapaperTags: tags });
    } catch {
      // The list shows Instapaper's problems; the sidebar keeps what it had.
    }
  }

  /** Loads a list from the start, or the next page of the list already shown. */
  async loadInstapaperList(view: InstapaperView, more = false) {
    const current = this.state.instapaperList;
    const offset = more && current.view && sameView(current.view, view) ? current.items.length : 0;
    this.set({ instapaperList: { ...(offset ? current : emptyInstapaperList), view, loading: true, problem: null } });
    const showing = () => {
      const s = this.state.selection;
      return s.kind === "instapaper" && sameView(s.view, view);
    };
    try {
      const page = await this.withInstapaper((api) => api.list(view, offset, INSTAPAPER_PAGE));
      if (!showing()) return;
      const before = this.state.instapaperList.items;
      const items = offset ? [...before, ...page.bookmarks.filter((b) => !before.some((c) => c.id === b.id))] : page.bookmarks;
      this.set({ instapaperList: { view, items, total: page.total, loading: false, problem: null } });
    } catch (e) {
      if (showing()) this.set({ instapaperList: { ...this.state.instapaperList, loading: false, problem: problemText(e) } });
    }
  }

  /** Runs one change to a saved article and updates the list, the open article and the sidebar's counts. */
  private async changeBookmark(id: number, run: (api: SavedService) => Promise<Change>): Promise<boolean> {
    try {
      const change = await this.withInstapaper(run);
      const list = this.state.instapaperList;
      const items = list.view ? applyChange(list.view, list.items, id, change) : list.items;
      const open = this.state.instapaperOpen;
      const justSaved = this.state.instapaperJustSaved;
      this.set({
        instapaperList: { ...list, items, total: Math.max(0, list.total - (list.items.length - items.length)), problem: null },
        instapaperOpen: open && open.id === id ? patchBookmark(open, change) : open,
        instapaperJustSaved: justSaved && justSaved.id === id ? patchBookmark(justSaved, change) : justSaved,
      });
      void this.loadInstapaperSides();
      return true;
    } catch (e) {
      this.set({ instapaperList: { ...this.state.instapaperList, problem: problemText(e) } });
      return false;
    }
  }

  /** Saves a feed article and keeps it as `instapaperJustSaved`, so it can be filed at once. Throws when Instapaper refuses. */
  saveToInstapaper = async (url: string, title: string) => {
    const bookmark = await this.withInstapaper((api) => api.save(url, title));
    this.set({ instapaperJustSaved: bookmark?.id ? bookmark : null });
    void this.loadInstapaperSides();
  };

  /**
   * Every saved article Search looks through (search.ts): Saved has no search of its own, so its
   * lists are read and searched here. A refused sign-in signs out, as elsewhere.
   */
  loadSavedForSearch = (): Promise<SavedArticles> =>
    this.withInstapaper(async (api) => {
      const folders = this.state.instapaperFolders.length > 0 ? this.state.instapaperFolders : await api.folders();
      return loadSaved(savedViews(folders), (view, offset, limit) => api.list(view, offset, limit), (e) => e instanceof InstapaperError && e.problem === "auth");
    }).catch((): SavedArticles => ({ bookmarks: [], incomplete: true }));

  /** A saved article opened from Search: the reader shows it with Saved's buttons. */
  openSaved = (b: Bookmark) => this.set({ instapaperOpen: b });

  /** The dialog for the article just saved was closed. */
  closeJustSaved = () => this.set({ instapaperJustSaved: null });

  archiveBookmark = (b: Bookmark, archived: boolean) =>
    this.changeBookmark(b.id, async (api) => {
      await api.move(b.id, archived ? "archive" : "home");
      return { kind: "archived", archived };
    });

  likeBookmark = (b: Bookmark, liked: boolean) =>
    this.changeBookmark(b.id, async (api) => ({ kind: "liked", liked: (await api.like(b.id, liked)).liked }));

  /** Into a folder, or back to Unread (null). */
  moveBookmark = (b: Bookmark, folderId: number | null) =>
    this.changeBookmark(b.id, async (api) => {
      await api.move(b.id, folderId ?? "home");
      return { kind: "moved", folderId };
    });

  /** Makes folder `title` and moves the article into it (Move to folder, or the dialog after saving). */
  moveBookmarkToNewFolder = (b: Bookmark, title: string) => {
    const name = title.trim();
    if (!name) return Promise.resolve(false);
    return this.changeBookmark(b.id, async (api) => {
      const folder = await api.createFolder(name);
      await api.move(b.id, folder.id);
      return { kind: "moved", folderId: folder.id };
    });
  };

  tagBookmark = (b: Bookmark, add: string[], removeIds: number[]) =>
    this.changeBookmark(b.id, async (api) => ({ kind: "tags", tags: await api.setTags(b.id, add, removeIds) }));

  deleteBookmark = (b: Bookmark) =>
    this.changeBookmark(b.id, async (api) => {
      await api.remove(b.id);
      return { kind: "deleted" };
    });

  /** Folder and tag management (Manage feeds and folders). Each returns what went wrong, or null. */
  private async manageInstapaper(run: (api: SavedService) => Promise<void>): Promise<string | null> {
    try {
      await this.withInstapaper(run);
      return null;
    } catch (e) {
      return problemText(e);
    } finally {
      void this.loadInstapaperSides();
    }
  }

  createInstapaperFolder = (title: string) =>
    this.manageInstapaper(async (api) => {
      const created = await api.createFolder(title);
      this.set({ instapaperFolders: [...this.state.instapaperFolders, created].sort((a, b) => a.position - b.position) });
    });

  /** Its articles go back to Unread at Instapaper. */
  deleteInstapaperFolder = (id: number) =>
    this.manageInstapaper(async (api) => {
      await api.deleteFolder(id);
      this.set({ instapaperFolders: this.state.instapaperFolders.filter((f) => f.id !== id) });
      const s = this.state.selection;
      if (s.kind === "instapaper" && s.view.kind === "folder" && s.view.id === id) this.select({ kind: "instapaper", view: { kind: "home" } });
    });

  moveInstapaperFolder = (index: number, delta: number) =>
    this.manageInstapaper(async (api) => {
      const order = reorderedPositions(this.state.instapaperFolders, index, delta);
      if (order) this.set({ instapaperFolders: await api.reorderFolders(order) });
    });

  renameInstapaperTag = (tag: InstapaperTag, name: string) =>
    this.manageInstapaper(async (api) => {
      const renamed = await api.renameTag(tag.id, name);
      this.set({ instapaperTags: this.state.instapaperTags.map((x) => (x.id === tag.id ? renamed : x)) });
      const s = this.state.selection;
      if (s.kind === "instapaper" && s.view.kind === "tag" && s.view.name === tag.name) this.select({ kind: "instapaper", view: { kind: "tag", name: renamed.name } });
    });

  /**
   * Runs one call on Saved: Instapaper when signed in, else the articles saved in this browser. A
   * token Instapaper no longer accepts signs the user out, so they can sign in again.
   */
  async withInstapaper<T>(run: (api: SavedService) => Promise<T>): Promise<T> {
    const session = this.state.instapaper;
    if (!session) return run(this.localSaved);
    try {
      return await run(new InstapaperApi(session.token));
    } catch (e) {
      if (e instanceof InstapaperError && e.problem === "auth") await this.disconnectInstapaper();
      throw e;
    }
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
      this.updateSettings({ lastSyncedAt: Date.now() }, false);
      this.set({ syncStatus: { kind: "idle" } });
      if (newFeeds) void this.refresh();
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
