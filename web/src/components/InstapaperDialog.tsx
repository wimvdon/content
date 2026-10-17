import { Archive, ArchiveRestore, ArrowDown, ArrowUp, ExternalLink, Folder as FolderIcon, FolderInput, Heart, Plus, Tag as TagIcon, Trash2, X } from "lucide-react";
import { useEffect, useState } from "react";
import { app } from "@/lib/app";
import { formatDateTime, t } from "@/lib/i18n";
import { reorderedPositions, type Bookmark, type Folder, type InstapaperApi, type Tag, type View } from "@/lib/instapaper";
import { problemText } from "@/lib/instapaperText";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { Dialog } from "./ui/dialog";
import { Input, Select } from "./ui/input";

const PAGE = 25;

type Props = { open: boolean; onOpenChange: (open: boolean) => void };

const sameView = (a: View, b: View) => JSON.stringify(a) === JSON.stringify(b);

/** Folders and tags change with every move, so they are read again after each change. */
const readSides = () => app.withInstapaper(async (api) => ({ folders: await api.folders(), tags: await api.tags() }));

/** The user's Instapaper account: Unread, Archive, Liked, folders and tags, with the actions on each article. */
export function InstapaperDialog({ open, onOpenChange }: Props) {
  const [view, setView] = useState<View>({ kind: "home" });
  const [items, setItems] = useState<Bookmark[]>([]);
  const [total, setTotal] = useState(0);
  /** The list the items belong to. Until it matches the chosen list, that list is still loading. */
  const [loadedFor, setLoadedFor] = useState<View | null>(null);
  const [more, setMore] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [folders, setFolders] = useState<Folder[]>([]);
  const [tags, setTags] = useState<Tag[]>([]);
  const [manage, setManage] = useState<"folders" | "tags" | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<Bookmark | null>(null);
  const loading = more || loadedFor === null || !sameView(loadedFor, view);

  const choose = (next: View) => {
    setProblem(null);
    setView(next);
  };

  const refreshSides = () =>
    void readSides()
      .then((sides) => {
        setFolders(sides.folders);
        setTags(sides.tags);
      })
      .catch(() => undefined);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    readSides()
      .then((sides) => {
        if (cancelled) return;
        setFolders(sides.folders);
        setTags(sides.tags);
      })
      .catch((e) => !cancelled && setProblem(problemText(e)));
    return () => {
      cancelled = true;
    };
  }, [open]);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    app
      .withInstapaper((api) => api.list(view, 0, PAGE))
      .then((page) => {
        if (cancelled) return;
        setItems(page.bookmarks);
        setTotal(page.total);
        setLoadedFor(view);
      })
      .catch((e) => {
        if (cancelled) return;
        setItems([]);
        setTotal(0);
        setLoadedFor(view);
        setProblem(problemText(e));
      });
    return () => {
      cancelled = true;
    };
  }, [open, view]);

  /** Runs one change; a failure is shown above the list. */
  const act = async (run: (api: InstapaperApi) => Promise<void>) => {
    setProblem(null);
    try {
      await app.withInstapaper(run);
    } catch (e) {
      setProblem(problemText(e));
    }
  };

  const loadMore = async () => {
    setMore(true);
    try {
      const page = await app.withInstapaper((api) => api.list(view, items.length, PAGE));
      setItems((current) => [...current, ...page.bookmarks.filter((b) => !current.some((c) => c.id === b.id))]);
      setTotal(page.total);
    } catch (e) {
      setProblem(problemText(e));
    } finally {
      setMore(false);
    }
  };

  const drop = (id: number) => {
    setItems((current) => current.filter((b) => b.id !== id));
    setTotal((n) => Math.max(n - 1, 0));
  };
  const patch = (id: number, change: Partial<Bookmark>) => setItems((current) => current.map((b) => (b.id === id ? { ...b, ...change } : b)));

  const toggleArchive = (b: Bookmark) =>
    act(async (api) => {
      await api.move(b.id, b.archived ? "home" : "archive");
      if (view.kind === "archive" || view.kind === "home" || view.kind === "folder") drop(b.id);
      else patch(b.id, { archived: !b.archived });
      refreshSides();
    });

  const toggleLike = (b: Bookmark) =>
    act(async (api) => {
      const updated = await api.like(b.id, !b.liked);
      if (view.kind === "liked" && !updated.liked) drop(b.id);
      else patch(b.id, { liked: updated.liked });
    });

  const moveTo = (b: Bookmark, target: "home" | number) =>
    act(async (api) => {
      await api.move(b.id, target);
      if (view.kind === "home" || view.kind === "folder") drop(b.id);
      else patch(b.id, { folder_id: target === "home" ? null : target, archived: false });
      refreshSides();
    });

  const changeTags = (b: Bookmark, add: string[], remove: number[]) =>
    act(async (api) => {
      const next = await api.setTags(b.id, add, remove);
      if (view.kind === "tag" && !next.some((tag) => tag.name === view.name)) drop(b.id);
      else patch(b.id, { tags: next });
      refreshSides();
    });

  const remove = (b: Bookmark) =>
    act(async (api) => {
      await api.remove(b.id);
      drop(b.id);
      refreshSides();
    });

  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={t("instapaper")} closeLabel={t("close")} wide>
      <div className="space-y-4">
        <div className="flex flex-wrap gap-2" role="tablist" aria-label={t("instapaper")}>
          <Chip selected={view.kind === "home"} onClick={() => choose({ kind: "home" })}>
            {t("unread")}
          </Chip>
          <Chip selected={view.kind === "archive"} onClick={() => choose({ kind: "archive" })}>
            {t("instapaperArchiveList")}
          </Chip>
          <Chip selected={view.kind === "liked"} onClick={() => choose({ kind: "liked" })}>
            {t("instapaperLiked")}
          </Chip>
          {folders.map((folder) => (
            <Chip key={`f${folder.id}`} selected={sameView(view, { kind: "folder", id: folder.id })} onClick={() => choose({ kind: "folder", id: folder.id })}>
              <FolderIcon className="size-3.5" />
              {folder.title}
            </Chip>
          ))}
          {tags.map((tag) => (
            <Chip key={`t${tag.id}`} selected={sameView(view, { kind: "tag", name: tag.name })} onClick={() => choose({ kind: "tag", name: tag.name })}>
              <TagIcon className="size-3.5" />
              {tag.name}
            </Chip>
          ))}
        </div>
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant={manage === "folders" ? "tonal" : "ghost"} onClick={() => setManage(manage === "folders" ? null : "folders")}>
            {t("instapaperManageFolders")}
          </Button>
          <Button size="sm" variant={manage === "tags" ? "tonal" : "ghost"} onClick={() => setManage(manage === "tags" ? null : "tags")}>
            {t("instapaperManageTags")}
          </Button>
        </div>
        {manage === "folders" ? (
          <FolderManager
            folders={folders}
            onChange={setFolders}
            onProblem={setProblem}
            onDeleted={(id) => {
              if (view.kind === "folder" && view.id === id) choose({ kind: "home" });
              refreshSides();
            }}
          />
        ) : null}
        {manage === "tags" ? (
          <TagManager
            tags={tags}
            onChange={setTags}
            onProblem={setProblem}
            onRenamed={(from, to) => {
              if (view.kind === "tag" && view.name === from) choose({ kind: "tag", name: to });
              else refreshSides();
            }}
          />
        ) : null}
        {problem ? (
          <p role="alert" className="text-sm text-destructive">
            {problem}
          </p>
        ) : null}
        {items.length === 0 && !loading && !problem ? <p className="text-sm text-muted-foreground">{t("instapaperEmpty")}</p> : null}
        <ul className="space-y-2">
          {items.map((b) => (
            <BookmarkRow
              key={b.id}
              bookmark={b}
              folders={folders}
              onArchive={() => void toggleArchive(b)}
              onLike={() => void toggleLike(b)}
              onMove={(target) => void moveTo(b, target)}
              onDelete={() => setConfirmDelete(b)}
              onTags={(add, removeIds) => void changeTags(b, add, removeIds)}
            />
          ))}
        </ul>
        {items.length < total ? (
          <Button variant="tonal" onClick={() => void loadMore()} disabled={loading}>
            {t("instapaperLoadMore")}
          </Button>
        ) : null}
      </div>
      <Dialog
        open={confirmDelete !== null}
        onOpenChange={(o) => !o && setConfirmDelete(null)}
        title={t("instapaperDelete")}
        closeLabel={t("close")}
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmDelete(null)}>
              {t("cancel")}
            </Button>
            <Button
              variant="destructive"
              onClick={() => {
                if (confirmDelete) void remove(confirmDelete);
                setConfirmDelete(null);
              }}
            >
              <Trash2 />
              {t("instapaperDelete")}
            </Button>
          </>
        }
      >
        <p className="text-sm">{t("instapaperDeleteConfirm")}</p>
      </Dialog>
    </Dialog>
  );
}

