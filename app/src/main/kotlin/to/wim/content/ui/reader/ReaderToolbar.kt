package to.wim.content.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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

/** The toolbar's height, and the room it keeps from the bottom edge. */
internal val ReaderToolbarHeight = 64.dp
internal val ReaderToolbarMargin = 16.dp

/**
 * The reader's actions as a floating toolbar (Material 3 Expressive): a pill above the article, at
 * the bottom in the middle, that slides away while reading down. Built from a `Surface` and a `Row`
 * (Material 3 1.4 has no public FloatingToolbar). Its buttons are all in one neutral colour
 * (`onSurfaceVariant`, as Material 3's toolbars have it): Share, the highlights' count, Archive and
 * Organise look alike; only the tonal Save stands out, as the one thing to do first.
 */
@Composable
internal fun ReaderToolbar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically { it * 2 } + fadeIn(),
        exit = slideOutVertically { it * 2 } + fadeOut(),
    ) {
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
    }
}

/** A text button in the toolbar takes the toolbar's neutral colour, like its icon buttons. */
@Composable
internal fun toolbarTextButtonColors(): ButtonColors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)
