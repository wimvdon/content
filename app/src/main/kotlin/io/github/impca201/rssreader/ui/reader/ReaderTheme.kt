package io.github.impca201.rssreader.ui.reader

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.impca201.rssreader.data.Appearance
import io.github.impca201.rssreader.data.ReaderBackground
import io.github.impca201.rssreader.data.ReaderFont
import io.github.impca201.rssreader.data.ReaderStyle
import io.github.impca201.rssreader.ui.theme.appColorScheme

/** Warm paper with dark brown ink; links in a deeper brown. Contrast stays above 7:1 for text. */
private fun ColorScheme.sepia(): ColorScheme =
    copy(
        primary = Color(0xFF8A4B12),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFF1D9B5),
        onPrimaryContainer = Color(0xFF4A2800),
        secondaryContainer = Color(0xFFEADBC0),
        onSecondaryContainer = Color(0xFF3D2F1E),
        background = Color(0xFFF6EEDC),
        onBackground = Color(0xFF3D2F1E),
        surface = Color(0xFFF6EEDC),
        onSurface = Color(0xFF3D2F1E),
        surfaceVariant = Color(0xFFE8DBC1),
        onSurfaceVariant = Color(0xFF65543F),
        outline = Color(0xFF8C7A62),
        outlineVariant = Color(0xFFD9C8A8),
        surfaceContainerLowest = Color(0xFFFBF6EA),
        surfaceContainerLow = Color(0xFFF1E7D2),
        surfaceContainer = Color(0xFFEDE2CA),
        surfaceContainerHigh = Color(0xFFE8DCC2),
        surfaceContainerHighest = Color(0xFFE2D5B9),
    )

/**
 * The reader page's colours: the app's theme, sepia, or the app's dark colours. Everything on
 * the reader screen (top bar, sheet, text) uses them, so it reads as one page.
 */
@Composable
fun ReaderTheme(
    style: ReaderStyle,
    appearance: Appearance,
    appDark: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors =
        when (style.background) {
            ReaderBackground.Default -> MaterialTheme.colorScheme
            ReaderBackground.Sepia -> appColorScheme(context, dark = false, dynamicColor = false).sepia()
            ReaderBackground.Dark ->
                if (appDark) MaterialTheme.colorScheme else appColorScheme(context, dark = true, appearance.dynamicColor)
        }
    MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography, content = content)
}

fun ReaderFont.fontFamily(): FontFamily =
    when (this) {
        ReaderFont.Sans -> FontFamily.SansSerif
        ReaderFont.Serif -> FontFamily.Serif
    }

/** Body text in the reader's font, size and spacing. */
@Composable
fun readerBodyStyle(style: ReaderStyle) =
    MaterialTheme.typography.bodyLarge.copy(
        fontFamily = style.font.fontFamily(),
        fontSize = style.textSize.sp,
        lineHeight = style.spacing.lineHeight.em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        color = MaterialTheme.colorScheme.onSurface,
    )
