import { Archive, BookOpen, Check, Folder as FolderGlyph, Heart, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { app, useApp } from "@/lib/app";
import { normalizeUrl } from "@/lib/discovery";
import { plural, t } from "@/lib/i18n";
import { isWebLink, itemTags } from "@/lib/items";
import { choiceOf, emptyChoice, newFolder, places, samePlace, tagChoices, withTyped, type Place, type SaveChoice } from "@/lib/saveChoice";
import { savedFolders } from "@/lib/saved";
import { actionOf, canConfirm, outcomeOf, problemKey, type ShareStatus } from "@/lib/sendTo";
import { sharedArticle, type SharedArticle } from "@/lib/sharing";
import { SendToSection } from "./ShareWithUser";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { Dialog } from "./ui/dialog";
import { Input } from "./ui/input";

/**
 * Save and share (#143, #256), the one sheet for an article, as on Android: Add link (nothing is
 * saved until Save, and Cancel saves nothing), just saved from the reader ("Saved to …"), or one
 * saved before (Organise). In order: the address (Add link only), the folder as chips, the tags with
 * Like in front, the note, then Send with Content when signed in (the people shared with, several at
 * once, and Add address; not in Organise, which has Share next to it). The button says Save or Save
 * and send. Organise has Remove at the bottom left, with Undo in its message. Cancel, or closing the
 * sheet, saves and sends nothing: right after a save it takes the save back.
 */
/** A message under the sheet: with Open for a new save (its key), or Undo (`undo`). */
type Message = (text: string, openKey?: string, undo?: () => void) => void;

export function SaveSheet({ onMessage }: { onMessage: Message }) {
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
  onMessage: Message;
}) {
  const { library, account } = useApp();
  const [choice, setChoice] = useState(initial);
  const signedIn = account !== null;
  const [address, setAddress] = useState("");
  // A folder or tag typed but not added yet: Save takes it too, rather than dropping it.
  const [folderName, setFolderName] = useState("");
  const [tagName, setTagName] = useState("");
  const saved = kind !== "new";
  const savedItem = itemKey ? library.items.find((i) => i.key === itemKey) : undefined;
  const typed = normalizeUrl(address);
  const link = typed && isWebLink(typed) ? typed : null;
  const name = (place: Place) => (place.kind === "toread" ? t("toRead") : place.kind === "archive" ? t("archiveTab") : place.name);
  // Sends to everyone picked (#256), then one message says how it went, after where it was saved.
  const send = async (to: string[], article: SharedArticle, savedTo: string | null, openKey?: string) => {
    const results: [string, ShareStatus][] = [];
    for (const email of to) results.push([email, await app.shareWithUser(email, [article])]);
    const outcome = outcomeOf(results);
    if (!outcome) return;
    const sent = "sent" in outcome ? (outcome.sent.length === 1 ? t("shareUserSent").replace("{email}", outcome.sent[0]) : plural("sentPeople", outcome.sent.length)) : t(problemKey(outcome.status));
    onMessage(savedTo ? `${savedTo} · ${sent}` : sent, openKey);
  };
  const save = () => {
    const chosen = withTyped(choice, folderName, tagName, savedFolders(library));
    const savedTo = t("savedTo").replace("{place}", name(chosen.place));
    const item = itemKey ? library.items.find((i) => i.key === itemKey) : undefined;
    if (saved && itemKey) {
      if (JSON.stringify({ ...chosen, sendTo: [] }) !== JSON.stringify({ ...initial, sendTo: [] })) app.organise(itemKey, chosen);
      // Saved from the reader: already in the article, so no Open (#142).
      if (chosen.sendTo.length > 0 && item) void send(chosen.sendTo, sharedArticle(item), kind === "justSaved" ? savedTo : null);
      else if (kind === "justSaved") onMessage(savedTo);
    } else if (link) {
      const key = app.saveNew(link, chosen);
      if (!key) onMessage(t("addLinkInvalid"));
      else if (chosen.sendTo.length > 0) void send(chosen.sendTo, { url: link, title: link }, savedTo, key);
      else onMessage(savedTo, key);
    }
    app.closeSheet();
  };
  // Cancel, or closing the sheet, saves and sends nothing: just saved, the save is taken back.
  const cancel = () => {
    if (kind === "justSaved" && itemKey) app.undoSave(itemKey);
    app.closeSheet();
  };
  // The sheet itself picks To read or the Archive, so the title doesn't name one (#229).
  // Organise has nothing to share (Share is next to it), so it is named for what it does.
  const title = kind === "organise" ? t("organiseTitle") : t("saveShareTitle");
  const action = actionOf(choice);
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && cancel()}
      title={title}
      closeLabel={t("close")}
      footer={
        <>
          {/* Organising a saved article: Remove at the bottom left, away from Save, with Undo. Just saved,
              Cancel already takes the save back. */}
          {kind === "organise" && savedItem ? (
            <Button
              variant="destructive"
              className="mr-auto"
              onClick={() => {
                app.removeItem(savedItem.key);
                onMessage(t("savedRemoved"), undefined, () => app.restoreItem(savedItem));
                app.closeSheet();
              }}
            >
              <Trash2 />
              {t("remove")}
            </Button>
          ) : null}
          <Button variant="ghost" onClick={cancel}>
            {t("cancel")}
          </Button>
          <Button onClick={save} disabled={!canConfirm(choice, saved || link !== null)}>
            {action === "save" ? t("saveArticle") : action === "saveAndSend" ? t("saveAndSend") : t("shareUserSend")}
          </Button>
        </>
      }
    >
      <div className="space-y-5">
        {kind === "new" ? (
          <Input autoFocus value={address} onChange={(e) => setAddress(e.target.value)} onKeyDown={(e) => e.key === "Enter" && link && save()} placeholder={t("webAddress")} aria-label={t("webAddress")} type="url" inputMode="url" />
        ) : null}
        <Section title={t("savedPlace")}>
          <FolderChips
            place={choice.place}
            folders={savedFolders(library)}
            onChange={(place) => setChoice({ ...choice, place })}
            name={folderName}
            setName={setFolderName}
          />
        </Section>
        {/* Only sent: nothing of it is kept, so there is nothing to tag or note. */}
        {choice.keep ? (
          <>
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
          </>
        ) : null}
        {signedIn && kind !== "organise" ? (
          <Section title={t("shareUserMenu")}>
            <SendToSection picked={choice.sendTo} onChange={(sendTo) => setChoice({ ...choice, sendTo })} />
          </Section>
        ) : null}
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
const on = "border-transparent bg-accent text-accent-foreground";

