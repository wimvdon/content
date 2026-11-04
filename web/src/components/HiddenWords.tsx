import { Plus, X } from "lucide-react";
import { useMemo, useState } from "react";
import { app, useApp } from "@/lib/app";
import { entriesText, hiddenPreview, hiddenWords, withEntry, withoutEntry } from "@/lib/articleFilter";
import { plural, t } from "@/lib/i18n";
import { Button } from "./ui/button";
import { Dialog } from "./ui/dialog";
import { Input } from "./ui/input";

/**
 * Hide articles with words (#277), as on Android: a row in Settings › Article list with the words as
 * its summary; clicking it opens a popup to change them: each word or group of words is a chip with an
 * X, and a field adds one at a time (2026-11-02). While typing, it shows how many recent
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
  const [entries, setEntries] = useState(() => hiddenWords(initial));
  const [typed, setTyped] = useState("");
  // What Save stores: the chips, and what is still in the field.
  const words = useMemo(() => withEntry(entries, typed), [entries, typed]);
  const add = () => {
    setEntries(withEntry(entries, typed));
    setTyped("");
  };
  const feeds = useMemo(() => new Map(library.feeds.map((f) => [f.url, f.title])), [library.feeds]);
  // The feed's name counts too ("WIRED coupon code").
  const preview = useMemo(() => hiddenPreview(library.articles, words, undefined, (a) => feeds.get(a.feedUrl) ?? null), [library.articles, words, feeds]);
  return (
    <Dialog
      popup
      open
      onOpenChange={(o) => !o && onClose()}
      title={t("hiddenWordsTitle")}
      closeLabel={t("close")}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            {t("cancel")}
          </Button>
          <Button
            onClick={() => {
              app.updateSettings({ hiddenWords: entriesText(words) });
              onClose();
            }}
          >
            {t("save")}
          </Button>
        </>
      }
    >
      <div className="space-y-2">
        {entries.length > 0 ? (
          <ul className="flex flex-wrap gap-2 pb-1">
            {entries.map((entry) => (
              <li key={entry}>
                <span className="inline-flex h-8 max-w-full items-center gap-1 rounded-lg border pr-1 pl-3 text-sm">
                  <span className="max-w-48 truncate">{entry}</span>
                  <button
                    type="button"
                    onClick={() => setEntries(withoutEntry(entries, entry))}
                    aria-label={t("hiddenWordsRemove").replace("{name}", entry)}
                    title={t("hiddenWordsRemove").replace("{name}", entry)}
                    className="inline-flex size-6 cursor-pointer items-center justify-center rounded-sm text-muted-foreground hover:bg-muted hover:text-foreground"
                  >
                    <X className="size-4" aria-hidden="true" />
                  </button>
                </span>
              </li>
            ))}
          </ul>
        ) : null}
        <div className="flex gap-2">
          <Input
            autoFocus
            value={typed}
            onChange={(e) => setTyped(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                add();
              }
            }}
            aria-label={t("hiddenWordsLabel")}
            placeholder={t("hiddenWordsLabel")}
          />
          <Button variant="ghost" size="icon" onClick={add} disabled={typed.trim() === ""} aria-label={t("add")} title={t("add")}>
            <Plus />
          </Button>
        </div>
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
