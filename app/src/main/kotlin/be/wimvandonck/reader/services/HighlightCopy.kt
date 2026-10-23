package be.wimvandonck.reader.services

import be.wimvandonck.reader.data.Anchors
import be.wimvandonck.reader.data.Highlight
import be.wimvandonck.reader.sync.Json

/**
 * What became of one highlight at a service: its copy there ([remoteId]) with the note it was sent
 * with, or when the service refused it for its plan's limit ([refusedAt]).
 */
data class HighlightLink(
    val remoteId: String? = null,
    val note: String = "",
    val refusedAt: Long? = null,
)

/**
 * Copies Content's highlights to a service (#164), when the user turned that on. Content keeps
 * them either way; the service only gets copies. Before copying an article's highlights, the ones
 * the service already has are looked up, and one with the same passage counts as the copy (another
 * device may have sent it). A changed note is sent as a new highlight (the old one deleted), since
 * a service can't edit one; a highlight deleted here is deleted there. A plan's limit keeps the
 * highlight in Content, marked as not copied, and it is tried again a day later. Plain Kotlin,
 * tested with a fake service.
 */
object HighlightCopy {
    /** A highlight refused for a plan's limit is tried again after this long. */
    const val RETRY_MS = 24 * 60 * 60 * 1000L

    /** Whether an article's highlights need a call: one new, with a changed note, deleted here, or due for a retry. */
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

    /** What [push] left: the links, and why it stopped early (null when it got through). */
    data class Pushed(
        val links: Map<String, HighlightLink>,
        val problem: ServiceProblem?,
    )

    /** Copies [highlights] to the article [remoteId] at [service]; [links] is what was copied before. */
    suspend fun push(
        service: HighlightService,
        remoteId: String,
        highlights: List<Highlight>,
        links: Map<String, HighlightLink>,
        now: Long,
    ): Pushed {
        val out = links.toMutableMap()
        try {
            val ids = highlights.mapTo(HashSet()) { it.id }
            for ((id, link) in links) {
                if (id in ids) continue
                link.remoteId?.let { service.removeHighlight(it) }
                out.remove(id)
            }
            // Copies made before (by another device, or before this one forgot): the same passage is the copy.
            if (highlights.any { out[it.id]?.remoteId == null }) {
                val taken = out.values.mapNotNullTo(HashSet()) { it.remoteId }
                for (r in service.highlights(remoteId)) {
                    if (r.remoteId in taken) continue
                    val text = Anchors.fold(r.text)
                    val same = highlights.firstOrNull { h -> out[h.id]?.remoteId == null && Anchors.fold(h.quote) == text } ?: continue
                    out[same.id] = HighlightLink(r.remoteId, r.note.trim())
                    taken += r.remoteId
                }
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
            return Pushed(out, e.problem)
        }
        return Pushed(out, null)
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
