import { Archive, ArchiveRestore, Folder as FolderGlyph, FolderInput, Heart, Inbox, NotebookPen, Plus, Tag as TagIcon, Trash2, X } from "lucide-react";
import { useState } from "react";
import { app, useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import { itemTags, type Item } from "@/lib/items";
import { folderOf, savedFolders } from "@/lib/saved";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { Dialog } from "./ui/dialog";
import { Input } from "./ui/input";

/** The reader's buttons for a saved article: like, archive, its folder, tags, note, remove. */
export function SavedActions({ item, onRemoved }: { item: Item; onRemoved: () => void }) {
  const [open, setOpen] = useState<"folder" | "tags" | "note" | "remove" | null>(null);
  const archived = item.readingList?.state === "archived";
  return (
    <>
      <Button
        variant={item.liked ? "tonal" : "ghost"}
        size="icon"
        onClick={() => app.likeItem(item.key, !item.liked)}
        aria-pressed={item.liked}
        aria-label={item.liked ? t("unlike") : t("like")}
        title={item.liked ? t("unlike") : t("like")}
      >
        <Heart className={cn(item.liked && "fill-current")} />
      </Button>
      <Button variant="ghost" size="icon" onClick={() => app.archiveItem(item.key, !archived)} aria-label={archived ? t("unarchive") : t("archive")} title={archived ? t("unarchive") : t("archive")}>
        {archived ? <ArchiveRestore /> : <Archive />}
      </Button>
      <Button variant="ghost" size="icon" onClick={() => setOpen("folder")} aria-label={t("folder")} title={t("folder")}>
        <FolderInput />
      </Button>
      <Button variant="ghost" size="icon" onClick={() => setOpen("tags")} aria-label={t("tags")} title={t("tags")}>
        <TagIcon />
      </Button>
      <Button variant={item.note ? "tonal" : "ghost"} size="icon" onClick={() => setOpen("note")} aria-label={t("note")} title={t("note")}>
        <NotebookPen />
      </Button>
      <Button variant="ghost" size="icon" onClick={() => setOpen("remove")} aria-label={t("savedRemove")} title={t("savedRemove")}>
        <Trash2 />
      </Button>
      <FolderDialog item={item} open={open === "folder"} onClose={() => setOpen(null)} />
      <Dialog open={open === "tags"} onOpenChange={(o) => !o && setOpen(null)} title={t("tags")} closeLabel={t("close")}>
        <TagsEditor item={item} />
      </Dialog>
      <NoteDialog key={open === "note" ? "open" : "closed"} item={item} open={open === "note"} onClose={() => setOpen(null)} />
      <Dialog
        open={open === "remove"}
        onOpenChange={(o) => !o && setOpen(null)}
        title={t("savedRemove")}
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
                app.removeItem(item.key);
                onRemoved();
              }}
            >
              <Trash2 />
              {t("savedRemove")}
            </Button>
          </>
        }
      >
        <p className="text-sm">{t("savedRemoveBody")}</p>
      </Dialog>
    </>
  );
}

/** To read or one of the folders, like choosing a feed's folder. */
function FolderDialog({ item, open, onClose }: { item: Item; open: boolean; onClose: () => void }) {
  const { library } = useApp();
  const current = folderOf(item);
  const archived = item.readingList?.state === "archived";
  const choose = (folder: string | null) => {
    onClose();
    if (folder !== current || archived) app.fileItem(item.key, folder);
  };
  const option = (folder: string | null, label: string, icon: React.ReactNode) => {
    const selected = !archived && (current ?? null)?.toLowerCase() === folder?.toLowerCase();
    return (
      <button
        key={folder ?? "toread"}
        type="button"
        role="radio"
        aria-checked={selected}
        onClick={() => choose(folder)}
        className={cn("flex w-full cursor-pointer items-center gap-3 rounded-lg px-3 py-2 text-left text-sm hover:bg-muted", selected && "bg-accent font-medium text-accent-foreground")}
      >
        <span className="text-muted-foreground [&>*]:size-5">{icon}</span>
        {label}
      </button>
    );
  };
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()} title={t("folder")} closeLabel={t("close")}>
      <div role="radiogroup" aria-label={t("folder")} className="space-y-0.5">
        {option(null, t("toRead"), <Inbox />)}
        {savedFolders(library).map((f) => option(f, f, <FolderGlyph />))}
      </div>
      <NewFolder onCreate={(name) => choose(name)} />
    </Dialog>
  );
}

/** The article's note: what you write about it, shown on its card in a folder and searched. */
function NoteDialog({ item, open, onClose }: { item: Item; open: boolean; onClose: () => void }) {
  const [note, setNote] = useState(item.note);
  const keep = () => {
    if (note.trim() !== item.note) app.setItemNote(item.key, note);
    onClose();
  };
  return (
    <Dialog
      open={open}
      onOpenChange={(o) => !o && keep()}
      title={t("note")}
      closeLabel={t("close")}
      footer={<Button onClick={keep}>{t("done")}</Button>}
    >
      <textarea
        autoFocus
        value={note}
        onChange={(e) => setNote(e.target.value)}
        aria-label={t("note")}
        rows={6}
        className="w-full resize-y rounded-lg border bg-transparent px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
      />
    </Dialog>
  );
}

