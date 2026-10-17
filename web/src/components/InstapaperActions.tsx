import { Archive, ArchiveRestore, FolderInput, Heart, Inbox, Plus, Tag as TagIcon, Trash2, X } from "lucide-react";
import { useState } from "react";
import { app, useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import type { Bookmark } from "@/lib/instapaper";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { Dialog } from "./ui/dialog";
import { Input } from "./ui/input";

/** The reader's buttons for a saved Instapaper article: like, archive, move to a folder, tags, delete. */
export function InstapaperActions({ bookmark: b, onDeleted }: { bookmark: Bookmark; onDeleted: () => void }) {
  const [open, setOpen] = useState<"move" | "tags" | "delete" | null>(null);
  return (
    <>
      <Button
        variant={b.liked ? "tonal" : "ghost"}
        size="icon"
        onClick={() => void app.likeBookmark(b, !b.liked)}
        aria-pressed={b.liked}
        aria-label={b.liked ? t("instapaperUnlike") : t("instapaperLike")}
        title={b.liked ? t("instapaperUnlike") : t("instapaperLike")}
      >
        <Heart className={cn(b.liked && "fill-current")} />
      </Button>
      <Button
        variant="ghost"
        size="icon"
        onClick={() => void app.archiveBookmark(b, !b.archived)}
        aria-label={b.archived ? t("instapaperMoveToUnread") : t("instapaperArchive")}
        title={b.archived ? t("instapaperMoveToUnread") : t("instapaperArchive")}
      >
        {b.archived ? <ArchiveRestore /> : <Archive />}
      </Button>
      <Button variant="ghost" size="icon" onClick={() => setOpen("move")} aria-label={t("instapaperMoveTo")} title={t("instapaperMoveTo")}>
        <FolderInput />
      </Button>
      <Button variant="ghost" size="icon" onClick={() => setOpen("tags")} aria-label={t("instapaperTags")} title={t("instapaperTags")}>
        <TagIcon />
      </Button>
      <Button variant="ghost" size="icon" onClick={() => setOpen("delete")} aria-label={t("instapaperDelete")} title={t("instapaperDelete")}>
        <Trash2 />
      </Button>
      <MoveDialog bookmark={b} open={open === "move"} onClose={() => setOpen(null)} />
      <TagsDialog bookmark={b} open={open === "tags"} onClose={() => setOpen(null)} />
      <Dialog
        open={open === "delete"}
        onOpenChange={(o) => !o && setOpen(null)}
        title={t("instapaperDelete")}
        closeLabel={t("close")}
        footer={
          <>
            <Button variant="ghost" onClick={() => setOpen(null)}>
              {t("cancel")}
            </Button>
            <Button
              variant="destructive"
              onClick={() => {
                setOpen(null);
                void app.deleteBookmark(b).then((done) => done && onDeleted());
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
    </>
  );
}

/** Unread or one of the folders, like choosing a feed's folder. */
function MoveDialog({ bookmark: b, open, onClose }: { bookmark: Bookmark; open: boolean; onClose: () => void }) {
  const { instapaperFolders } = useApp();
  const choose = (folderId: number | null) => {
    onClose();
    if (folderId !== b.folder_id || b.archived) void app.moveBookmark(b, folderId);
  };
  const option = (folderId: number | null, label: string, icon: React.ReactNode) => (
    <button
      key={folderId ?? "home"}
      type="button"
      role="radio"
      aria-checked={!b.archived && b.folder_id === folderId}
      onClick={() => choose(folderId)}
      className={cn(
        "flex w-full cursor-pointer items-center gap-3 rounded-lg px-3 py-2 text-left text-sm hover:bg-muted",
        !b.archived && b.folder_id === folderId && "bg-accent font-medium text-accent-foreground",
      )}
    >
      <span className="text-muted-foreground [&>*]:size-5">{icon}</span>
      {label}
    </button>
  );
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()} title={t("instapaperMoveTo")} closeLabel={t("close")}>
      <div role="radiogroup" aria-label={t("instapaperMoveTo")} className="space-y-0.5">
        {option(null, t("unread"), <Inbox />)}
        {instapaperFolders.map((f) => option(f.id, f.title, <FolderInput />))}
      </div>
      <NewFolder
        onCreate={(title) => {
          onClose();
          void app.moveBookmarkToNewFolder(b, title);
        }}
      />
    </Dialog>
  );
}

/** The article's tags: remove one, add one (new names become new tags), or pick one the account has. */
function TagsDialog({ bookmark: b, open, onClose }: { bookmark: Bookmark; open: boolean; onClose: () => void }) {
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()} title={t("instapaperTags")} closeLabel={t("close")}>
      <TagsEditor bookmark={b} />
    </Dialog>
  );
}