function Chip({ selected, onClick, children }: { selected: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={selected}
      onClick={onClick}
      className={cn(
        "inline-flex max-w-full cursor-pointer items-center gap-1.5 rounded-full border px-3 py-1 text-sm",
        selected ? "border-transparent bg-accent font-medium text-accent-foreground" : "hover:bg-muted",
      )}
    >
      {children}
    </button>
  );
}

/** Only web addresses are linked, so a saved item can't carry a script address. */
function webAddress(url: string | null): string | null {
  if (!url) return null;
  try {
    const parsed = new URL(url);
    return parsed.protocol === "https:" || parsed.protocol === "http:" ? parsed.toString() : null;
  } catch {
    return null;
  }
}

function hostOf(url: string | null): string {
  try {
    return url ? new URL(url).hostname.replace(/^www\./, "") : "";
  } catch {
    return "";
  }
}

function BookmarkRow({
  bookmark: b,
  folders,
  onArchive,
  onLike,
  onMove,
  onDelete,
  onTags,
}: {
  bookmark: Bookmark;
  folders: Folder[];
  onArchive: () => void;
  onLike: () => void;
  onMove: (target: "home" | number) => void;
  onDelete: () => void;
  onTags: (add: string[], removeIds: number[]) => void;
}) {
  const [tag, setTag] = useState("");
  const link = webAddress(b.url);
  const title = b.title || b.url || "";
  const addTag = () => {
    const name = tag.trim();
    if (!name) return;
    setTag("");
    if (!b.tags.some((x) => x.name.toLowerCase() === name.toLowerCase())) onTags([name], []);
  };
  return (
    <li className="rounded-xl border p-3">
      <div className="min-w-0">
        {link ? (
          <a href={link} target="_blank" rel="noopener noreferrer" className="flex items-center gap-1.5 font-medium hover:underline" title={t("openWebsite")}>
            <span className="min-w-0 truncate">{title}</span>
            <ExternalLink className="size-3.5 shrink-0 text-muted-foreground" />
          </a>
        ) : (
          <p className="truncate font-medium">{title}</p>
        )}
        <p className="truncate text-xs text-muted-foreground">{[hostOf(b.url), formatDateTime(b.time * 1000)].filter(Boolean).join(" · ")}</p>
        {b.description ? <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">{b.description}</p> : null}
      </div>
      <div className="mt-2 flex flex-wrap items-center gap-1">
        <Button variant={b.liked ? "tonal" : "ghost"} size="iconSm" onClick={onLike} aria-pressed={b.liked} aria-label={b.liked ? t("instapaperUnlike") : t("instapaperLike")} title={b.liked ? t("instapaperUnlike") : t("instapaperLike")}>
          <Heart className={cn(b.liked && "fill-current")} />
        </Button>
        <Button variant="ghost" size="iconSm" onClick={onArchive} aria-label={b.archived ? t("instapaperMoveToUnread") : t("instapaperArchive")} title={b.archived ? t("instapaperMoveToUnread") : t("instapaperArchive")}>
          {b.archived ? <ArchiveRestore /> : <Archive />}
        </Button>
        {folders.length > 0 ? (
          <span className="inline-flex items-center gap-1">
            <FolderInput className="size-4 text-muted-foreground" aria-hidden="true" />
            <Select
              className="h-8 w-auto max-w-40 text-xs"
              aria-label={t("instapaperMoveTo")}
              title={t("instapaperMoveTo")}
              value={b.folder_id === null ? "home" : String(b.folder_id)}
              onChange={(e) => onMove(e.currentTarget.value === "home" ? "home" : Number(e.currentTarget.value))}
            >
              <option value="home">{t("unread")}</option>
              {folders.map((folder) => (
                <option key={folder.id} value={folder.id}>
                  {folder.title}
                </option>
              ))}
            </Select>
          </span>
        ) : null}
        <Button variant="destructive" size="iconSm" className="ml-auto" onClick={onDelete} aria-label={t("instapaperDelete")} title={t("instapaperDelete")}>
          <Trash2 />
        </Button>
      </div>
      <div className="mt-2 flex flex-wrap items-center gap-1.5">
        {b.tags.map((x) => (
          <span key={x.id} className="inline-flex items-center gap-1 rounded-full bg-muted px-2 py-0.5 text-xs">
            {x.name}
            <button type="button" className="cursor-pointer rounded-full hover:bg-background" aria-label={`${t("instapaperRemoveTag")}: ${x.name}`} title={t("instapaperRemoveTag")} onClick={() => onTags([], [x.id])}>
              <X className="size-3" />
            </button>
          </span>
        ))}
        <form
          className="flex items-center gap-1"
          onSubmit={(e) => {
            e.preventDefault();
            addTag();
          }}
        >
          <Input className="h-7 w-32 px-2 text-xs" value={tag} onChange={(e) => setTag(e.target.value)} placeholder={t("instapaperAddTag")} aria-label={t("instapaperAddTag")} maxLength={60} />
          <Button type="submit" variant="ghost" size="iconSm" className="size-7" aria-label={t("instapaperAddTag")} title={t("instapaperAddTag")} disabled={!tag.trim()}>
            <Plus />
          </Button>
        </form>
      </div>
    </li>
  );
}

function FolderManager({
  folders,
  onChange,
  onProblem,
  onDeleted,
}: {
  folders: Folder[];
  onChange: (folders: Folder[]) => void;
  onProblem: (text: string) => void;
  onDeleted: (id: number) => void;
}) {
  const [title, setTitle] = useState("");
  const run = async (work: (api: InstapaperApi) => Promise<void>) => {
    try {
      await app.withInstapaper(work);
    } catch (e) {
      onProblem(problemText(e));
    }
  };
  return (
    <section className="space-y-2 rounded-xl border p-3">
      <p className="text-xs text-muted-foreground">{t("instapaperDeleteFolderNote")}</p>
      <ul className="space-y-1">
        {folders.map((folder, index) => (
          <li key={folder.id} className="flex items-center gap-1">
            <span className="min-w-0 flex-1 truncate text-sm">{folder.title}</span>
            <Button
              variant="ghost"
              size="iconSm"
              disabled={index === 0}
              aria-label={t("moveUp")}
              title={t("moveUp")}
              onClick={() => {
                const order = reorderedPositions(folders, index, -1);
                if (order) void run(async (api) => onChange(await api.reorderFolders(order)));
              }}
            >
              <ArrowUp />
            </Button>
            <Button
              variant="ghost"
              size="iconSm"
              disabled={index === folders.length - 1}
              aria-label={t("moveDown")}
              title={t("moveDown")}
              onClick={() => {
                const order = reorderedPositions(folders, index, 1);
                if (order) void run(async (api) => onChange(await api.reorderFolders(order)));
              }}
            >
              <ArrowDown />
            </Button>
            <Button
              variant="destructive"
              size="iconSm"
              aria-label={`${t("instapaperDeleteFolder")}: ${folder.title}`}
              title={t("instapaperDeleteFolder")}
              onClick={() =>
                void run(async (api) => {
                  await api.deleteFolder(folder.id);
                  onChange(folders.filter((f) => f.id !== folder.id));
                  onDeleted(folder.id);
                })
              }
            >
              <Trash2 />
            </Button>
          </li>
        ))}
      </ul>
      <form
        className="flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          const name = title.trim();
          if (!name) return;
          setTitle("");
          void run(async (api) => {
            const created = await api.createFolder(name);
            onChange([...folders, created].sort((a, b) => a.position - b.position));
          });
        }}
      >
        <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder={t("newFolder")} aria-label={t("newFolder")} maxLength={100} />
        <Button type="submit" variant="tonal" disabled={!title.trim()}>
          <Plus />
          {t("add")}
        </Button>
      </form>
    </section>
  );
}

