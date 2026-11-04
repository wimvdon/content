import * as T from "@radix-ui/react-toggle-group";
import { cn } from "./cn";

/** A small single choice (Theme, the reader's font…): shadcn/ui's tabs list, the chosen one raised. */
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
      className="inline-flex h-9 items-center rounded-lg bg-muted p-[3px] text-muted-foreground"
    >
      {options.map((o) => (
        <T.Item
          key={o.value}
          value={o.value}
          className={cn(
            "inline-flex h-full cursor-pointer items-center justify-center rounded-md px-3 text-sm font-medium transition-colors hover:text-foreground",
            "data-[state=on]:bg-background data-[state=on]:text-foreground data-[state=on]:shadow-sm",
          )}
        >
          {o.label}
        </T.Item>
      ))}
    </T.Root>
  );
}