/** The article's tags: remove one, add one (new names become new tags), or pick one already used. */
export function TagsEditor({ item }: { item: Item }) {
  const { library } = useApp();
  const [name, setName] = useState("");
  const has = (n: string) => item.tags.some((x) => x.toLowerCase() === n.toLowerCase());
  const add = (n: string) => {
    const tag = n.trim();
    if (tag && !has(tag)) app.tagItem(item.key, [...item.tags, tag]);
  };
  const others = itemTags(library).filter((x) => !has(x));
  return (
    <div className="space-y-4">
      {item.tags.length > 0 ? (
        <div className="flex flex-wrap gap-1.5">
          {item.tags.map((x) => (
            <span key={x} className="inline-flex items-center gap-1 rounded-full bg-accent px-3 py-1 text-sm text-accent-foreground">
              {x}
              <button
                type="button"
                className="cursor-pointer rounded-full hover:bg-background/60"
                aria-label={`${t("tagRemove")}: ${x}`}
                title={t("tagRemove")}
                onClick={() => app.tagItem(item.key, item.tags.filter((y) => y !== x))}
              >
                <X className="size-3.5" />
              </button>
            </span>
          ))}
        </div>
      ) : null}
      <form
        className="flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          add(name);
          setName("");
        }}
      >
        <Input value={name} onChange={(e) => setName(e.target.value)} placeholder={t("tagAdd")} aria-label={t("tagAdd")} maxLength={60} />
        <Button type="submit" variant="tonal" disabled={!name.trim()}>
          <Plus />
          {t("add")}
        </Button>
      </form>
      {others.length > 0 ? (
        <div className="flex flex-wrap gap-1.5">
          {others.map((x) => (
            <button key={x} type="button" onClick={() => add(x)} className="inline-flex cursor-pointer items-center gap-1 rounded-full border px-3 py-1 text-sm hover:bg-muted">
              <Plus className="size-3.5" />
              {x}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}

/**
 * Opens right after a feed article is saved: like it, archive it, put it in a folder and tag it at
 * once. Every choice applies straight away; closing leaves the article as it is (in Saved).
 */
export function JustSavedDialog() {
  const { justSaved, library } = useApp();
  const item = justSaved ? library.items.find((i) => i.key === justSaved) : undefined;
  const archived = item?.readingList?.state === "archived";
  const chip = (i: Item, folder: string | null, label: string) => {
    const selected = !archived && (folderOf(i)?.toLowerCase() ?? null) === (folder?.toLowerCase() ?? null);
    return (
      <button
        key={folder ?? "toread"}
        type="button"
        role="radio"
        aria-checked={selected}
        onClick={() => !selected && app.fileItem(i.key, folder)}
        className={cn(
          "inline-flex cursor-pointer items-center gap-1.5 rounded-lg border px-3 py-1.5 text-sm",
          selected ? "border-transparent bg-accent font-medium text-accent-foreground" : "hover:bg-muted",
        )}
      >
        {folder === null ? <Inbox className="size-4" /> : <FolderInput className="size-4" />}
        {label}
      </button>
    );
  };
  return (
    <Dialog
      open={item !== undefined}
      onOpenChange={(o) => !o && app.closeJustSaved()}
      title={t("saved")}
      closeLabel={t("close")}
      footer={<Button onClick={app.closeJustSaved}>{t("done")}</Button>}
    >
      {item ? (
        <div className="space-y-5">
          <div className="flex flex-wrap gap-1.5">
            <Toggle pressed={item.liked} onClick={() => app.likeItem(item.key, !item.liked)} icon={<Heart className={cn("size-4", item.liked && "fill-current")} />} label={t("like")} />
            <Toggle pressed={archived} onClick={() => app.archiveItem(item.key, !archived)} icon={<Archive className="size-4" />} label={t("archive")} />
          </div>
          <section className="space-y-2">
            <h3 className="text-sm font-medium text-primary">{t("folder")}</h3>
            <div role="radiogroup" aria-label={t("folder")} className="flex flex-wrap gap-1.5">
              {chip(item, null, t("toRead"))}
              {savedFolders(library).map((f) => chip(item, f, f))}
            </div>
            <NewFolder onCreate={(name) => app.fileItem(item.key, name)} />
          </section>
          <section className="space-y-2">
            <h3 className="text-sm font-medium text-primary">{t("tags")}</h3>
            <TagsEditor item={item} />
          </section>
        </div>
      ) : null}
    </Dialog>
  );
}

/** A chip that is on or off, like Like and Archive after saving. */
function Toggle({ pressed, onClick, icon, label }: { pressed: boolean; onClick: () => void; icon: React.ReactNode; label: string }) {
  return (
    <button
      type="button"
      aria-pressed={pressed}
      onClick={onClick}
      className={cn(
        "inline-flex cursor-pointer items-center gap-1.5 rounded-lg border px-3 py-1.5 text-sm",
        pressed ? "border-transparent bg-accent font-medium text-accent-foreground" : "hover:bg-muted",
      )}
    >
      {icon}
      {label}
    </button>
  );
}

/** "New folder" under a list of folders: shows a field for its name; adding files the article there. */
function NewFolder({ onCreate }: { onCreate: (name: string) => void }) {
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
