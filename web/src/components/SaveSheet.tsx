import { Heart, Trash2 } from "lucide-react";
import { useState } from "react";
import { app, useApp } from "@/lib/app";
import { normalizeUrl } from "@/lib/discovery";
import { t } from "@/lib/i18n";
import { isWebLink } from "@/lib/items";
import { topicNames } from "@/lib/topics";
import { choiceOf, emptyChoice, offersArchive, tagChoices, toArchive, withoutTopic, withTag, withTopicRenamed, withTyped, type Place, type SaveChoice } from "@/lib/saveChoice";
import { savedFolders } from "@/lib/saved";
import { canConfirm } from "@/lib/sendTo";
import { RemoveDialog } from "./SavedActions";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { Dialog } from "./ui/dialog";
import { TopicChips, chip, chipOn } from "./TopicChips";
import { Input } from "./ui/input";

/**
 * Organise (#143, #304, #305), the one sheet for an article, as on Android: Add link (nothing is
 * saved until Save, and Cancel saves nothing), just saved from the reader ("Saved to …"), or one
 * saved before. Headed Organise every way in: it doesn't send (#304; Share does). In order: the
 * address (Add link only), the topics with Like in front, the note. The Archive is a state, not a
 * list to pick (#305): saving, the buttons say where it goes, Save to Archive or Save (To read).
 * An article saved before has Remove at the bottom left, which asks first, and doesn't move (the
 * reader's Archive button and a card's menu do). Closing the sheet (× at the top right) is Cancel:
 * it saves nothing, and right after a save it takes the save back.
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
  kind: "new" | "justSaved" | "organise";
  initial: SaveChoice;
  itemKey: string | null;
  onMessage: Message;
}) {
  const { library } = useApp();
  const [choice, setChoice] = useState(initial);
  const [address, setAddress] = useState("");
  const has = (name: string) => choice.tags.some((x) => x.trim().toLowerCase() === name.trim().toLowerCase());
  const saved = kind !== "new";
  const savedItem = itemKey ? library.items.find((i) => i.key === itemKey) : undefined;
  const [removing, setRemoving] = useState(false);
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
  // The sheet itself picks To read or the Archive, so the title doesn't name one (#229). Saving and
  // organising are one sheet, headed Organise (#304): sending is Share's.
  const title = t("organiseTitle");
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && cancel()}
      title={title}
      closeLabel={t("close")}
      footer={
        <>
          {/* Organising a saved article: Remove at the bottom left, away from Save; it asks first. Just
              saved, Cancel already takes the save back. */}
          {kind === "organise" && savedItem ? (
            <Button
              variant="destructive"
              className="mr-auto"
              onClick={() => setRemoving(true)}
            >
              <Trash2 />
              {t("remove")}
            </Button>
          ) : null}
          {/* Saving, the buttons say where it goes (#305); Cancel is the × at the top right. */}
          {offersArchive(kind === "organise", choice) ? (
            <Button variant="outline" onClick={() => save(toArchive(choice))} disabled={!canConfirm(choice, saved || link !== null)}>
              {t("saveToArchive")}
            </Button>
          ) : null}
          <Button onClick={() => save(choice)} disabled={!canConfirm(choice, saved || link !== null)}>
            {t("saveArticle")}
          </Button>
        </>
      }
    >
      {removing && savedItem ? (
        <RemoveDialog
          item={savedItem}
          open
          onClose={() => setRemoving(false)}
          onRemoved={() => {
            onMessage(t("savedRemoved"));
            app.closeSheet();
          }}
        />
      ) : null}
      <div className="space-y-5">
        {kind === "new" ? (
          <Input autoFocus value={address} onChange={(e) => setAddress(e.target.value)} onKeyDown={(e) => e.key === "Enter" && link && save(choice)} placeholder={t("webAddress")} aria-label={t("webAddress")} type="url" inputMode="url" />
        ) : null}
        {/* Only sent: nothing of it is kept, so there is nothing to tag or note. */}
        {choice.keep ? (
          <>
        <Section title={t("tags")}>
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
              <button type="button" aria-pressed={choice.liked} onClick={() => setChoice((c) => ({ ...c, liked: !c.liked }))} className={cn(chip, choice.liked ? chipOn : "hover:bg-muted")}>
                <Heart className={cn("size-4", choice.liked && "fill-current")} aria-hidden="true" />
                {t("like")}
              </button>
            }
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