/** The place as chips, pick one (#156): To read and Archive, a divider, each folder, and New folder. */
function FolderChips({
  place,
  folders,
  onChange,
  name,
  setName,
}: {
  place: Place;
  folders: string[];
  onChange: (p: Place) => void;
  name: string;
  setName: (name: string) => void;
}) {
  const [making, setMaking] = useState(false);
  return (
    <div className="space-y-2">
      <div role="radiogroup" aria-label={t("savedPlace")} className="flex flex-wrap items-center gap-1.5">
        {places(folders, place).map((option) => {
          const selected = samePlace(option, place);
          const Icon = selected ? Check : option.kind === "toread" ? BookOpen : option.kind === "archive" ? Archive : FolderGlyph;
          return (
            <button key={option.kind === "folder" ? `f:${option.name}` : option.kind} type="button" role="radio" aria-checked={selected} onClick={() => onChange(option)} className={cn(chip, selected ? on : "hover:bg-muted")}>
              <Icon className="size-4" aria-hidden="true" />
              {option.kind === "toread" ? t("toRead") : option.kind === "archive" ? t("archive") : option.name}
            </button>
          );
        })}
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
 * The tags (#143), as on Android: Like, then every tag in use as a chip to click on or off, in a
 * steady order so nothing moves when one is chosen, then New tag, which opens a field to make a tag
 * that doesn't exist yet.
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
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center gap-1.5">
        <button type="button" aria-pressed={liked} onClick={onLike} className={cn(chip, liked ? on : "hover:bg-muted")}>
          <Heart className={cn("size-4", liked && "fill-current")} aria-hidden="true" />
          {t("like")}
        </button>
        {tagChoices(allTags, tags).map((x) => {
          const chosen = has(x);
          return (
            <button
              key={x}
              type="button"
              aria-pressed={chosen}
              onClick={() => onChange(chosen ? tags.filter((y) => y.toLowerCase() !== x.toLowerCase()) : [...tags, x])}
              className={cn(chip, chosen ? on : "hover:bg-muted")}
            >
              {x}
            </button>
          );
        })}
        {!adding ? (
          <button type="button" onClick={() => setAdding(true)} className={cn(chip, "hover:bg-muted")}>
            <Plus className="size-4" aria-hidden="true" />
            {t("tagAdd")}
          </button>
        ) : null}
      </div>
      {adding ? (
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
      ) : null}
    </div>
  );
}
