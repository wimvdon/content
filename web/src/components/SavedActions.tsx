import { Archive, ArchiveRestore, FolderInput, Heart, Trash2 } from "lucide-react";
import { useState } from "react";
import { app } from "@/lib/app";
import { t } from "@/lib/i18n";
import type { Item } from "@/lib/items";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { Dialog } from "./ui/dialog";

/** The reader's buttons for a saved article: like, archive, Organise (folder, tags, note), remove. */
export function SavedActions({ item, onRemoved }: { item: Item; onRemoved: () => void }) {
  const [open, setOpen] = useState<"remove" | null>(null);
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
      {/* Organise (#143): the folder, tags with Like, and the note, in one sheet. */}
      <Button variant="ghost" size="icon" onClick={() => app.openOrganise(item.key)} aria-label={t("organiseTitle")} title={t("organiseTitle")}>
        <FolderInput />
      </Button>
      <Button variant="ghost" size="icon" onClick={() => setOpen("remove")} aria-label={t("savedRemove")} title={t("savedRemove")}>
        <Trash2 />
      </Button>
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
