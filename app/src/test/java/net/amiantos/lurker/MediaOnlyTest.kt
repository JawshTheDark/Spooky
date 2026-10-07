// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A message that's only a GIF/image link shows the picture instead of the link. */
class MediaOnlyTest {
    private val gif = "https://media.tenor.com/wP4KiKYK0aUAAAAM/bill-hicks-mic-tap.gif"

    @Test
    fun onlyLinksCount() {
        assertEquals(listOf(gif), linkOnlyUrls(gif))
        assertEquals(listOf(gif), linkOnlyUrls("  $gif  "))
        assertEquals(listOf(gif, gif), linkOnlyUrls("$gif $gif"))
        // Formatting codes don't count as text.
        assertEquals(listOf(gif), linkOnlyUrls("\u0002$gif\u0002"))
        assertNull(linkOnlyUrls("lol $gif"))
        assertNull(linkOnlyUrls("<$gif>")) // "don't unfurl this"
        assertNull(linkOnlyUrls(""))
    }

    @Test
    fun onDeviceImagesStandInForTheirLinks() {
        assertTrue(localImagesCoverText(gif))
        assertTrue(localImagesCoverText("https://i.imgur.com/a.png https://x.org/b.jpg"))
        // Video and audio keep their link; so do pages and anything with words.
        assertFalse(localImagesCoverText("https://x.org/clip.mp4"))
        assertFalse(localImagesCoverText("https://x.org/song.mp3"))
        assertFalse(localImagesCoverText("https://example.com/article"))
        assertFalse(localImagesCoverText("look $gif"))
        // Past the embed cap, some links wouldn't be drawn — keep the text.
        assertFalse(localImagesCoverText("https://a.org/1.gif https://a.org/2.gif https://a.org/3.gif https://a.org/4.gif"))
    }
}
