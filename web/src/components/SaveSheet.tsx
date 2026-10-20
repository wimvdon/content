import { Archive, BookOpen, Check, Folder as FolderGlyph, Heart, Plus, Trash2, X } from "lucide-react";
import { useState } from "react";
import { app, useApp } from "@/lib/app";
import { normalizeUrl } from "@/lib/discovery";
import { t } from "@/lib/i18n";
import { isWebLink, itemTags } from "@/lib/items";
import { choiceOf, emptyChoice, newFolder, places, samePlace, withTyped, type Place, type SaveChoice } from "@/lib/saveChoice";
import { savedFolders } from "@/lib/saved";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { Dialog } from "./ui/dialog";
import { Input } from "./ui/input";

/**
 * The one sheet for an article (#143), as on Android: Add link (nothing is saved until Save, and
 * Cancel saves nothing), just saved from the reader ("Saved to …"), or Organise for one saved
 * before. In order: the address (Add link only), the folder as chips (Archive too for a new save),
 * the tags with Like in front, then the note. Closing a sheet whose save already happened keeps
 * what it says.
 */
export function SaveSheet({ onMessage }: { onMessage: (text: string, openKey?: string) => void }) {
  const { sheet, library } = useApp();
  const item = sheet && sheet.kind !== "new" ? library.items.find((i) => i.key === sheet.key) : undefined;
  if (!sheet || (sheet.kind !== "new" && !item)) return null;
  // A fresh state for every article the sheet opens on.
  return <Sheet key={sheet.kind === "new" ? "new" : `${sheet.kind}:${sheet.key}`} kind={sheet.kind} initial={item ? choiceOf(item) : emptyChoice} itemKey={item?.key ?? null} onMessage={onMessage} />;
}

function Sheet({
  kind,
  initial,
  itemKey,
  onMessage,
}: {
  kind: "new" | "justSaved" | "organise";
  initial: SaveChoice;
  itemKey: string | null;
  onMessage: (text: string, openKey?: string) => void;
}) {
  const { library } = useApp();
  const [choice, setChoice] = useState(initial);
  const [address, setAddress] = useState("");
  // A folder or tag typed but not added yet: Save takes it too, rather than dropping it.
  const [folderName, setFolderName] = useState("");
  const [tagName, setTagName] = useState("");
  const saved = kind !== "new";
  const typed = normalizeUrl(address);
  const link = typed && isWebLink(typed) ? typed : null;
  const name = (place: Place) => (place.kind === "toread" ? t("toRead") : place.kind === "archive" ? t("archiveTab") : place.name);
  const save = () => {
    const chosen = withTyped(choice, folderName, tagName, savedFolders(library));
    const savedTo = t("savedTo").replace("{place}", name(chosen.place));
    if (saved && itemKey) {
      if (JSON.stringify(chosen) !== JSON.stringify(initial)) app.organise(itemKey, chosen);
      // Saved from the reader: already in the article, so no Open (#142).
      if (kind === "justSaved") onMessage(savedTo);
    } else if (link) {
      const key = app.saveNew(link, chosen);
      if (key) onMessage(savedTo, key);
      else onMessage(t("addLinkInvalid"));
    }
    app.closeSheet();
  };
  // Cancel, or closing the sheet, changes nothing (#156); an article saved a moment ago stays where it went.
  const cancel = () => {
    if (kind === "justSaved") onMessage(t("savedTo").replace("{place}", name(initial.place)));
    app.closeSheet();
  };
  const title = kind === "new" ? t("saveTitle") : kind === "organise" ? t("organiseTitle") : t("savedTo").replace("{place}", name(initial.place));
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && cancel()}
      title={title}
      closeLabel={t("close")}
      footer={
        <>
          {/* Just saved, Remove takes the save back; closing keeps it as it was saved. */}
          {kind === "justSaved" && itemKey ? (
            <Button
              variant="destructive"
              onClick={() => {
                app.undoSave(itemKey);
                onMessage(t("savedRemoved"));
                app.closeSheet();
              }}
            >
              <Trash2 />
              {t("remove")}
            </Button>
          ) : (
            <Button variant="ghost" onClick={cancel}>
              {t("cancel")}
            </Button>
          )}
          <Button onClick={save} disabled={!saved && !link}>
            {t("saveArticle")}
          </Button>
        </>
      }
    >
      <div className="space-y-5">
        {kind === "new" ? (
          <Input autoFocus value={address} onChange={(e) => setAddress(e.target.value)} onKeyDown={(e) => e.key === "Enter" && link && save()} placeholder={t("webAddress")} aria-label={t("webAddress")} type="url" inputMode="url" />
        ) : null}
        <Section title={t("folder")}>
          <FolderChips place={choice.place} folders={savedFolders(library)} onChange={(place) => setChoice({ ...choice, place })} name={folderName} setName={setFolderName} />
        </Section>
        <Section title={t("tags")}>
          <TagsRow
            tags={choice.tags}
            allTags={itemTags(library)}
            onChange={(tags) => setChoice({ ...choice, tags })}
            liked={choice.liked}
            onLike={() => setChoice({ ...choice, liked: !choice.liked })}
            name={tagName}
            setName={setTagName}
          />
        </Section>
        <Section title={t("note")}>
          <textarea
            value={choice.note}
            onChange={(e) => setChoice({ ...choice, note: e.target.value })}
            aria-label={t("note")}
            placeholder={t("note")}
            rows={4}
            className="w-full resize-y rounded-lg border bg-transparent px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </Section>
      </div>
    </Dialog>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-2">
      <h3 className="text-sm font-medium text-primary">{title}</h3>
      {children}
    </section>
  );
}

