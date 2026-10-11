// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lurker 2.4.3 splits canReact: adding, removing and replying are each their own answer. */
class TagSupportTest {
    @Test
    fun ircSoAddsButCannotTakeBack() {
        // UnrealIRCd with CLIENTTAGDENY=*,-draft/react,…,-draft/reply,-reply
        val t = TagSupport.parse(JSONObject("""{"canReact":false,"canAddReaction":true,"canRemoveReaction":false,"canReply":true}"""))
        assertEquals(TagSupport(addReaction = true, removeReaction = false, reply = true), t)
    }

    @Test
    fun aServerBefore243MeantAllOfThem() {
        assertEquals(TagSupport(true, true, null), TagSupport.parse(JSONObject("""{"canReact":true}""")))
        assertEquals(TagSupport(false, false, null), TagSupport.parse(JSONObject("""{"canReact":false}""")))
    }

    @Test
    fun nothingSaidIsNothingKnown() {
        assertNull(TagSupport.parse(JSONObject("""{"networkId":1,"state":"disconnected"}""")))
    }
}
