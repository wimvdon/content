package io.github.impca201.rssreader.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveTest {
    @Test
    fun `finds the newest file id`() {
        assertEquals("id1", GoogleDrive.fileIdFromList("""{"files":[{"id":"id1"},{"id":"id2"}]}"""))
        assertNull(GoogleDrive.fileIdFromList("""{"files":[]}"""))
        assertThrows(SyncException::class.java) { GoogleDrive.fileIdFromList("<html>") }
    }

    @Test
    fun `multipart upload puts the file in the app folder`() {
        val body = GoogleDrive.multipartBody("{}")
        assertTrue(body.contains("\"parents\":[\"appDataFolder\"]"))
        assertTrue(body.contains("\"name\":\"rss-reader-sync.json\""))
        assertTrue(body.endsWith("\r\n{}\r\n--rss-reader-sync-boundary--"))
    }
}
