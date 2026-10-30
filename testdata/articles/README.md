# Sample article pages

Each file is a web page and what Content must make of it: text that must be in the article
(`text`, compared as plain text), HTML it must contain exactly (`htmlContains`), words that must
not be in it (`absent`), or no article at all (`noArticle`). The web app's tests
(`web/test/articleExtractor.test.ts`) read every file. The Android app runs the same extractor
(`web/src/lib/articleExtractor.ts`, with Defuddle), so these hold for both apps. Add a page here
when one comes out wrong.
