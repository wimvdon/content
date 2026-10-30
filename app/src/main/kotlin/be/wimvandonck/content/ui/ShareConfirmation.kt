package be.wimvandonck.content.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import be.wimvandonck.content.R

/**
 * The message on the see-through screens over another app (saved, sent, feed added), after their
 * sheet or dialog closed: Content's own snackbar, so it shows whatever Android does with toasts. It
 * goes when it times out or the screen is tapped anywhere (but [actionLabel]), and then [onDone]
 * closes the screen, back to the other app.
 */
@Composable
fun ShareConfirmation(
    text: String,
    onDone: () -> Unit,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = null, indication = null, onClickLabel = stringResource(R.string.action_back), onClick = onDone)
            .safeDrawingPadding(),
        contentAlignment = Alignment.BottomCenter,
    ) { SnackbarHost(snackbar) }
    LaunchedEffect(text) {
        if (snackbar.showSnackbar(text, actionLabel = actionLabel, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) onAction()
        onDone()
    }
}