function TagManager({
  tags,
  onChange,
  onProblem,
  onRenamed,
}: {
  tags: Tag[];
  onChange: (tags: Tag[]) => void;
  onProblem: (text: string) => void;
  onRenamed: (from: string, to: string) => void;
}) {
  const [names, setNames] = useState<Record<number, string>>({});
  return (
    <section className="space-y-1 rounded-xl border p-3">
      {tags.map((tag) => {
        const name = names[tag.id] ?? tag.name;
        const changed = name.trim() !== "" && name.trim() !== tag.name;
        return (
          <form
            key={tag.id}
            className="flex items-center gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              if (!changed) return;
              const next = name.trim();
              app
                .withInstapaper((api) => api.renameTag(tag.id, next))
                .then((renamed) => {
                  onChange(tags.map((x) => (x.id === tag.id ? renamed : x)));
                  setNames((current) => ({ ...current, [tag.id]: renamed.name }));
                  onRenamed(tag.name, renamed.name);
                })
                .catch((error) => onProblem(problemText(error)));
            }}
          >
            <Input className="h-8" value={name} onChange={(e) => setNames((current) => ({ ...current, [tag.id]: e.target.value }))} aria-label={t("instapaperTagName")} maxLength={60} />
            <Button type="submit" variant="tonal" size="sm" disabled={!changed}>
              {t("instapaperRenameTag")}
            </Button>
          </form>
        );
      })}
    </section>
  );
}
