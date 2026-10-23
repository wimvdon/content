package be.wimvandonck.reader.services

/**
 * What a service can keep of the reading list besides the core (#109). The core (link, title,
 * to read or archived, liked, tags) is what every reading-list service shares and is always synced.
 * Reader keeps everything itself; a feature a service lacks is just not copied there.
 */
enum class ServiceFeature {
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
    Offline,
    Refused,

    /** A plan's limit was reached (Instapaper: five highlights a month on a free account). */
    LimitReached,
}

/** A highlight as the service has it (#164): its id there, the passage, its note, which occurrence (from 0) and when it was made. */
data class RemoteHighlight(
    val remoteId: String,
    val text: String,
    val note: String = "",
    val position: Int = 0,
    val createdAt: Long = 0,
)

/**
 * A service that can keep copies of highlights (#164). Only copies: Content keeps its highlights
 * either way. [addHighlight] throws [ServiceProblem.LimitReached] when the plan allows no more.
 */
interface HighlightService {
    /** The article's highlights there. */
    suspend fun highlights(remoteId: String): List<RemoteHighlight>

    /** Makes a highlight of the [position]th occurrence (from 0) of [text] in the article [remoteId]. */
    suspend fun addHighlight(
        remoteId: String,
        text: String,
        note: String,
        position: Int,
    ): RemoteHighlight

    /** Deletes one; one already gone is fine. */
    suspend fun removeHighlight(highlightId: String)
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
    /** When it last changed there, in milliseconds; 0 when the service doesn't say (#145: the later change wins). */
    val changedAt: Long = 0,
    /**
     * In a row of what was last seen: true while it is in step with an article in Reader (in the
     * service's scope, #145), false for one seen there but not imported, or archived there when it
     * left the scope.
     */
    val linked: Boolean = true,
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
 * A service Saved can be connected to (#109, #145): Instapaper and Raindrop.io. Only
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

    /** The service's folders by name, for choosing what to import (#145). */
    suspend fun folders(): List<String>

    /**
     * Where each of Reader's folder names came from at the service, as an id there (#145): a
     * nested collection arrives flat ("Research / Media"), and this keeps the link to it, so filing
     * an article there later lands it in that collection. Read and set by [ServiceSync] around a
     * sync, so it holds across restarts.
     */
    var origins: Map<String, String>
}
