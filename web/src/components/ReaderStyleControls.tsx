import { Minus, Plus } from "lucide-react";
import { app, useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import { MAX_TEXT_SIZE, MIN_TEXT_SIZE, withTextSize, type ReaderBackground, type ReaderFont, type ReaderSpacing, type ReaderStyle } from "@/lib/readerStyle";
import { pageClass, textStyle, useAppDark } from "./readerPage";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";
import { Segmented } from "./ui/segmented";
import { SwitchRow } from "./ui/switch";

/** Font, text size, spacing, background and Auto dark, like the Android app's text settings. */
export function ReaderStyleControls({ preview }: { preview?: boolean }) {
  const { settings } = useApp();
  const style = settings.readerStyle;
  const appDark = useAppDark(settings.theme);
  const change = (readerStyle: ReaderStyle) => app.updateSettings({ readerStyle });
  return (
    <div className="space-y-4">
      {preview ? (
        <p className={cn("rounded-xl border bg-background p-4 text-foreground", pageClass(style, appDark))} style={textStyle(style)}>
          {t("readerPreview")}
        </p>
      ) : null}
      <Row label={t("font")}>
        <Segmented<ReaderFont>
          label={t("font")}
          value={style.font}
          onChange={(font) => change({ ...style, font })}
          options={[
            { value: "sans", label: t("fontSans") },
            { value: "serif", label: t("fontSerif") },
          ]}
        />
      </Row>
      <Row label={t("textSize")}>
        <div className="flex items-center gap-1">
          <Button variant="outline" size="icon" onClick={() => change(withTextSize(style, style.textSize - 1))} disabled={style.textSize <= MIN_TEXT_SIZE} aria-label={t("smaller")} title={t("smaller")}>
            <Minus />
          </Button>
          <span className="w-10 text-center text-sm tabular-nums" aria-live="polite">
            {style.textSize}
          </span>
          <Button variant="outline" size="icon" onClick={() => change(withTextSize(style, style.textSize + 1))} disabled={style.textSize >= MAX_TEXT_SIZE} aria-label={t("larger")} title={t("larger")}>
            <Plus />
          </Button>
        </div>
      </Row>
      <Row label={t("spacing")}>
        <Segmented<ReaderSpacing>
          label={t("spacing")}
          value={style.spacing}
          onChange={(spacing) => change({ ...style, spacing })}
          options={[
            { value: "compact", label: t("spacingCompact") },
            { value: "normal", label: t("spacingNormal") },
            { value: "relaxed", label: t("spacingRelaxed") },
          ]}
        />
      </Row>
      <Row label={t("background")}>
        <Segmented<ReaderBackground>
          label={t("background")}
          value={style.background}
          onChange={(background) => change({ ...style, background })}
          options={[
            { value: "default", label: t("backgroundDefault") },
            { value: "sepia", label: t("backgroundSepia") },
            { value: "dark", label: t("backgroundDark") },
          ]}
        />
      </Row>
      <SwitchRow title={t("autoDark")} summary={t("autoDarkSummary")} checked={style.autoDark} onChange={(autoDark) => change({ ...style, autoDark })} />
    </div>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2">
      <span className="text-sm font-medium">{label}</span>
      {children}
    </div>
  );
}
