import { Check, GripVertical, Pencil, Plus } from "lucide-react";
import { useState } from "react";
import { app } from "@/lib/app";
import { t } from "@/lib/i18n";
import type { Folder } from "@/lib/library";
import { moved } from "@/lib/topics";
import { FolderDialog } from "./Dialogs";
import { FolderIcon } from "./FolderIcon";
import { cn } from "./ui/cn";

export const chip = "inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-lg border px-3 text-sm disabled:cursor-default disabled:opacity-50";
export const chipOn = "border-transparent bg-accent text-accent-foreground";

/**
 * The topics as chips in the Add feed and Organise sheets (#314), as on Android: each with its
 * icon, the same in both. `leading` comes first (No topic, or Like); `extra` are names the article
 * has that aren't topics (yet). At the end: **New topic** (the topic dialog; the new topic is chosen
 * at once) and **Edit**, which puts a grip and a pen on every topic chip and turns into **Done**:
 * drag a chip (or focus it and press ← →) to reorder the topics, click it to rename it, change its
 * icon or remove it (renaming it to another topic's name joins the two).
 */
export function TopicChips({
  topics,
  selected,
  onToggle,
  onCreated,
  leading,
  extra = [],
  isExtraSelected = () => true,
  onToggleExtra = () => {},
  onRenamed,
  onRemoved,
  disabled = false,
}: {
  topics: Folder[];
  selected: (topic: Folder) => boolean;
  onToggle: (topic: Folder) => void;
  /** A topic made here, by its id and name. */
  onCreated: (id: string, title: string) => void;
  leading?: React.ReactNode;
  extra?: string[];
  isExtraSelected?: (name: string) => boolean;
  onToggleExtra?: (name: string) => void;
  onRenamed?: (from: string, to: string) => void;
  onRemoved?: (title: string) => void;
  disabled?: boolean;
}) {
  const [editing, setEditing] = useState(false);
  const [dialog, setDialog] = useState<Folder | "new" | null>(null);
  // While dragging: the order shown, which is stored on drop.
  const [order, setOrder] = useState<string[] | null>(null);
  const [dragged, setDragged] = useState<string | null>(null);
  const byId = new Map(topics.map((f) => [f.id, f]));
  const shown = (order ?? topics.map((f) => f.id)).flatMap((id) => (byId.has(id) ? [byId.get(id)!] : []));
  const step = (id: string, by: number) => {
    const ids = topics.map((f) => f.id);
    const from = ids.indexOf(id);
    const next = moved(ids, from, from + by);
    if (next !== ids) app.reorderTopics(next);
  };
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {!editing ? leading : null}
      {shown.map((topic) =>
        editing ? (
          <button
            key={topic.id}
            type="button"
            draggable
            onDragStart={(e) => {
              e.dataTransfer.effectAllowed = "move";
              setDragged(topic.id);
              setOrder(topics.map((f) => f.id));
            }}
            onDragOver={(e) => {
              e.preventDefault();
              if (!dragged || dragged === topic.id || !order) return;
              setOrder(moved(order, order.indexOf(dragged), order.indexOf(topic.id)));
            }}
            onDrop={(e) => e.preventDefault()}
            onDragEnd={() => {
              if (order) app.reorderTopics(order);
              setDragged(null);
              setOrder(null);
            }}
            onKeyDown={(e) => {
              if (e.key === "ArrowLeft" || e.key === "ArrowRight") {
                e.preventDefault();
                step(topic.id, e.key === "ArrowLeft" ? -1 : 1);
              }
            }}
            onClick={() => setDialog(topic)}
            aria-label={`${t("editFolder")}: ${topic.title}`}
            className={cn(chip, "cursor-grab pl-1.5 hover:bg-muted", dragged === topic.id && "opacity-50")}
          >
            <GripVertical className="size-4 text-muted-foreground" aria-hidden="true" />
            <FolderIcon name={topic.icon} className="size-4" />
            {topic.title}
            <Pencil className="size-3.5 text-muted-foreground" aria-hidden="true" />
          </button>
        ) : (
          <button key={topic.id} type="button" aria-pressed={selected(topic)} disabled={disabled} onClick={() => onToggle(topic)} className={cn(chip, selected(topic) ? chipOn : "hover:bg-muted")}>
            <FolderIcon name={topic.icon} className="size-4" />
            {topic.title}
          </button>
        ),
      )}
      {!editing
        ? extra.map((name) => (
            <button key={`extra:${name}`} type="button" aria-pressed={isExtraSelected(name)} disabled={disabled} onClick={() => onToggleExtra(name)} className={cn(chip, isExtraSelected(name) ? chipOn : "hover:bg-muted")}>
              {name}
            </button>
          ))
        : null}
      {!editing ? (
        <button type="button" disabled={disabled} onClick={() => setDialog("new")} className={cn(chip, "hover:bg-muted")}>
          <Plus className="size-4" aria-hidden="true" />
          {t("newFolder")}
        </button>
      ) : null}
      {topics.length > 0 || editing ? (
        <button type="button" disabled={disabled} aria-pressed={editing} onClick={() => setEditing(!editing)} className={cn(chip, "hover:bg-muted")}>
          {editing ? <Check className="size-4" aria-hidden="true" /> : <Pencil className="size-4" aria-hidden="true" />}
          {editing ? t("done") : t("edit")}
        </button>
      ) : null}
      {dialog ? (
        <FolderDialog
          folder={dialog}
          onClose={() => setDialog(null)}
          onCreated={(id) => {
            const made = app.get().library.folders.find((f) => f.id === id);
            if (made) onCreated(id, made.title);
          }}
          onRenamed={onRenamed}
          onRemoved={onRemoved}
        />
      ) : null}
    </div>
  );
}
