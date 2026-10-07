// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lurker 2.4: reactions, per-network /away, PREFIX-ordered members, image upload guards. */
class Lurker24Test {
    // ---- reactions -----------------------------------------------------------

    @Test
    fun reactionsGroupByValueInFirstReactedOrder() {
        val groups = groupReactions(
            listOf(
                Reaction("bob", "👍", self = false),
                Reaction("ann", "fyad", self = false),
                Reaction("me", "👍", self = true),
            ),
        )
        assertEquals(listOf("👍", "fyad"), groups.map { it.value })
        assertEquals(listOf("bob", "me"), groups[0].nicks)
        assertTrue(groups[0].mine)
        assertFalse(groups[1].mine)
        assertEquals(emptyList<ReactionGroup>(), groupReactions(null))
    }

    @Test
    fun aReactionIsShortSingleLineText() {
        assertTrue(isValidReaction("👍"))
        assertTrue(isValidReaction("fyad"))
        // A skin-toned, ZWJ-joined emoji is ONE grapheme, not seven code units.
        assertTrue(isValidReaction("👩🏽‍💻".repeat(64)))
        assertFalse(isValidReaction("a".repeat(65)))
        assertFalse(isValidReaction("  "))
        assertFalse(isValidReaction("two\nlines"))
    }

    // ---- /away (#994) -----------------------------------------------------------

    private fun away(input: String) =
        (Commands.parse(input, "#chan", hasNetwork = true) as ParsedInput.Ops).ops.single()

    @Test
    fun awayIsThisNetworkUnlessToldOtherwise() {
        assertEquals(WireOp("away", text = "brb", all = null), away("/away brb"))
        assertEquals(WireOp("away", text = "gone", all = true), away("/away -all gone"))
        assertEquals(WireOp("away", text = "lunch", all = false), away("/away -one lunch"))
        // An empty message is /back.
        assertEquals(WireOp("away", text = "", all = null), away("/away"))
        assertEquals(WireOp("away", text = "", all = null), away("/back"))
        assertEquals(WireOp("away", text = "", all = true), away("/back -all"))
    }

    // ---- members follow the network's PREFIX (#1032) -----------------------------

    @Test
    fun membersRankAndGlyphByTheNetworksOwnLadder() {
        // A ladder with a mode the fixed q/a/o/h/v table didn't know.
        val ladder = listOf('y' to '!', 'o' to '@', 'v' to '+')
        val oper = Member("root", listOf("y"))
        val op = Member("op", listOf("o", "v"))
        val plain = Member("pleb")
        assertEquals("!", oper.prefixFor(ladder))
        assertEquals("@", op.prefixFor(ladder))
        assertEquals("", plain.prefixFor(ladder))
        assertEquals(listOf("root", "op", "pleb"), listOf(plain, op, oper).sortedBy { it.rankFor(ladder) }.map { it.nick })
        // No ladder advertised: the old fixed table.
        assertEquals("@", op.prefixFor(null))
        assertEquals(op.rank, op.rankFor(null))
    }

    // ---- uploads: never flatten an animated PNG ---------------------------------

    private fun chunk(type: String, len: Int = 0): ByteArray =
        byteArrayOf(0, 0, (len shr 8).toByte(), len.toByte()) + type.toByteArray() + ByteArray(len) + ByteArray(4)

    private val sig = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)

    @Test
    fun anApngIsSpottedBeforeItsImageData() {
        assertTrue(isAnimatedPng(sig + chunk("IHDR", 13) + chunk("acTL", 8) + chunk("IDAT", 4)))
        assertFalse(isAnimatedPng(sig + chunk("IHDR", 13) + chunk("IDAT", 4) + chunk("acTL", 8)))
        assertFalse(isAnimatedPng(sig + chunk("IHDR", 13) + chunk("IDAT", 4)))
    }
}
