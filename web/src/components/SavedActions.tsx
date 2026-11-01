import { Archive, ArchiveRestore, ListPlus, Quote, Share2, Tag, Trash2 } from "lucide-react";
import { app } from "@/lib/app";
import { plural, t } from "@/lib/i18n";
import type { Item } from "@/lib/items";
import { Button } from "./ui/button";
import { Dialog } from "./ui/dialog";

/**
 * The reader's library bar (#139, #156, #305), under the text: one Save before the article is
 * saved; after, two buttons that say in words what they do, as on Android. **Archive** archives it
 * in one tap (back to the list, with Undo); once archived it is **Back to read**, as Mark as read
 * turns into Mark as unread. **Organise** opens the sheet with the topics, Like and the note. The
 * highlights and Share sit at the right end.
 */
export function LibraryBar({
  item,
  onSave,
  onShare,
  onArchived,
  highlightCount,
  onHighlights,
}: {
  item: Item | null;
  onSave: (() => void) | null;
  onShare: (() => void) | null;
  onArchived: (undo: () => void) => void;
  /** How many highlights it has; with any, a quote mark and the count open their list, next to Share. */
  highlightCount: number;
  onHighlights: () => void;
}) {
  // At the right end, after Save or Archive and Organise: the highlights, then Share.
  const share = (
    <div className="ml-auto flex shrink-0 items-center">
      {highlightCount > 0 ? (
        <Button variant="ghost" onClick={onHighlights} aria-label={plural("highlightsCount", highlightCount)} title={t("highlightsTitle")}>
          <Quote />
          {highlightCount}
        </Button>
      ) : null}
      {onShare ? (
        <Button variant="ghost" size="icon" onClick={onShare} aria-label={t("share")} title={t("share")}>
          <Share2 />
        </Button>
      ) : null}
    </div>
  );
  if (!item) {
    if (!onSave && !onShare && highlightCount === 0) return null;
    return (
      <div className="flex items-center gap-1 border-t bg-muted/40 px-2 py-2">
        {onSave ? (
          <Button variant="tonal" onClick={onSave} className="ml-2">
            <ListPlus />
            {t("saveArticle")}
          </Button>
        ) : null}
        {share}
      </div>
    );
  }
  const archived = item.readingList?.state === "archived";
  return (
    <div className="flex items-center gap-1 border-t bg-muted/40 px-2 py-2">
      <Button
        variant="ghost"
        className="min-w-0"
        onClick={() => {
          app.archiveItem(item.key, !archived);
          // Archived: done with it, back to the list with Undo. Back to read keeps it open.
          if (!archived) onArchived(() => app.archiveItem(item.key, false));
        }}
      >
        {archived ? <ArchiveRestore /> : <Archive />}
        <span className="truncate">{t(archived ? "backToRead" : "archive")}</span>
      </Button>
      <Button variant="ghost" className="min-w-0" onClick={() => app.openOrganise(item.key)}>
        <Tag />
        <span className="truncate">{t("organiseTitle")}</span>
      </Button>
      {share}
    </div>
  );
}

/** Remove from Saved, wherever it is offered (the reader's ⋮, Organise, a row's menu): asks first, and says what goes with it. */
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
