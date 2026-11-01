import { Archive, BookOpen, Heart, ListPlus, Quote, Share2, Trash2 } from "lucide-react";
import { app } from "@/lib/app";
import { plural, t } from "@/lib/i18n";
import type { Item } from "@/lib/items";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { Dialog } from "./ui/dialog";

/**
 * The reader's library actions as a floating toolbar, as on Android: a pill at the bottom in the
 * middle, over the text, that steps aside while reading down ([hidden]). One Save before the article
 * is saved; after, every action one click away, as icons (2026-11-01, instead of Archive and
 * Organise): first **Archive** (tonal, like Save) while it is to read, or **To read** (plain) once
 * archived, in words; then **Like** (a filled heart while liked). Tags, the note and the highlights
 * sit under the article's title. Then the highlights, and Share as its own round button next
 * to the pill. Remove from Saved is in the reader's ⋮.
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
  /** How many highlights it has; with any, a quote mark and the count open their list. */
  highlightCount: number;
  onHighlights: () => void;
  /** Stepped aside while reading down. */
  hidden: boolean;
}) {
  const highlights =
    highlightCount > 0 ? (
      <Button variant="ghost" onClick={onHighlights} aria-label={plural("highlightsCount", highlightCount)} title={t("highlightsTitle")}>
        <Quote />
        {highlightCount}
      </Button>
    ) : null;
  const share = onShare ? (
    <button
      type="button"
      onClick={onShare}
      aria-label={t("share")}
      title={t("share")}
      className="pointer-events-auto flex size-14 shrink-0 cursor-pointer items-center justify-center rounded-2xl bg-accent text-accent-foreground shadow-lg hover:bg-accent/80 [&_svg]:size-5"
    >
      <Share2 />
    </button>
  ) : null;
  if (!item) {
    if (!onSave && !onShare && highlightCount === 0) return null;
    return (
      <Floating hidden={hidden} fab={share}>
        {onSave ? (
          <Button variant="tonal" onClick={onSave}>
            <ListPlus />
            {t("saveArticle")}
          </Button>
        ) : null}
        {highlights}
      </Floating>
    );
  }
  const archived = item.readingList?.state === "archived";
  return (
    <Floating hidden={hidden} fab={share}>
      {/* What moves it, in words: Archive (tonal, like Save: the next step) while to read, To read
          (plain: it puts it back) once archived. */}
      {archived ? (
        <Button variant="ghost" className="min-w-0" onClick={() => app.archiveItem(item.key, false)}>
          <BookOpen />
          <span className="truncate">{t("toRead")}</span>
        </Button>
      ) : (
        <Button
          variant="tonal"
          className="min-w-0"
          onClick={() => {
            app.archiveItem(item.key, true);
            onArchived(() => app.archiveItem(item.key, false));
          }}
        >
          <Archive />
          <span className="truncate">{t("archive")}</span>
        </Button>
      )}
      <Button variant="ghost" size="icon" aria-pressed={item.liked} aria-label={t("like")} title={t("like")} onClick={() => app.likeItem(item.key, !item.liked)}>
        <Heart className={cn(item.liked && "fill-current")} />
      </Button>
      {highlights}
    </Floating>
  );
}

/** The pill itself, with Share beside it: at the bottom in the middle of the reader, sliding down out of sight when [hidden]. */
function Floating({ hidden, fab, children }: { hidden: boolean; fab?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="pointer-events-none absolute inset-x-0 bottom-4 z-10 flex justify-center px-4">
      <div
        inert={hidden}
        className={cn("flex min-w-0 items-center gap-2 transition duration-200 ease-out motion-reduce:transition-none", hidden && "translate-y-[calc(100%+1rem)] opacity-0")}
      >
        <div className="pointer-events-auto flex min-w-0 items-center gap-1 rounded-full border bg-card p-2 shadow-lg">{children}</div>
        {fab}
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
