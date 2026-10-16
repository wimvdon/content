// Copies the Android app's folder icons (Material Symbols, Apache 2.0) into public/, so the web
// app shows the same icons. Runs before `dev` and `build`.
import { copyFileSync, mkdirSync } from "node:fs";

const from = new URL("../app/src/main/assets/", import.meta.url);
const to = new URL("./public/", import.meta.url);
mkdirSync(to, { recursive: true });
for (const name of ["material_symbols.txt", "material_symbols_LICENSE.txt"]) copyFileSync(new URL(name, from), new URL(name, to));
