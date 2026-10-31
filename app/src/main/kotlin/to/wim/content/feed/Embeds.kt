package to.wim.content.feed

import java.net.URLDecoder

/**
 * The services whose embedded players and posts the reader knows. [label] is the service's
 * own name, never translated. A service with a player plays inside the article once the card is
 * tapped; X and Instagram posts need their sites' scripts, so their cards open the post instead.
 */
enum class EmbedKind(
    val label: String?,
) {
    YouTube("YouTube"),
    Vimeo("Vimeo"),
    Spotify("Spotify"),
    SoundCloud("SoundCloud"),
    Dailymotion("Dailymotion"),
    AppleMusic("Apple Music"),
    ApplePodcasts("Apple Podcasts"),
    Bandcamp("Bandcamp"),
    TikTok("TikTok"),
    X("X"),
    Instagram("Instagram"),

    /** A video file of the article's own site, played by the reader's own player. */
    Video(null),

    /** An iframe of another kind: its card opens it in the browser, it never plays inside. */
    Other(null),
}

/**
 * How big a player is: [height] in dp when it is fixed (audio players), else [aspect] (width ÷
 * height) for videos.
 */
data class EmbedSize(
    val aspect: Float = 16f / 9f,
    val height: Int? = null,
) {
    companion object {
        val VIDEO = EmbedSize()
        val VERTICAL = EmbedSize(aspect = 9f / 16f)

        fun fixed(height: Int) = EmbedSize(height = height)
    }
}

/**
 * Recognises embedded players by their address. Plain Kotlin, the same as the web app's
 * `embeds.ts`: [ArticleBlocks.embedFor] gives every iframe its card from here.
 */
object Embeds {
    private val youTube = Regex("""(?:youtube(?:-nocookie)?\.com/(?:embed/|watch\?v=|v/|shorts/|live/)|youtu\.be/)([A-Za-z0-9_-]{6,20})""")
    private val vimeo = Regex("""vimeo\.com/(?:video/)?(\d+)""")
    private val spotify = Regex("""open\.spotify\.com/(?:embed(?:-podcast)?/)?(?:intl-[a-z-]+/)?(track|album|playlist|episode|show|artist)/([A-Za-z0-9]+)""")
    private val dailymotion = Regex("""(?:dailymotion\.com/(?:embed/)?video/|dai\.ly/|dailymotion\.com/player\.html\?(?:.*&)?video=)([A-Za-z0-9]+)""")
    private val appleEmbed = Regex("""^https://embed\.(music|podcasts)\.apple\.com/(.+)$""")
    private val tikTok = Regex("""tiktok\.com/(?:embed/(?:v2/)?|player/v1/|@[^/]+/video/)(\d+)""")
    private val tweet = Regex("""(?:platform\.twitter\.com/embed/Tweet\.html\?(?:.*&)?id=|(?:twitter|x)\.com/[^/]+/status/)(\d+)""")
    private val instagram = Regex("""instagram\.com/(p|reel|tv)/([A-Za-z0-9_-]+)""")

