/** How articles look in the reader, like the Android app's ReaderStyle (data/Settings.kt). */

export type ReaderFont = "sans" | "serif";
export type ReaderSpacing = "compact" | "normal" | "relaxed";
/** The page colour: the app's theme, sepia paper, or dark whatever the theme. */
export type ReaderBackground = "default" | "sepia" | "dark";

export interface ReaderStyle {
  font: ReaderFont;
  /** In CSS pixels. */
  textSize: number;
  spacing: ReaderSpacing;
  background: ReaderBackground;
  /** Use the dark page whenever the app is dark, whatever background says (e.g. sepia by day). */
  autoDark: boolean;
}

export const MIN_TEXT_SIZE = 14;
export const MAX_TEXT_SIZE = 28;
export const DEFAULT_TEXT_SIZE = 18;

/** Line height as a multiple of the text size. */
export const LINE_HEIGHT: Record<ReaderSpacing, number> = { compact: 1.3, normal: 1.5, relaxed: 1.75 };

export const defaultReaderStyle: ReaderStyle = {
  font: "serif",
  textSize: DEFAULT_TEXT_SIZE,
  spacing: "normal",
  background: "default",
  autoDark: false,
};

export function withTextSize(style: ReaderStyle, size: number): ReaderStyle {
  return { ...style, textSize: Math.min(MAX_TEXT_SIZE, Math.max(MIN_TEXT_SIZE, Math.round(size))) };
}

/** The page colour actually shown, given whether the app's theme is dark. */
export function pageBackground(style: ReaderStyle, appDark: boolean): ReaderBackground {
  return style.autoDark && appDark ? "dark" : style.background;
}

/** A stored style, with anything missing or unknown replaced by the default. */
export function readerStyleOf(saved: unknown): ReaderStyle {
  const s = (saved && typeof saved === "object" ? saved : {}) as Partial<Record<keyof ReaderStyle, unknown>>;
  const pick = <T extends string>(value: unknown, options: readonly T[], fallback: T): T =>
    options.includes(value as T) ? (value as T) : fallback;
  return withTextSize(
    {
      font: pick(s.font, ["sans", "serif"], defaultReaderStyle.font),
      textSize: DEFAULT_TEXT_SIZE,
      spacing: pick(s.spacing, ["compact", "normal", "relaxed"], defaultReaderStyle.spacing),
      background: pick(s.background, ["default", "sepia", "dark"], defaultReaderStyle.background),
      autoDark: typeof s.autoDark === "boolean" ? s.autoDark : defaultReaderStyle.autoDark,
    },
    typeof s.textSize === "number" && Number.isFinite(s.textSize) ? s.textSize : DEFAULT_TEXT_SIZE,
  );
}
