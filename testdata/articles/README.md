# Sample article pages

Each file is a web page and what Reader must make of it: text that must be in the article
(`text`, compared as plain text), HTML it must contain exactly (`htmlContains`), words that must
not be in it (`absent`), or no article at all (`noArticle`). Both apps' tests read every file, so
the Android app (`feed/ArticleExtractor.kt`) and the web app (`web/src/lib/articleExtractor.ts`)
clean a page up the same way. Add a page here when one comes out wrong.
