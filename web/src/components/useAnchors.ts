import { useMemo } from "react";
import { resolve, type Anchor } from "@/lib/anchors";
import { pageText } from "@/lib/highlightDom";
import type { Highlight } from "@/lib/items";

/** Where each highlight is in the text shown, for the sheets: approximate, or not found. */
export function useAnchors(html: string, highlights: Highlight[]): Map<string, Anchor> {
  return useMemo(() => {
    const el = document.createElement("div");
    el.innerHTML = html;
    const text = pageText(el).text;
    return new Map(highlights.map((h) => [h.id, resolve(h, text)]));
  }, [html, highlights]);
}
