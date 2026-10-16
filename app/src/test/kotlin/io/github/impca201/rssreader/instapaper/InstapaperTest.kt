package io.github.impca201.rssreader.instapaper

import org.junit.Assert.assertEquals
import org.junit.Test

class InstapaperTest {
    @Test
    fun `reads the Simple API's answers`() {
        assertEquals(InstapaperResult.Ok, Instapaper.resultOf(200))
        assertEquals(InstapaperResult.Ok, Instapaper.resultOf(201))
        assertEquals(InstapaperResult.WrongPassword, Instapaper.resultOf(403))
        assertEquals(InstapaperResult.BadRequest, Instapaper.resultOf(400))
        assertEquals(InstapaperResult.RateLimited, Instapaper.resultOf(429))
        assertEquals(InstapaperResult.ServerError, Instapaper.resultOf(500))
    }

    @Test
    fun `sends the title only when there is one`() {
        assertEquals(mapOf("url" to "https://a.example/1", "title" to "Hello"), Instapaper.addForm("https://a.example/1", " Hello "))
        assertEquals(mapOf("url" to "https://a.example/1"), Instapaper.addForm("https://a.example/1", " "))
    }
}
