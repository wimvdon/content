import { cn } from "./cn";

/**
 * A choice in a row of them, on or off: shadcn/ui's toggle (outline), no check mark (the fill shows
 * it, and the row doesn't move when one is chosen). `role` is "radio" for one choice of several
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
        "inline-flex h-8 shrink-0 cursor-pointer items-center gap-1.5 rounded-md border px-3 text-sm shadow-xs transition-colors",
        selected ? "border-foreground/25 bg-accent font-medium text-accent-foreground" : "bg-background hover:bg-accent hover:text-accent-foreground",
      )}
    >
      {children}
    </button>
  );
}
