import { GripVertical, Pencil, Plus } from "lucide-react";
import { useState } from "react";
import { app, useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import type { Folder } from "@/lib/library";
import { moved } from "@/lib/topics";
import { FolderDialog } from "./Dialogs";
import { FolderIcon } from "./FolderIcon";
import { Dialog } from "./ui/dialog";
import { cn } from "./ui/cn";

/** The dragged chip's key, so another group can take it (Manage feeds). */
export const CHIP_TYPE = "application/x-content-chip";

export const chip = "inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-lg border px-3 text-sm disabled:cursor-default disabled:opacity-50";
export const chipOn = "border-transparent bg-accent text-accent-foreground";
/** New topic and Edit topics are actions, not topics: text buttons, so they don't look like chips. */
const action = "inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-lg px-2 text-sm font-medium text-primary hover:bg-muted disabled:cursor-default disabled:opacity-50";

/**
 * Chips to reorder and edit, as on Android (`ReorderableChips`): topics in Manage topics, and feeds in
 * Manage feeds. Each has a grip, its icon, its name and a pen. Drag a chip among the others (or focus
 * it and press ← →) to reorder; the new order is stored on drop. Clicking a chip edits it. With
 * `onDropFrom`, a chip of another group (Manage feeds' topics) dropped on one of these goes before it.
 */
export function EditChips<T>({
  items,
  id,
  label,
  icon,
  onReorder,
  onEdit,
  editLabel,
  error = () => false,
  onDropFrom,
}: {
  items: T[];
  id: (item: T) => string;
  label: (item: T) => string;
  icon: (item: T) => React.ReactNode;
  onReorder: (ids: string[]) => void;
  onEdit: (item: T) => void;
  editLabel: string;
  /** A name in the error colour (a feed that fails to download). */
  error?: (item: T) => boolean;
  /** A chip of another group dropped on the chip `before`. */
  onDropFrom?: (key: string, before: string) => void;
}) {
  // While dragging: the order shown, which is stored on drop.
  const [order, setOrder] = useState<string[] | null>(null);
  const [dragged, setDragged] = useState<string | null>(null);
  const byId = new Map(items.map((x) => [id(x), x]));
  const ids = items.map(id);
  const shown = (order ?? ids).flatMap((k) => (byId.has(k) ? [byId.get(k)!] : []));
  const step = (k: string, by: number) => {
    const from = ids.indexOf(k);
    const next = moved(ids, from, from + by);
    if (next !== ids) onReorder(next);
  };
  return (
    <>
      {shown.map((item) => {
        const k = id(item);
        return (
          <button
            key={k}
            type="button"
            draggable
            onDragStart={(e) => {
              e.dataTransfer.effectAllowed = "move";
              e.dataTransfer.setData(CHIP_TYPE, k);
              setDragged(k);
              setOrder(ids);
            }}
            onDragOver={(e) => {
              e.preventDefault();
              if (!dragged || dragged === k || !order) return;
              setOrder(moved(order, order.indexOf(dragged), order.indexOf(k)));
            }}
            onDrop={(e) => {
              e.preventDefault();
              const from = e.dataTransfer.getData(CHIP_TYPE);
              if (onDropFrom && from && !ids.includes(from)) {
                e.stopPropagation();
                onDropFrom(from, k);
              }
            }}
            onDragEnd={() => {
              if (order && order.some((x, i) => x !== ids[i])) onReorder(order);
              setDragged(null);
              setOrder(null);
            }}
            onKeyDown={(e) => {
              if (e.key === "ArrowLeft" || e.key === "ArrowRight") {
                e.preventDefault();
                step(k, e.key === "ArrowLeft" ? -1 : 1);
              }
            }}
            onClick={() => onEdit(item)}
            aria-label={`${editLabel}: ${label(item)}`}
            className={cn(chip, "cursor-grab pl-1.5 hover:bg-muted", dragged === k && "opacity-50")}
          >
            <GripVertical className="size-4 text-muted-foreground" aria-hidden="true" />
            {icon(item)}
            <span className={cn("max-w-48 truncate", error(item) && "text-destructive")}>{label(item)}</span>
            <Pencil className="size-3.5 text-muted-foreground" aria-hidden="true" />
          </button>
        );
      })}
    </>
  );
}

/**
 * The topics as chips, the same in the Add feed, Edit feed and Save/Organise sheets, as on Android:
 * each with its icon. `leading` comes first (No topic, or Like); `extra` are names the article has
 * that aren't topics (yet). At the end **Edit topics** opens Manage topics over the sheet; Done
 * comes back to it as it was. With `alwaysEditing` (Manage topics itself) every chip has a grip
 * and a pen ([EditChips]): click one to rename it, change its icon or remove it (a name already
 * taken joins the two); **New topic** after them.
 */
export function TopicChips({
  topics,
  selected,
  onToggle,
  onCreated = () => {},
  leading,
  extra = [],
  isExtraSelected = () => true,
  onToggleExtra = () => {},
  onRenamed,
  onRemoved,
  disabled = false,
  alwaysEditing = false,
}: {
  topics: Folder[];
  selected: (topic: Folder) => boolean;
  onToggle: (topic: Folder) => void;
  /** A topic made here, by its id and name. */
  onCreated?: (id: string, title: string) => void;
  leading?: React.ReactNode;
  extra?: string[];
  isExtraSelected?: (name: string) => boolean;
  onToggleExtra?: (name: string) => void;
  onRenamed?: (from: string, to: string) => void;
  onRemoved?: (title: string) => void;
  disabled?: boolean;
  alwaysEditing?: boolean;
}) {
  const [dialog, setDialog] = useState<Folder | "new" | null>(null);
  const [managing, setManaging] = useState(false);
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {alwaysEditing ? (
        <EditChips
          items={topics}
          id={(f) => f.id}
          label={(f) => f.title}
          icon={(f) => <FolderIcon name={f.icon} className="size-4" />}
          onReorder={(ids) => app.reorderTopics(ids)}
          onEdit={(f) => setDialog(f)}
          editLabel={t("editFolder")}
        />
      ) : (
        <>
          {leading}
          {topics.map((topic) => (
            <button key={topic.id} type="button" aria-pressed={selected(topic)} disabled={disabled} onClick={() => onToggle(topic)} className={cn(chip, selected(topic) ? chipOn : "hover:bg-muted")}>
              <FolderIcon name={topic.icon} className="size-4" />
              {topic.title}
            </button>
          ))}
          {extra.map((name) => (
            <button key={`extra:${name}`} type="button" aria-pressed={isExtraSelected(name)} disabled={disabled} onClick={() => onToggleExtra(name)} className={cn(chip, isExtraSelected(name) ? chipOn : "hover:bg-muted")}>
              {name}
            </button>
          ))}
        </>
      )}
      {alwaysEditing ? (
        <button type="button" disabled={disabled} onClick={() => setDialog("new")} className={action}>
          <Plus className="size-4" aria-hidden="true" />
          {t("newFolder")}
        </button>
      ) : (
        <button type="button" disabled={disabled} onClick={() => setManaging(true)} className={action}>
          <Pencil className="size-4" aria-hidden="true" />
          {t("editTopics")}
        </button>
      )}
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
      {managing ? <ManageTopicsDialog open onOpenChange={setManaging} onRenamed={onRenamed} onRemoved={onRemoved} /> : null}
    </div>
  );
}

/**
 * Manage topics, as on Android's sheet: from Settings, and from Edit topics in the sheets, over them.
 * The topics as editing chips, then New topic. Every change is stored at once, so it has **Done**.
 */
export function ManageTopicsDialog({
  open,
  onOpenChange,
  onRenamed,
  onRemoved,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onRenamed?: (from: string, to: string) => void;
  onRemoved?: (title: string) => void;
}) {
  const { library } = useApp();
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={t("manageTitle")} closeLabel={t("close")} done={t("done")}>
      <p className="pb-3 text-sm text-muted-foreground">{t("manageTopicsHint")}</p>
      <TopicChips topics={library.folders} selected={() => false} onToggle={() => {}} onRenamed={onRenamed} onRemoved={onRemoved} alwaysEditing />
    </Dialog>
  );
}
