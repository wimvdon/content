import { t } from "@/lib/i18n";
import { cn } from "./ui/cn";

/** Something new waits there (#167): a small dot in the primary colour, like the Android app's NewDot. `label` says what (read out). */
export function NewDot({ className, label }: { className?: string; label?: string }) {
  return <span role="img" aria-label={label ?? t("savedSharedNew")} className={cn("inline-block size-2 shrink-0 rounded-full bg-primary", className)} />;
}
