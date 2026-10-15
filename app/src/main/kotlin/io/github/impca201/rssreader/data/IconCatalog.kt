package io.github.impca201.rssreader.data

/**
 * The icons a folder can have: the Material Symbols set, read from the app's assets. Each icon
 * has a name ("sports-soccer"), search words (its name's words and its category) and the path
 * data to draw it (24 × 24).
 */
class IconCatalog(
    val icons: List<Icon>,
) {
    data class Icon(
        val name: String,
        val keywords: List<String>,
        val paths: List<String>,
    ) {
        /** The name as words, for screen readers: "sports soccer". */
        val label: String get() = name.replace('-', ' ')
    }

    private val byName = icons.associateBy { it.name }

    fun icon(name: String): Icon? = byName[name]

    /**
     * Icons matching what the user typed, best first: the exact name, names starting with it,
     * names whose words start with every typed word, then matches on category or related words.
     * With nothing typed: the suggested icons first, then all others.
     */
    fun search(query: String): List<Icon> {
        val words = query.lowercase().split(nonWord).filter { it.isNotEmpty() }
        if (words.isEmpty()) {
            val suggested = SUGGESTED.mapNotNull(byName::get)
            return suggested + icons.filterNot { it.name in SUGGESTED }
        }
        val joined = words.joinToString("-")
        return icons
            .mapNotNull { icon ->
                val nameWords = icon.name.split('-')
                val score =
                    when {
                        icon.name == joined -> 0
                        icon.name.startsWith(joined) -> 1
                        words.all { w -> nameWords.any { it.startsWith(w) } } -> 2
                        words.all { w -> nameWords.any { it.startsWith(w) } || icon.keywords.any { it.startsWith(w) } } -> 3
                        else -> return@mapNotNull null
                    }
                icon to score
            }.sortedWith(compareBy({ it.second }, { it.first.name.length }, { it.first.name }))
            .map { it.first }
    }

    companion object {
        const val DEFAULT = "folder"

        private val nonWord = Regex("[^\\p{L}\\p{N}]+")

        /** Shown first when nothing is searched: topics people often make folders for. */
        val SUGGESTED =
            listOf(
                "folder", "star", "favorite", "newspaper", "devices", "code", "science", "work", "payments",
                "sports-soccer", "sports-esports", "music-note", "movie", "menu-book", "photo-camera",
                "restaurant", "flight", "park", "home", "school", "palette", "public", "rocket-launch",
                "pets", "directions-car", "fitness-center", "local-cafe", "travel-explore", "lightbulb",
                "podcasts", "brush", "eco", "health-and-safety", "shopping-bag", "smart-toy", "rss-feed",
            )

        /** Extra search words for icons whose names miss common words. */
        private val RELATED =
            mapOf(
                "newspaper" to listOf("news", "articles", "press"),
                "devices" to listOf("tech", "technology", "gadgets"),
                "payments" to listOf("money", "finance", "economy"),
                "sports-esports" to listOf("games", "gaming", "videogames"),
                "menu-book" to listOf("books", "reading", "literature"),
                "restaurant" to listOf("food", "cooking", "recipes"),
                "flight" to listOf("travel", "plane"),
                "park" to listOf("nature", "outdoors", "trees"),
                "favorite" to listOf("heart", "love"),
                "public" to listOf("world", "globe", "international"),
                "smart-toy" to listOf("ai", "robot"),
                "health-and-safety" to listOf("health", "medicine"),
                "palette" to listOf("art", "design"),
                "rocket-launch" to listOf("space", "startup"),
            )

        /** Folder icons stored before this catalog existed, by their old names. */
        private val OLD_NAMES =
            mapOf(
                "Folder" to "folder", "Star" to "star", "Heart" to "favorite", "News" to "newspaper",
                "Tech" to "devices", "Code" to "code", "Science" to "science", "Work" to "work",
                "Money" to "payments", "Sports" to "sports-soccer", "Games" to "sports-esports",
                "Music" to "music-note", "Movies" to "movie", "Books" to "menu-book", "Photos" to "photo-camera",
                "Food" to "restaurant", "Travel" to "flight", "Nature" to "park", "Home" to "home",
            )

        /** A stored folder icon: today's names as they are, names from before translated, nothing → folder. */
        fun fromStored(value: String?): String =
            when {
                value.isNullOrBlank() -> DEFAULT
                else -> OLD_NAMES[value] ?: value
            }

        /** Reads the asset: `#` comment lines, then `name<TAB>categories<TAB>path|path` per icon. */
        fun parse(text: String): IconCatalog =
            IconCatalog(
                text
                    .lineSequence()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .mapNotNull { line ->
                        val fields = line.split('\t')
                        if (fields.size != 3 || fields[0].isEmpty() || fields[2].isEmpty()) return@mapNotNull null
                        val categories = fields[1].split(',').flatMap { it.split(nonWord) }.filter { it.isNotEmpty() }
                        Icon(
                            name = fields[0],
                            keywords = categories + RELATED[fields[0]].orEmpty(),
                            paths = fields[2].split('|'),
                        )
                    }.toList(),
            )
    }
}
