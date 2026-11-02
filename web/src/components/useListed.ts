import { useMemo } from "react";
import { useApp } from "@/lib/app";
import { hiddenWords, withoutHidden } from "@/lib/articleFilter";

/** The library the feed lists, counts and chips show: without the articles Hide articles with words leaves out (#277). */
export function useListedLibrary() {
  const { library, settings } = useApp();
  return useMemo(() => withoutHidden(library, hiddenWords(settings.hiddenWords)), [library, settings.hiddenWords]);
}
