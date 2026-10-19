package be.wimvandonck.reader.services

import be.wimvandonck.reader.data.Anchors
import be.wimvandonck.reader.data.Highlight
import be.wimvandonck.reader.data.Item

/**
 * Highlights between Reader and a service (#102). Reader's highlights are its own data; a service
 * gets copies only when the user turned that on, and highlights made in the service's own apps are
 * taken in. A service that can't edit a highlight gets a changed note as a new highlight (the old
 * one deleted). A plan's limit keeps the highlight in Reader, marked as not at the service, and it
 * is tried again a day later. Plain Kotlin, tested with a fake service.
 */
object HighlightSync {
    /** A highlight refused for a plan's limit is tried again after this long. */
    const val RETRY_MS = 24 * 60 * 60 * 1000L

    /**
     * Takes in an article's highlights as the service has them: new ones become Reader's (placed by
     * their text and occurrence), one with the same passage as a highlight here is linked to it, and
     * one deleted there goes here too. Taken-in highlights get ids like "instapaper-501". Returns the item's highlights and the links afterwards.
     */
    fun takeIn(
        serviceId: String,
        item: Item,
        remote: List<RemoteHighlight>,
        links: Map<String, HighlightLink>,
    ): Pair<List<Highlight>, Map<String, HighlightLink>> {
        val remoteIds = remote.mapTo(HashSet()) { it.remoteId }
        val out = links.toMutableMap()
        val gone = links.filterValues { it.remoteId != null && it.remoteId !in remoteIds }.keys
        val highlights = item.highlights.filterTo(ArrayList()) { it.id !in gone }
        gone.forEach(out::remove)
        val linked = out.values.mapNotNullTo(HashSet()) { it.remoteId }
        for (r in remote) {
            if (r.remoteId in linked) continue
            val text = Anchors.fold(r.text)
            val same = highlights.firstOrNull { h -> out[h.id]?.remoteId == null && Anchors.fold(h.quote) == text }
            if (same != null) {
                out[same.id] = HighlightLink(r.remoteId, same.note)
                continue
            }
            val made = Highlight(id = "$serviceId-${r.remoteId}", quote = r.text.trim(), occurrence = r.position, note = r.note.trim(), createdAt = r.createdAt)
            highlights += made
            out[made.id] = HighlightLink(r.remoteId, made.note)
        }
        return highlights to out
    }

    /** What [push] left: the links, and why it stopped early (null when it got through). */
    data class Pushed(
        val links: Map<String, HighlightLink>,
        val problem: ServiceProblem?,
    )

    /**
     * Copies an article's highlights to the service: new ones are made there, ones with a changed
     * note are made again, ones deleted here are deleted there. [positionOf] says which occurrence
     * of its text a highlight is in the service's text.
     */
    suspend fun push(
        service: ReadingListService,
        remoteId: String,
        item: Item,
        links: Map<String, HighlightLink>,
        now: Long,
        positionOf: (Highlight) -> Int = { it.occurrence },
    ): Pushed {
        val out = links.toMutableMap()
        try {
            val ids = item.highlights.mapTo(HashSet()) { it.id }
            for ((id, link) in links) {
                if (id in ids) continue
                link.remoteId?.let { service.removeHighlight(it) }
                out.remove(id)
            }
            var limitReached = false
            for (h in item.highlights.sortedBy { it.createdAt }) {
                val link = out[h.id]
                if (link?.remoteId != null && link.note == h.note) continue
                val refusedAt = link?.refusedAt
                if (refusedAt != null && (limitReached || now - refusedAt < RETRY_MS)) continue
                if (limitReached) {
                    out[h.id] = HighlightLink(note = h.note, refusedAt = now)
                    continue
                }
                // A changed note: the service can't edit one, so the old copy goes and a new one is made.
                link?.remoteId?.let {
                    service.removeHighlight(it)
                    out.remove(h.id)
                }
                try {
                    val made = service.addHighlight(remoteId, h.quote, h.note, positionOf(h))
                    out[h.id] = HighlightLink(made.remoteId, h.note)
                } catch (e: ServiceException) {
                    if (e.problem != ServiceProblem.LimitReached) throw e
                    limitReached = true
                    out[h.id] = HighlightLink(note = h.note, refusedAt = now)
                }
            }
        } catch (e: ServiceException) {
            return Pushed(out, e.problem)
        }
        return Pushed(out, null)
    }

    /** The highlights of [item] the service refused (shown as "not in Instapaper"). */
    fun notCopied(links: Map<String, HighlightLink>): Set<String> = links.filterValues { it.remoteId == null && it.refusedAt != null }.keys
}
