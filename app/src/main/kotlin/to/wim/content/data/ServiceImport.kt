package to.wim.content.data

import to.wim.content.sync.Json

/**
 * What was imported from a service when it was connected (#145): when, how many, and whether the
 * user chose to bring in everything it had or nothing. Asked once, when connecting; from then on
 * everything in Saved syncs both ways. Plain Kotlin, unit tested.
 */
data class ServiceImport(
    val at: Long,
    val count: Int,
    /** False when the user chose not to import: what the service had stays there until it changes. */
    val everything: Boolean = true,
) {
    companion object {
        /** As stored in the settings and the sync file (docs/sync.md). */
        fun encode(i: ServiceImport): String = Json.write(linkedMapOf("at" to i.at, "count" to i.count.toLong(), "everything" to i.everything))

        fun decode(text: String?): ServiceImport? {
            val m = text?.let { runCatching { Json.parse(it) }.getOrNull() } as? Map<*, *> ?: return null
            val at = (m["at"] as? Number)?.toLong() ?: return null
            return ServiceImport(at, (m["count"] as? Number)?.toInt() ?: 0, m["everything"] != false)
        }
    }
}

/** The import records as stored on the device: a JSON object by service id. */
object ServiceSettings {
    fun readImports(text: String?): Map<String, ServiceImport> {
        val m = text?.let { runCatching { Json.parse(it) }.getOrNull() } as? Map<*, *> ?: return emptyMap()
        return m.entries.mapNotNull { (k, v) -> (k as? String)?.let { id -> (v as? String)?.let(ServiceImport::decode)?.let { id to it } } }.toMap()
    }

    fun writeImports(imports: Map<String, ServiceImport>): String = Json.write(imports.mapValues { ServiceImport.encode(it.value) })
}
