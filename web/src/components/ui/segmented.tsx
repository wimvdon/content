import * as T from "@radix-ui/react-toggle-group";
import { Check } from "lucide-react";
import { cn } from "./cn";

/** A connected two-way choice (like Unread | All in the Android app). */
export function Segmented<T extends string>({
  value,
  onChange,
  options,
  label,
}: {
  value: T;
  onChange: (value: T) => void;
  options: { value: T; label: string }[];
  label: string;
}) {
  return (
    <T.Root
      type="single"
      value={value}
      onValueChange={(v) => v && onChange(v as T)}
      aria-label={label}
      className="inline-flex gap-0.5 rounded-full bg-muted p-0.5"
    >
      {options.map((o) => (
        <T.Item
          key={o.value}
          value={o.value}
          className={cn(
            "inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-full px-3 text-sm font-medium text-muted-foreground transition-colors",
            "data-[state=on]:bg-primary data-[state=on]:text-primary-foreground",
          )}
        >
          {value === o.value ? <Check className="size-3.5" /> : null}
          {o.label}
        </T.Item>
      ))}
    </T.Root>
  );
}
