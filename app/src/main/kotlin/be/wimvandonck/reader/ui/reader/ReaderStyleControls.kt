package be.wimvandonck.reader.ui.reader

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.data.ReaderBackground
import be.wimvandonck.reader.ui.theme.SwitchSetting
import be.wimvandonck.reader.data.ReaderFont
import be.wimvandonck.reader.data.ReaderSpacing
import be.wimvandonck.reader.data.ReaderStyle

/**
 * Font, text size, spacing and page colour of the reader. Shown in the reader's Text sheet and
 * in Settings › Reader; every change applies right away.
 */
@Composable
fun ReaderStyleControls(
    style: ReaderStyle,
    onChange: (ReaderStyle) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
) {
    Column(modifier) {
    Column(Modifier.padding(horizontal = horizontalPadding, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Choice(
            label = stringResource(R.string.reader_font),
            options = ReaderFont.entries,
            selected = style.font,
            optionLabel = { stringResource(it.labelRes()) },
            optionStyle = { MaterialTheme.typography.labelLarge.copy(fontFamily = it.fontFamily()) },
            onSelect = { onChange(style.copy(font = it)) },
        )
        TextSizeSlider(style, onChange)
        Choice(
            label = stringResource(R.string.reader_spacing),
            options = ReaderSpacing.entries,
            selected = style.spacing,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = { onChange(style.copy(spacing = it)) },
        )
        Choice(
            label = stringResource(R.string.reader_background),
            options = ReaderBackground.entries,
            selected = style.background,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = { onChange(style.copy(background = it)) },
        )
    }
        // A list row brings its own 16dp side padding.
        Box(Modifier.padding(horizontal = horizontalPadding - 16.dp)) {
            SwitchSetting(
                title = stringResource(R.string.reader_auto_dark_title),
                summary = stringResource(R.string.reader_auto_dark_summary),
                checked = style.autoDark,
                onCheckedChange = { onChange(style.copy(autoDark = it)) },
            )
        }
    }
}

@Composable
private fun TextSizeSlider(
    style: ReaderStyle,
    onChange: (ReaderStyle) -> Unit,
) {
    val label = stringResource(R.string.reader_text_size)
    val value = stringResource(R.string.reader_text_size_value, style.textSize)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The two letters only show the size; the slider says it.
            Text(SAMPLE_LETTER, fontSize = 14.sp, fontFamily = style.font.fontFamily(), modifier = Modifier.clearAndSetSemantics {})
            Slider(
                value = style.textSize.toFloat(),
                onValueChange = { onChange(style.withTextSize(Math.round(it))) },
                valueRange = ReaderStyle.MIN_TEXT_SIZE.toFloat()..ReaderStyle.MAX_TEXT_SIZE.toFloat(),
                steps = ReaderStyle.MAX_TEXT_SIZE - ReaderStyle.MIN_TEXT_SIZE - 1,
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = label
                            stateDescription = value
                        },
            )
            Text(SAMPLE_LETTER, fontSize = 24.sp, fontFamily = style.font.fontFamily(), modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
private fun <T> Choice(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    optionStyle: @Composable (T) -> TextStyle = { MaterialTheme.typography.labelLarge },
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(optionLabel(option), style = optionStyle(option)) }
            }
        }
    }
}

/** Not translated: it only shows the letter size, like the "A" on a text size button. */
private const val SAMPLE_LETTER = "A"

@StringRes
fun ReaderFont.labelRes(): Int =
    when (this) {
        ReaderFont.Sans -> R.string.reader_font_sans
        ReaderFont.Serif -> R.string.reader_font_serif
    }

@StringRes
fun ReaderSpacing.labelRes(): Int =
    when (this) {
        ReaderSpacing.Compact -> R.string.reader_spacing_compact
        ReaderSpacing.Normal -> R.string.reader_spacing_normal
        ReaderSpacing.Relaxed -> R.string.reader_spacing_relaxed
    }

@StringRes
fun ReaderBackground.labelRes(): Int =
    when (this) {
        ReaderBackground.Default -> R.string.reader_background_default
        ReaderBackground.Sepia -> R.string.reader_background_sepia
        ReaderBackground.Dark -> R.string.reader_background_dark
    }
