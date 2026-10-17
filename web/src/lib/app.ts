import { useSyncExternalStore } from "react";
import { GOOGLE_CLIENT_ID } from "./config";
import { findFeed } from "./discovery";
import { GoogleAuth, GoogleDrive, SyncError, type SyncProblem, type SyncProvider } from "./drive";
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
import * as L from "./library";
import { importOpml, parseOpml, writeOpml } from "./opml";
import { fetchViaProxy, FetchError } from "./proxy";
import { defaultReaderStyle, readerStyleOf, type ReaderStyle } from "./readerStyle";
import { load, remove, save } from "./storage";
import * as S from "./sync";
import * as SS from "./syncSettings";

/** Everything the web app shows, kept in one place; components subscribe with useApp(). */

export type Theme = "system" | "light" | "dark";
export type SyncService = "none" | "googleDrive";

export interface Settings {
  syncService: SyncService;
  lastSyncedAt: number | null;
  unreadOnly: boolean;
  theme: Theme;
  fullArticles: boolean;
  readerStyle: ReaderStyle;
  markReadOnScroll: boolean;
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
  /** The Instapaper account, when signed in. Kept in this browser, and in the sync file when Drive sync is on. */
  instapaper: InstapaperSession | null;
  /** The result of coming back from Instapaper's sign-in page, shown once. */
  instapaperNotice: "signedIn" | "failed" | null;
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
  syncService: "none",
  lastSyncedAt: null,
  unreadOnly: true,
  theme: "system",
  fullArticles: true,
  readerStyle: defaultReaderStyle,
  markReadOnScroll: false,
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
    instapaperFolders: [],
    instapaperTags: [],
    instapaperList: emptyInstapaperList,
    instapaperOpen: null,
    instapaperJustSaved: null,
  };
  private listeners = new Set<() => void>();
  private knownRead = new Set<string>();
  private syncTimer: ReturnType<typeof setTimeout> | null = null;
  private syncing: Promise<void> | null = null;
  private auth: GoogleAuth | null = null;

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
    const [library, settings, base, instapaper] = await Promise.all([
      load<L.Library>("library").catch(() => undefined),
      load<Partial<Settings>>("settings").catch(() => undefined),
      load<string>("syncBase").catch(() => undefined),
      load<InstapaperSession>("instapaper").catch(() => undefined),
    ]);
    const merged = { ...defaultSettings, ...settings, readerStyle: readerStyleOf(settings?.readerStyle) };
    this.knownRead = new Set(Object.keys((base && S.decode(base)?.read) || {}));
    this.set({ loaded: true, library: library ?? L.emptyLibrary(), settings: merged, instapaper: instapaper?.token ? instapaper : null });
    void this.loadInstapaperSides();
    if (location.pathname === CALLBACK_PATH) await this.finishInstapaperSignIn(location.search);
    this.syncWhileVisible();
    if (merged.syncService !== "none") await this.sync();
    if (this.state.library.feeds.length > 0) await this.refresh();
  }

  /** Syncs while the page is visible: when it comes back, and every minute (like Android). */
  private syncWhileVisible() {
    const due = () => document.visibilityState === "visible" && void this.syncIfDue();
    document.addEventListener("visibilitychange", due);
    setInterval(due, S.VISIBLE_INTERVAL_MS);
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
    void save("library", library).catch(() => undefined);
    if (syncSoon) this.scheduleSync(before, library);
  }

  private scheduleSync(before: L.Library, after: L.Library) {
    if (S.encode(S.documentOf(before)) !== S.encode(S.documentOf(after))) this.syncSoon();
  }

  /** Syncs a moment after the last of a row of changes. */
  private syncSoon() {
    if (this.state.settings.syncService === "none") return;
    if (this.syncTimer) clearTimeout(this.syncTimer);
    this.syncTimer = setTimeout(() => void this.sync(), S.CHANGE_DELAY_MS);
  }

  updateSettings(patch: Partial<Settings>, syncSoon = true) {
    const before = this.state.settings;
    const settings = { ...before, ...patch };
    this.set({ settings });
    void save("settings", settings).catch(() => undefined);
    if (syncSoon && !sameValues(SS.syncedSettings(before, null), SS.syncedSettings(settings, null))) this.syncSoon();
  }

  select(selection: L.Selection) {
    this.set({ selection, readThisVisit: new Set() });
    if (selection.kind === "instapaper") void this.loadInstapaperList(selection.view);
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
      for (const [url, parsed] of results) out = L.mergeFeed(out, url, parsed, now, this.knownRead);
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
        this.knownRead,
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
      this.set({ instapaperOpen: this.state.instapaperList.items.find((b) => String(b.id) === article.id) ?? null });
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

  // Instapaper (docs/instapaper.md). Sign-in happens on Instapaper's own page; the token stays in this browser.

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
      await save("instapaper", session);
      this.set({ instapaper: session, instapaperNotice: "signedIn" });
      void this.loadInstapaperSides();
      this.syncSoon();
    } catch {
      this.set({ instapaperNotice: "failed" });
    }
  }

  clearInstapaperNotice() {
    this.set({ instapaperNotice: null });
  }

  async disconnectInstapaper() {
    await this.setInstapaper(null);
    this.syncSoon();
  }

  /** Signs in or out in this browser, for example because another device did (sync). */
  private async setInstapaper(session: InstapaperSession | null) {
    if (session) await save("instapaper", session).catch(() => undefined);
    else await remove("instapaper").catch(() => undefined);
    if (session) {
      this.set({ instapaper: session });
      void this.loadInstapaperSides();
      return;
    }
    this.set({ instapaper: null, instapaperFolders: [], instapaperTags: [], instapaperList: emptyInstapaperList, instapaperOpen: null, instapaperJustSaved: null });
    if (this.state.selection.kind === "instapaper") this.select({ kind: "all" });
  }

  // Instapaper's lists, folders and tags, shown like the feeds (instapaperLibrary.ts).

  /** Reads the folders and tags again (their counts change with every move). */
  async loadInstapaperSides() {
    if (!this.state.instapaper) return;
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
  private async changeBookmark(id: number, run: (api: InstapaperApi) => Promise<Change>): Promise<boolean> {
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

  tagBookmark = (b: Bookmark, add: string[], removeIds: number[]) =>
    this.changeBookmark(b.id, async (api) => ({ kind: "tags", tags: await api.setTags(b.id, add, removeIds) }));

  deleteBookmark = (b: Bookmark) =>
    this.changeBookmark(b.id, async (api) => {
      await api.remove(b.id);
      return { kind: "deleted" };
    });

  /** Folder and tag management (Manage feeds and folders). Each returns what went wrong, or null. */
  private async manageInstapaper(run: (api: InstapaperApi) => Promise<void>): Promise<string | null> {
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

  /** Runs one Instapaper call. A token Instapaper no longer accepts signs the user out, so they can sign in again. */
  async withInstapaper<T>(run: (api: InstapaperApi) => Promise<T>): Promise<T> {
    const session = this.state.instapaper;
    if (!session) throw new InstapaperError("auth");
    try {
      return await run(new InstapaperApi(session.token));
    } catch (e) {
      if (e instanceof InstapaperError && e.problem === "auth") await this.disconnectInstapaper();
      throw e;
    }
  }

  // Sync, the same way as the Android app (docs/sync.md).

  private provider(): SyncProvider | null {
    if (this.state.settings.syncService !== "googleDrive") return null;
    if (!this.auth) this.auth = new GoogleAuth(GOOGLE_CLIENT_ID);
    return new GoogleDrive(this.auth);
  }

  /** From a click: shows Google's screen, then syncs. */
  async connectGoogleDrive() {
    this.auth = new GoogleAuth(GOOGLE_CLIENT_ID);
    try {
      await this.auth.connect();
    } catch (e) {
      this.set({ syncStatus: { kind: "failed", problem: e instanceof SyncError ? e.problem : "needsApproval" } });
      return;
    }
    this.updateSettings({ syncService: "googleDrive", lastSyncedAt: null });
    await this.sync();
  }

  async disconnect() {
    this.auth?.forget();
    this.auth = null;
    this.knownRead = new Set();
    await remove("syncBase").catch(() => undefined);
    this.updateSettings({ syncService: "none", lastSyncedAt: null });
    this.set({ syncStatus: { kind: "idle" } });
  }

  sync(): Promise<void> {
    this.syncing ??= this.syncOnce().finally(() => (this.syncing = null));
    return this.syncing;
  }

  private async syncOnce() {
    const target = this.provider();
    if (!target) return;
    this.set({ syncStatus: { kind: "syncing" } });
    try {
      const remoteText = await target.read();
      const remote = remoteText === null ? null : S.decode(remoteText);
      if (remoteText !== null && !remote) throw new SyncError("unreadableFile");
      const baseText = remote ? await load<string>("syncBase").catch(() => undefined) : undefined;
      const base = baseText ? S.decode(baseText) : null;
      const current = this.state.library;
      const local = { ...S.localDocument(current, base), settings: SS.syncedSettings(this.state.settings, this.state.instapaper, base?.settings) };
      const merged = remote ? S.merge(base, local, remote) : local;
      const newFeeds = S.hasNewFeeds(current, merged);
      this.updateLibrary(() => S.apply(this.state.library, merged), false);
      const out = S.pruned(merged, Date.now() - S.KEEP_READ_MS);
      const outText = S.encode(out);
      if (!remote || S.encode(remote) !== outText) await target.write(outText);
      await save("syncBase", outText);
      this.knownRead = new Set(Object.keys(out.read));
      this.updateSettings({ ...SS.applySynced(this.state.settings, out.settings), lastSyncedAt: Date.now() }, false);
      const session = SS.instapaperOf(out.settings);
      if (!sameSession(session, this.state.instapaper)) await this.setInstapaper(session);
      this.set({ syncStatus: { kind: "idle" } });
      if (newFeeds) void this.refresh();
    } catch (e) {
      this.set({ syncStatus: { kind: "failed", problem: e instanceof SyncError ? e.problem : "server" } });
    }
  }
}

function sameValues(a: Record<string, string>, b: Record<string, string>): boolean {
  const keys = Object.keys(a);
  return keys.length === Object.keys(b).length && keys.every((k) => a[k] === b[k]);
}

function sameSession(a: InstapaperSession | null, b: InstapaperSession | null): boolean {
  return a === b || (!!a && !!b && a.token === b.token && a.username === b.username && a.userId === b.userId);
}

function size(link: HTMLLinkElement): number {
  return Number((link.getAttribute("sizes") ?? "").split("x")[0]) || (link.rel.includes("apple-touch-icon") ? 180 : 16);
}

export const app = new AppStore();

export function useApp(): State {
  return useSyncExternalStore(app.subscribe, app.get);
}
