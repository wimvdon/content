import { Button } from "./ui/button";
import { t } from "../lib/i18n";
import { appLinks } from "../lib/raindrop";

/** Raindrop's callback for the Android app (#112): buttons that open Reader (or its test version) with it. */
export function RaindropCallback() {
  const links = appLinks(location.host, location.search);
  return (
    <main className="mx-auto flex min-h-screen max-w-md flex-col justify-center gap-4 p-6">
      <h1 className="text-2xl font-semibold">{t("raindropCallbackTitle")}</h1>
      {links ? (
        <>
          <p className="text-muted-foreground">{t("raindropCallbackBody")}</p>
          <Button asChild>
            <a href={links.app}>{t("raindropOpenReader")}</a>
          </Button>
          <Button asChild variant="outline">
            <a href={links.dev}>{t("raindropOpenReaderDev")}</a>
          </Button>
        </>
      ) : (
        <p className="text-muted-foreground">{t("raindropCallbackElsewhere")}</p>
      )}
    </main>
  );
}
