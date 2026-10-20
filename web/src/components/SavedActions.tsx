import { Archive, ArchiveRestore, ChevronDown, Folder as FolderGlyph, Heart, ListPlus, Trash2 } from "lucide-react";
import { app } from "@/lib/app";
import { t } from "@/lib/i18n";
import type { Item } from "@/lib/items";
import { folderOf } from "@/lib/saved";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { Dialog } from "./ui/dialog";

/**
 * The reader's library bar (#139), under the text: one Save before the article is saved; after,
 * its folder (which opens Organise: folder, tags with Like, note), Like and Archive (or Back to
 * read). The same as the Android app's bottom bar.
 */
export function LibraryBar({ item, onSave }: { item: Item | null; onSave: (() => void) | null }) {
  if (!item) {
    if (!onSave) return null;
    return (
      <div className="flex justify-end border-t bg-muted/40 px-4 py-2">
        <Button variant="tonal" onClick={onSave}>
          <ListPlus />
          {t("saveArticle")}
        </Button>
      </div>
    );
  }
  const archived = item.readingList?.state === "archived";
  const place = archived ? t("archiveTab") : (folderOf(item) ?? t("toRead"));
  return (
    <div className="flex items-center gap-1 border-t bg-muted/40 px-2 py-2">
      <Button variant="ghost" onClick={() => app.openOrganise(item.key)} title={t("organiseTitle")} className="min-w-0">
        <FolderGlyph />
        <span className="truncate">{place}</span>
        <ChevronDown />
      </Button>
      <div className="flex-1" />
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
      {/* At once: the article just read is the one most often done with. */}
      <Button variant="ghost" onClick={() => app.archiveItem(item.key, !archived)}>
        {archived ? <ArchiveRestore /> : <Archive />}
        {archived ? t("unarchive") : t("archive")}
      </Button>
    </div>
  );
}

/** Remove from Saved (the reader's ⋮): asks first, and says what goes with it. */
export function RemoveDialog({ item, open, onClose, onRemoved }: { item: Item; open: boolean; onClose: () => void; onRemoved: () => void }) {
  return (
    <Dialog
      open={open}
      onOpenChange={(o) => !o && onClose()}
      title={t("savedRemove")}
      closeLabel={t("close")}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t("cancel")}
          </Button>
          <Button
            variant="destructive"
            onClick={() => {
              onClose();
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
  );
}
