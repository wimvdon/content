package be.wimvandonck.content.data

import be.wimvandonck.content.sync.Json

/**
 * Which apps Content's share sheet offers first (#251). Android doesn't tell apps which share targets
 * someone uses most, so Content counts the picks in its own sheet (on this device only); apps never
 * picked follow, well-known messaging and mail apps first, then by name. Plain Kotlin, unit tested.
 */
object ShareApps {
    /** How many apps the sheet shows before "More". */
    const val SHOWN = 7

    /** Apps people often share links with, in this order, while nothing has been picked yet. */
    val POPULAR =
        listOf(
            "com.whatsapp",
            "org.thoughtcrime.securesms",
            "org.telegram.messenger",
            "com.google.android.apps.messaging",
            "com.google.android.gm",
            "com.facebook.orca",
            "com.instagram.android",
            "com.microsoft.office.outlook",
            "com.slack",
            "com.google.android.keep",
        )

    /** The id a pick is counted under: the app and, when it has several share targets, which one. */
    fun idOf(
        packageName: String,
        activityName: String?,
    ): String = if (activityName.isNullOrEmpty()) packageName else "$packageName/$activityName"

    /** [apps] in the order the sheet shows them: most picked first, then [POPULAR], then by label. */
    fun <T> rank(
        apps: List<T>,
        counts: Map<String, Int>,
        id: (T) -> String,
        label: (T) -> String,
    ): List<T> {
        fun popularity(app: T): Int {
            val i = POPULAR.indexOf(id(app).substringBefore('/'))
            return if (i < 0) POPULAR.size else i
        }
        return apps.sortedWith(
            compareByDescending<T> { counts[id(it)] ?: 0 }
                .thenBy { popularity(it) }
                .thenBy { label(it).lowercase() },
        )
    }

    /** [counts] with one more pick of [app]. */
    fun count(
        counts: Map<String, Int>,
        app: String,
    ): Map<String, Int> = counts + (app to (counts[app] ?: 0) + 1)

    fun encode(counts: Map<String, Int>): String = Json.write(counts.mapValues { it.value.toLong() })

    fun decode(text: String?): Map<String, Int> {
        val m = text?.let { runCatching { Json.parse(it) }.getOrNull() } as? Map<*, *> ?: return emptyMap()
        return m.entries.mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? Number)?.toInt()?.let { key to it } } }.toMap()
    }
}
