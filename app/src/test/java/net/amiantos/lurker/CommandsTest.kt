// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the pure composer parser against the wire ops it should emit. */
class CommandsTest {
    private fun ops(input: String, target: String = "#chan", net: Boolean = true) =
        (Commands.parse(input, target, net) as ParsedInput.Ops)

    @Test
    fun plainTextIsASend() {
        val r = ops("hello world")
        assertEquals(1, r.ops.size)
        assertEquals("send", r.ops[0].type)
        assertEquals("hello world", r.ops[0].text)
        assertEquals(null, r.ops[0].target) // current buffer
    }

    @Test
    fun doubleSlashEscapesToLiteralMessage() {
        val r = ops("//not a command")
        assertEquals("send", r.ops[0].type)
        assertEquals("/not a command", r.ops[0].text)
    }

    @Test
    fun meBecomesAction() {
        val r = ops("/me waves")
        assertEquals("action", r.ops[0].type)
        assertEquals("waves", r.ops[0].text)
    }

    @Test
    fun msgTargetsAnotherNick() {
        val r = ops("/msg bob hey there")
        assertEquals("send", r.ops[0].type)
        assertEquals("bob", r.ops[0].target)
        assertEquals("hey there", r.ops[0].text)
    }

    @Test
    fun queryOpensBufferAndOptionallySends() {
        val justOpen = ops("/query bob")
        assertEquals("bob", justOpen.openTarget)
        assertTrue(justOpen.ops.isEmpty())

        val openAndSend = ops("/query bob hi")
        assertEquals("bob", openAndSend.openTarget)
        assertEquals("send", openAndSend.ops[0].type)
        assertEquals("bob", openAndSend.ops[0].target)
    }

    @Test
    fun joinNormalizesChannelAndDoesNotOptimisticallyOpen() {
        val r = ops("/join lobby")
        assertEquals("join", r.ops[0].type)
        assertEquals("#lobby", r.ops[0].channel)
        // No openTarget: channels are pending until channel-joined (a 470 forward
        // could land us elsewhere), so we don't optimistically open the request.
        assertEquals(null, r.openTarget)
    }

    @Test
    fun joinWithKeyFallsBackToRaw() {
        val r = ops("/join #secret hunter2")
        assertEquals("raw", r.ops[0].type)
        assertEquals("JOIN #secret hunter2", r.ops[0].line)
    }

    @Test
    fun partDefaultsToCurrentChannel() {
        // /part closes the buffer: it emits a close-buffer op (which PARTs with the
        // reason server-side) targeting the current channel. /cycle keeps a bare part.
        val r = ops("/part later", target = "#dev")
        assertEquals("close", r.ops[0].type)
        assertEquals("#dev", r.ops[0].target)
        assertEquals("later", r.ops[0].reason)
    }

    @Test
    fun nickLowersToRaw() {
        assertEquals("NICK newname", ops("/nick newname").ops[0].line)
    }

    @Test
    fun kickUsesCurrentChannel() {
        val r = ops("/kick spammer be nice", target = "#room")
        assertEquals("raw", r.ops[0].type)
        assertEquals("KICK #room spammer :be nice", r.ops[0].line)
    }

    @Test
    fun opBuildsModeStringForEachNick() {
        val r = ops("/op alice bob", target = "#room")
        assertEquals("MODE #room +oo alice bob", r.ops[0].line)
    }

    @Test
    fun topicWithExplicitChannel() {
        val r = ops("/topic #other new topic here")
        assertEquals("TOPIC #other :new topic here", r.ops[0].line)
    }

    @Test
    fun cycleEmitsPartThenJoin() {
        val r = ops("/cycle brb", target = "#room")
        assertEquals(listOf("part", "join"), r.ops.map { it.type })
        assertEquals("#room", r.openTarget)
    }

    @Test
    fun unknownCommandIsAUsageError() {
        val r = Commands.parse("/frobnicate x", "#chan", true)
        assertTrue(r is ParsedInput.Local && r.isError)
    }

    @Test
    fun helpWorksWithoutANetwork() {
        val r = Commands.parse("/help", "#chan", hasNetwork = false)
        assertTrue(r is ParsedInput.Local && !r.isError)
    }

    @Test
    fun networkCommandWithoutNetworkErrors() {
        val r = Commands.parse("/nick x", "#chan", hasNetwork = false)
        assertTrue(r is ParsedInput.Local && r.isError)
    }

    @Test
    fun aliasExpandsPositionalAndChan() {
        val a = listOf(AliasEntry(1, "wave", "/me waves at \$1 in \$chan"))
        assertEquals("/me waves at bob in #lurker",
            Commands.expandAlias("/wave bob", a, "me", "#lurker"))
    }

    @Test
    fun aliasNoParamAppendsArgs() {
        val a = listOf(AliasEntry(1, "j", "/join"))
        assertEquals("/join #test", Commands.expandAlias("/j #test", a, "me", "#x"))
    }

    @Test
    fun nonAliasUnchanged() {
        assertEquals("/whois bob", Commands.expandAlias("/whois bob", emptyList(), "me", "#x"))
    }

    @Test
    fun listOpensBrowserWithNoFilter() {
        val r = Commands.parse("/list", "#chan", hasNetwork = true)
        assertTrue(r is ParsedInput.Browse && r.query == null)
    }

    @Test
    fun listCarriesFilter() {
        val r = Commands.parse("/list *linux*", "#chan", hasNetwork = true)
        assertTrue(r is ParsedInput.Browse && r.query == "*linux*")
    }

    @Test
    fun channelsIsAliasForList() {
        assertTrue(Commands.parse("/channels", "#chan", hasNetwork = true) is ParsedInput.Browse)
    }

    // ---- DCC CHAT (irssi syntax, mirroring the web client) ----------------

    private fun dcc(input: String) = Commands.parse(input, "#chan", hasNetwork = true)

    @Test
    fun dccChatOffersToANick() {
        assertEquals(ParsedInput.DccChat("bob", close = false, passive = false), dcc("/dcc chat bob"))
        assertEquals(ParsedInput.DccChat("bob", close = false, passive = true), dcc("/dcc chat -passive bob"))
    }

    @Test
    fun dccCloseIsTypeFirst() {
        assertEquals(ParsedInput.DccChat("bob", close = true), dcc("/dcc close chat bob"))
        // The old shorthand would have closed a chat with a peer named "chat".
        assertTrue(dcc("/dcc close bob") is ParsedInput.Local)
        // Read literally this would OFFER a chat to "close".
        assertTrue(dcc("/dcc chat close bob") is ParsedInput.Local)
    }

    @Test
    fun dccRefusesBufferNamesChannelsAndUnknownFlags() {
        assertTrue(dcc("/dcc chat =bob") is ParsedInput.Local)
        assertTrue(dcc("/dcc chat #room") is ParsedInput.Local)
        assertTrue(dcc("/dcc chat -active bob") is ParsedInput.Local)
        assertTrue(dcc("/dcc chat") is ParsedInput.Local)
    }

    @Test
    fun bareWhoisAndPingInADccChatTargetThePeer() {
        assertEquals("WHOIS bob", ops("/whois", target = "=bob").ops[0].line)
        assertEquals("PRIVMSG bob :PING", ops("/ping", target = "=bob").ops[0].line)
        // Never put the `=` buffer name on the IRC wire.
        assertEquals("PRIVMSG bob :VERSION", ops("/ctcp =bob version").ops[0].line)
    }
}
