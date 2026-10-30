/**
 * The web app's article extractor for the Android app, which runs it in a hidden WebView
 * (feed/WebArticleExtractor.kt). The Android build bundles this file with `npm run build:android`,
 * so both apps use the Defuddle version in package-lock.json.
 */

import { extractArticle } from "../lib/articleExtractor";

declare global {
  var extractArticle: (html: string, pageUrl: string) => string | null;
}

globalThis.extractArticle = extractArticle;
