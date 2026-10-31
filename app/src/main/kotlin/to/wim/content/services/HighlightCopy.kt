package to.wim.content.services

import to.wim.content.data.Anchors
import to.wim.content.data.Highlight
import to.wim.content.sync.Json

/**
 * What became of one highlight at a service: its copy there ([remoteId]) with the note it was last
 * seen or sent with, or when the service refused it for its plan's limit ([refusedAt]).
 */
data class HighlightLink(
    val remoteId: String? = null,
    val note: String = "",
    val refusedAt: Long? = null,
)

/**
 * Keeps an article's highlights the same in Content and a service (#164), when the user turned that
 * on. Both ways: a highlight made in the service's own apps comes into Content (placed by its passage
 * and which occurrence it is), one deleted there is deleted here, and a note changed there comes in;
 * Content's go there. One with the same passage on both sides is the same highlight (another device
 * may have sent it). A note changed here is sent as a new highlight (the old one deleted), since a
 * service can't edit one; a highlight deleted here is deleted there. A plan's limit keeps the
 * highlight in Content, marked as not copied, and it is tried again a day later. Plain Kotlin,
 * tested with a fake service.
 */
object HighlightCopy {
    /** A highlight refused for a plan's limit is tried again after this long. */
    const val RETRY_MS = 24 * 60 * 60 * 1000L

    /** Ids of highlights that came from a service start like this ("instapaper-501"). */
    fun idFor(
        service: String,
        remoteId: String,
    ) = "$service-$remoteId"

    /** Whether an article's highlights need sending: one new, with a changed note, deleted here, or due for a retry. */
    fun pending(
        highlights: List<Highlight>,
        links: Map<String, HighlightLink>,
        now: Long,
    ): Boolean {
        val ids = highlights.mapTo(HashSet()) { it.id }
        if (links.any { (id, link) -> id !in ids && link.remoteId != null }) return true
        return highlights.any { h ->
            val link = links[h.id]
            val refusedAt = link?.refusedAt
            when {
                link == null -> true
                link.remoteId != null -> link.note != h.note
                else -> refusedAt == null || now - refusedAt >= RETRY_MS
            }
        }
    }

    /** What [sync] left: the article's highlights and links, and why it stopped early (null when it got through). */
    data class Synced(
        val highlights: List<Highlight>,
        val links: Map<String, HighlightLink>,
        val problem: ServiceProblem?,
    )

    /**
     * Syncs the highlights of the article [remoteId] at [service]: what changed there comes in, then
     * what changed here goes there. Without [look], an article with nothing to send costs no call.
     */
    suspend fun sync(
        service: HighlightService,
        serviceId: String,
        remoteId: String,
        highlights: List<Highlight>,
        links: Map<String, HighlightLink>,
        now: Long,
        look: Boolean = false,
    ): Synced {
        if (!look && !pending(highlights, links, now)) return Synced(highlights, links, null)
        val remote =
            try {
                service.highlights(remoteId)
            } catch (e: ServiceException) {
                return Synced(highlights, links, e.problem)
            }
        val (taken, takenLinks) = takeIn(serviceId, highlights, links, remote)
        return push(service, remoteId, taken, takenLinks, now)
    }

    /**
     * The article's highlights and links after taking in what the service has: one deleted there goes
     * here too, a note changed there (and not here) comes in, one with the same passage as a highlight
     * here is linked to it, and the rest become Content's.
     */
    fun takeIn(
        serviceId: String,
        highlights: List<Highlight>,
        links: Map<String, HighlightLink>,
        remote: List<RemoteHighlight>,
    ): Pair<List<Highlight>, Map<String, HighlightLink>> {
        val byId = remote.associateBy { it.remoteId }
        val out = links.toMutableMap()
        // Deleted there: deleted here too.
        val gone = links.filterValues { it.remoteId != null && it.remoteId !in byId }.keys
        gone.forEach(out::remove)
        val result =
            highlights.filter { it.id !in gone }.mapTo(ArrayList()) { h ->
                val link = out[h.id]
                val there = link?.remoteId?.let(byId::get)
                // A note changed there and not here comes in.
                if (link != null && there != null && there.note.trim() != link.note && h.note == link.note) {
                    out[h.id] = link.copy(note = there.note.trim())
                    h.copy(note = there.note.trim())
                } else {
                    h
                }
            }
        val linked = out.values.mapNotNullTo(HashSet()) { it.remoteId }
        for (r in remote) {
            if (r.remoteId in linked || r.text.isBlank()) continue
            val text = Anchors.fold(r.text)
            val index = result.indexOfFirst { h -> out[h.id]?.remoteId == null && Anchors.fold(h.quote) == text }
            if (index >= 0) {
                // The same passage: the same highlight; a note only there comes in.
                val h = result[index]
                val note = h.note.ifBlank { r.note.trim() }
                result[index] = h.copy(note = note)
                out[h.id] = HighlightLink(r.remoteId, r.note.trim())
            } else {
                val made = Highlight(id = idFor(serviceId, r.remoteId), quote = r.text.trim(), occurrence = r.position, note = r.note.trim(), createdAt = r.createdAt)
                result += made
                out[made.id] = HighlightLink(r.remoteId, made.note)
            }
            linked += r.remoteId
        }
        return result to out
    }

