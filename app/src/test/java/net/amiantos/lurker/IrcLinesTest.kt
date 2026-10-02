// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Test

/** Direct mode sends one IRC line per composer line; KICL rejects CR/LF/NUL. */
class IrcLinesTest {
    @Test
    fun splitsOnEveryKindOfLineBreak() {
        assertEquals(listOf("a", "b", "c", "d"), ircLines("a\nb\r\nc\rd"))
    }

    @Test
    fun aQuoteBecomesTwoLines() {
        assertEquals(listOf("> what they said", "my reply"), ircLines("> what they said\nmy reply"))
    }

    @Test
    fun blankLinesAndNulsAreDropped() {
        assertEquals(listOf("hi", "there"), ircLines("hi\n\n  \nthe\u0000re\n"))
        assertEquals(emptyList<String>(), ircLines(null))
    }
}
