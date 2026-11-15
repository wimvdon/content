import { useMemo } from "react";
import { useApp } from "@/lib/app";
import { hiddenWords, withoutHidden } from "@/lib/articleFilter";
import { decodeSenders, withoutMuted } from "@/lib/newsletterSenders";

/**
 * The library the feed lists, counts and chips show: without the articles Hide articles with words
 * leaves out (#277), and without the muted newsletter senders' newsletters.
 */
export function useListedLibrary() {
  const { library, settings } = useApp();
  return useMemo(
    () => withoutMuted(withoutHidden(library, hiddenWords(settings.hiddenWords)), decodeSenders(settings.newsletterSenders)),
    [library, settings.hiddenWords, settings.newsletterSenders],
  );
}

/** The newsletter senders' choices (newsletterSenders.ts). */
export function useSenderRules() {
  const { settings } = useApp();
  return useMemo(() => decodeSenders(settings.newsletterSenders), [settings.newsletterSenders]);
}
