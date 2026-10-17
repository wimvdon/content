package be.wimvandonck.reader.instapaper

import org.junit.Assert.assertEquals
import org.junit.Test

class InstapaperTabsTest {
    private val folders = listOf(InstapaperFolder(1, "Recipes", 1), InstapaperFolder(2, "Work", 2))
    private val tags = listOf(InstapaperTag(7, "Cooking"), InstapaperTag(8, "Travel"))

    @Test
    fun `the lists, then the folders in order, then one tab for every tag`() {
        assertEquals(
            listOf(
                InstapaperTab.Unread,
                InstapaperTab.Liked,
                InstapaperTab.Archive,
                InstapaperTab.Folder(1),
                InstapaperTab.Folder(2),
                InstapaperTab.Tags,
            ),
            InstapaperTabs.of(folders, tags),
        )
        assertEquals(listOf(InstapaperTab.Unread, InstapaperTab.Liked, InstapaperTab.Archive), InstapaperTabs.of(emptyList(), emptyList()))
    }

    @Test
    fun `every list has its tab, and every tag shares the Tags tab`() {
        assertEquals(InstapaperTab.Unread, InstapaperTabs.tabOf(InstapaperView.Home))
        assertEquals(InstapaperTab.Folder(2), InstapaperTabs.tabOf(InstapaperView.InFolder(2)))
        assertEquals(InstapaperTab.Tags, InstapaperTabs.tabOf(InstapaperView.WithTag("Travel")))
    }

    @Test
    fun `the Tags tab keeps the tag shown, else opens the first`() {
        assertEquals(InstapaperView.WithTag("Travel"), InstapaperTabs.viewOf(InstapaperTab.Tags, tags, InstapaperView.WithTag("Travel")))
        assertEquals(InstapaperView.WithTag("Cooking"), InstapaperTabs.viewOf(InstapaperTab.Tags, tags, InstapaperView.Liked))
        assertEquals(InstapaperView.Home, InstapaperTabs.viewOf(InstapaperTab.Tags, emptyList(), null))
        assertEquals(InstapaperView.InFolder(1), InstapaperTabs.viewOf(InstapaperTab.Folder(1), tags, null))
    }
}
