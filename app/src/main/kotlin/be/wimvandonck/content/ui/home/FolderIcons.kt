package be.wimvandonck.content.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import be.wimvandonck.content.R
import be.wimvandonck.content.data.IconCatalog
import be.wimvandonck.content.rssReaderApp
import java.util.concurrent.ConcurrentHashMap

/** A folder icon by its Material Symbols name, to use with `Icon` (which tints it); the folder icon if unknown. */
@Composable
fun rememberFolderIconPainter(name: String): Painter {
    val catalog = LocalContext.current.rssReaderApp.iconCatalog
    val vector = remember(name) { catalog.icon(name)?.let(FolderIconVectors::get) }
    return if (vector != null) rememberVectorPainter(vector) else painterResource(R.drawable.ic_folder)
}

/** Drawn icons, kept so scrolling the icon picker doesn't parse the same paths again. */
object FolderIconVectors {
    private val cache = ConcurrentHashMap<String, ImageVector>()

    fun get(icon: IconCatalog.Icon): ImageVector =
        cache.getOrPut(icon.name) {
            ImageVector
                .Builder(name = icon.name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
                .apply { icon.paths.forEach { addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.Black)) } }
                .build()
        }
}
