import { Folder as FolderGlyph, Tag as TagGlyph, Trash2 } from "lucide-react";
import { useState } from "react";
import { app, useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import { itemTags } from "@/lib/items";
import { folderCounts } from "@/lib/saved";
import { ConfirmRemove } from "./ConfirmRemove";
import { Button } from "./ui/button";
import { Input } from "./ui/input";

/** Saved's folders and tags: their own page, apart from the feeds' folders (Manage feeds). */
/**
 * The folders and tags of the saved articles, as on Android: rename either (to another's name joins
 * the two), remove a folder (its articles go to the Archive) or a tag (the articles stay). Folders
 * are made by filing an article in one, tags by adding them to an article.
 */
export function SavedManage() {
  const { library } = useApp();
  const folders = [...folderCounts(library)];
  const tags = itemTags(library);
  if (folders.length === 0 && tags.length === 0) {
    return (
      <div className="px-1 py-6 text-center">
        <p className="font-medium">{t("manageTagsEmptyTitle")}</p>
        <p className="mt-1 text-sm text-muted-foreground">{t("manageTagsEmptyBody")}</p>
      </div>
    );
  }
  return (
    <section className="space-y-4">
      {folders.length > 0 ? (
        <div>
          <h3 className="px-1 pb-1 text-sm font-medium text-primary">{t("savedFolders")}</h3>
          <ul className="space-y-1">
            {folders.map(([name, count]) => (
              <NameRow
                key={`f:${name}`}
                name={name}
                count={count}
                icon={<FolderGlyph className="size-5 shrink-0 text-muted-foreground" aria-hidden="true" />}
                label={t("folderRename")}
                removeLabel={t("folderRemove")}
                onRename={(to) => app.renameSavedFolder(name, to)}
                onRemove={() => app.removeSavedFolder(name)}
                confirmBody={t("removeSavedFolderBody").replace("{name}", name)}
              />
            ))}
          </ul>
          <p className="px-1 pt-1 text-xs text-muted-foreground">{t("folderRemoveNote")}</p>
        </div>
      ) : null}
      {tags.length > 0 ? (
        <div>
          <h3 className="px-1 pb-1 text-sm font-medium text-primary">{t("tags")}</h3>
          <ul className="space-y-1">
            {tags.map((tag) => (
              <NameRow
                key={`t:${tag}`}
                name={tag}
                icon={<TagGlyph className="size-5 shrink-0 text-muted-foreground" aria-hidden="true" />}
                label={t("tagRename")}
                removeLabel={t("tagRemoveEverywhere")}
                onRename={(to) => app.renameSavedTag(tag, to)}
                onRemove={() => app.removeSavedTag(tag)}
                confirmBody={t("removeTagBody").replace("{name}", tag)}
              />
            ))}
          </ul>
        </div>
      ) : null}
    </section>
  );
}

function NameRow({
  name,
  count,
  icon,
  label,
  removeLabel,
  onRename,
  onRemove,
  confirmBody,
}: {
  name: string;
  count?: number;
  icon: React.ReactNode;
  label: string;
  removeLabel: string;
  onRename: (to: string) => void;
  onRemove: () => void;
  /** What removing it does, asked first. */
  confirmBody: string;
}) {
  const [value, setValue] = useState(name);
  const [confirming, setConfirming] = useState(false);
  const changed = value.trim() !== "" && value.trim() !== name;
  return (
    <li>
      <form
        className="flex items-center gap-2 px-2"
        onSubmit={(e) => {
          e.preventDefault();
          if (changed) onRename(value.trim());
        }}
      >
        {icon}
        <Input className="h-8" value={value} onChange={(e) => setValue(e.target.value)} aria-label={label} maxLength={100} />
        {count !== undefined ? <span className="w-6 shrink-0 text-right text-xs text-muted-foreground tabular-nums">{count}</span> : null}
        <Button type="submit" variant="tonal" size="sm" disabled={!changed}>
          {label}
        </Button>
        <Button type="button" variant="ghost" size="iconSm" onClick={() => setConfirming(true)} aria-label={`${removeLabel}: ${name}`} title={removeLabel}>
          <Trash2 />
        </Button>
      </form>
      <ConfirmRemove open={confirming} title={removeLabel} body={confirmBody} onConfirm={onRemove} onClose={() => setConfirming(false)} />
    </li>
  );
}