const chip = "inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-lg border px-3 text-sm";
const on = "border-transparent bg-accent font-medium text-accent-foreground";

/** The place as chips, pick one (#156): To read and Archive, a divider, each folder, and New folder. */
function FolderChips({ place, folders, onChange, name, setName }: { place: Place; folders: string[]; onChange: (p: Place) => void; name: string; setName: (name: string) => void }) {
  const [making, setMaking] = useState(false);
  return (
    <div className="space-y-2">
      <div role="radiogroup" aria-label={t("folder")} className="flex flex-wrap items-center gap-1.5">
        {places(folders, place).map((option) => {
          const selected = samePlace(option, place);
          const Icon = selected ? Check : option.kind === "toread" ? BookOpen : option.kind === "archive" ? Archive : FolderGlyph;
          return [
            <button key={option.kind === "folder" ? `f:${option.name}` : option.kind} type="button" role="radio" aria-checked={selected} onClick={() => onChange(option)} className={cn(chip, selected ? on : "hover:bg-muted")}>
              <Icon className="size-4" aria-hidden="true" />
              {option.kind === "toread" ? t("toRead") : option.kind === "archive" ? t("archive") : option.name}
            </button>,
            // The folders after To read and Archive, set apart as Like is from the tags.
            option.kind === "archive" ? <span key="divider" className="mx-1 h-6 w-px bg-border" aria-hidden="true" /> : null,
          ];
        })}
        {!making ? (
          <button type="button" onClick={() => setMaking(true)} className={cn(chip, "hover:bg-muted")}>
            <Plus className="size-4" aria-hidden="true" />
            {t("newFolder")}
          </button>
        ) : null}
      </div>
      {making ? (
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            const made = newFolder(name, folders);
            if (made) onChange(made);
            setName("");
            setMaking(false);
          }}
        >
          <Input autoFocus value={name} onChange={(e) => setName(e.target.value)} placeholder={t("newFolder")} aria-label={t("newFolder")} maxLength={60} />
          <Button type="submit" variant="tonal" disabled={!name.trim()}>
            <Plus />
            {t("add")}
          </Button>
        </form>
      ) : null}
    </div>
  );
}

/**
 * The tags in one row, the same shape as Saved's filter row: Like, a divider, the tags (removable),
 * then Add a tag, which opens a field with the other tags in use to add with a click.
 */
function TagsRow({
  tags,
  allTags,
  onChange,
  liked,
  onLike,
  name,
  setName,
}: {
  tags: string[];
  allTags: string[];
  onChange: (tags: string[]) => void;
  liked: boolean;
  onLike: () => void;
  name: string;
  setName: (name: string) => void;
}) {
  const [adding, setAdding] = useState(false);
  const has = (n: string) => tags.some((x) => x.toLowerCase() === n.toLowerCase());
  const add = (n: string) => {
    const tag = n.trim();
    if (tag && !has(tag)) onChange([...tags, tag]);
  };
  const others = allTags.filter((x) => !has(x));
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center gap-1.5">
        <button type="button" aria-pressed={liked} onClick={onLike} className={cn(chip, liked ? on : "hover:bg-muted")}>
          <Heart className={cn("size-4", liked && "fill-current")} aria-hidden="true" />
          {t("like")}
        </button>
        <span className="mx-1 h-6 w-px bg-border" aria-hidden="true" />
        {tags.map((x) => (
          <span key={x} className={cn(chip, on, "cursor-default pr-1.5")}>
            {x}
            <button type="button" className="cursor-pointer rounded-full p-0.5 hover:bg-background/60" aria-label={`${t("tagRemove")}: ${x}`} title={t("tagRemove")} onClick={() => onChange(tags.filter((y) => y !== x))}>
              <X className="size-3.5" />
            </button>
          </span>
        ))}
        {!adding ? (
          <button type="button" onClick={() => setAdding(true)} className={cn(chip, "hover:bg-muted")}>
            <Plus className="size-4" aria-hidden="true" />
            {t("tagAdd")}
          </button>
        ) : null}
      </div>
      {adding ? (
        <>
          <form
            className="flex gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              add(name);
              setName("");
            }}
          >
            <Input autoFocus value={name} onChange={(e) => setName(e.target.value)} placeholder={t("tagAdd")} aria-label={t("tagAdd")} maxLength={60} />
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
        </>
      ) : null}
    </div>
  );
}