    /** The embed for an https address (already resolved), or [EmbedKind.Other]. */
    fun of(url: String): ArticleBlock.Embed {
        youTube.find(url)?.let {
            val id = it.groupValues[1]
            return ArticleBlock.Embed(
                EmbedKind.YouTube,
                "https://www.youtube.com/watch?v=$id",
                "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                player = "https://www.youtube-nocookie.com/embed/$id?autoplay=1&rel=0&playsinline=1",
            )
        }
        vimeo.find(url)?.let {
            val id = it.groupValues[1]
            return ArticleBlock.Embed(EmbedKind.Vimeo, "https://vimeo.com/$id", null, player = "https://player.vimeo.com/video/$id?autoplay=1")
        }
        spotify.find(url)?.let {
            val (type, id) = it.destructured
            // Always Spotify's full player, with its cover: the compact one (152 high) is cramped on a phone.
            return ArticleBlock.Embed(
                EmbedKind.Spotify,
                "https://open.spotify.com/$type/$id",
                null,
                player = "https://open.spotify.com/embed/$type/$id",
                size = EmbedSize.fixed(352),
            )
        }
        if (url.startsWith("https://w.soundcloud.com/player/")) {
            val track = query(url, "url")
            val page = track?.takeIf { it.startsWith("https://soundcloud.com/") } ?: "https://soundcloud.com"
            val visual = query(url, "visual") == "true"
            return ArticleBlock.Embed(EmbedKind.SoundCloud, page, null, player = url, size = EmbedSize.fixed(if (visual) 300 else 166))
        }
        dailymotion.find(url)?.let {
            val id = it.groupValues[1]
            return ArticleBlock.Embed(
                EmbedKind.Dailymotion,
                "https://www.dailymotion.com/video/$id",
                "https://www.dailymotion.com/thumbnail/video/$id",
                player = "https://www.dailymotion.com/embed/video/$id?autoplay=1",
            )
        }
        appleEmbed.find(url)?.let {
            val (service, path) = it.destructured
            val kind = if (service == "music") EmbedKind.AppleMusic else EmbedKind.ApplePodcasts
            // One song or episode (?i=…) is a short player; an album, playlist or show a tall one.
            val one = query(url, "i") != null
            return ArticleBlock.Embed(kind, "https://$service.apple.com/$path", null, player = url, size = EmbedSize.fixed(if (one) 175 else 450))
        }
        if (url.startsWith("https://bandcamp.com/EmbeddedPlayer/")) {
            val large = "size=large" in url
            return ArticleBlock.Embed(EmbedKind.Bandcamp, url, null, player = url, size = EmbedSize.fixed(if (large) 470 else 120))
        }
        tikTok.find(url)?.let {
            val id = it.groupValues[1]
            return ArticleBlock.Embed(EmbedKind.TikTok, "https://www.tiktok.com/embed/v2/$id", null, player = "https://www.tiktok.com/player/v1/$id", size = EmbedSize.VERTICAL)
        }
        tweet.find(url)?.let { return ArticleBlock.Embed(EmbedKind.X, "https://x.com/i/status/${it.groupValues[1]}", null) }
        instagram.find(url)?.let {
            val (type, id) = it.destructured
            return ArticleBlock.Embed(EmbedKind.Instagram, "https://www.instagram.com/$type/$id/", null)
        }
        return ArticleBlock.Embed(EmbedKind.Other, url, null)
    }

    /** A video file of the site: it plays in the reader's own player, with its poster as preview. */
    fun video(
        url: String,
        poster: String?,
    ): ArticleBlock.Embed = ArticleBlock.Embed(EmbedKind.Video, url, poster, player = url)

    /**
     * The page a player is shown in: the service's player filling it, or the reader's own video
     * player for a video file. Loaded with [PLAYER_ORIGIN] as its address, so the services see
     * Content's web app as the page embedding them, as they require. Null without a player.
     */
    fun playerPage(embed: ArticleBlock.Embed): String? {
        val player = embed.player ?: return null
        val src = Html.escape(player)
        val content =
            if (embed.kind == EmbedKind.Video) {
                """<video src="$src" controls autoplay playsinline></video>"""
            } else {
                """<iframe src="$src" allow="autoplay; encrypted-media; fullscreen; picture-in-picture; clipboard-write" allowfullscreen referrerpolicy="strict-origin-when-cross-origin"></iframe>"""
            }
        return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}iframe,video{display:block;border:0;width:100%;height:100%}</style>" +
            "</head><body>$content</body></html>"
    }

    /**
     * How a player is loaded (Android): a service's player as the page itself, with Content's web
     * app as its referrer ([PlayerLoad.Url]); a site's video file in a small page of the reader's own
     * ([PlayerLoad.Page], from [playerPage]). Null without a player.
     */
    fun playerLoad(embed: ArticleBlock.Embed): PlayerLoad? {
        val player = embed.player ?: return null
        if (embed.kind != EmbedKind.Video) return PlayerLoad.Url(player)
        return playerPage(embed)?.let(PlayerLoad::Page)
    }

    /** The address player pages are loaded under, and the referrer players see: Content's web app. */
    const val PLAYER_ORIGIN = "https://content.wim.to/"

    /** One query parameter of [url], decoded, or null. */
    private fun query(
        url: String,
        name: String,
    ): String? =
        url
            .substringAfter('?', "")
            .split('&')
            .firstOrNull { it.substringBefore('=') == name }
            ?.substringAfter('=', "")
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
}

/** See [Embeds.playerLoad]. */
sealed interface PlayerLoad {
    data class Url(
        val url: String,
    ) : PlayerLoad

    data class Page(
        val html: String,
    ) : PlayerLoad
}
