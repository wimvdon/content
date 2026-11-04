import { CheckCheck } from "lucide-react";
import { armed, progress, RESISTANCE } from "@/lib/pullToMark";
import { t } from "@/lib/i18n";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";

/**
 * Mark all as read at the end of the list: click it, or scroll on past the end ([pull]), the same
 * gesture as on Android in the web's own look: it moves up a little while its ring fills and turns;
 * far enough, it becomes the primary button; let go and it slides back (index.css).
 */
export function MarkAllReadButton({ pull, onClick }: { pull: number; onClick: () => void }) {
  const on = armed(pull);
  const p = progress(pull);
  const scale = 1 + 0.03 * p;
  return (
    <div
      className={cn("flex justify-center pt-4", pull === 0 && "spring-back")}
      style={{ transform: `translateY(${-pull * RESISTANCE}px)` }}
    >
      <span key={on ? "on" : "off"} className={cn("inline-block", on && "spring-pop")} style={on ? undefined : { transform: `scale(${scale})` }}>
        <Button variant={on ? "default" : "outline"} onClick={onClick} className="transition-colors">
          {pull > 0 && !on ? (
            <svg viewBox="0 0 24 24" className="size-4" style={{ transform: `rotate(${-90 + p * 270}deg)` }} aria-hidden="true">
              <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeOpacity="0.2" strokeWidth="2.5" />
              <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeWidth="2.5" strokeDasharray={`${p * 56.55} 56.55`} strokeLinecap="round" />
            </svg>
          ) : (
            <CheckCheck className={on ? "spring-in" : undefined} />
          )}
          {t(on ? "markAllReadRelease" : "markAllRead")}
        </Button>
      </span>
    </div>
  );
}
