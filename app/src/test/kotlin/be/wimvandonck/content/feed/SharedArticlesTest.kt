package be.wimvandonck.content.feed

import be.wimvandonck.content.sync.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The sample pages both apps are tested on (testdata/articles, also read by the web app's
 * articleExtractor.test.ts), so the Android app and the web app clean a page up the same way.
 */
class SharedArticlesTest {
    private val dir = listOf(File("../testdata/articles"), File("testdata/articles")).first { it.isDirectory }

    @Test
    fun `every sample page comes out as both apps expect`() {
        val files = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }
        assertTrue("no sample pages in $dir", files.size > 5)
        for (file in files) {
            val sample = Json.parse(file.readText()) as Map<*, *>
            fun list(key: String) = (sample[key] as? List<*>).orEmpty().filterIsInstance<String>()
            val article = ArticleExtractor.extract(sample["html"] as String, sample["url"] as String)
            if (sample["noArticle"] == true) {
                assertNull(file.name, article)
                continue
            }
            assertNotNull(file.name, article)
            val text = Html.toPlainText(article!!)
            for (t in list("text")) assertTrue("${file.name}: missing text \"$t\" in $article", text.contains(t))
            for (h in list("htmlContains")) assertTrue("${file.name}: missing \"$h\" in $article", article.contains(h))
            for (a in list("absent")) assertFalse("${file.name}: should not contain \"$a\": $article", article.contains(a))
        }
    }
}
