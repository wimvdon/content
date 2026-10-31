package to.wim.content.data

/**
 * How far a saved article has been read, as a share of the text scrolled past (0 to 1), and where
 * to scroll back to on opening. The web app's `readingProgress.ts` does the same. Plain Kotlin,
 * unit tested.
 */
object ReadingPosition {
    /** Where the reader is: 0 at the top, 1 at the bottom; null when the page doesn't scroll. */
    fun of(
        scroll: Int,
        max: Int,
    ): Float? = if (max <= 0) null else (scroll.toFloat() / max).coerceIn(0f, 1f)

    /** Where to scroll back to, or null to start at the top: barely begun or (nearly) finished starts there. */
    fun scrollFor(
        progress: Float,
        max: Int,
    ): Int? = if (max <= 0 || progress < 0.02f || progress > 0.98f) null else Math.round(max * progress)

    /**
     * How long the page's height must stay the same before scrolling back (#270): pictures and the
     * full text arrive after the first frame, and a position worked out on a shorter page lands too
     * high, which leaving the article then stores as the new progress.
     */
    const val SETTLE_MS = 400L
}
