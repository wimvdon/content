import { app, useApp } from "@/lib/app";
import { language, plural, t } from "@/lib/i18n";
import { INSTAPAPER_ID } from "@/lib/instapaperSync";
import { Button } from "./ui/button";
import { SwitchRow } from "./ui/switch";

const NAME = "Instapaper";

/**
 * Instapaper in Settings (#145), as on Android's service screen: connect or disconnect it, what was
 * brought in when it was connected, and copying highlights. Everything in Saved syncs, both ways:
 * there is nothing to choose.
 */
export function InstapaperSettings() {
  const { instapaper, settings } = useApp();
  // Instapaper is a connected service, separate from the Content account (#166).
  if (!instapaper) {
    return (
      <section className="space-y-2">
        <h3 className="text-sm font-medium text-primary">{NAME}</h3>
        <Button variant="outline" onClick={() => app.connectInstapaper()}>
          {t("instapaperSignIn")}
        </Button>
      </section>
    );
  }
  const record = settings.imports[INSTAPAPER_ID];
  return (
    <section className="space-y-4">
      <h3 className="text-sm font-medium text-primary">{NAME}</h3>
      {record ? (
        <div className="space-y-2">
          <h4 className="text-sm font-medium">{t("serviceImportTitle")}</h4>
          <p className="text-sm">{plural("serviceImported", record.count).replace("{date}", new Date(record.at).toLocaleDateString(language, { dateStyle: "long" }))}</p>
        </div>
      ) : null}
      <Button variant="ghost" onClick={() => void app.disconnectInstapaper()}>
        {t("instapaperSignOut")}
      </Button>
      <div className="space-y-2">
        <h4 className="text-sm font-medium">{t("highlightsTitle")}</h4>
        <SwitchRow title={t("copyHighlights")} summary={t("copyHighlightsSummary")} checked={settings.copyHighlights} onChange={app.setCopyHighlights} />
      </div>
    </section>
  );
}