function TagsEditor({ bookmark: b }: { bookmark: Bookmark }) {
  const { instapaperTags } = useApp();
  const [name, setName] = useState("");
  const has = (n: string) => b.tags.some((x) => x.name.toLowerCase() === n.toLowerCase());
  const add = (n: string) => {
    const tag = n.trim();
    if (tag && !has(tag)) void app.tagBookmark(b, [tag], []);
  };
  const others = instapaperTags.filter((x) => !has(x.name));
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-1.5">
        {b.tags.length === 0 ? <p className="text-sm text-muted-foreground">{t("instapaperEmpty")}</p> : null}
        {b.tags.map((x) => (
          <span key={x.id} className="inline-flex items-center gap-1 rounded-full bg-accent px-3 py-1 text-sm text-accent-foreground">
            {x.name}
            <button type="button" className="cursor-pointer rounded-full hover:bg-background/60" aria-label={`${t("instapaperRemoveTag")}: ${x.name}`} title={t("instapaperRemoveTag")} onClick={() => void app.tagBookmark(b, [], [x.id])}>
              <X className="size-3.5" />
            </button>
          </span>
        ))}
      </div>
      <form
        className="flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          add(name);
          setName("");
        }}
      >
        <Input value={name} onChange={(e) => setName(e.target.value)} placeholder={t("instapaperAddTag")} aria-label={t("instapaperAddTag")} maxLength={60} />
        <Button type="submit" variant="tonal" disabled={!name.trim()}>
          <Plus />
          {t("add")}
        </Button>
      </form>
      {others.length > 0 ? (
        <div className="flex flex-wrap gap-1.5">
          {others.map((x) => (
            <button key={x.id} type="button" onClick={() => add(x.name)} className="inline-flex cursor-pointer items-center gap-1 rounded-full border px-3 py-1 text-sm hover:bg-muted">
              <Plus className="size-3.5" />
              {x.name}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}

/**
 * Opens right after a feed article is saved to Instapaper: put it in a folder and tag it at once.
 * Every choice applies straight away; closing leaves the article as it is (in Unread).
 */
export function JustSavedDialog() {
  const { instapaperJustSaved: b, instapaperFolders } = useApp();
  const chip = (folderId: number | null, label: string) => {
    const selected = (b?.folder_id ?? null) === folderId;
    return (
      <button
        key={folderId ?? "home"}
        type="button"
        role="radio"
        aria-checked={selected}
        onClick={() => b && !selected && void app.moveBookmark(b, folderId)}
        className={cn(
          "inline-flex cursor-pointer items-center gap-1.5 rounded-lg border px-3 py-1.5 text-sm",
          selected ? "border-transparent bg-accent font-medium text-accent-foreground" : "hover:bg-muted",
        )}
      >
        {folderId === null ? <Inbox className="size-4" /> : <FolderInput className="size-4" />}
        {label}
      </button>
    );
  };
  return (
    <Dialog
      open={b !== null}
      onOpenChange={(o) => !o && app.closeJustSaved()}
      title={t("instapaperSaved")}
      closeLabel={t("close")}
      footer={<Button onClick={app.closeJustSaved}>{t("done")}</Button>}
    >
      {b ? (
        <div className="space-y-5">
          <section className="space-y-2">
            <h3 className="text-sm font-medium text-primary">{t("folder")}</h3>
            <div role="radiogroup" aria-label={t("folder")} className="flex flex-wrap gap-1.5">
              {chip(null, t("unread"))}
              {instapaperFolders.map((f) => chip(f.id, f.title))}
            </div>
            <NewFolder onCreate={(title) => void app.moveBookmarkToNewFolder(b, title)} />
          </section>
          <section className="space-y-2">
            <h3 className="text-sm font-medium text-primary">{t("instapaperTags")}</h3>
            <TagsEditor bookmark={b} />
          </section>
        </div>
      ) : null}
    </Dialog>
  );
}

/** "New folder" under a list of folders: shows a field for its name; adding makes the folder and moves the article into it. */
function NewFolder({ onCreate }: { onCreate: (title: string) => void }) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  if (!open) {
    return (
      <Button variant="ghost" className="mt-2" onClick={() => setOpen(true)}>
        <Plus />
        {t("newFolder")}
      </Button>
    );
  }
  return (
    <form
      className="mt-2 flex gap-2"
      onSubmit={(e) => {
        e.preventDefault();
        if (!name.trim()) return;
        onCreate(name.trim());
        setName("");
        setOpen(false);
      }}
    >
      <Input autoFocus value={name} onChange={(e) => setName(e.target.value)} placeholder={t("newFolder")} aria-label={t("newFolder")} maxLength={60} />
      <Button type="submit" variant="tonal" disabled={!name.trim()}>
        <Plus />
        {t("add")}
      </Button>
    </form>
  );
}
