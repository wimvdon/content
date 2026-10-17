import { useSyncExternalStore, type CSSProperties } from "react";
import type { Theme } from "@/lib/app";
import { LINE_HEIGHT, pageBackground, type ReaderStyle } from "@/lib/readerStyle";

/** How the reader page looks: its colours, font, size and spacing (Settings › Reader). */

const darkQuery = () => window.matchMedia("(prefers-color-scheme: dark)");

/** Whether the app is dark: the theme chosen, or the device's. */
export function useAppDark(theme: Theme): boolean {
  const systemDark = useSyncExternalStore(
    (onChange) => {
      const media = darkQuery();
      media.addEventListener("change", onChange);
      return () => media.removeEventListener("change", onChange);
    },
    () => darkQuery().matches,
  );
  return theme === "dark" || (theme === "system" && systemDark);
}

/** The class that gives the reader page its colours (see index.css), or "" for the app's theme. */
export function pageClass(style: ReaderStyle, appDark: boolean): string {
  const background = pageBackground(style, appDark);
  return background === "sepia" ? "sepia" : background === "dark" ? "dark" : "";
}

/** The article text's font, size and line height. */
export function textStyle(style: ReaderStyle): CSSProperties {
  return {
    fontFamily: style.font === "serif" ? "var(--font-serif)" : "var(--font-sans)",
    fontSize: `${style.textSize}px`,
    lineHeight: LINE_HEIGHT[style.spacing],
  };
}
