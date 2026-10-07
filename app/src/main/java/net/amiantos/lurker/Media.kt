// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

/** What kind of media a URL points at, for inline-embed rendering. */
enum class MediaKind { IMAGE, VIDEO, AUDIO }

// Extension sets shared by the inline embeds and the full-screen viewer.
val IMAGE_EXTS = listOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".avif")
val VIDEO_EXTS = listOf(".mp4", ".webm", ".mkv", ".mov", ".m4v")
// .3gp/.3gpp are technically video containers, but in practice they're phone
// voice memos (AMR/AAC audio, no video track) — route them to the audio bar so
// they get a scrubber, not a black video box. .aac/.amr are plain audio.
val AUDIO_EXTS = listOf(".mp3", ".ogg", ".opus", ".flac", ".wav", ".m4a", ".3gp", ".3gpp", ".aac", ".amr")

/**
 * Classify a URL by media type, or null if it isn't obviously media. Mirrors the
 * web client's `uploadHostMatch.ts`: match a known extension at the end of the
 * path OR as a `<ext>/` segment — upload hosts commonly append a transform path
 * (`…/photo.jpg/large`). Query/fragment are stripped first.
 */
fun mediaKindForUrl(url: String): MediaKind? {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    fun hit(exts: List<String>) = exts.any { path.endsWith(it) || path.contains("$it/") }
    return when {
        hit(IMAGE_EXTS) -> MediaKind.IMAGE
        hit(VIDEO_EXTS) -> MediaKind.VIDEO
        hit(AUDIO_EXTS) -> MediaKind.AUDIO
        else -> null
    }
}

/**
 * A GIPHY link as its plain direct GIF, or null if [url] isn't one.
 *
 * GIPHY share links are web pages (`giphy.com/gifs/some-title-<id>`), which
 * don't embed; keyboard GIFs arrive as media links carrying tracking queries
 * (`media2.giphy.com/media/v1.…/<id>/giphy.gif?cid=…`). Both name the same file
 * by its id, so send `https://media.giphy.com/media/<id>/giphy.gif` — short,
 * untracked, and a .gif every client embeds.
 */
fun giphyDirectGif(url: String): String? {
    val u = url.trim()
    val page = Regex(
        """^https?://(?:www\.)?giphy\.com/(?:gifs|stickers|embed|clips)/(?:[^/?#]*-)?([A-Za-z0-9]{8,})/?(?:[?#].*)?$""",
    )
    val media = Regex(
        """^https?://(?:media\d*|i)\.giphy\.com/media/(?:v1\.[^/]+/)?([A-Za-z0-9]{8,})/[^?#]*\.(?:gif|webp|mp4)(?:[?#].*)?$""",
    )
    val short = Regex("""^https?://i\.giphy\.com/([A-Za-z0-9]{8,})\.(?:gif|webp)(?:[?#].*)?$""")
    val id = (page.find(u) ?: media.find(u) ?: short.find(u))?.groupValues?.get(1) ?: return null
    return "https://media.giphy.com/media/$id/giphy.gif"
}

/** The media URLs in a message body, capped so one paste can't flood a bubble. */
fun mediaUrlsIn(text: String, limit: Int = 3): List<Pair<String, MediaKind>> =
    Mirc.urls(text).mapNotNull { u -> mediaKindForUrl(u)?.let { u to it } }.take(limit)
