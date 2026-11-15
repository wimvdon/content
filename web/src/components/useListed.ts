import { useMemo } from "react";
import { useApp } from "@/lib/app";
import { hiddenWords, withoutHidden } from "@/lib/articleFilter";
import { decodeSenders, withMovedSenders, withoutMuted } from "@/lib/newsletterSenders";

/**
 * The library the feed lists, counts and chips show: without the articles Hide articles with words
 * leaves out (#277), with the newsletter senders moved to another tag listed under it, and without
 * the muted senders' newsletters.
 */
export function useListedLibrary() {
  const { library, settings } = useApp();
  return useMemo(() => {
    const rules = decodeSenders(settings.newsletterSenders);
    return withoutMuted(withMovedSenders(withoutHidden(library, hiddenWords(settings.hiddenWords)), rules), rules);
  }, [library, settings.hiddenWords, settings.newsletterSenders]);
}

/** The library with the senders moved to another tag listed under it, muted ones kept: what the senders' pages list. */
export function useMovedLibrary() {
  const { library, settings } = useApp();
  return useMemo(() => withMovedSenders(library, decodeSenders(settings.newsletterSenders)), [library, settings.newsletterSenders]);
}

/** The newsletter senders' choices (newsletterSenders.ts). */
export function useSenderRules() {
  const { settings } = useApp();
  return useMemo(() => decodeSenders(settings.newsletterSenders), [settings.newsletterSenders]);
}
