import { useState } from "react";
import { app, useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import { Button } from "./ui/button";
import { Dialog } from "./ui/dialog";
import { SwitchRow } from "./ui/switch";

const NAME = "Instapaper";

/**
 * Instapaper in Manage › Saved (#145, #251), as on Android: connect or disconnect it, and how it is
 * doing. Everything in Saved syncs both ways, highlights included: there is nothing to choose.
 */
export function InstapaperSettings() {
  const { instapaper, instapaperBusy, instapaperImportChoice, settings } = useApp();
  // Instapaper is a connected service, separate from the Content account (#166).
  if (!instapaper) {
    return (
      <section className="space-y-2">
        <h3 className="text-sm font-semibold">{NAME}</h3>
        <p className="text-sm text-muted-foreground">{t("instapaperBody")}</p>
        <Button variant="outline" onClick={() => app.connectInstapaper()}>
          {t("instapaperSignIn")}
        </Button>
      </section>
    );
  }
  // Just connected, and the question closed without an answer (#251): answer it here.
  if (instapaperImportChoice) {
    return (
      <section className="space-y-2">
        <h3 className="text-sm font-semibold">{NAME}</h3>
        <p className="text-sm text-muted-foreground">{t("instapaperImportBody")}</p>
        <div className="flex flex-wrap gap-2">
          <Button onClick={() => app.chooseInstapaperImport(true)}>{t("serviceImportTitle")}</Button>
          <Button variant="outline" onClick={() => app.chooseInstapaperImport(false)}>
            {t("instapaperImportSkip")}
          </Button>
          <Button variant="ghost" onClick={() => void app.disconnectInstapaper()}>
            {t("instapaperSignOut")}
          </Button>
        </div>
      </section>
    );
  }
  return (
    <section className="space-y-2">
      <h3 className="text-sm font-semibold">{NAME}</h3>
      <p className="text-sm text-muted-foreground">{instapaperBusy ? t("syncing") : t("instapaperSyncsEverything")}</p>
      <Button variant="ghost" onClick={() => void app.disconnectInstapaper()}>
        {t("instapaperSignOut")}
      </Button>
      <NewsletterUnreadSwitch checked={settings.newsletterUnread} />
    </section>
  );
}

/**
 * Mark incoming newsletters as unread (newsletters.ts): here and on + › Subscribe to newsletter's
 * Instapaper page. On, a newsletter that comes in is bold with a dot until read, and a dot on Saved
 * and the Newsletters chip says one waits; off, they come in read (turning it off reads them).
 */
export function NewsletterUnreadSwitch({ checked }: { checked: boolean }) {
  return <SwitchRow title={t("newsletterUnread")} summary={t("newsletterUnreadSummary")} checked={checked} onChange={(v) => app.setNewsletterUnread(v)} />;
}

/** Asked once, right after connecting Instapaper (#251): bring in everything it has now, or not. */
export function InstapaperImportDialog() {
  const { instapaperImportChoice } = useApp();
  // Closed without an answer: Manage › Saved still asks.
  const [dismissed, setDismissed] = useState(false);
  return (
    <Dialog
      open={instapaperImportChoice && !dismissed}
      onOpenChange={(o) => !o && setDismissed(true)}
      title={t("instapaperImportTitle")}
      closeLabel={t("close")}
      footer={
        <>
          <Button variant="ghost" onClick={() => app.chooseInstapaperImport(false)}>
            {t("instapaperImportSkip")}
          </Button>
          <Button onClick={() => app.chooseInstapaperImport(true)}>{t("serviceImportTitle")}</Button>
        </>
      }
    >
      <p className="text-sm">{t("instapaperImportBody")}</p>
    </Dialog>
  );
}
