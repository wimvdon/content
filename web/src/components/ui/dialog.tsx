import * as D from "@radix-ui/react-dialog";
import { ArrowLeft, X } from "lucide-react";
import { createContext, useCallback, useContext, useEffect, useId, useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { t } from "@/lib/i18n";
import { cn } from "./cn";

type Props = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
  closeLabel: string;
  wide?: boolean;
  /** A sheet: Cancel in words at the bottom left instead of ×, the same as on Android. */
  cancel?: string;
  /** A dialog whose changes are kept at once (Manage feeds, Manage topics): Done at the bottom right instead of ×. */
  done?: string;
  /** The back arrow's label, when this dialog opens as a page of another. */
  backLabel?: string;
  /** A button at the right end of the title row: Remove's trash can, away from Cancel and Save. */
  headerAction?: ReactNode;
  /**
   * A confirmation (Remove …?): a small dialog over the one it is asked from, never a page of it, so
   * it reads as a question, as on Android.
   */
  popup?: boolean;
};

/** A page open in a dialog: what Back does (the same as closing it). */
type Page = { id: string; back: () => void };

type Host = { slot: HTMLDivElement | null; top: string | null; add: (page: Page) => void; remove: (id: string) => void };

const PageHost = createContext<Host | null>(null);

/**
 * A dialog, the way every one in the app opens (Material: one dialog, never one over another). Opened
 * from within another dialog (Edit topics in Add feed, a feed in Manage feeds, Manage feeds in
 * Settings), it doesn't open a second one: it becomes a page of that dialog, with a back arrow before
 * its title, as on Android (ContentSheet). Back (the arrow or Escape) shows the dialog under it as it
 * was: its state stays where it was declared.
 */
export function Dialog(props: Props) {
  const host = useContext(PageHost);
  return host && !props.popup ? <DialogPage host={host} {...props} /> : <RootDialog {...props} />;
}

function RootDialog({ open, onOpenChange, title, description, children, footer, closeLabel, wide, cancel, done, headerAction }: Props) {
  const [pages, setPages] = useState<Page[]>([]);
  const [slot, setSlot] = useState<HTMLDivElement | null>(null);
  const top = pages.at(-1) ?? null;
  // Stable, so a page's effect runs only when it opens or closes.
  const add = useCallback((page: Page) => setPages((list) => [...list.filter((p) => p.id !== page.id), page]), []);
  const remove = useCallback((id: string) => setPages((list) => list.filter((p) => p.id !== id)), []);
  const host: Host = { slot, top: top?.id ?? null, add, remove };
  return (
    <D.Root open={open} onOpenChange={onOpenChange}>
      <D.Portal>
        <D.Overlay className="fixed inset-0 z-40 bg-black/40 backdrop-blur-[1px]" />
        <D.Content
          className={cn(
            "fixed top-1/2 left-1/2 z-50 flex max-h-[90dvh] w-[calc(100vw-2rem)] -translate-x-1/2 -translate-y-1/2 flex-col rounded-2xl border bg-card shadow-xl focus:outline-none",
            wide ? "max-w-2xl" : "max-w-md",
          )}
          // Escape on a page goes back to the dialog under it; on the dialog itself it closes.
          onEscapeKeyDown={(e) => {
            if (!top) return;
            e.preventDefault();
            top.back();
          }}
        >
          <PageHost.Provider value={host}>
            <div className={cn("flex min-h-0 flex-1 flex-col", top && "hidden")}>
              <div className="flex items-start gap-2 px-6 pt-5 pb-3">
                <div className="min-w-0 flex-1">
                  <D.Title className="text-lg font-semibold">{title}</D.Title>
                  {description ? <D.Description className="mt-1 text-sm text-muted-foreground">{description}</D.Description> : <D.Description className="sr-only">{title}</D.Description>}
                </div>
                {headerAction ? <div className="-mt-1 -mr-2 shrink-0">{headerAction}</div> : null}
                {cancel || done ? null : (
                  <D.Close className="-mr-2 rounded-full p-2 hover:bg-muted" aria-label={closeLabel}>
                    <X className="size-4" />
                  </D.Close>
                )}
              </div>
              {/* pt-1: the first field's focus ring isn't cut off by the scrolling body. */}
              <div className="min-h-0 flex-1 overflow-y-auto px-6 pt-1 pb-4">{children}</div>
              {/* Cancel at the bottom left, as on Android (SheetActions); a Remove (mr-auto) sits next
                  to it, the main button at the right; Done at the far right. */}
              {footer || cancel || done ? (
                <div className="flex flex-wrap items-center gap-2 border-t px-6 py-3">
                  {cancel ? <D.Close className="-ml-3 h-9 shrink-0 cursor-pointer rounded-lg px-3 text-sm font-medium text-primary hover:bg-muted">{cancel}</D.Close> : null}
                  <div className="flex flex-1 flex-wrap items-center justify-end gap-2">
                    {footer}
                    {done ? <D.Close className="h-9 shrink-0 cursor-pointer rounded-lg bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary/90">{done}</D.Close> : null}
                  </div>
                </div>
              ) : null}
            </div>
            {/* The pages opened from within it: only the last one shows. */}
            <div ref={setSlot} className="contents" />
          </PageHost.Provider>
        </D.Content>
      </D.Portal>
    </D.Root>
  );
}

function DialogPage({ host, open, onOpenChange, title, description, children, footer, backLabel, headerAction }: Props & { host: Host }) {
  const id = useId();
  const back = useRef(() => onOpenChange(false));
  useLayoutEffect(() => {
    back.current = () => onOpenChange(false);
  });
  const { add, remove } = host;
  const box = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    add({ id, back: () => back.current() });
    return () => remove(id);
  }, [open, id, add, remove]);
  const shown = open && host.top === id;
  // A page coming into view takes the focus (its first field, or the back arrow).
  useEffect(() => {
    if (!shown) return;
    const first = box.current?.querySelector<HTMLElement>("input, textarea, select") ?? box.current?.querySelector<HTMLElement>("button");
    first?.focus();
  }, [shown]);
  if (!open || !host.slot) return null;
  return createPortal(
    <div ref={box} className={cn("flex min-h-0 flex-1 flex-col", !shown && "hidden")} role="group" aria-label={title}>
      <div className="flex items-start gap-1 py-3 pr-6 pl-3">
        <button type="button" onClick={() => back.current()} className="mt-0.5 shrink-0 cursor-pointer rounded-full p-2 hover:bg-muted" aria-label={backLabel ?? t("back")}>
          <ArrowLeft className="size-4" />
        </button>
        <div className="min-w-0 flex-1 pt-1">
          <h2 className="text-lg font-semibold">{title}</h2>
          {description ? <p className="mt-1 text-sm text-muted-foreground">{description}</p> : null}
        </div>
        {headerAction ? <div className="-mr-2 shrink-0">{headerAction}</div> : null}
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto px-6 pt-1 pb-4">{children}</div>
      {footer ? (
        <div className="flex flex-wrap items-center gap-2 border-t px-6 py-3">
          <div className="flex flex-1 flex-wrap items-center justify-end gap-2">{footer}</div>
        </div>
      ) : null}
    </div>,
    host.slot,
  );
}
