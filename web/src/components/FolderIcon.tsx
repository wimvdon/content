import { Folder as FolderGlyph } from "lucide-react";
import { useEffect, useState } from "react";
import { loadIcons, type SymbolIcon } from "@/lib/icons";

let cache: Map<string, SymbolIcon> | null = null;

export function useIcons(): Map<string, SymbolIcon> | null {
  const [icons, setIcons] = useState(cache);
  useEffect(() => {
    if (!icons) void loadIcons().then((m) => setIcons((cache = m)));
  }, [icons]);
  return icons;
}

export function SymbolSvg({ icon, className = "size-5" }: { icon: SymbolIcon; className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="currentColor" aria-hidden="true">
      {icon.paths.map((d, i) => (
        <path key={i} d={d} />
      ))}
    </svg>
  );
}

export function FolderIcon({ name, className = "size-5" }: { name: string; className?: string }) {
  const icons = useIcons();
  const icon = icons?.get(name) ?? icons?.get("folder");
  return icon ? <SymbolSvg icon={icon} className={className} /> : <FolderGlyph className={className} aria-hidden="true" />;
}
