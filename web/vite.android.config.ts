import { readFileSync } from "node:fs";
import { defineConfig } from "vite";

/**
 * Bundles the article extractor for the Android app (see src/android/articleExtractor.ts), with
 * Defuddle's license next to it. The Android build runs this (app/build.gradle.kts) with
 * ANDROID_ASSETS set to the folder it adds to the app's assets.
 */
export default defineConfig({
  publicDir: false,
  plugins: [
    {
      name: "defuddle-license",
      generateBundle() {
        this.emitFile({ type: "asset", fileName: "article-extractor_LICENSE.txt", source: readFileSync("node_modules/defuddle/LICENSE", "utf8") });
      },
    },
  ],
  build: {
    outDir: process.env.ANDROID_ASSETS ?? "dist-android",
    emptyOutDir: true,
    target: "chrome100",
    lib: { entry: "src/android/articleExtractor.ts", formats: ["iife"], name: "articleExtractor", fileName: () => "article-extractor.js" },
  },
});
