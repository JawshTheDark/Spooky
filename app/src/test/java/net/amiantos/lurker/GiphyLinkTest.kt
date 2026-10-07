// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** GIPHY links become the plain direct .gif every client embeds. */
class GiphyLinkTest {
    private val direct = "https://media.giphy.com/media/3o7TKSjRrfIPjeiVyM/giphy.gif"

    @Test
    fun aSharePageBecomesTheGif() {
        assertEquals(direct, giphyDirectGif("https://giphy.com/gifs/cat-funny-3o7TKSjRrfIPjeiVyM"))
        assertEquals(direct, giphyDirectGif("https://giphy.com/gifs/3o7TKSjRrfIPjeiVyM"))
        assertEquals(direct, giphyDirectGif("https://giphy.com/stickers/hello-3o7TKSjRrfIPjeiVyM?utm_source=x"))
        assertEquals(direct, giphyDirectGif("https://giphy.com/embed/3o7TKSjRrfIPjeiVyM"))
    }

    @Test
    fun aKeyboardMediaLinkLosesItsTracking() {
        assertEquals(
            direct,
            giphyDirectGif("https://media2.giphy.com/media/v1.Y2lkPTc5MGI3NjEx/3o7TKSjRrfIPjeiVyM/giphy.gif?cid=abc&rid=giphy.gif&ct=g"),
        )
        assertEquals(direct, giphyDirectGif("https://media.giphy.com/media/3o7TKSjRrfIPjeiVyM/200w.webp"))
        assertEquals(direct, giphyDirectGif("https://i.giphy.com/3o7TKSjRrfIPjeiVyM.gif"))
    }

    @Test
    fun anythingElseIsLeftAlone() {
        assertNull(giphyDirectGif("https://giphy.com/"))
        assertNull(giphyDirectGif("https://giphy.com/search/cats"))
        assertNull(giphyDirectGif("https://media.tenor.com/abc/tenor.gif"))
        assertNull(giphyDirectGif("check this https://giphy.com/gifs/3o7TKSjRrfIPjeiVyM"))
        assertNull(giphyDirectGif("https://notgiphy.com/gifs/3o7TKSjRrfIPjeiVyM"))
    }
}
