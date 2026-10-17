import { useEffect, useState } from "react";
import { loadIcons, type SymbolIcon } from "@/lib/icons";

/** The folder icon catalog, loaded once when first needed. */
let cache: Map<string, SymbolIcon> | null = null;

export function useIcons(): Map<string, SymbolIcon> | null {
  const [icons, setIcons] = useState(cache);
  useEffect(() => {
    if (!icons) void loadIcons().then((m) => setIcons((cache = m)));
  }, [icons]);
  return icons;
}
