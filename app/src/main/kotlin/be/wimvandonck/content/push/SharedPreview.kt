package be.wimvandonck.content.push

/**
 * What a "shared" poke says about the share (#235), for the notification: the first article's title,
 * intro and picture, who shared it and how many articles. The feed loader puts it in the message
 * (worker/src/push.ts, `previewData`); an older one sends none, and the notification says only that
 * something was shared. Plain Kotlin, unit tested.
 */
data class SharedPreview(
    val title: String,
    val intro: String,
    val imageUrl: String?,
    val from: String,
    val count: Int,
) {
    companion object {
        /** The preview in a message's [data], or null when it has none. */
        fun of(data: Map<String, String>): SharedPreview? {
            val title = data["title"]?.trim().orEmpty()
            if (title.isEmpty()) return null
            return SharedPreview(
                title = title,
                intro = data["intro"]?.trim().orEmpty(),
                // Only a secure address, as for every picture the app loads.
                imageUrl = data["image"]?.trim()?.takeIf { it.startsWith("https://") },
                from = data["from"]?.trim().orEmpty(),
                count = data["count"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            )
        }
    }
}
