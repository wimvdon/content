import { useSyncExternalStore } from "react";
import { findFeed } from "./discovery";
import { GoogleAuth, GoogleDrive, SyncError, type SyncProblem, type SyncProvider } from "./drive";
import { parseFeed } from "./feedParser";
import * as L from "./library";
import { importOpml, parseOpml, writeOpml } from "./opml";
import { fetchViaProxy, FetchError } from "./proxy";
import { defaultReaderStyle, readerStyleOf, type ReaderStyle } from "./readerStyle";
import { load, remove, save } from "./storage";
import * as S from "./sync";

/** Everything the web app shows, kept in one place; components subscribe with useApp(). */

export type Theme = "system" | "light" | "dark";
export type SyncService = "none" | "googleDrive";

export interface Settings {
  /** The Cloudflare Worker that loads feeds (worker/). */
  proxyUrl: string;
  /** Google's OAuth client ID for this web app (docs/sync.md). */
  googleClientId: string;
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
}

const defaults: Settings = {
  proxyUrl: import.meta.env.VITE_PROXY_URL ?? "",
  googleClientId: import.meta.env.VITE_GOOGLE_CLIENT_ID ?? "",
  syncService: "none",
  lastSyncedAt: null,
  unreadOnly: true,
  theme: "system",
  fullArticles: true,
  readerStyle: defaultReaderStyle,
  markReadOnScroll: false,
};

const PARALLEL = 4;
const CHANGE_DELAY_MS = 5_000;

class AppStore {
  private state: State = {
    loaded: false,
    library: L.emptyLibrary(),
    settings: defaults,
    selection: { kind: "all" },
    readThisVisit: new Set(),
    refreshing: false,
    problems: new Map(),
    syncStatus: { kind: "idle" },
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
    const [library, settings, base] = await Promise.all([
      load<L.Library>("library").catch(() => undefined),
      load<Partial<Settings>>("settings").catch(() => undefined),
      load<string>("syncBase").catch(() => undefined),
    ]);
    const merged = { ...defaults, ...settings, readerStyle: readerStyleOf(settings?.readerStyle) };
    // Addresses set when building win over empty saved ones.
    if (!merged.proxyUrl) merged.proxyUrl = defaults.proxyUrl;
    if (!merged.googleClientId) merged.googleClientId = defaults.googleClientId;
    this.knownRead = new Set(Object.keys((base && S.decode(base)?.read) || {}));
    this.set({ loaded: true, library: library ?? L.emptyLibrary(), settings: merged });
    if (merged.syncService !== "none") await this.sync();
    if (this.state.library.feeds.length > 0) await this.refresh();
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
    if (this.state.settings.syncService === "none") return;
    if (S.encode(S.documentOf(before)) === S.encode(S.documentOf(after))) return;
    if (this.syncTimer) clearTimeout(this.syncTimer);
    this.syncTimer = setTimeout(() => void this.sync(), CHANGE_DELAY_MS);
  }

  updateSettings(patch: Partial<Settings>) {
    const settings = { ...this.state.settings, ...patch };
    this.set({ settings });
    void save("settings", settings).catch(() => undefined);
  }

  select(selection: L.Selection) {
    this.set({ selection, readThisVisit: new Set() });
  }

  setUnreadOnly(unreadOnly: boolean) {
    this.updateSettings({ unreadOnly });
    this.set({ readThisVisit: new Set() });
  }

  private fetch = (url: string) => fetchViaProxy(this.state.settings.proxyUrl, url);

  async refresh() {
    if (this.state.refreshing || !this.state.settings.proxyUrl) return;
    this.set({ refreshing: true, readThisVisit: new Set() });
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

  // Sync, the same way as the Android app (docs/sync.md).

  private provider(): SyncProvider | null {
    if (this.state.settings.syncService !== "googleDrive") return null;
    if (!this.auth) this.auth = new GoogleAuth(this.state.settings.googleClientId);
    return new GoogleDrive(this.auth);
  }

  /** From a click: shows Google's screen, then syncs. */
  async connectGoogleDrive() {
    this.auth = new GoogleAuth(this.state.settings.googleClientId);
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
      const local = S.localDocument(current, base);
      const merged = remote ? S.merge(base, local, remote) : local;
      const newFeeds = S.hasNewFeeds(current, merged);
      this.updateLibrary(() => S.apply(this.state.library, merged), false);
      const out = S.pruned(merged, Date.now() - S.KEEP_READ_MS);
      const outText = S.encode(out);
      if (!remote || S.encode(remote) !== outText) await target.write(outText);
      await save("syncBase", outText);
      this.knownRead = new Set(Object.keys(out.read));
      this.updateSettings({ lastSyncedAt: Date.now() });
      this.set({ syncStatus: { kind: "idle" } });
      if (newFeeds) void this.refresh();
    } catch (e) {
      this.set({ syncStatus: { kind: "failed", problem: e instanceof SyncError ? e.problem : "server" } });
    }
  }
}

function size(link: HTMLLinkElement): number {
  return Number((link.getAttribute("sizes") ?? "").split("x")[0]) || (link.rel.includes("apple-touch-icon") ? 180 : 16);
}

export const app = new AppStore();

export function useApp(): State {
  return useSyncExternalStore(app.subscribe, app.get);
}
