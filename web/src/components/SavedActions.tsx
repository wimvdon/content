import { Archive, ArchiveRestore, Heart, BookOpen, Quote, Share2, Tag, Trash2 } from "lucide-react";
import { app } from "@/lib/app";
import { plural, t } from "@/lib/i18n";
import type { Item } from "@/lib/items";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { Dialog } from "./ui/dialog";

/**
 * The reader's library actions, in the reader's top bar before Aa and ⋮ (the web's own toolbar, not
 * Android's floating one, 2026-11-04). No button has a fill of its own: the next step stands out by
 * its words (shown from a small width up; an icon on a phone-sized window). Before the article is saved: **Save**
 * with its name, the highlights, Share. After: **Archive** with its name while it is to read, or only
 * the unarchive icon (To read) once archived; then **Like** (a filled heart while liked), **Tags** and
 * **Share** as icons. The note and the highlights sit under the article's title; Remove from Saved
 * is in the reader's ⋮.
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
  /** How many highlights it has; with any, a quote mark and the count open their list. */
  highlightCount: number;
  onHighlights: () => void;
}) {
  const highlights =
    highlightCount > 0 ? (
      <Button variant="ghost" onClick={onHighlights} aria-label={plural("highlightsCount", highlightCount)} title={t("highlightsTitle")}>
        <Quote />
        {highlightCount}
      </Button>
    ) : null;
  const share = onShare ? (
    <Button variant="ghost" size="icon" onClick={onShare} aria-label={t("share")} title={t("share")}>
      <Share2 />
    </Button>
  ) : null;
  if (!item) {
    if (!onSave && !onShare && highlightCount === 0) return null;
    return (
      <Group>
        {onSave ? (
          <Button variant="ghost" onClick={onSave} aria-label={t("saveArticle")} className="px-2.5 sm:px-4">
            <BookOpen />
            <span className="hidden sm:inline">{t("saveArticle")}</span>
          </Button>
        ) : null}
        {highlights}
        {share}
      </Group>
    );
  }
  const archived = item.readingList?.state === "archived";
  return (
    <Group>
      {/* What moves it: Archive in words while to read (the next step), the unarchive icon once archived. */}
      {archived ? (
        <Button variant="ghost" size="icon" aria-label={t("toRead")} title={t("toRead")} onClick={() => app.archiveItem(item.key, false)}>
          <ArchiveRestore />
        </Button>
      ) : (
        <Button
          variant="ghost"
          className="min-w-0 px-2.5 sm:px-4"
          aria-label={t("archive")}
          onClick={() => {
            app.archiveItem(item.key, true);
            onArchived(() => app.archiveItem(item.key, false));
          }}
        >
          <Archive />
          <span className="hidden truncate sm:inline">{t("archive")}</span>
        </Button>
      )}
      <Button variant="ghost" size="icon" aria-pressed={item.liked} aria-label={t("like")} title={t("like")} onClick={() => app.likeItem(item.key, !item.liked)}>
        <Heart className={cn(item.liked && "fill-current")} />
      </Button>
      <Button variant="ghost" size="icon" aria-label={t("tags")} title={t("tags")} onClick={() => app.openTopics(item.key)}>
        <Tag />
      </Button>
      {highlights}
      {share}
    </Group>
  );
}

/** The actions in the top bar, set apart from Aa and ⋮ by a line. */
function Group({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-w-0 items-center gap-0.5 border-r pr-1 mr-1">
      {children}
    </div>
  );
}

/** Remove from Saved, wherever it is offered (the reader's ⋮, Organise, a row's menu): asks first, and says what goes with it. */
export function RemoveDialog({ item, open, onClose, onRemoved }: { item: Item; open: boolean; onClose: () => void; onRemoved: () => void }) {
  return (
    <Dialog
      popup
      open={open}
      onOpenChange={(o) => !o && onClose()}
      title={t("savedRemove")}
      closeLabel={t("close")}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
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
