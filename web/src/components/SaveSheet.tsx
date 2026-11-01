import { Archive, BookOpen, Heart } from "lucide-react";
import { useState } from "react";
import { app, useApp } from "@/lib/app";
import { normalizeUrl } from "@/lib/discovery";
import { t } from "@/lib/i18n";
import { isWebLink } from "@/lib/items";
import { topicNames } from "@/lib/topics";
import { choiceOf, emptyChoice, isArchived, tagChoices, withArchived, withoutTopic, withTag, withTopicRenamed, withTyped, type Place, type SaveChoice } from "@/lib/saveChoice";
import { savedFolders } from "@/lib/saved";
import { canConfirm } from "@/lib/sendTo";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { Dialog } from "./ui/dialog";
import { TopicChips, chip, chipOn } from "./TopicChips";
import { Input } from "./ui/input";

/**
 * The one sheet for an article (#143, #304, #305), as on Android: Add link (nothing is saved until
 * Save, and Cancel saves nothing), just saved from the reader ("Saved to …"), or one saved before.
 * Headed Save while saving and Organise for one saved before; it doesn't send (#304; Share does).
 * In order: the address (Add link only), To read or Archive (To read to start with when saving; Save
 * shows its icon), the topics with Like in front, the note. Cancel at the bottom left, with Remove
 * next to it for an article saved before (it asks first); Save at the right. Cancel, or closing the
 * sheet, saves nothing; right after a save it takes the save back.
 */
