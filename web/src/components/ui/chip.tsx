import { Check } from "lucide-react";
import { cn } from "./cn";

/**
 * A Material 3 filter chip: on (with a check) or off. `role` is "radio" for one choice of several
 * (inside a radiogroup), else it is a toggle button.
 */
export function FilterChip({
  selected,
  onClick,
  children,
  role,
}: {
  selected: boolean;
  onClick: () => void;
  children: React.ReactNode;
  role?: "radio";
}) {
  return (
    <button
      type="button"
      role={role}
      aria-checked={role ? selected : undefined}
      aria-pressed={role ? undefined : selected}
      onClick={onClick}
      className={cn(
        "inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-lg border px-3 text-sm",
        selected ? "border-transparent bg-accent font-medium text-accent-foreground" : "hover:bg-muted",
      )}
    >
      {selected ? <Check className="size-4" /> : null}
      {children}
    </button>
  );
}
