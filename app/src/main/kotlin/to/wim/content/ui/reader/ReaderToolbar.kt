package to.wim.content.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * When the reader's floating toolbar shows (Material 3's floating toolbar): it steps aside
 * while you read down, and comes back when you scroll up, at the top, and at the end of the article
 * (where Archive is what you want next). Plain Kotlin, tested on the JVM.
 */
object ReaderToolbarVisibility {
    /**
     * [collapsedFraction]: how far the top bar has slid away (0 shown, 1 gone; it follows the
     * scrolling); [atEnd]: the article is scrolled to its end, or too short to scroll.
     */
    fun shown(
        collapsedFraction: Float,
        atEnd: Boolean,
    ): Boolean = atEnd || collapsedFraction < 0.5f
}

/**
 * What the reader's ⋮ menu offers, in this order. A feed article not saved: Mark as unread, the feed's
 * text or the full article. Once saved (also right after Save in the reader, 2026-11-02) it is a saved
 * article's menu: no Mark as unread or text switch, and Remove from Saved at the end. Both: Open in
 * browser, Sign in to this site (Sign in again once signed in, for when the site's sign-in ran out),
Report a problem. A newsletter feed's article (2026-11-12) is the whole email already and has no
 * site to pay for: no text switch and no Sign in. Plain Kotlin, tested on the JVM.
 */
object ReaderMenu {
    enum class Entry { MarkUnread, ToggleText, OpenInBrowser, SignIn, SignInAgain, Report, Remove }

    fun entries(
        /** It is (also) an article of one of your feeds, which has a read state. */
        feedArticle: Boolean,
        /** It is in Saved (to read or archived). */
        saved: Boolean,
        hasLink: Boolean,
        canSignIn: Boolean,
        canReport: Boolean,
        /** Already signed in to the article's site: Sign in again replaces that sign-in. */
        signedIn: Boolean = false,
        /** An article of a newsletter feed (a newsletter address's): its text is the whole newsletter. */
        newsletter: Boolean = false,
    ): List<Entry> =
        buildList {
            if (feedArticle && !saved) add(Entry.MarkUnread)
            if (hasLink && !saved && !newsletter) add(Entry.ToggleText)
            if (hasLink) add(Entry.OpenInBrowser)
            if (canSignIn && !newsletter) add(if (signedIn) Entry.SignInAgain else Entry.SignIn)
            if (canReport) add(Entry.Report)
            if (saved) add(Entry.Remove)
        }
}

/** The toolbar's height, and the room it keeps from the bottom edge. */
internal val ReaderToolbarHeight = 64.dp
internal val ReaderToolbarMargin = 16.dp

/**
 * The reader's actions as a floating toolbar (Material 3 Expressive): a pill above the article, at
 * the bottom in the middle, that slides away while reading down. Built from a `Surface` and a `Row`
 * (Material 3 1.4 has no public FloatingToolbar). No button has a fill of its own (2026-11-02): all
 * are in the toolbar's neutral colour (`onSurfaceVariant`, as Material 3's toolbars have it), and the
 * next step stands out by its words: Save, or Archive, is an icon with text; the rest are icons.
 * Share sits apart (2026-11-07, owner's wish): [separate], a floating action button beside the pill,
 * in the same style as + in the lists, as Material 3 pairs a floating toolbar with a FAB.
 */
@Composable
internal fun ReaderToolbar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    separate: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically { it * 2 } + fadeIn(),
        exit = slideOutVertically { it * 2 } + fadeOut(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.height(ReaderToolbarHeight).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
            separate?.invoke()
        }
    }
}

/** A text button in the toolbar takes the toolbar's neutral colour, like its icon buttons. */
@Composable
internal fun toolbarTextButtonColors(): ButtonColors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)
