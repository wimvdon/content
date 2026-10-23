import { Check } from "lucide-react";
import { cn } from "./cn";

/**
 * A Material 3 filter chip: on (with a check) or off. `role` is "radio" for one choice of several
 * (inside a radiogroup), else it is a toggle button. `check: false` leaves the check out, so a row of
 * chips doesn't move when one is chosen (the fill shows it).
 */
export function FilterChip({
  selected,
  onClick,
  children,
  role,
  check = true,
}: {
  selected: boolean;
  onClick: () => void;
  children: React.ReactNode;
  role?: "radio";
  check?: boolean;
}) {
  return (
    <button
      type="button"
      role={role}
      aria-checked={role ? selected : undefined}
      aria-pressed={role ? undefined : selected}
      onClick={onClick}
      className={cn(
        "inline-flex h-8 shrink-0 cursor-pointer items-center gap-1.5 rounded-lg border px-3 text-sm",
        selected ? cn("border-transparent bg-accent text-accent-foreground", check && "font-medium") : "hover:bg-muted",
      )}
    >
      {selected && check ? <Check className="size-4" /> : null}
      {children}
    </button>
  );
}
