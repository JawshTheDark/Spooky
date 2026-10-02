// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A bouncer's reconnect replay must not append lines we already hold. */
class ReplayDedupeTest {
    private fun msg(
        id: Long, nick: String, text: String, time: String?, msgid: String? = null, self: Boolean = false,
    ) = Msg(id, "message", nick, text, self = self, time = time, msgid = msgid)

    @Test
    fun sameMsgidIsAReplay() {
        val held = listOf(msg(1, "bob", "hi", "2026-10-02T23:42:38.120Z", msgid = "abc"))
        assertTrue(isReplayDuplicate(held, msg(2, "bob", "hi", "2026-10-02T23:42:38.120Z", msgid = "abc")))
    }

    @Test
    fun sameServerTimeNickAndTextIsAReplayWithoutMsgid() {
        val held = listOf(msg(1, "bob", "hi", "2026-10-02T23:42:38.120Z"))
        assertTrue(isReplayDuplicate(held, msg(2, "Bob", "hi", "2026-10-02T23:42:38.120Z")))
    }

    @Test
    fun someoneRepeatingThemselvesIsKept() {
        val held = listOf(msg(1, "bob", "lol", "2026-10-02T23:42:38.120Z", msgid = "a"))
        assertFalse(isReplayDuplicate(held, msg(2, "bob", "lol", "2026-10-02T23:42:40.000Z", msgid = "b")))
        assertFalse(isReplayDuplicate(held, msg(3, "bob", "lol", "2026-10-02T23:42:41.000Z")))
    }

    @Test
    fun ourOwnLineReplayedMatchesTheCopyWeShowedAtSendTime() {
        // Optimistic copy stamped by our clock; the replay by the server's.
        val held = listOf(msg(1, "Jawsh", "brb", "2026-10-02T23:40:00.000Z", self = true))
        assertTrue(isReplayDuplicate(held, msg(2, "Jawsh", "brb", "2026-10-02T23:40:03.500Z", self = true)))
    }

    @Test
    fun untaggedLinesAreNeverDropped() {
        val held = listOf(msg(1, "bob", "hi", null))
        assertFalse(isReplayDuplicate(held, msg(2, "bob", "hi", null)))
    }
}
