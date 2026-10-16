/**
 * Folder icons: the same Material Symbols set as the Android app (app/src/main/assets,
 * copied into public/ when building), loaded only when a folder icon is shown.
 */

export interface SymbolIcon {
  name: string;
  keywords: string[];
  paths: string[];
}

let loading: Promise<Map<string, SymbolIcon>> | null = null;

export function loadIcons(): Promise<Map<string, SymbolIcon>> {
  loading ??= fetch("/material_symbols.txt")
    .then((r) => (r.ok ? r.text() : ""))
    .then(parseIcons)
    .catch(() => new Map());
  return loading;
}

export function parseIcons(text: string): Map<string, SymbolIcon> {
  const icons = new Map<string, SymbolIcon>();
  for (const line of text.split("\n")) {
    if (!line.trim() || line.startsWith("#")) continue;
    const [name, categories, paths] = line.split("\t");
    if (!name || !paths) continue;
    icons.set(name, { name, keywords: (categories ?? "").split(/[^a-z0-9]+/i).filter(Boolean), paths: paths.split("|") });
  }
  return icons;
}

/** Icons whose name or category contains every word typed; popular topics when nothing is typed. */
export function searchIcons(icons: Map<string, SymbolIcon>, query: string, limit = 120): SymbolIcon[] {
  const words = query.toLowerCase().split(/\s+/).filter(Boolean);
  if (words.length === 0) return POPULAR.map((n) => icons.get(n)).filter((i): i is SymbolIcon => !!i);
  const out: SymbolIcon[] = [];
  for (const icon of icons.values()) {
    const hay = icon.name + " " + icon.keywords.join(" ");
    if (words.every((w) => hay.includes(w))) out.push(icon);
    if (out.length >= limit) break;
  }
  return out;
}

const POPULAR = [
  "folder", "newspaper", "public", "devices", "code", "science", "work", "payments", "sports-soccer", "sports-esports",
  "music-note", "movie", "menu-book", "photo-camera", "restaurant", "flight", "park", "home", "favorite", "star",
  "school", "palette", "pets", "directions-car", "computer", "smartphone", "rocket-launch", "lightbulb", "trending-up", "podcasts",
];
