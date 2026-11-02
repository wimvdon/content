package to.wim.content.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleBlocksTest {
    private val base = "https://example.com/posts/1"

    @Test
    fun `splits text, pictures with captions and embeds in order`() {
        val blocks =
            ArticleBlocks.split(
                """
                <p>Intro</p>
                <figure><img src="/a.jpg" alt="A"><figcaption>The <b>caption</b></figcaption></figure>
                <p>Middle <img src="b.png"> text</p>
                <iframe src="https://www.youtube-nocookie.com/embed/abc123XYZ_-" allowfullscreen></iframe>
                <p>End</p>
                """.trimIndent(),
                base,
            )
        assertEquals(
            listOf(
                ArticleBlock.Text::class,
                ArticleBlock.Image::class,
                ArticleBlock.Text::class,
                ArticleBlock.Image::class,
                ArticleBlock.Text::class,
                ArticleBlock.Embed::class,
                ArticleBlock.Text::class,
            ),
            blocks.map { it::class },
        )
        assertEquals(ArticleBlock.Image("https://example.com/a.jpg", "The caption"), blocks[1])
        assertEquals(ArticleBlock.Image("https://example.com/posts/b.png", null), blocks[3])
        assertEquals(
            ArticleBlock.Embed(
                EmbedKind.YouTube,
                "https://www.youtube.com/watch?v=abc123XYZ_-",
                "https://i.ytimg.com/vi/abc123XYZ_-/hqdefault.jpg",
                player = "https://www.youtube-nocookie.com/embed/abc123XYZ_-?rel=0&playsinline=1",
            ),
            blocks[5],
        )
    }

    @Test
    fun `a caption written twice in a row shows once`() {
        val blocks = ArticleBlocks.split("<p>The bell. Credit: A. Photographer</p><p>The bell.  Credit: A. Photographer</p><p>Text</p>", base)
        assertEquals(listOf(ArticleBlock.Text("The bell. Credit: A. Photographer"), ArticleBlock.Text("Text")), blocks)
    }

    @Test
    fun `recognises Vimeo, plain videos and other embeds`() {
        assertEquals(EmbedKind.Vimeo, ArticleBlocks.embedFor("https://player.vimeo.com/video/76979871", base)?.kind)
        assertEquals("https://vimeo.com/76979871", ArticleBlocks.embedFor("https://player.vimeo.com/video/76979871", base)?.url)
        assertEquals("https://player.vimeo.com/video/76979871", ArticleBlocks.embedFor("https://player.vimeo.com/video/76979871", base)?.player)
        val other = ArticleBlocks.embedFor("http://maps.example.com/embed?x=1", base)!!
        // Unknown iframes never play inside: their card opens them (upgraded to https).
        assertEquals(ArticleBlock.Embed(EmbedKind.Other, "https://maps.example.com/embed?x=1", null), other)
        assertNull(ArticleBlocks.embedFor("javascript:alert(1)", base))
        val video = ArticleBlocks.split("<video poster=\"p.jpg\"><source src=\"v.mp4\"></video>", base).single()
        assertEquals(ArticleBlock.Embed(EmbedKind.Video, "https://example.com/posts/v.mp4", "https://example.com/posts/p.jpg", player = "https://example.com/posts/v.mp4"), video)
    }

    @Test
    fun `knows the major players, with the page to open and the player to load`() {
        fun embed(src: String) = ArticleBlocks.embedFor(src, base)!!
        val track = embed("//open.spotify.com/embed/track/4uLU6hMCjMI75M1A2tKUQC?utm_source=generator")
        assertEquals(EmbedKind.Spotify, track.kind)
        assertEquals("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC", track.url)
        assertEquals("https://open.spotify.com/embed/track/4uLU6hMCjMI75M1A2tKUQC", track.player)
        // Always the full player: the compact one is cramped.
        assertEquals(EmbedSize.fixed(352), track.size)
        assertEquals(EmbedSize.fixed(352), embed("https://open.spotify.com/embed/playlist/37i9dQZF1DXcBWIGoYBM5M").size)
        assertEquals("https://open.spotify.com/episode/abc", embed("https://open.spotify.com/embed-podcast/episode/abc").url)

        val cloud = embed("https://w.soundcloud.com/player/?url=https%3A//soundcloud.com/artist/song&color=%23ff5500")
        assertEquals(EmbedKind.SoundCloud, cloud.kind)
        assertEquals("https://soundcloud.com/artist/song", cloud.url)
        assertEquals("https://w.soundcloud.com/player/?url=https%3A//soundcloud.com/artist/song&color=%23ff5500", cloud.player)
        assertEquals(EmbedSize.fixed(166), cloud.size)

        val daily = embed("https://www.dailymotion.com/embed/video/x8abc12")
        assertEquals(EmbedKind.Dailymotion, daily.kind)
        assertEquals("https://www.dailymotion.com/video/x8abc12", daily.url)
        assertEquals("https://www.dailymotion.com/embed/video/x8abc12", daily.player)
        assertEquals("https://www.dailymotion.com/video/x8abc12", embed("https://geo.dailymotion.com/player.html?video=x8abc12").url)

        val song = embed("https://embed.music.apple.com/be/album/name/123?i=456")
        assertEquals(EmbedKind.AppleMusic, song.kind)
        assertEquals("https://music.apple.com/be/album/name/123?i=456", song.url)
        assertEquals(EmbedSize.fixed(175), song.size)
        assertEquals(EmbedSize.fixed(450), embed("https://embed.music.apple.com/be/album/name/123").size)
        assertEquals(EmbedKind.ApplePodcasts, embed("https://embed.podcasts.apple.com/us/podcast/show/id1").kind)

        assertEquals(EmbedKind.Bandcamp, embed("https://bandcamp.com/EmbeddedPlayer/album=1/size=large/").kind)
        val tikTok = embed("https://www.tiktok.com/embed/v2/7212345678901234567")
        assertEquals("https://www.tiktok.com/player/v1/7212345678901234567", tikTok.player)
        assertEquals(EmbedSize.VERTICAL, tikTok.size)
        assertEquals(EmbedKind.YouTube, embed("https://www.youtube.com/live/abcdefgh123").kind)

        // Posts need their sites' scripts: a card that opens the post, no player.
        val post = embed("https://platform.twitter.com/embed/Tweet.html?dnt=true&id=1234567890")
        assertEquals(ArticleBlock.Embed(EmbedKind.X, "https://x.com/i/status/1234567890", null), post)
        assertEquals(ArticleBlock.Embed(EmbedKind.Instagram, "https://www.instagram.com/p/Cabc_12/", null), embed("https://www.instagram.com/p/Cabc_12/embed/captioned"))
    }

    @Test
    fun `a player's page holds only the player, its address escaped`() {
        val page = Embeds.playerPage(ArticleBlock.Embed(EmbedKind.SoundCloud, "https://soundcloud.com/a", null, player = "https://w.soundcloud.com/player/?url=https%3A//soundcloud.com/a&x=\"><script>"))!!
        assertTrue(page.contains("<iframe src=\"https://w.soundcloud.com/player/?url=https%3A//soundcloud.com/a&amp;x=&quot;&gt;&lt;script&gt;\""))
        assertFalse(page.contains("<script>"))
        val video = Embeds.playerPage(Embeds.video("https://example.com/v.mp4", null))!!
        assertTrue(video.contains("<video src=\"https://example.com/v.mp4\" controls playsinline></video>"))
        assertNull(Embeds.playerPage(ArticleBlock.Embed(EmbedKind.Other, "https://example.com/", null)))
    }

    @Test
    fun `skips tracking pixels and picks the largest srcset picture`() {
        val blocks =
            ArticleBlocks.split(
                """<img src="https://feeds.feedburner.com/~r/x/~4/abc" width="1" height="1">""" +
                    """<img src="https://stats.wordpress.com/b.gif?x=1">""" +
                    """<img src="s.jpg" srcset="s.jpg 400w, l.jpg 1600w, m.jpg 800w">""" +
                    """<img data-src="lazy.jpg" src="data:image/gif;base64,R0lGOD">""",
                base,
            )
        assertEquals(
            listOf("https://example.com/posts/l.jpg", "https://example.com/posts/lazy.jpg"),
            blocks.map { (it as ArticleBlock.Image).url },
        )
    }

    @Test
    fun `Substack's pictures keep the commas in their addresses`() {
        // Substack (and other image servers) put commas in the address: they are no separators.
        val srcset = "https://substackcdn.com/image/fetch/\$s_!aB1c!,w_424,c_limit,f_auto,q_auto:good,fl_progressive:steep/https%3A%2F%2Fsubstack-post-media.s3.amazonaws.com%2Fpublic%2Fimages%2Fpic.png 424w, https://substackcdn.com/image/fetch/\$s_!aB1c!,w_1456,c_limit,f_auto,q_auto:good,fl_progressive:steep/https%3A%2F%2Fsubstack-post-media.s3.amazonaws.com%2Fpublic%2Fimages%2Fpic.png 1456w,https://substackcdn.com/image/fetch/\$s_!aB1c!,w_848,c_limit,f_auto,q_auto:good,fl_progressive:steep/https%3A%2F%2Fsubstack-post-media.s3.amazonaws.com%2Fpublic%2Fimages%2Fpic.png 848w"
        assertEquals(
            listOf("https://substackcdn.com/image/fetch/\$s_!aB1c!,w_424,c_limit,f_auto,q_auto:good,fl_progressive:steep/https%3A%2F%2Fsubstack-post-media.s3.amazonaws.com%2Fpublic%2Fimages%2Fpic.png" to "424w", "https://substackcdn.com/image/fetch/\$s_!aB1c!,w_1456,c_limit,f_auto,q_auto:good,fl_progressive:steep/https%3A%2F%2Fsubstack-post-media.s3.amazonaws.com%2Fpublic%2Fimages%2Fpic.png" to "1456w", "https://substackcdn.com/image/fetch/\$s_!aB1c!,w_848,c_limit,f_auto,q_auto:good,fl_progressive:steep/https%3A%2F%2Fsubstack-post-media.s3.amazonaws.com%2Fpublic%2Fimages%2Fpic.png" to "848w"),
            ArticleBlocks.srcsetCandidates(srcset),
        )
        val html = """<figure><a href="https://substackcdn.com/image/fetch/${'$'}s_!aB1c!,w_1456,c_limit,f_auto,q_auto:good,fl_progressive:steep/https%3A%2F%2Fsubstack-post-media.s3.amazonaws.com%2Fpublic%2Fimages%2Fpic.png"><picture><source type="image/webp" srcset="$srcset"><img src="https://substackcdn.com/image/fetch/${'$'}s_!aB1c!,w_848,c_limit,f_auto,q_auto:good,fl_progressive:steep/https%3A%2F%2Fsubstack-post-media.s3.amazonaws.com%2Fpublic%2Fimages%2Fpic.png" srcset="$srcset" width="1456"></picture></a><figcaption>A chart</figcaption></figure>"""
        assertEquals(listOf(ArticleBlock.Image("https://substackcdn.com/image/fetch/\$s_!aB1c!,w_1456,c_limit,f_auto,q_auto:good,fl_progressive:steep/https%3A%2F%2Fsubstack-post-media.s3.amazonaws.com%2Fpublic%2Fimages%2Fpic.png", "A chart")), ArticleBlocks.split(html, base))
        // A comma right after an address ends it; a candidate without descriptor counts as 1x.
        assertEquals(listOf("a.jpg" to "", "b.jpg" to "2x"), ArticleBlocks.srcsetCandidates("a.jpg, b.jpg 2x"))
        assertEquals(listOf("a,b.jpg" to ""), ArticleBlocks.srcsetCandidates(" a,b.jpg,, "))
        assertEquals(emptyList<Pair<String, String>>(), ArticleBlocks.srcsetCandidates(null))
    }

    @Test
    fun `picture addresses become absolute https`() {
        assertEquals("https://cdn.example.com/a.jpg", ArticleBlocks.imageUrl("http://cdn.example.com/a.jpg", base))
        assertEquals("https://cdn.example.com/a.jpg", ArticleBlocks.imageUrl("//cdn.example.com/a.jpg", base))
        assertEquals("https://example.com/img/a.jpg", ArticleBlocks.imageUrl("/img/a.jpg", base))
        assertNull(ArticleBlocks.imageUrl("data:image/png;base64,xx", base))
        assertNull(ArticleBlocks.imageUrl("ftp://example.com/a.jpg", base))
        assertNull(ArticleBlocks.imageUrl("  ", base))
    }

    @Test
    fun `finds the first real picture`() {
        assertEquals(
            "https://example.com/posts/real.jpg",
            ArticleBlocks.firstImage("<img src=\"px.gif\" width=\"1\"><p>x</p><img src=\"real.jpg\">", base),
        )
        assertNull(ArticleBlocks.firstImage("<p>No pictures</p>", base))
    }

    @Test
    fun `the same picture in another size counts as the same`() {
        assertTrue(ArticleBlocks.isSameImage("https://a.com/wp/photo-1024x768.jpg", "https://a.com/wp/photo.jpg?w=2000"))
        assertTrue(ArticleBlocks.isSameImage("https://a.com/x/photo@2x.png", "https://cdn.a.com/photo.png"))
        assertFalse(ArticleBlocks.isSameImage("https://a.com/one.jpg", "https://a.com/two.jpg"))
        assertFalse(ArticleBlocks.isSameImage(null, "https://a.com/one.jpg"))
    }

    @Test
    fun `text without content is dropped, and a tracking pixel doesn't split a paragraph`() {
        val blocks = ArticleBlocks.split("<p>One</p><img src=\"https://x.com/1x1.gif\" width=\"1\"><p>Two and <img src=\"https://x.com/p.gif\" height=\"1\">three</p><p>   </p>", base)
        assertEquals(listOf(ArticleBlock.Text("One"), ArticleBlock.Text("Two and three")), blocks)
    }

    @Test
    fun `a video service's player loads as the page itself, a site's video in a page of its own`() {
        // YouTube refuses a player inside a page without an address (error 153): it loads directly.
        val youTube = ArticleBlocks.embedFor("https://www.youtube.com/embed/abc123XYZ_-", base)!!
        assertEquals(PlayerLoad.Url("https://www.youtube-nocookie.com/embed/abc123XYZ_-?rel=0&playsinline=1"), Embeds.playerLoad(youTube))
        val video = Embeds.playerLoad(Embeds.video("https://example.com/v.mp4", null))
        assertTrue(video is PlayerLoad.Page && video.html.contains("<video src=\"https://example.com/v.mp4\""))
        assertNull(Embeds.playerLoad(ArticleBlock.Embed(EmbedKind.X, "https://x.com/i/status/1", null)))
        assertEquals("https://content.wim.to/", Embeds.PLAYER_ORIGIN)
    }

    @Test
    fun `an audio player loads directly too, on a see-through view`() {
        // Inside an iframe of a page of our own, Spotify, SoundCloud and Bandcamp didn't load on Android.
        val spotify = Embeds.of("https://open.spotify.com/embed/episode/abc123")
        assertEquals(PlayerLoad.Url("https://open.spotify.com/embed/episode/abc123"), Embeds.playerLoad(spotify))
        assertTrue(Embeds.seeThrough(spotify))
        val soundCloud = Embeds.of("https://w.soundcloud.com/player/?url=https%3A//api.soundcloud.com/tracks/1&visual=true")
        assertEquals(PlayerLoad.Url("https://w.soundcloud.com/player/?url=https%3A//api.soundcloud.com/tracks/1&visual=true"), Embeds.playerLoad(soundCloud))
        assertTrue(Embeds.seeThrough(soundCloud))
        val bandcamp = Embeds.of("https://bandcamp.com/EmbeddedPlayer/album=123/size=large/bgcol=ffffff/linkcol=0687f5/transparent=true/")
        assertEquals(PlayerLoad.Url("https://bandcamp.com/EmbeddedPlayer/album=123/size=large/bgcol=ffffff/linkcol=0687f5/transparent=true/"), Embeds.playerLoad(bandcamp))
        assertTrue(Embeds.seeThrough(bandcamp))
        // Videos keep their black view, and the reader's own video player its black page.
        val youTube = Embeds.of("https://www.youtube.com/embed/abc123XYZ_-")
        assertFalse(Embeds.seeThrough(youTube))
        assertFalse(Embeds.seeThrough(Embeds.video("https://example.com/v.mp4", null)))
        assertTrue((Embeds.playerLoad(Embeds.video("https://example.com/v.mp4", null)) as PlayerLoad.Page).html.contains("background:#000"))
    }

    @Test
    fun `posts, maps, pens and GIFs of the common services play inside`() {
        val bluesky = Embeds.of("https://embed.bsky.app/embed/did:plc:abc123/app.bsky.feed.post/3kxyz")
        assertEquals(EmbedKind.Bluesky, bluesky.kind)
        assertEquals("https://bsky.app/profile/did:plc:abc123/post/3kxyz", bluesky.url)
        assertEquals("https://embed.bsky.app/embed/did:plc:abc123/app.bsky.feed.post/3kxyz", bluesky.player)
        assertTrue(bluesky.size.fitsContent)
        val mastodon = Embeds.of("https://mastodon.social/@Gargron/112345678901234567/embed")
        assertEquals(EmbedKind.Mastodon, mastodon.kind)
        assertEquals("https://mastodon.social/@Gargron/112345678901234567", mastodon.url)
        assertTrue(mastodon.size.fitsContent)
        val threads = Embeds.of("https://www.threads.com/@zuck/post/C8abc_1/embed")
        assertEquals(EmbedKind.Threads, threads.kind)
        assertEquals("https://www.threads.net/@zuck/post/C8abc_1", threads.url)
        val reddit = Embeds.of("https://embed.reddit.com/r/Android/comments/1abcde/some_title/?utm_source=embedv2")
        assertEquals(EmbedKind.Reddit, reddit.kind)
        assertEquals("https://www.reddit.com/r/Android/comments/1abcde/some_title/", reddit.url)
        assertEquals("https://embed.reddit.com/r/Android/comments/1abcde/some_title/?embed=true", reddit.player)
        // Twitch is told it plays inside Content's web app, and doesn't start by itself.
        val twitch = Embeds.of("https://player.twitch.tv/?video=v123456&parent=example.com&autoplay=true")
        assertEquals(EmbedKind.Twitch, twitch.kind)
        assertEquals("https://www.twitch.tv/videos/123456", twitch.url)
        assertEquals("https://player.twitch.tv/?video=v123456&parent=content.wim.to&autoplay=false", twitch.player)
        assertTrue(Embeds.playerLoad(twitch) is PlayerLoad.Page)
        val clip = Embeds.of("https://clips.twitch.tv/embed?clip=FunnyClip&parent=example.com")
        assertEquals("https://clips.twitch.tv/FunnyClip", clip.url)
        val map = Embeds.of("https://www.google.com/maps/embed?pb=!1m18!1m12")
        assertEquals(EmbedKind.GoogleMaps, map.kind)
        assertEquals("https://www.google.com/maps/embed?pb=!1m18!1m12", map.player)
        val pen = Embeds.of("https://codepen.io/someone/embed/preview/abcDEF?default-tab=result")
        assertEquals(EmbedKind.CodePen, pen.kind)
        assertEquals("https://codepen.io/someone/pen/abcDEF", pen.url)
        assertEquals(400, pen.size.height)
        val gif = Embeds.of("https://giphy.com/embed/xT9IgG50Fb7Mi0prBC")
        assertEquals(EmbedKind.Giphy, gif.kind)
        assertEquals("https://giphy.com/gifs/xT9IgG50Fb7Mi0prBC", gif.url)
        // An address that only looks like one stays an unknown embed.
        assertEquals(EmbedKind.Other, Embeds.of("https://example.com/@me/123/other").kind)
    }

    @Test
    fun `posts written for a service's script become their players`() {
        val html =
            "<p>Before</p>" +
                "<blockquote class=\"bluesky-embed\" data-bluesky-uri=\"at://did:plc:abc123/app.bsky.feed.post/3kxyz\" data-bluesky-cid=\"x\"><p>Hello</p></blockquote>" +
                "<blockquote class=\"mastodon-embed\" data-embed-url=\"https://mastodon.social/@Gargron/1123/embed\"><a href=\"https://mastodon.social/@Gargron/1123\">Post</a></blockquote>" +
                "<blockquote class=\"text-post-media\" data-text-post-permalink=\"https://www.threads.net/@zuck/post/C8abc\" data-text-post-version=\"0\"><a href=\"x\">Post</a></blockquote>" +
                "<blockquote class=\"reddit-embed-bq\" data-embed-height=\"500\"><a href=\"https://www.reddit.com/r/Android/comments/1abcde/some_title/\">Title</a></blockquote>" +
                "<blockquote class=\"bluesky-embed\"><p>No address</p></blockquote>" +
                "<blockquote><p>A plain quote</p></blockquote>"
        val blocks = ArticleBlocks.split(html, base)
        val kinds = blocks.filterIsInstance<ArticleBlock.Embed>().map { it.kind }
        assertEquals(listOf(EmbedKind.Bluesky, EmbedKind.Mastodon, EmbedKind.Threads, EmbedKind.Reddit), kinds)
        // A quote it can't read, and a plain quote, stay quotes.
        val text = blocks.filterIsInstance<ArticleBlock.Quote>().flatMap { it.paragraphs }.joinToString(" ")
        assertTrue(text.contains("No address"))
        assertTrue(text.contains("A plain quote"))
    }

    @Test
    fun `a post's measured height is kept within bounds`() {
        assertEquals(480, Embeds.measuredHeight("480"))
        assertEquals(481, Embeds.measuredHeight("480.6"))
        assertNull(Embeds.measuredHeight("0"))
        assertNull(Embeds.measuredHeight("null"))
        assertNull(Embeds.measuredHeight(null))
        assertEquals(Embeds.MAX_POST_HEIGHT, Embeds.measuredHeight("99999"))
    }
}
