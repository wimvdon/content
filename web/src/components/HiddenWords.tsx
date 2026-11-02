import { useMemo, useState } from "react";
import { app, useApp } from "@/lib/app";
import { hiddenPreview, hiddenWords } from "@/lib/articleFilter";
import { plural, t } from "@/lib/i18n";
import { Button } from "./ui/button";
import { Dialog } from "./ui/dialog";

/**
 * Hide articles with words (#277), as on Android: a row in Settings › Article list with the words as
 * its summary; clicking it opens a popup to change them. While typing, it shows how many recent
 * articles the words would hide and the newest of them (title and feed), so you see what goes
 * before Save.
 */
export function HiddenWordsRow() {
  const { settings } = useApp();
  const [editing, setEditing] = useState(false);
  const words = hiddenWords(settings.hiddenWords);
  return (
    <>
      <button type="button" onClick={() => setEditing(true)} className="flex w-full cursor-pointer flex-col rounded-lg py-2 text-left">
        <span className="block text-sm font-medium">{t("hiddenWordsTitle")}</span>
        <span className="block text-sm text-muted-foreground">{words.length === 0 ? t("hiddenWordsNone") : words.join(", ")}</span>
      </button>
      {editing ? <HiddenWordsDialog initial={settings.hiddenWords} onClose={() => setEditing(false)} /> : null}
    </>
  );
}

function HiddenWordsDialog({ initial, onClose }: { initial: string; onClose: () => void }) {
  const { library } = useApp();
  const [draft, setDraft] = useState(initial);
  const words = useMemo(() => hiddenWords(draft), [draft]);
  const preview = useMemo(() => hiddenPreview(library.articles, words), [library.articles, words]);
  const feeds = useMemo(() => new Map(library.feeds.map((f) => [f.url, f.title])), [library.feeds]);
  return (
    <Dialog
      popup
      open
      onOpenChange={(o) => !o && onClose()}
      title={t("hiddenWordsTitle")}
      closeLabel={t("close")}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t("cancel")}
          </Button>
          <Button
            onClick={() => {
              app.updateSettings({ hiddenWords: draft.trim() });
              onClose();
            }}
          >
            {t("save")}
          </Button>
        </>
      }
    >
      <div className="space-y-2">
        <textarea
          autoFocus
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          aria-label={t("hiddenWordsLabel")}
          placeholder={t("hiddenWordsLabel")}
          rows={3}
          className="w-full resize-y rounded-lg border bg-transparent px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
        />
        <p className="text-xs text-muted-foreground">{t("hiddenWordsHint")}</p>
        {words.length > 0 ? (
          <div className="space-y-2 pt-2" aria-live="polite">
            <p className="text-sm font-medium">{preview.count === 0 ? t("hiddenWordsPreviewNone") : plural("hiddenWordsPreview", preview.count)}</p>
            <ul className="space-y-2">
              {preview.newest.map((a) => (
                <li key={`${a.feedUrl} ${a.id}`} className="min-w-0">
                  <p className="line-clamp-2 text-sm">{a.title}</p>
                  {feeds.get(a.feedUrl) ? <p className="truncate text-xs text-muted-foreground">{feeds.get(a.feedUrl)}</p> : null}
                </li>
              ))}
            </ul>
          </div>
        ) : null}
      </div>
    </Dialog>
  );
}
