package be.wimvandonck.reader.sync

/**
 * Where the sync file lives. Google Drive is the first; others (Dropbox, OneDrive, WebDAV) only
 * need to read and write one small file. See docs/sync.md.
 */
interface SyncProvider {
    /** The sync file's text, or null when there is none yet. */
    suspend fun read(): String?

    suspend fun write(text: String)
}

/** Why syncing failed. */
enum class SyncProblem {
    /** No connection, or the service didn't answer. */
    Offline,

    /** The user has to approve access (again). */
    NeedsApproval,

    /** The service refused or failed. */
    ServerError,

    /** The sync file is from a newer version of the app, or damaged; it is left alone. */
    UnreadableFile,

    /** Google doesn't know this build of the app (its package name and signing key), see docs/sync.md. */
    NotSetUp,
}

/** Google's status code for an app it doesn't know (CommonStatusCodes.DEVELOPER_ERROR). */
private const val DEVELOPER_ERROR = 10

/** Why Google's authorization failed, from its status code (null when it gave none). */
fun authorizationProblem(statusCode: Int?): SyncProblem = if (statusCode == DEVELOPER_ERROR) SyncProblem.NotSetUp else SyncProblem.Offline

class SyncException(
    val problem: SyncProblem,
    cause: Throwable? = null,
) : Exception(problem.name, cause)

/** The services the app can sync with. */
enum class SyncService { None, GoogleDrive }
