package to.wim.content.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.wim.content.data.Appearance
import to.wim.content.rssReaderApp

// Fallback palette (no dynamic color, or dynamic color turned off): the azure of the app icon,
// with the icon's sky blue as the tertiary accent. Material 3 tonal values from seed #0091EA.
private val LightColors =
    lightColorScheme(
        primary = Color(0xFF0062A0),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD0E4FF),
        onPrimaryContainer = Color(0xFF00497A),
        secondary = Color(0xFF526070),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD6E4F7),
        onSecondaryContainer = Color(0xFF3B4857),
        tertiary = Color(0xFF406376),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFC3E8FE),
        onTertiaryContainer = Color(0xFF274B5D),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        background = Color(0xFFF9F9FC),
        onBackground = Color(0xFF1A1C1E),
        surface = Color(0xFFF9F9FC),
        onSurface = Color(0xFF1A1C1E),
        surfaceVariant = Color(0xFFDFE3EB),
        onSurfaceVariant = Color(0xFF42474E),
        outline = Color(0xFF73777F),
        outlineVariant = Color(0xFFC2C7CF),
        inverseSurface = Color(0xFF2F3033),
        inverseOnSurface = Color(0xFFF1F0F4),
        inversePrimary = Color(0xFF9CCAFF),
        surfaceDim = Color(0xFFDADADD),
        surfaceBright = Color(0xFFF9F9FC),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF4F3F7),
        surfaceContainer = Color(0xFFEEEDF1),
        surfaceContainerHigh = Color(0xFFE8E8EB),
        surfaceContainerHighest = Color(0xFFE2E2E6),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF9CCAFF),
        onPrimary = Color(0xFF003256),
        primaryContainer = Color(0xFF00497A),
        onPrimaryContainer = Color(0xFFD0E4FF),
        secondary = Color(0xFFBAC8DB),
        onSecondary = Color(0xFF243140),
        secondaryContainer = Color(0xFF3B4857),
        onSecondaryContainer = Color(0xFFD6E4F7),
        tertiary = Color(0xFFA7CCE1),
        onTertiary = Color(0xFF0C3445),
        tertiaryContainer = Color(0xFF274B5D),
        onTertiaryContainer = Color(0xFFC3E8FE),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF121316),
        onBackground = Color(0xFFE2E2E6),
        surface = Color(0xFF121316),
        onSurface = Color(0xFFE2E2E6),
        surfaceVariant = Color(0xFF42474E),
        onSurfaceVariant = Color(0xFFC2C7CF),
        outline = Color(0xFF8C9199),
        outlineVariant = Color(0xFF42474E),
        inverseSurface = Color(0xFFE2E2E6),
        inverseOnSurface = Color(0xFF2F3033),
        inversePrimary = Color(0xFF0062A0),
        surfaceDim = Color(0xFF121316),
        surfaceBright = Color(0xFF38393C),
        surfaceContainerLowest = Color(0xFF0C0E11),
        surfaceContainerLow = Color(0xFF1A1C1E),
        surfaceContainer = Color(0xFF1E2022),
        surfaceContainerHigh = Color(0xFF282A2D),
        surfaceContainerHighest = Color(0xFF333538),
    )

/** The app's colours for the given darkness: wallpaper colours with [dynamicColor], else its own blue. */
fun appColorScheme(
    context: Context,
    dark: Boolean,
    dynamicColor: Boolean,
): ColorScheme =
    when {
        // minSdk 31, so wallpaper colours are always available.
        dynamicColor ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

@Composable
fun RssReaderTheme(
    appearance: Appearance = Appearance(),
    content: @Composable () -> Unit,
) {
    val dark = appearance.isDark(isSystemInDarkTheme())
    SystemBarsFollowTheme(dark)
    MaterialTheme(
        colorScheme = appColorScheme(LocalContext.current, dark, appearance.dynamicColor),
        content = content,
    )
}

/** [RssReaderTheme] with the user's theme settings. Draws nothing until they're read (a few ms). */
@Composable
fun RssReaderAppTheme(content: @Composable () -> Unit) {
    val settings by LocalContext.current.rssReaderApp.settings.settings
        .collectAsStateWithLifecycle()
    val current = settings ?: return
    RssReaderTheme(appearance = current.appearance, content = content)
}
