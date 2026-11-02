package to.wim.content.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs one message at a time in [scope]: a new one cancels the one before, whether it is still
 * showing or waiting its turn, so messages replace each other instead of queueing up. Each belongs
 * to the place (screen) it was shown on: moving to another one closes it ([movedTo], 2026-11-02),
 * and so does a tap elsewhere ([cancel]).
 */
class LatestOnly(
    private val scope: CoroutineScope,
) {
    private var job: Job? = null
    private var place: Any? = null

    @Synchronized
    fun launch(
        place: Any? = null,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        job?.cancel()
        this.place = place
        job = scope.launch(block = block)
    }

    /** Closes the message showing (or waiting), if any. */
    @Synchronized
    fun cancel() {
        job?.cancel()
        job = null
    }

    /** Now at [place]: a message shown on another place closes; one shown here stays. */
    @Synchronized
    fun movedTo(place: Any?) {
        if (job != null && this.place != place) cancel()
    }
}
