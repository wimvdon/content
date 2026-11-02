package to.wim.content.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs one message at a time in [scope]: a new one cancels the one before, whether it is still
 * showing or waiting its turn, so messages replace each other instead of queueing up.
 */
class LatestOnly(
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    @Synchronized
    fun launch(block: suspend CoroutineScope.() -> Unit) {
        job?.cancel()
        job = scope.launch(block = block)
    }
}
