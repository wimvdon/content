import { CheckCheck } from "lucide-react";
import { armed, progress, RESISTANCE } from "@/lib/pullToMark";
import { t } from "@/lib/i18n";
import { Button } from "./ui/button";

/** Mark all as read at the end of the list: click it, or scroll on past the end ([pull]). */
export function MarkAllReadButton({ pull, onClick }: { pull: number; onClick: () => void }) {
  const on = armed(pull);
  const p = progress(pull);
  return (
    <div className="flex justify-center pt-4" style={pull > 0 ? { transform: `translateY(${-pull * RESISTANCE}px)` } : undefined}>
      <Button variant={on ? "tonal" : "outline"} onClick={onClick}>
        {pull > 0 && !on ? (
          <svg viewBox="0 0 24 24" className="size-4 -rotate-90" aria-hidden="true">
            <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeOpacity="0.2" strokeWidth="2.5" />
            <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeWidth="2.5" strokeDasharray={`${p * 56.55} 56.55`} strokeLinecap="round" />
          </svg>
        ) : (
          <CheckCheck />
        )}
        {t(on ? "markAllReadRelease" : "markAllRead")}
      </Button>
    </div>
  );
}
