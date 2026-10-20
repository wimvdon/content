package be.wimvandonck.reader.services

/**
 * What a service can keep of the reading list besides the core (#109). The core (link, title,
 * to read or archived, liked, tags) is what every reading-list service shares and is always synced.
 * Reader keeps everything itself; a feature a service lacks is just not copied there.
 */
enum class ServiceFeature {
    /** The service's own text of the article (Instapaper's parsed text, own account only). */
    Text,

    /** How far the article has been read. */
    Progress,

    /** Highlights and their notes (#102). */
    Highlights,

    /** The item's note (Raindrop keeps one per bookmark). */
    Notes,
}

/** Why a service call failed. A failure never loses anything in Reader; the change is tried again later. */
enum class ServiceProblem {
    /** The sign-in no longer works: sign in again. */
    SignedOut,

    /** Too many requests: try again later. That is normal, not an error to show. */
    RateLimited,

    /** The account's plan allows no more of this for now (Instapaper: five highlights a month without Premium). */
    LimitReached,
    Offline,
    Refused,
}

class ServiceException(
    val problem: ServiceProblem,
    cause: Throwable? = null,
) : Exception(problem.name, cause)

/**
 * A saved article as the service has it, reduced to the core: link, title, to read or archived, its
 * folder, liked and tags. Service extras are mapped onto it by the service.
 */
data class RemoteEntry(
    val remoteId: String,
    val url: String,
    val title: String,
    val description: String = "",
    val imageUrl: String? = null,
    /** When it was saved, in milliseconds. */
    val addedAt: Long,
    val archived: Boolean,
    val liked: Boolean,
    val tags: List<String>,
    /** The folder it is in (#119), or null for To read (and when archived). */
    val folder: String? = null,
    /** Its note, with [Notes][ServiceFeature.Notes]; null when the service keeps none. */
    val note: String? = null,
    /** 0 to 1, with [Progress][ServiceFeature.Progress]. */
    val progress: Float = 0f,
    /** A detail only the service uses, kept with what was last seen. */
    val extra: String? = null,
    /** Reader's highlights as copied there or taken from there (#102), by Reader's highlight id. */
    val highlights: Map<String, HighlightLink> = emptyMap(),
)

/** A highlight as the service has it. */
data class RemoteHighlight(
    val remoteId: String,
    val text: String,
    val note: String,
    /** Which occurrence of [text] in the service's text, counting from 0. */
    val position: Int,
    /** When it was made, in milliseconds. */
    val createdAt: Long,
)

/**
 * A Reader highlight and its copy at the service: the service's id and the note sent, or, when the
 * service refused it (a plan's limit), when that was, so it shows as "not in Instapaper" and is
 * tried again later.
 */
data class HighlightLink(
    val remoteId: String? = null,
    val note: String = "",
    val refusedAt: Long? = null,
)

/** What changed at the service since a cursor. */
data class RemoteChanges(
    val entries: List<RemoteEntry>,
    val deleted: List<String>,
    /** To give to the next [ReadingListService.changes]. */
    val cursor: String,
    /**
     * True when [entries] is everything the service has (a service without "changes since"): what
     * was there before and isn't now was deleted there.
     */
    val complete: Boolean = false,
)

/**
 * A service the reading list can be connected to (#109): Instapaper now, others later. Only
 * implementations know a service's specifics; [ServiceSync] keeps Reader's reading list and the
 * service the same through these calls. Tests use a fake.
 */
interface ReadingListService {
    /** A short, stable name ("instapaper"), used to store what was synced. */
    val id: String

    val features: Set<ServiceFeature>

    /**
     * Every change since [cursor] (null: everything there is). [full] is false for the quick sync
     * after a change here, when a service that can only list everything may skip listing.
     */
    suspend fun changes(
        cursor: String?,
        full: Boolean = true,
    ): RemoteChanges

    /** Saves a link (or brings back one the service already has) and returns it as the service has it now. */
    suspend fun add(
        url: String,
        title: String,
        tags: List<String>,
        archived: Boolean,
        folder: String? = null,
        note: String = "",
    ): RemoteEntry

    /** Applies the core fields of [want] that differ from [have] (the entry as last seen) and returns the entry afterwards. */
    suspend fun update(
        have: RemoteEntry,
        want: RemoteEntry,
    ): RemoteEntry

    /** Takes the article off the service's list. */
    suspend fun remove(remoteId: String)

    /** The service's text of the article as HTML, or null when it has none for this account. */
    suspend fun text(remoteId: String): String?

    /** The article's highlights at the service, with [Highlights][ServiceFeature.Highlights]. */
    suspend fun highlights(remoteId: String): List<RemoteHighlight> = emptyList()

    /** Makes a highlight there; [ServiceProblem.LimitReached] when the account may make no more now. */
    suspend fun addHighlight(
        remoteId: String,
        text: String,
        note: String,
        position: Int,
    ): RemoteHighlight = throw ServiceException(ServiceProblem.Refused)

    /** Deletes a highlight there; one already gone counts as deleted. */
    suspend fun removeHighlight(remoteId: String) = Unit
}
