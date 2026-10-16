// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The DCC overhaul on newer Lurker: per-account grants, kept files, and the
 * rule for who a DCC can go to. An older server sends none of this, so every
 * missing field has to land on a safe default.
 */
class DccOverhaulTest {
    @Test fun aPersonIsASinglePeer() {
        assertTrue(isSinglePeerTarget("bob"))
        assertTrue(isSinglePeerTarget("[away]bob"))
    }

    @Test fun channelsListsAndMasksAreNot() {
        for (t in listOf("", "#room", "&local", "@#room", "+#room", "bob,#x", "bob,alice", "\$\$*", "\$*.org", "a b")) {
            assertFalse(t, isSinglePeerTarget(t))
        }
    }

    @Test fun configReadsTheGrants() {
        val c = DccConfig.parse(
            JSONObject("""{"chat":true,"receive":false,"send":true,"maxFileBytes":1048576,"canListen":false,"usedBytes":512,"limitBytes":4096}"""),
        )
        assertEquals(DccConfig(chat = true, receive = false, send = true, maxFileBytes = 1048576, canListen = false, usedBytes = 512, limitBytes = 4096), c)
    }

    @Test fun configWithoutALimitHasNone() {
        assertNull(DccConfig.parse(JSONObject("""{"chat":true,"limitBytes":null}""")).limitBytes)
        val bare = DccConfig.parse(JSONObject("{}"))
        assertFalse(bare.chat || bare.receive || bare.send)
        assertNull(bare.limitBytes)
    }

    @Test fun keptFileParses() {
        val f = DccFile.parse(
            JSONObject("""{"id":7,"name":"notes.txt","size":42,"origin":"received","created_at":"2026-10-01T00:00:00Z","transfer_id":3,"peer_nick":"bob","network_id":2}"""),
        )
        assertEquals(DccFile(7, "notes.txt", 42, "received", "2026-10-01T00:00:00Z", "bob", 2), f)
    }

    @Test fun uploadedFileHasNoPeer() {
        val f = DccFile.parse(JSONObject("""{"id":8,"name":"a.zip","size":1,"origin":"uploaded","peer_nick":null,"network_id":null}"""))!!
        assertNull(f.peerNick)
        assertNull(f.networkId)
        assertNull(DccFile.parse(JSONObject("""{"name":"no id"}""")))
    }

    @Test fun savedNamesCantEscapeTheFolder() {
        assertEquals("passwd", ServerDownloads.safeName("../../etc/passwd"))
        assertEquals("evil.exe", ServerDownloads.safeName("C:\\evil.exe"))
        assertEquals("ab.txt", ServerDownloads.safeName("a<b>.txt"))
        assertEquals("hidden", ServerDownloads.safeName(".hidden"))
        assertEquals("file", ServerDownloads.safeName(""))
        assertEquals("file", ServerDownloads.safeName("..."))
        assertEquals(180, ServerDownloads.safeName("x".repeat(400)).length)
    }

    @Test fun stateLabelsReadPlainly() {
        assertEquals("waiting for you", dccStateLabel("pending_approval"))
        assertEquals("waiting for them", dccStateLabel("offered"))
        assertEquals("done", dccStateLabel("completed"))
        assertEquals("connecting", dccStateLabel("connecting"))
    }

    @Test fun anOfferedSendCanBeCancelledAndAFinishedOneCleared() {
        fun t(state: String) = DccTransfer(1, "bob", "f", state, 0, 10, "send", null)
        assertTrue(t("offered").isActive)
        assertFalse(t("offered").isFinished)
        assertTrue(t("rejected").isFinished)
    }
}
