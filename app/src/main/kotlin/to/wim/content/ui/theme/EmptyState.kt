package to.wim.content.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * What sits behind an empty page's icon: a still circle unless the app's theme picks its own
 * (Theme.kt): a [shape], and a [motion] for it (the icon stays still). Content picks its cookie,
 * which springs in and turns a little as the page appears (2026-11-03).
 */
class EmptyStateBadge(
    val shape: Shape = CircleShape,
    val motion: @Composable () -> Modifier = { Modifier },
)

val LocalEmptyStateBadge = staticCompositionLocalOf { EmptyStateBadge() }

/**
 * What a screen shows when it has nothing to show: nothing added yet, nothing matches, loading
 * failed, or something has to be set up first. An icon in a tinted circle, a title, an optional
 * explanation and up to two buttons ([actions]: a filled one for the next step, an outlined or
 * text one for an alternative). Centred and scrollable, so pull-to-refresh works on an empty
 * screen too. See DESIGN.md, "Empty and setup states".
 */
@Composable
fun EmptyState(
    icon: Painter,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                Modifier.widthIn(max = 360.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val badge = LocalEmptyStateBadge.current
                Box(
                    Modifier
                        .padding(bottom = 8.dp)
                        .size(72.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .then(badge.motion())
                            .background(MaterialTheme.colorScheme.secondaryContainer, badge.shape),
                    )
                    Icon(icon, contentDescription = null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                if (body != null) {
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Column(
                    Modifier.padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = actions,
                )
            }
        }
    }
}

/** [EmptyState] with a Material icon. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actions: @Composable ColumnScope.() -> Unit = {},
) = EmptyState(rememberVectorPainter(icon), title, modifier, body, actions)
