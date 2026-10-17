import { useState } from "react";
import type { Feed } from "@/lib/library";
import { cn } from "./ui/cn";

/** The website's icon on a light tile (like the Android app), or the feed's first letter. */
export function FeedIcon({ feed, className = "size-5" }: { feed: Feed | undefined; className?: string }) {
  const [failed, setFailed] = useState(false);
  const letter = (feed?.title.trim()[0] ?? "?").toUpperCase();
  if (feed?.iconUrl && !failed) {
    return (
      // The picture fills 80% of the tile; a percentage padding would be a share of the whole row.
      <span className={cn("inline-flex shrink-0 items-center justify-center rounded-md bg-[#f4f4f4]", className)}>
        <img src={feed.iconUrl} alt="" className="size-[80%] object-contain" referrerPolicy="no-referrer" loading="lazy" onError={() => setFailed(true)} />
      </span>
    );
  }
  return (
    <span className={cn("inline-flex shrink-0 items-center justify-center rounded-md bg-accent text-[0.65em] font-semibold text-accent-foreground", className)} aria-hidden="true">
      {letter}
    </span>
  );
}
