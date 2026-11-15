package to.wim.content.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same cases as the web app's `emailCleanup.test.ts`. */
class EmailCleanupTest {
    @Test
    fun `hidden preheader text is removed`() {
        assertEquals(
            "<p>Real</p>",
            EmailCleanup.clean("""<div style="display:none;max-height:0px;overflow:hidden">Preheader text &zwnj;&nbsp;</div><p>Real</p>"""),
        )
        assertEquals("<p>Real</p>", EmailCleanup.clean("""<div style="display: none !important"><div>inner</div>x</div><p>Real</p>"""))
        assertEquals("<p>Real</p>", EmailCleanup.clean("""<span style="opacity:0;color:#fff">Hidden</span><p>Real</p>"""))
        assertEquals("<p>Real</p>", EmailCleanup.clean("""<span style='mso-hide:all'>Hidden</span><p>Real</p>"""))
        assertEquals("<p>Real</p>", EmailCleanup.clean("""<div style="font-size:0px;max-height:0">Hidden</div><p>Real</p>"""))
    }

    @Test
    fun `visible content is never removed`() {
        val kept =
            listOf(
                """<p style="opacity:0.5">Faded</p>""",
                """<p style="max-height:0.5em">Small</p>""",
                """<div style="font-size:0"><img src="https://a.example/x.jpg"></div>""",
                """<div style="max-height:0"><a href="https://a.example/">A link</a></div>""",
                """<table style="display:none"><tr><td>Table</td></tr></table>""",
                """<div style="display:none">Never closed<p>Real</p>""",
                """<div style="max-height:0">${"word ".repeat(200)}</div>""",
                """<div style="color:red">Red</div>""",
            )
        for (html in kept) assertEquals(html, EmailCleanup.clean(html))
    }

    @Test
    fun `a hidden element inside a visible one leaves the rest`() {
        assertEquals(
            "<div><p>One</p><p>Two</p></div>",
            EmailCleanup.clean("""<div><p>One</p><span style="display:none">x<span>y</span></span><p>Two</p></div>"""),
        )
    }

    @Test
    fun `a view in browser link near the top is removed in several languages`() {
        val labels =
            listOf(
                "View in browser",
                "View in your browser",
                "View online",
                "Read online",
                "View this email in your browser",
                "View in browser →",
                "Open in browser",
                "Web version",
                "Bekijk in je browser",
                "Bekijk deze e-mail in je browser",
                "Online bekijken",
                "Bekijk online",
                "Voir en ligne",
                "Voir dans le navigateur",
                "Im Browser ansehen",
                "Ver en el navegador",
                "Visualizza nel browser",
            )
        for (label in labels) {
            assertEquals("label $label", "<p></p><p>Hello</p>", EmailCleanup.clean("""<p><a href="https://x.example/v">$label</a></p><p>Hello</p>"""))
        }
    }

    @Test
    fun `the words before a view in browser link go with it`() {
        assertEquals(
            "<p></p><p>Hello</p>",
            EmailCleanup.clean("""<p>Trouble viewing this email? <a href="https://x.example/v"><span>View in your browser</span></a></p><p>Hello</p>"""),
        )
        assertEquals(
            "<p>Hello there. </p>",
            EmailCleanup.clean("""<p>Hello there. <a href="https://x.example/v">View online</a></p>"""),
        )
    }

    @Test
    fun `other links and links further down stay`() {
        val kept =
            listOf(
                """<p><a href="https://x.example/">Read more</a></p>""",
                """<p><a href="https://x.example/">Read online store reviews</a></p>""",
                """<p><a href="https://x.example/">View in browser tabs</a></p>""",
                """<p>${"word ".repeat(120)}</p><p><a href="https://x.example/v">View in browser</a></p>""",
                """<!-- <a href="https://x.example/v">View in browser</a> -->""",
            )
        for (html in kept) assertEquals(html, EmailCleanup.clean(html))
    }

    @Test
    fun `spacer pictures are removed`() {
        assertEquals("<p>a</p>", EmailCleanup.clean("""<p>a<img src="https://a.example/spacer.gif" width="20"></p>"""))
        assertEquals("<p>a</p>", EmailCleanup.clean("""<p>a<img src="https://a.example/img/blank.gif?v=2"></p>"""))
        assertEquals("<p>a</p>", EmailCleanup.clean("""<p>a<img src="https://a.example/p.png" style="width:1px;height:1px"></p>"""))
        assertEquals("<p>a</p>", EmailCleanup.clean("""<p>a<IMG style='height: 0 !important' src="https://a.example/p.png"></p>"""))
        val photo = """<p><img src="https://a.example/photo.jpg" style="width:100%"><img src="https://a.example/spacers/photo.jpg"></p>"""
        assertEquals(photo, EmailCleanup.clean(photo))
    }

    @Test
    fun `a pixel by its style is 0 or 1 pixel wide or high`() {
        assertTrue(EmailCleanup.isPixelStyle("width:1px"))
        assertTrue(EmailCleanup.isPixelStyle("border:0; HEIGHT: 0"))
        assertTrue(EmailCleanup.isPixelStyle("width:1PX !important;display:block"))
        assertFalse(EmailCleanup.isPixelStyle("width:100px"))
        assertFalse(EmailCleanup.isPixelStyle("width:10px"))
        assertFalse(EmailCleanup.isPixelStyle("width:0.5em"))
        assertFalse(EmailCleanup.isPixelStyle("max-width:1px"))
        assertFalse(EmailCleanup.isPixelStyle(null))
    }

    @Test
    fun `a whole email loses its chrome and keeps its text`() {
        val email =
            """<html><head><style>a{color:red}</style></head><body>""" +
                """<div style="display:none">Preview text</div>""" +
                """<p><a href="https://x.example/v">View in browser</a></p>""" +
                """<img src="https://a.example/spacer.gif" width="600" height="20">""" +
                """<h1>Title</h1><p>Real <b>content</b></p></body></html>"""
        assertEquals(
            """<html><head><style>a{color:red}</style></head><body><p></p><h1>Title</h1><p>Real <b>content</b></p></body></html>""",
            EmailCleanup.clean(email),
        )
    }
}
