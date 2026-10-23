package be.wimvandonck.content.data

import be.wimvandonck.content.sync.Json

/**
 * What a service and Reader share, both ways (#145): everything in Saved, or a set of folders, To
 * read counting as one ([TO_READ]). Archived articles are in no folder, so only Everything covers
 * them. Everything is the default, so nothing changes until someone says otherwise. Plain Kotlin,
 * unit tested.
 */
data class ServiceScope(
    /** The folders, case ignored, with [TO_READ] for To read; null for everything in Saved. */
    val folders: Set<String>? = null,
) {
    val everything: Boolean get() = folders == null

    /** Whether an item of Saved is in this scope. */
    fun covers(item: Item): Boolean {
        val reading = item.readingList ?: return false
        if (folders == null) return true
        if (reading.state != ReadingState.ToRead) return false
        return coversFolder(Saved.folderOf(item))
    }

    /** Whether an article in [folder] (null: To read) or archived would be in this scope. */
    fun coversPlace(
        folder: String?,
        archived: Boolean,
    ): Boolean = folders == null || (!archived && coversFolder(folder))

    private fun coversFolder(folder: String?): Boolean {
        val name = folder?.trim()?.ifEmpty { null } ?: TO_READ
        return folders.orEmpty().any { it.equals(name, ignoreCase = true) }
    }

    /** The scope with [folder] (null: To read) ticked or not. */
    fun with(
        folder: String?,
        on: Boolean,
    ): ServiceScope {
        val name = folder?.trim()?.ifEmpty { null } ?: TO_READ
        val now = folders.orEmpty().filterNot { it.equals(name, ignoreCase = true) }.toSet()
        return ServiceScope(if (on) now + name else now)
    }

    companion object {
        /** To read, as one of the scope's folders. */
        const val TO_READ = ""

        val Everything = ServiceScope()

        /** As stored in the sync file's settings (docs/sync.md): "*" for everything, else a JSON list. */
        fun encode(scope: ServiceScope): String = scope.folders?.let { Json.write(it.sortedBy(String::lowercase)) } ?: "*"

        fun decode(text: String?): ServiceScope? {
            if (text == null) return null
            if (text == "*") return Everything
            val list = runCatching { Json.parse(text) }.getOrNull() as? List<*> ?: return null
            return ServiceScope(list.filterIsInstance<String>().toSet())
        }
    }
}

/**
 * What was imported from a service when it was connected (#145): when, how many, and from where
 * (null [folders]: everything it had). Import is one step, done once; Import again repeats it.
 */
data class ServiceImport(
    val at: Long,
    val count: Int,
    /** The folders imported, with [ServiceScope.TO_READ] for To read; null for everything. */
    val folders: Set<String>? = null,
    /** Whether its archive was imported too (always with everything). */
    val archive: Boolean = true,
) {
    /** Whether an article there in [folder] (null: To read) or archived is one to bring in. */
    fun includes(
        folder: String?,
        archived: Boolean,
    ): Boolean {
        val chosen = folders ?: return true
        if (archived) return archive
        val name = folder?.trim()?.ifEmpty { null } ?: ServiceScope.TO_READ
        return chosen.any { it.equals(name, ignoreCase = true) }
    }

    companion object {
        fun encode(i: ServiceImport): String =
            Json.write(linkedMapOf("at" to i.at, "count" to i.count.toLong(), "folders" to i.folders?.sortedBy(String::lowercase), "archive" to i.archive))

        fun decode(text: String?): ServiceImport? {
            val m = text?.let { runCatching { Json.parse(it) }.getOrNull() } as? Map<*, *> ?: return null
            val at = (m["at"] as? Number)?.toLong() ?: return null
            return ServiceImport(at, (m["count"] as? Number)?.toInt() ?: 0, (m["folders"] as? List<*>)?.filterIsInstance<String>()?.toSet(), m["archive"] != false)
        }
    }
}

/** The scopes and import records as stored on the device: a JSON object by service id. */
object ServiceSettings {
    fun readScopes(text: String?): Map<String, ServiceScope> = readMap(text) { ServiceScope.decode(it) }

    fun writeScopes(scopes: Map<String, ServiceScope>): String = Json.write(scopes.mapValues { ServiceScope.encode(it.value) })

    fun readImports(text: String?): Map<String, ServiceImport> = readMap(text) { ServiceImport.decode(it) }

    fun writeImports(imports: Map<String, ServiceImport>): String = Json.write(imports.mapValues { ServiceImport.encode(it.value) })

    private fun <T : Any> readMap(
        text: String?,
        decode: (String) -> T?,
    ): Map<String, T> {
        val m = text?.let { runCatching { Json.parse(it) }.getOrNull() } as? Map<*, *> ?: return emptyMap()
        return m.entries.mapNotNull { (k, v) -> (k as? String)?.let { id -> (v as? String)?.let(decode)?.let { id to it } } }.toMap()
    }
}
