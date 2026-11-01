import * as D from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "./cn";

export function Dialog({
  open,
  onOpenChange,
  title,
  description,
  children,
  footer,
  closeLabel,
  wide,
  cancel,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
  closeLabel: string;
  wide?: boolean;
  /** A sheet (#305): Cancel in words at the top right instead of ×, the same as on Android. */
  cancel?: string;
}) {
  return (
    <D.Root open={open} onOpenChange={onOpenChange}>
      <D.Portal>
        <D.Overlay className="fixed inset-0 z-40 bg-black/40 backdrop-blur-[1px]" />
        <D.Content
          className={cn(
            "fixed top-1/2 left-1/2 z-50 flex max-h-[90dvh] w-[calc(100vw-2rem)] -translate-x-1/2 -translate-y-1/2 flex-col rounded-2xl border bg-card shadow-xl focus:outline-none",
            wide ? "max-w-2xl" : "max-w-md",
          )}
          aria-describedby={description ? undefined : undefined}
        >
          <div className="flex items-start gap-2 px-6 pt-5 pb-3">
            <div className="min-w-0 flex-1">
              <D.Title className="text-lg font-semibold">{title}</D.Title>
              {description ? <D.Description className="mt-1 text-sm text-muted-foreground">{description}</D.Description> : <D.Description className="sr-only">{title}</D.Description>}
            </div>
            {cancel ? (
              <D.Close className="-mr-2 h-9 shrink-0 cursor-pointer rounded-lg px-3 text-sm font-medium text-primary hover:bg-muted">{cancel}</D.Close>
            ) : (
              <D.Close className="-mr-2 rounded-full p-2 hover:bg-muted" aria-label={closeLabel}>
                <X className="size-4" />
              </D.Close>
            )}
          </div>
          {/* pt-1: the first field's focus ring isn't cut off by the scrolling body. */}
          <div className="min-h-0 flex-1 overflow-y-auto px-6 pt-1 pb-4">{children}</div>
          {footer ? <div className="flex flex-wrap justify-end gap-2 border-t px-6 py-3">{footer}</div> : null}
        </D.Content>
      </D.Portal>
    </D.Root>
  );
}