    /** Sends what changed here: new ones made there, changed notes made again, deleted ones deleted there. */
    private suspend fun push(
        service: HighlightService,
        remoteId: String,
        highlights: List<Highlight>,
        links: Map<String, HighlightLink>,
        now: Long,
    ): Synced {
        val out = links.toMutableMap()
        try {
            val ids = highlights.mapTo(HashSet()) { it.id }
            for ((id, link) in links) {
                if (id in ids) continue
                link.remoteId?.let { service.removeHighlight(it) }
                out.remove(id)
            }
            var limitReached = false
            for (h in highlights.sortedBy { it.createdAt }) {
                val link = out[h.id]
                if (link?.remoteId != null && link.note == h.note) continue
                val refusedAt = link?.refusedAt
                if (link?.remoteId == null && refusedAt != null && (limitReached || now - refusedAt < RETRY_MS)) continue
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
                    val made = service.addHighlight(remoteId, h.quote, h.note, h.occurrence)
                    out[h.id] = HighlightLink(made.remoteId, h.note)
                } catch (e: ServiceException) {
                    if (e.problem != ServiceProblem.LimitReached) throw e
                    limitReached = true
                    out[h.id] = HighlightLink(note = h.note, refusedAt = now)
                }
            }
        } catch (e: ServiceException) {
            return Synced(highlights, out, e.problem)
        }
        return Synced(highlights, out, null)
    }

    /**
     * [current] (the item's highlights now) with what a sync changed from [before] to [after]: added,
     * removed and changed notes. A highlight changed here meanwhile keeps that change.
     */
    fun apply(
        current: List<Highlight>,
        before: List<Highlight>,
        after: List<Highlight>,
    ): List<Highlight> {
        val was = before.associateBy { it.id }
        val now = after.associateBy { it.id }
        val removed = was.keys - now.keys
        val result =
            current.filter { it.id !in removed }.map { h ->
                val b = was[h.id]
                val a = now[h.id]
                if (b != null && a != null && a.note != b.note && h.note == b.note) h.copy(note = a.note) else h
            }
        val have = result.mapTo(HashSet()) { it.id }
        return result + after.filter { it.id !in was && it.id !in have }
    }

    /** The highlights the service refused (shown as "Not in Instapaper"). */
    fun notCopied(links: Map<String, HighlightLink>): Set<String> = links.filterValues { it.remoteId == null && it.refusedAt != null }.keys

    /** Every article's links, by item key and highlight id, as stored text. */
    fun encode(links: Map<String, Map<String, HighlightLink>>): String =
        Json.write(
            links.filterValues { it.isNotEmpty() }.mapValues { (_, byId) ->
                byId.mapValues { (_, l) -> linkedMapOf<String, Any?>("id" to l.remoteId, "note" to l.note, "refusedAt" to l.refusedAt) }
            },
        )

    fun decode(text: String?): Map<String, Map<String, HighlightLink>> {
        val root = text?.takeIf { it.isNotEmpty() }?.let { runCatching { Json.parse(it) }.getOrNull() } as? Map<*, *> ?: return emptyMap()
        val out = HashMap<String, Map<String, HighlightLink>>()
        for ((key, value) in root) {
            if (key !is String || value !is Map<*, *>) continue
            val byId = HashMap<String, HighlightLink>()
            for ((id, link) in value) {
                if (id !is String || link !is Map<*, *>) continue
                byId[id] = HighlightLink(link["id"] as? String, link["note"] as? String ?: "", (link["refusedAt"] as? Number)?.toLong())
            }
            out[key] = byId
        }
        return out
    }
}
