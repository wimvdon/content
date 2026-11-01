import { Archive, ListPlus, Quote, Share2, Tag, Trash2 } from "lucide-react";
import { app } from "@/lib/app";
import { plural, t } from "@/lib/i18n";
import type { Item } from "@/lib/items";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { Dialog } from "./ui/dialog";

/**
 * The reader's library actions (#139, #156, #305) as a floating toolbar, as on Android: a pill at
 * the bottom in the middle, over the text, that steps aside while reading down ([hidden]). One Save
 * before the article is saved; after, **Archive** (only while it is to read: it archives it in one
 * tap and the article stays open, with a message and Undo) and **Organise** (To read or Archive,
 * the topics, Like and the note; archived, the only one). Then the highlights and Share.
 */
export function LibraryBar({
  item,
  onSave,
  onShare,
  onArchived,
  highlightCount,
  onHighlights,
  hidden,
}: {
  item: Item | null;
  onSave: (() => void) | null;
  onShare: (() => void) | null;
  onArchived: (undo: () => void) => void;
  /** How many highlights it has; with any, a quote mark and the count open their list, next to Share. */
  highlightCount: number;
  onHighlights: () => void;
  /** Stepped aside while reading down. */
  hidden: boolean;
}) {
  // After Save or Archive and Organise: the highlights, then Share.
  const share = (
    <div className="flex shrink-0 items-center">
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
      <Floating hidden={hidden}>
        {onSave ? (
          <Button variant="tonal" onClick={onSave}>
            <ListPlus />
            {t("saveArticle")}
          </Button>
        ) : null}
        {share}
      </Floating>
    );
  }
  const archived = item.readingList?.state === "archived";
  return (
    <Floating hidden={hidden}>
      {!archived ? (
        <Button
          variant="ghost"
          className="min-w-0"
          onClick={() => {
            app.archiveItem(item.key, true);
            onArchived(() => app.archiveItem(item.key, false));
          }}
        >
          <Archive />
          <span className="truncate">{t("archive")}</span>
        </Button>
      ) : null}
      <Button variant="ghost" className="min-w-0" onClick={() => app.openOrganise(item.key)}>
        <Tag />
        <span className="truncate">{t("organiseTitle")}</span>
      </Button>
      {share}
    </Floating>
  );
}

/** The pill itself: at the bottom in the middle of the reader, sliding down out of sight when [hidden]. */
function Floating({ hidden, children }: { hidden: boolean; children: React.ReactNode }) {
  return (
    <div className="pointer-events-none absolute inset-x-0 bottom-4 z-10 flex justify-center px-4">
      <div
        inert={hidden}
        className={cn(
          "pointer-events-auto flex min-w-0 items-center gap-1 rounded-full border bg-card p-2 shadow-lg transition duration-200 ease-out motion-reduce:transition-none",
          hidden && "translate-y-[calc(100%+1rem)] opacity-0",
        )}
      >
        {children}
      </div>
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
