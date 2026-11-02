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
    Bluesky("Bluesky"),
    Mastodon("Mastodon"),
    Threads("Threads"),
    Reddit("Reddit"),
    Twitch("Twitch"),
    GoogleMaps("Google Maps"),
    CodePen("CodePen"),
    Giphy("GIPHY"),
    X("X"),
    Instagram("Instagram"),

    /** A video file of the article's own site, played by the reader's own player. */
    Video(null),

    /** An iframe of another kind: its card opens it in the browser, it never plays inside. */
    Other(null),
}

/**
 * How big a player is: [height] in dp when it is fixed (audio players), else [aspect] (width ÷
 * height) for videos. A post (Bluesky, Mastodon, …) [fitsContent]: it takes the height of what it
 * shows once loaded, [height] until then (the web app keeps that height when the post can't say).
 */
data class EmbedSize(
    val aspect: Float = 16f / 9f,
    val height: Int? = null,
    val fitsContent: Boolean = false,
) {
    companion object {
        val VIDEO = EmbedSize()
        val VERTICAL = EmbedSize(aspect = 9f / 16f)

        fun fixed(height: Int) = EmbedSize(height = height)

        fun post(height: Int) = EmbedSize(height = height, fitsContent = true)
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
    private val bluesky = Regex("""^https://embed\.bsky\.app/embed/(did:[a-z0-9]+:[A-Za-z0-9._:%-]+)/app\.bsky\.feed\.post/([A-Za-z0-9]+)""")
    private val mastodon = Regex("""^https://([a-z0-9.-]+\.[a-z]{2,})/@([A-Za-z0-9_]+(?:@[a-z0-9.-]+)?)/(\d+)/embed""")
    private val threads = Regex("""^https://(?:www\.)?threads\.(?:net|com)/@([A-Za-z0-9._]+)/post/([A-Za-z0-9_-]+)/embed""")
    private val reddit = Regex("""^https://(?:embed\.reddit\.com|(?:www\.)?redditmedia\.com)/(r/[A-Za-z0-9_]+/comments/[A-Za-z0-9]+(?:/[^?#]*)?)""")
    private val twitchVideo = Regex("""^https://player\.twitch\.tv/\?""")
    private val twitchClip = Regex("""^https://clips\.twitch\.tv/embed\?""")
    private val googleMaps = Regex("""^https://(?:www\.|maps\.)?google\.[a-z.]+/maps/embed""")
    private val codePen = Regex("""^https://codepen\.io/([A-Za-z0-9_-]+)/embed/(?:preview/)?([A-Za-z0-9]+)""")
    private val giphy = Regex("""^https://giphy\.com/embed/([A-Za-z0-9]+)""")

    // Posts written as a quote that the service's script turns into the post (scripts never run in
    // the reader): the class and the attribute that name the post.
    private val postQuote = Regex("""<blockquote\b[^>]*\bclass\s*=\s*["'][^"']*\b(bluesky-embed|mastodon-embed|text-post-media|reddit-embed-bq)\b[^>]*>.*?</blockquote\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val blueskyUri = Regex("""data-bluesky-uri\s*=\s*["']at://(did:[^/"']+)/app\.bsky\.feed\.post/([A-Za-z0-9]+)["']""")
    private val mastodonEmbed = Regex("""data-embed-url\s*=\s*["'](https://[^"']+/embed)["']""")
    private val threadsPermalink = Regex("""data-text-post-permalink\s*=\s*["'](https://(?:www\.)?threads\.(?:net|com)/@[A-Za-z0-9._]+/post/[A-Za-z0-9_-]+)""")
    private val redditLink = Regex("""href\s*=\s*["']https://(?:www\.|old\.)?reddit\.com/(r/[A-Za-z0-9_]+/comments/[A-Za-z0-9]+(?:/[^"'?#]*)?)""")

    /**
     * [html] with each post written as a quote for the service's script (Bluesky, Mastodon,
     * Threads, Reddit) turned into an iframe of that post, so the reader shows it like the iframes
     * it knows. A quote it can't read stays a quote. The same as the web app's `postQuotesToFrames`.
     */
    fun postQuotesToFrames(html: String): String =
        postQuote.replace(html) { m ->
            val quote = m.value
            val src =
                when (m.groupValues[1].lowercase()) {
                    "bluesky-embed" -> blueskyUri.find(quote)?.let { "https://embed.bsky.app/embed/${it.groupValues[1]}/app.bsky.feed.post/${it.groupValues[2]}" }
                    "mastodon-embed" -> mastodonEmbed.find(quote)?.groupValues?.get(1)
                    "text-post-media" -> threadsPermalink.find(quote)?.let { it.groupValues[1] + "/embed" }
                    else -> redditLink.find(quote)?.let { "https://embed.reddit.com/${it.groupValues[1]}" }
                }
            if (src == null) quote else """<iframe src="${Html.escape(src)}"></iframe>"""
        }

    /**
     * The embed for an https address (already resolved), or [EmbedKind.Other]. [statedHeight] is
     * the height the site gives the iframe, used where the address doesn't say it (Bandcamp).
     */
    fun of(
        url: String,
        statedHeight: Int? = null,
    ): ArticleBlock.Embed {
        youTube.find(url)?.let {
            val id = it.groupValues[1]
            return ArticleBlock.Embed(
                EmbedKind.YouTube,
                "https://www.youtube.com/watch?v=$id",
                "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                player = "https://www.youtube-nocookie.com/embed/$id?rel=0&playsinline=1",
            )
        }
        vimeo.find(url)?.let {
            val id = it.groupValues[1]
            return ArticleBlock.Embed(EmbedKind.Vimeo, "https://vimeo.com/$id", null, player = "https://player.vimeo.com/video/$id")
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
                player = "https://www.dailymotion.com/embed/video/$id",
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
            return ArticleBlock.Embed(EmbedKind.Bandcamp, url, null, player = url, size = EmbedSize.fixed(statedHeight ?: bandcampHeight(url)))
        }
        tikTok.find(url)?.let {
            val id = it.groupValues[1]
            return ArticleBlock.Embed(EmbedKind.TikTok, "https://www.tiktok.com/embed/v2/$id", null, player = "https://www.tiktok.com/player/v1/$id", size = EmbedSize.VERTICAL)
        }
        bluesky.find(url)?.let {
            val (did, post) = it.destructured
            return ArticleBlock.Embed(EmbedKind.Bluesky, "https://bsky.app/profile/$did/post/$post", null, player = it.value, size = EmbedSize.post(400))
        }
        mastodon.find(url)?.let {
            val (host, user, id) = it.destructured
            return ArticleBlock.Embed(EmbedKind.Mastodon, "https://$host/@$user/$id", null, player = it.value, size = EmbedSize.post(400))
        }
        threads.find(url)?.let {
            val (user, id) = it.destructured
            return ArticleBlock.Embed(EmbedKind.Threads, "https://www.threads.net/@$user/post/$id", null, player = "https://www.threads.net/@$user/post/$id/embed", size = EmbedSize.post(550))
        }
        reddit.find(url)?.let {
            val path = it.groupValues[1].trimEnd('/')
            return ArticleBlock.Embed(EmbedKind.Reddit, "https://www.reddit.com/$path/", null, player = "https://embed.reddit.com/$path/?embed=true", size = EmbedSize.post(400))
        }
        if (twitchVideo.containsMatchIn(url) || twitchClip.containsMatchIn(url)) {
            val clip = query(url, "clip")
            val video = query(url, "video")
            val channel = query(url, "channel")
            val page =
                when {
                    clip != null -> "https://clips.twitch.tv/$clip"
                    video != null -> "https://www.twitch.tv/videos/${video.removePrefix("v")}"
                    channel != null -> "https://www.twitch.tv/$channel"
                    else -> "https://www.twitch.tv"
                }
            // Twitch only plays inside the page it is told about: Content's web app.
            val withoutParent = url.split('?', limit = 2).let { (start, rest) -> start + "?" + rest.split('&').filterNot { p -> p.startsWith("parent=") || p.startsWith("autoplay=") }.joinToString("&") }
            return ArticleBlock.Embed(EmbedKind.Twitch, page, null, player = "$withoutParent&parent=content.wim.to&autoplay=false")
        }
        if (googleMaps.containsMatchIn(url)) return ArticleBlock.Embed(EmbedKind.GoogleMaps, url, null, player = url, size = EmbedSize(aspect = 4f / 3f))
        codePen.find(url)?.let {
            val (user, pen) = it.destructured
            return ArticleBlock.Embed(EmbedKind.CodePen, "https://codepen.io/$user/pen/$pen", null, player = url, size = EmbedSize.fixed(400))
        }
        giphy.find(url)?.let {
            val id = it.groupValues[1]
            return ArticleBlock.Embed(EmbedKind.Giphy, "https://giphy.com/gifs/$id", null, player = "https://giphy.com/embed/$id", size = EmbedSize(aspect = 4f / 3f))
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
        // A player of a fixed height (Spotify, SoundCloud, Apple, Bandcamp) gets exactly that height,
        // on a see-through page: it draws its own rounded card, so no black shows around it.
        val fixed = embed.size.height?.takeIf { embed.kind != EmbedKind.Video }
        val content =
            if (embed.kind == EmbedKind.Video) {
                """<video src="$src" controls playsinline></video>"""
            } else {
                val height = fixed?.let { """ style="height:${it}px"""" }.orEmpty()
                """<iframe src="$src"$height allow="autoplay; encrypted-media; fullscreen; picture-in-picture; clipboard-write" allowfullscreen referrerpolicy="strict-origin-when-cross-origin"></iframe>"""
            }
        val background = if (fixed != null) "transparent" else "#000"
        return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<style>html,body{margin:0;height:100%;background:$background;overflow:hidden}iframe,video{display:block;border:0;width:100%;height:100%}</style>" +
            "</head><body>$content</body></html>"
    }

    /**
     * How a player is loaded (Android): every service's player as the WebView's page itself, with
     * Content's web app as its referrer ([PlayerLoad.Url]; YouTube refuses one without, error 153),
     * and a site's video file in a small page of the reader's own ([PlayerLoad.Page], from
     * [playerPage]). The audio players (Spotify, SoundCloud, Apple, Bandcamp) loaded inside an iframe
     * of a page of our own didn't load at all on Android (2026-11-02); the view loads them only once
     * it has its height, so they pick the right layout. Null without a player.
     */
    fun playerLoad(embed: ArticleBlock.Embed): PlayerLoad? {
        val player = embed.player ?: return null
        // Twitch checks that it plays inside Content's web app, so it needs a page around it.
        if (embed.kind != EmbedKind.Video && embed.kind != EmbedKind.Twitch) return PlayerLoad.Url(player)
        return playerPage(embed)?.let(PlayerLoad::Page)
    }

    /**
     * Script that measures a post's page (Android): the lowest edge of anything on it, in CSS
     * pixels (dp in the view). The view starts small, so a page that fills the view still grows.
     */
    const val MEASURE_SCRIPT =
        "(function(){var b=0,a=document.body?document.body.getElementsByTagName('*'):[];" +
            "for(var i=0;i<a.length;i++){var r=a[i].getBoundingClientRect();if(r.height>0&&r.bottom>b)b=r.bottom;}" +
            "return Math.ceil(b+(window.scrollY||0));})()"

    /** The height a post takes for what [MEASURE_SCRIPT] gave back, or null when it measured nothing. */
    fun measuredHeight(result: String?): Int? {
        val height = result?.trim('"')?.toDoubleOrNull()?.let { kotlin.math.ceil(it).toInt() } ?: return null
        if (height < MIN_POST_HEIGHT) return null
        return height.coerceAtMost(MAX_POST_HEIGHT)
    }

    /** How high a post is before it has been measured (Android), and the limits of what it may take. */
    const val MIN_POST_HEIGHT = 120
    const val MAX_POST_HEIGHT = 3000

    /** Whether a player draws its own card (a fixed height), shown on a see-through view rather than on black. */
    fun seeThrough(embed: ArticleBlock.Embed): Boolean = embed.kind != EmbedKind.Video && embed.size.height != null

    /**
     * How high Bandcamp's player is for its address, when the site doesn't say: the small one 42,
     * the large one 120 without its big artwork (artwork=small or none), else 470 with it.
     */
    private fun bandcampHeight(url: String): Int {
        val path = url.lowercase()
        return when {
            "size=large" !in path -> if ("size=small" in path) 42 else 120
            "artwork=small" in path || "artwork=none" in path -> 120
            else -> 470
        }
    }

    private val styleHeight = Regex("""(?:^|;)\s*height\s*:\s*(\d+)(?:\.\d+)?\s*px""", RegexOption.IGNORE_CASE)

    /**
     * The height in pixels a site gives an iframe, from its height attribute ("120" or "120px") or
     * its style ("height: 120px"), or null when it gives none in pixels (a percentage, nothing) or
     * one out of bounds. The same as the web app's `statedHeight`.
     */
    fun statedHeight(
        attribute: String?,
        style: String?,
    ): Int? {
        val fromStyle = style?.let { styleHeight.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        val fromAttribute = attribute?.trim()?.removeSuffix("px")?.trim()?.toIntOrNull()
        return (fromStyle ?: fromAttribute)?.takeIf { it in 40..1500 }
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
