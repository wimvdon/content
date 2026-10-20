import { Check } from "lucide-react";
import { useState } from "react";
import { app, useApp } from "@/lib/app";
import { language, plural, t } from "@/lib/i18n";
import { INSTAPAPER_ID } from "@/lib/instapaperSync";
import { savedFolders } from "@/lib/saved";
import { coversPlace, EVERYTHING, TO_READ, withFolder, type ServiceImport, type ServiceScope } from "@/lib/serviceScope";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { Dialog } from "./ui/dialog";

const NAME = "Instapaper";

/**
 * Instapaper in Settings (#145), as on Android's service screen: what was imported (or the import,
 * once, right after signing in), and what it syncs, both ways: everything in Saved, or only some
 * folders. Unticking a folder says first how many articles it archives there.
 */
export function InstapaperSettings() {
  const { instapaper, instapaperWaiting, instapaperBusy, settings, library } = useApp();
  const [failed, setFailed] = useState(false);
  const [choosing, setChoosing] = useState<string[] | null>(null);
  const [asking, setAsking] = useState<{ scope: ServiceScope; count: number } | null>(null);
  if (!instapaper) return null;
  const record = settings.imports[INSTAPAPER_ID];
  const scope = settings.scopes[INSTAPAPER_ID] ?? EVERYTHING;
  const folders = savedFolders(library);
  const run = (what: Pick<ServiceImport, "folders" | "archive">) => {
    setFailed(false);
    void app.importInstapaper(what).then((ok) => setFailed(!ok));
  };
  const change = (next: ServiceScope) =>
    void app.instapaperLeaving(next).then((count) => (count === 0 ? app.setInstapaperScope(next) : setAsking({ scope: next, count })));
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
      <div className="space-y-2">
        <h4 className="text-sm font-medium">{t("serviceScopeTitle")}</h4>
        <div role="radiogroup" aria-label={t("serviceScopeTitle")} className="space-y-1">
          <Radio selected={scope.folders === null} label={t("serviceScopeAll")} onClick={() => scope.folders !== null && app.setInstapaperScope(EVERYTHING)} />
          {/* Starts from every folder there is, so nothing leaves until a folder is unticked. */}
          <Radio selected={scope.folders !== null} label={t("serviceScopeSome")} onClick={() => scope.folders === null && change({ folders: [TO_READ, ...folders] })} />
        </div>
        {scope.folders !== null ? (
          <div className="space-y-1 pl-7">
            <Check2 checked={coversPlace(scope, null, false)} label={t("toRead")} onChange={(on) => change(withFolder(scope, null, on))} />
            {folders.map((f) => (
              <Check2 key={f} checked={coversPlace(scope, f, false)} label={f} onChange={(on) => change(withFolder(scope, f, on))} />
            ))}
          </div>
        ) : null}
        <p className="text-xs text-muted-foreground">{t("serviceScopeNote").replace("{name}", NAME)}</p>
      </div>
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
      <Dialog
        open={asking !== null}
        onOpenChange={(o) => !o && setAsking(null)}
        title={t("serviceScopeLeaveTitle").replace("{name}", NAME)}
        closeLabel={t("close")}
        footer={
          <>
            <Button variant="ghost" onClick={() => setAsking(null)}>
              {t("cancel")}
            </Button>
            <Button
              onClick={() => {
                if (asking) app.setInstapaperScope(asking.scope);
                setAsking(null);
              }}
            >
              {t("serviceScopeLeaveConfirm")}
            </Button>
          </>
        }
      >
        <p className="text-sm">{asking ? plural("serviceScopeLeaveBody", asking.count).replace("{name}", NAME) : ""}</p>
      </Dialog>
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

function Radio({ selected, label, onClick }: { selected: boolean; label: string; onClick: () => void }) {
  return (
    <button type="button" role="radio" aria-checked={selected} onClick={onClick} className="flex w-full cursor-pointer items-center gap-3 rounded-lg px-1 py-1.5 text-left text-sm hover:bg-muted">
      <span className={cn("size-4 shrink-0 rounded-full border-2", selected ? "border-primary bg-primary shadow-[inset_0_0_0_2px_var(--color-card)]" : "border-muted-foreground")} />
      {label}
    </button>
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
