// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Both invite shapes the server sends render as a readable line. */
class InviteLineTest {
    private fun line(json: String): String {
        val e = JSONObject(json)
        return inviteLine(eventNick(e), e)
    }

    @Test
    fun ourOwnInviteNamesWhoWasInvited() {
        assertEquals(
            "Jawsh invited d3fc0n",
            line("""{"type":"invite","target":"#all-out-war","nick":"Jawsh","invited":"d3fc0n"}"""),
        )
    }

    @Test
    fun anInviteToUsNamesTheInviterAndChannel() {
        assertEquals(
            "bob invited you to #room",
            line("""{"type":"invite","target":":server:","from":"bob","channel":"#room"}"""),
        )
    }
}
