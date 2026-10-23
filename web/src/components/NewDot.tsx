import { t } from "@/lib/i18n";
import { cn } from "./ui/cn";

/** Something new waits there (#167): a small dot in the primary colour, like the Android app's NewDot. */
export function NewDot({ className }: { className?: string }) {
  return <span role="img" aria-label={t("savedSharedNew")} className={cn("inline-block size-2 shrink-0 rounded-full bg-primary", className)} />;
}