/** A message under the sheet, with Open for a new save (its key). */
type Message = (text: string, openKey?: string) => void;

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
  kind: "new" | "justSaved" | "topics" | "note";
  initial: SaveChoice;
  itemKey: string | null;
  onMessage: Message;
}) {
  const { library } = useApp();
  const [choice, setChoice] = useState(initial);
  const [address, setAddress] = useState("");
  const saved = kind !== "new";
  const typed = normalizeUrl(address);
  const link = typed && isWebLink(typed) ? typed : null;
  const name = (place: Place) => (place.kind === "toread" ? t("toRead") : place.kind === "archive" ? t("archiveTab") : place.name);
  const save = (picked: SaveChoice) => {
    const chosen = withTyped(picked, "", "", savedFolders(library));
    const savedTo = t("savedTo").replace("{place}", name(chosen.place));
    if (saved && itemKey) {
      if (JSON.stringify(chosen) !== JSON.stringify(initial)) app.organise(itemKey, chosen);
      // Saved from the reader: already in the article, so no Open (#142).
      if (kind === "justSaved") onMessage(savedTo);
    } else if (link) {
      const key = app.saveNew(link, chosen);
      if (!key) onMessage(t("addLinkInvalid"));
      else onMessage(savedTo, key);
    }
    app.closeSheet();
  };
  // Cancel, or closing the sheet, saves nothing: just saved, the save is taken back.
  const cancel = () => {
    if (kind === "justSaved" && itemKey) app.undoSave(itemKey);
    app.closeSheet();
  };
  // One sheet, headed for what it does: Save while saving, Organise for one saved before.
  const title = kind === "topics" ? t("tags") : kind === "note" ? t("note") : t("saveArticle");
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && cancel()}
      title={title}
      closeLabel={t("close")}
      cancel={t("cancel")}
      footer={
        <>
          {/* Save, with the icon of where it goes: To read's open book, the Archive's box. */}
          <Button onClick={() => save(choice)} disabled={!canConfirm(choice, saved || link !== null)}>
            {choice.keep ? isArchived(choice) ? <Archive /> : <BookOpen /> : null}
            {t("saveArticle")}
          </Button>
        </>
      }
    >
      <div className="space-y-5">
        {kind === "new" ? (
          <Input autoFocus value={address} onChange={(e) => setAddress(e.target.value)} onKeyDown={(e) => e.key === "Enter" && link && save(choice)} placeholder={t("webAddress")} aria-label={t("webAddress")} type="url" inputMode="url" />
        ) : null}
        {kind === "topics" ? (
          <Topics choice={choice} setChoice={setChoice} withLike={false} />
        ) : kind === "note" ? (
          <NoteField choice={choice} setChoice={setChoice} autoFocus />
        ) : null}
        {/* Only sent: nothing of it is kept, so there is nothing to tag or note. */}
        {kind !== "topics" && kind !== "note" && choice.keep ? (
          <>
        {/* To read or Archive, above the topics, as on Android's segmented choice. */}
        <div role="radiogroup" aria-label={t("saveArticle")} className="grid grid-cols-2 rounded-full border p-0.5">
          {[false, true].map((archive) => {
            const on = isArchived(choice) === archive;
            return (
              <button
                key={String(archive)}
                type="button"
                role="radio"
                aria-checked={on}
                onClick={() => setChoice((c) => withArchived(c, archive))}
                className={cn("inline-flex h-9 cursor-pointer items-center justify-center gap-2 rounded-full text-sm font-medium", on ? "bg-accent text-accent-foreground" : "text-muted-foreground hover:bg-muted")}
              >
                {archive ? <Archive className="size-4" aria-hidden="true" /> : <BookOpen className="size-4" aria-hidden="true" />}
                {archive ? t("archiveTab") : t("toRead")}
              </button>
            );
          })}
        </div>
        <Section title={t("tags")}>
          <Topics choice={choice} setChoice={setChoice} withLike />
        </Section>
        <Section title={t("note")}>
          <NoteField choice={choice} setChoice={setChoice} />
        </Section>
          </>
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

type ChoiceProps = { choice: SaveChoice; setChoice: React.Dispatch<React.SetStateAction<SaveChoice>> };

/** The topics as chips; Like first while saving (in the reader Like is its own button). */
function Topics({ choice, setChoice, withLike }: ChoiceProps & { withLike: boolean }) {
  const { library } = useApp();
  const has = (name: string) => choice.tags.some((x) => x.trim().toLowerCase() === name.trim().toLowerCase());
  return (
    <TopicChips
      topics={library.folders}
      selected={(topic) => has(topic.title)}
      onToggle={(topic) => setChoice((c) => withTag(c, topic.title))}
      // A topic made here is on the article at once.
      onCreated={(_, title) => setChoice((c) => (c.tags.some((x) => x.toLowerCase() === title.toLowerCase()) ? c : { ...c, tags: [...c.tags, title] }))}
      onRenamed={(from, to) => setChoice((c) => withTopicRenamed(c, from, to))}
      onRemoved={(name) => setChoice((c) => withoutTopic(c, name))}
      // Its names that aren't topics (yet), as plain chips after the topics.
      extra={tagChoices(topicNames(library), choice.tags).filter((name) => !library.folders.some((f) => f.title.toLowerCase() === name.trim().toLowerCase()))}
      isExtraSelected={has}
      onToggleExtra={(name) => setChoice((c) => withTag(c, name))}
      leading={
        withLike ? (
          <button type="button" aria-pressed={choice.liked} onClick={() => setChoice((c) => ({ ...c, liked: !c.liked }))} className={cn(chip, choice.liked ? chipOn : "hover:bg-muted")}>
            <Heart className={cn("size-4", choice.liked && "fill-current")} aria-hidden="true" />
            {t("like")}
          </button>
        ) : undefined
      }
    />
  );
}

/** The note, a few lines to start with. */
function NoteField({ choice, setChoice, autoFocus }: ChoiceProps & { autoFocus?: boolean }) {
  return (
    <textarea
      autoFocus={autoFocus}
      value={choice.note}
      onChange={(e) => setChoice((c) => ({ ...c, note: e.target.value }))}
      aria-label={t("note")}
      placeholder={t("note")}
      rows={4}
      className="w-full resize-y rounded-lg border bg-transparent px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
    />
  );
}
