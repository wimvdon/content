import { useMemo } from "react";
import { useApp } from "@/lib/app";
import { hiddenWords, withoutHidden } from "@/lib/articleFilter";
import { decodeSenders } from "@/lib/newsletterSenders";

/** The library the feed lists, counts and chips show: without the articles Hide articles with words leaves out (#277). */
export function useListedLibrary() {
  const { library, settings } = useApp();
  return useMemo(() => withoutHidden(library, hiddenWords(settings.hiddenWords)), [library, settings.hiddenWords]);
}

/** The newsletter senders' choices (newsletterSenders.ts). */
export function useSenderRules() {
  const { settings } = useApp();
  return useMemo(() => decodeSenders(settings.newsletterSenders), [settings.newsletterSenders]);
}
