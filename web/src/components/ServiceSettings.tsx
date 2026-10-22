import { Check } from "lucide-react";
import { useState } from "react";
import { app, useApp } from "@/lib/app";
import { language, plural, t } from "@/lib/i18n";
import { INSTAPAPER_ID } from "@/lib/instapaperSync";
import { TO_READ, type ServiceImport } from "@/lib/serviceScope";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { Dialog } from "./ui/dialog";

const NAME = "Instapaper";

/**
 * Instapaper in Settings (#145), as on Android's service screen: what was imported (or the import,
 * once, right after signing in). Everything in Saved syncs, both ways: folders became tags (#187).
 */
export function InstapaperSettings() {
  const { instapaper, instapaperWaiting, instapaperBusy, settings } = useApp();
  const [failed, setFailed] = useState(false);
  const [choosing, setChoosing] = useState<string[] | null>(null);
  if (!instapaper) return null;
  const record = settings.imports[INSTAPAPER_ID];
  const run = (what: Pick<ServiceImport, "folders" | "archive">) => {
    setFailed(false);
    void app.importInstapaper(what).then((ok) => setFailed(!ok));
  };
  return (
    <section className="space-y-4">
      <h3 className="text-sm font-medium text-primary">{NAME}</h3>
      <div className="space-y-2">
        <h4 className="text-sm font-medium">{t("serviceImportTitle")}</h4>
        {record ? (
          <p className="text-sm">
            {plural("serviceImported", record.count).replace("{date}", new Date(record.at).toLocaleDateString(language, { dateStyle: "long" }))}
          </p>
        ) : instapaperWaiting ? (
          <p className="text-sm text-muted-foreground">{t("serviceImportBody").replace("{name}", NAME)}</p>
        ) : null}
        {failed ? (
          <p role="alert" className="text-sm text-destructive">
            {t("serviceImportFailed")}
          </p>
        ) : null}
        <div className="flex flex-wrap gap-2">
          {instapaperWaiting ? (
            <Button onClick={() => run({ folders: null, archive: true })} disabled={instapaperBusy}>
              {t("serviceImportAll")}
            </Button>
          ) : null}
          <Button
            variant="outline"
            disabled={instapaperBusy}
            onClick={() => void app.instapaperFolderNames().then((names) => (names ? setChoosing(names) : setFailed(true)))}
          >
            {instapaperWaiting ? t("serviceImportChoose") : t("serviceImportAgain")}
          </Button>
        </div>
      </div>
      {/* What it syncs was a choice of folders; folders became tags (#187), so everything in Saved syncs. */}
      {choosing ? (
        <ImportDialog
          folders={choosing}
          onImport={(what) => {
            setChoosing(null);
            run(what);
          }}
          onClose={() => setChoosing(null)}
        />
      ) : null}
    </section>
  );
}

/** What to bring in: To read, some folders and the archive. */
function ImportDialog({ folders, onImport, onClose }: { folders: string[]; onImport: (what: Pick<ServiceImport, "folders" | "archive">) => void; onClose: () => void }) {
  const [picked, setPicked] = useState<string[]>([TO_READ, ...folders]);
  const [archive, setArchive] = useState(true);
  const toggle = (name: string, on: boolean) => setPicked((p) => (on ? [...p.filter((x) => x !== name), name] : p.filter((x) => x !== name)));
  const everything = archive && picked.length === folders.length + 1;
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={t("serviceImportWhat")}
      closeLabel={t("close")}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t("cancel")}
          </Button>
          <Button disabled={picked.length === 0 && !archive} onClick={() => onImport({ folders: everything ? null : picked, archive })}>
            {t("serviceImportStart")}
          </Button>
        </>
      }
    >
      <div className="space-y-1">
        <Check2 checked={picked.includes(TO_READ)} label={t("toRead")} onChange={(on) => toggle(TO_READ, on)} />
        {folders.map((f) => (
          <Check2 key={f} checked={picked.includes(f)} label={f} onChange={(on) => toggle(f, on)} />
        ))}
        <Check2 checked={archive} label={t("serviceImportArchive")} onChange={setArchive} />
      </div>
    </Dialog>
  );
}

function Check2({ checked, label, onChange }: { checked: boolean; label: string; onChange: (on: boolean) => void }) {
  return (
    <button type="button" role="checkbox" aria-checked={checked} onClick={() => onChange(!checked)} className="flex w-full cursor-pointer items-center gap-3 rounded-lg px-1 py-1.5 text-left text-sm hover:bg-muted">
      <span className={cn("flex size-4 shrink-0 items-center justify-center rounded border-2", checked ? "border-primary bg-primary text-primary-foreground" : "border-muted-foreground")}>
        {checked ? <Check className="size-3" strokeWidth={3} /> : null}
      </span>
      {label}
    </button>
  );
}
