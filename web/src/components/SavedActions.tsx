import { Archive, BookOpen, Check, ChevronDown, Folder as FolderGlyph, Heart, ListPlus, Plus, Quote, Share2, Tag, Trash2 } from "lucide-react";
import { useState } from "react";
import { app, useApp } from "@/lib/app";
import { plural, t } from "@/lib/i18n";
import type { Item } from "@/lib/items";
import { choiceOf, places, samePlace, tagsLabel, withTag, type Place } from "@/lib/saveChoice";
import { savedFolders, tabTags } from "@/lib/saved";
import { Button } from "./ui/button";
import { useEscape } from "./ui/useEscape";
import { cn } from "./ui/cn";
import { Dialog } from "./ui/dialog";

/**
 * The reader's library bar (#139, #156), under the text: one Save before the article is saved;
 * after, two menus, as the Organise sheet groups it; Share at the right end. **Folder** says where it is and offers To read
 * and Archive, a divider, then the folders and New folder; **Tags** says its tags (or Liked) and
 * offers Like, a divider, then the tags and Add a tag. New folder and Add a tag open Organise, the
 * only place to type. The same as the Android app's bottom bar.
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
  const { library } = useApp();
  const [open, setOpen] = useState<"places" | "tags" | null>(null);
  // At the right end, after Save or the folder and tags: the highlights, then Share.
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
  const choice = choiceOf(item);
  const tags = tabTags(library, { kind: "all" });
  const close = () => setOpen(null);
  const organise = () => {
    close();
    app.openOrganise(item.key);
  };
  return (
    <div className="flex items-center gap-1 border-t bg-muted/40 px-2 py-2">
      <div className="relative min-w-0">
        <Button variant="ghost" onClick={() => setOpen(open === "places" ? null : "places")} aria-expanded={open === "places"} aria-haspopup="menu" title={t("savedPlace")} className="max-w-full">
          <PlaceIcon place={choice.place} />
          <span className="truncate">{placeName(choice.place)}</span>
          <ChevronDown />
        </Button>
        {open === "places" ? (
          <Menu onClose={close}>
            {places(savedFolders(library), choice.place).map((place) => (
              <MenuChoice
                key={place.kind === "folder" ? `folder:${place.name}` : place.kind}
                icon={<PlaceIcon place={place} />}
                label={placeName(place)}
                selected={samePlace(place, choice.place)}
                onClick={() => {
                  close();
                  if (samePlace(place, choice.place)) return;
                  app.organise(item.key, { ...choice, place });
                  if (place.kind === "archive") onArchived(() => app.organise(item.key, choice));
                }}
              />
            ))}
          </Menu>
        ) : null}
      </div>
      <div className="relative min-w-0">
        <Button variant="ghost" onClick={() => setOpen(open === "tags" ? null : "tags")} aria-expanded={open === "tags"} aria-haspopup="menu" title={t("tags")} className="max-w-full">
          {choice.liked ? <Heart className="fill-current text-primary" /> : <Tag />}
          <span className="truncate">{tagsLabel(choice, t("liked")) ?? t("tags")}</span>
          <ChevronDown />
        </Button>
        {open === "tags" ? (
          <Menu onClose={close}>
            {/* Like and the tags leave the menu open: several can change at once. */}
            <MenuChoice icon={<Heart className={cn(choice.liked && "fill-current")} />} label={t("like")} selected={choice.liked} onClick={() => app.likeItem(item.key, !choice.liked)} />
            {tags.map((tag) => (
              <MenuChoice
                key={tag}
                icon={<Tag />}
                label={tag}
                selected={choice.tags.some((x) => x.toLowerCase() === tag.toLowerCase())}
                onClick={() => app.organise(item.key, withTag(choice, tag))}
              />
            ))}
            <MenuChoice icon={<Plus />} label={t("tagAdd")} onClick={organise} />
          </Menu>
        ) : null}
      </div>
      {share}
    </div>
  );
}

function placeName(place: Place): string {
  return place.kind === "toread" ? t("toRead") : place.kind === "archive" ? t("archiveTab") : place.name;
}

function PlaceIcon({ place }: { place: Place }) {
  return place.kind === "toread" ? <BookOpen /> : place.kind === "archive" ? <Archive /> : <FolderGlyph />;
}

/** A menu above the bar, closed by a click outside it. */
function Menu({ onClose, alignEnd, children }: { onClose: () => void; alignEnd?: boolean; children: React.ReactNode }) {
  useEscape(true, onClose);
  return (
    <>
      <div className="fixed inset-0 z-20" onClick={onClose} aria-hidden="true" />
      <div role="menu" className={cn("absolute bottom-full z-30 mb-1 max-h-80 w-60 overflow-y-auto rounded-xl border bg-card py-1 shadow-lg", alignEnd ? "right-0" : "left-0")}>
        {children}
      </div>
    </>
  );
}

/** A menu line: its icon, its name, and a check when it is the one chosen (or on). */
function MenuChoice({ icon, label, selected, onClick }: { icon: React.ReactNode; label: string; selected?: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      role={selected === undefined ? "menuitem" : "menuitemcheckbox"}
      aria-checked={selected}
      onClick={onClick}
      className="flex w-full cursor-pointer items-center gap-3 px-3 py-2 text-left text-sm hover:bg-muted [&_svg]:size-4 [&_svg]:shrink-0 [&_svg]:text-muted-foreground"
    >
      {icon}
      <span className="min-w-0 flex-1 truncate">{label}</span>
      {selected ? <Check /> : null}
    </button>
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
