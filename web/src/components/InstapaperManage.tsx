import { ArrowDown, ArrowUp, Folder as FolderGlyph, Plus, Tag as TagGlyph, Trash2 } from "lucide-react";
import { useState } from "react";
import { app, useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import type { Tag } from "@/lib/instapaper";
import { Button } from "./ui/button";
import { Dialog } from "./ui/dialog";
import { Input } from "./ui/input";

/** Manage Instapaper: its own page, apart from the feeds' folders (Manage feeds). */
export function InstapaperManageDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const { instapaper } = useApp();
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={instapaper ? t("instapaperManageTitle") : t("savedManageTitle")} closeLabel={t("close")} wide>
      <InstapaperManage />
    </Dialog>
  );
}

/**
 * Instapaper's folders and tags: create, reorder and delete folders; rename tags (Instapaper
 * creates tags when they are added to an article). Saved in this browser has only tags.
 */
function InstapaperManage() {
  const { instapaperFolders: folders, instapaperTags: tags, instapaper } = useApp();
  const [title, setTitle] = useState("");
  const [problem, setProblem] = useState<string | null>(null);
  const run = (work: Promise<string | null>) => void work.then(setProblem);
  return (
    <section className="space-y-4">
      {problem ? (
        <p role="alert" className="px-1 text-sm text-destructive">
          {problem}
        </p>
      ) : null}
      {instapaper ? (
      <div>
        <h3 className="px-1 pb-1 text-sm font-medium text-primary">{t("instapaperFolders")}</h3>
        <ul>
          {folders.map((folder, index) => (
            <li key={folder.id} className="flex items-center gap-2 rounded-lg px-2 py-1.5 hover:bg-card">
              <FolderGlyph className="size-5 text-muted-foreground" aria-hidden="true" />
              <span className="min-w-0 flex-1 truncate text-sm">{folder.title}</span>
              <Button variant="ghost" size="iconSm" disabled={index === 0} onClick={() => run(app.moveInstapaperFolder(index, -1))} aria-label={t("moveUp")} title={t("moveUp")}>
                <ArrowUp />
              </Button>
              <Button variant="ghost" size="iconSm" disabled={index === folders.length - 1} onClick={() => run(app.moveInstapaperFolder(index, 1))} aria-label={t("moveDown")} title={t("moveDown")}>
                <ArrowDown />
              </Button>
              <Button variant="ghost" size="iconSm" onClick={() => run(app.deleteInstapaperFolder(folder.id))} aria-label={`${t("instapaperDeleteFolder")}: ${folder.title}`} title={t("instapaperDeleteFolder")}>
                <Trash2 />
              </Button>
            </li>
          ))}
        </ul>
        <p className="px-1 pt-1 text-xs text-muted-foreground">{t("instapaperDeleteFolderNote")}</p>
        <form
          className="flex gap-2 px-1 pt-2"
          onSubmit={(e) => {
            e.preventDefault();
            const name = title.trim();
            if (!name) return;
            setTitle("");
            run(app.createInstapaperFolder(name));
          }}
        >
          <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder={t("newFolder")} aria-label={t("newFolder")} maxLength={100} />
          <Button type="submit" variant="tonal" disabled={!title.trim()}>
            <Plus />
            {t("add")}
          </Button>
        </form>
      </div>
      ) : null}
      {!instapaper && tags.length === 0 ? <p className="px-1 text-sm text-muted-foreground">{t("savedManageEmpty")}</p> : null}
      {tags.length > 0 ? (
        <div>
          <h3 className="px-1 pb-1 text-sm font-medium text-primary">{t("instapaperTags")}</h3>
          <ul className="space-y-1">
            {tags.map((tag) => (
              <TagRow key={tag.id} tag={tag} onProblem={setProblem} />
            ))}
          </ul>
        </div>
      ) : null}
    </section>
  );
}

function TagRow({ tag, onProblem }: { tag: Tag; onProblem: (text: string | null) => void }) {
  const [name, setName] = useState(tag.name);
  const changed = name.trim() !== "" && name.trim() !== tag.name;
  return (
    <li>
      <form
        className="flex items-center gap-2 px-2"
        onSubmit={(e) => {
          e.preventDefault();
          if (changed) void app.renameInstapaperTag(tag, name.trim()).then(onProblem);
        }}
      >
        <TagGlyph className="size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
        <Input className="h-8" value={name} onChange={(e) => setName(e.target.value)} aria-label={t("instapaperTagName")} maxLength={60} />
        <Button type="submit" variant="tonal" size="sm" disabled={!changed}>
          {t("instapaperRenameTag")}
        </Button>
      </form>
    </li>
  );
}
