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
 * Lurker 2.6: relay/bridge bots (the cases are Lurker's own parseRelay tests,
 * so a mark reads lines the same way here as on the web), /relay, /accept, the
 * metadata commands, Copy quote and metadata records.
 */
class Lurker26Test {
    private fun p(source: String?, nick: String, text: String) = RelayParse(source, nick, text)

    // ---- Envelopes --------------------------------------------------------------

    @Test fun builtInFormats() {
        assertEquals(p("Discord", "alice", "hello there"), Relay.parse("[Discord] <alice> hello there"))
        assertEquals(p(null, "bob", "hey everyone"), Relay.parse("<bob> hey everyone"))
        assertEquals(p("Telegram", "carol", "2 < 3 and [maybe] > nope"), Relay.parse("[Telegram] <carol> 2 < 3 and [maybe] > nope"))
        assertEquals(p("IRC", "dave", ""), Relay.parse("[IRC] <dave> "))
        assertEquals(p("Game Chat", "eve", "gg"), Relay.parse("[Game Chat] <eve> gg"))
    }

    @Test fun notAnEnvelope() {
        assertNull(Relay.parse("just a normal line from the bot"))
        assertNull(Relay.parse("lunch < later"))
        assertNull(Relay.parse(""))
        assertNull(Relay.parse(null))
    }

    @Test fun membershipPrefixesComeOffTheNick() {
        assertEquals(p(null, "bob", "hi"), Relay.parse("<+bob> hi"))
        assertEquals(p("net", "carol", "yo"), Relay.parse("[net] <@+carol> yo"))
        assertEquals(p(null, "dave", "hey"), Relay.parse("<dave> hey"))
    }

    @Test fun customTemplates() {
        assertEquals(p(null, "frank", "yo"), Relay.parse("frank: yo", "{nick}: {message}"))
        assertEquals(p("Matrix", "grace", "hi"), Relay.parse("(Matrix) grace » hi", "({source}) {nick} » {message}"))
        // Blank falls back to the built-ins.
        assertEquals(p("Slack", "heidi", "ok"), Relay.parse("[Slack] <heidi> ok", "   "))
        assertNull(Relay.parse("whatever", "no placeholders here"))
        assertNull(Relay.parse("<x> y", "<{nick}> no-message-placeholder"))
        assertFalse(Relay.isValidTemplate("<{nick}> static"))
        assertFalse(Relay.isValidTemplate("{message} only"))
        assertTrue(Relay.isValidTemplate("[{source}] <{nick}> {message}"))
    }

    @Test fun regexCharactersInATemplateAreLiterals() {
        assertEquals(p("a", "ivan", "done"), Relay.parse("a.*b ivan done", "{source}.*b {nick} {message}"))
        assertNull(Relay.parse("aXXb ivan done", "{source}.*b {nick} {message}"))
    }

    @Test fun reversedLayoutNeedsItsOwnTemplate() {
        val raw = "\u000303<EyeSeeYou> [Discord] Present: 𝔅𝔢𝔩𝔦𝔞𝔩 🌈 syrius"
        assertEquals(p("Discord", "EyeSeeYou", "Present: 𝔅𝔢𝔩𝔦𝔞𝔩 🌈 syrius"), Relay.parse(raw, "<{nick}> [{source}] {message}"))
        assertEquals(p(null, "EyeSeeYou", "[Discord] Present: 𝔅𝔢𝔩𝔦𝔞𝔩 🌈 syrius"), Relay.parse(raw))
    }

    @Test fun formattingIsStrippedFromTheEnvelopeButKeptInTheMessage() {
        assertEquals(p("efnet", "FAST", "ultros: bet"), Relay.parse("[efnet] <\u000313+FAST\u0003> ultros: bet"))
        assertEquals(p("efnet", "FAST", "hey there"), Relay.parse("\u000304[efnet]\u0003 <\u000313FAST\u0003> hey there"))
        assertEquals(p("Discord", "underlined", "hello"), Relay.parse("[\u0002Discord\u0002] <\u001funderlined\u001f> hello"))
        assertEquals(p("efnet", "FAST", "\u0002bold msg\u0002"), Relay.parse("[efnet] <\u000313FAST\u0003> \u0002bold msg\u0002"))
        assertEquals(
            p(null, "relaybot", "\u000304red\u0003 and \u000309green\u0003"),
            Relay.parse("<\u000307relaybot\u0003> \u000304red\u0003 and \u000309green\u0003"),
        )
    }

    // ---- Chained bridges (#801) ----------------------------------------------------

    private fun marks(m: Map<String, String>): (String) -> String? = { m[it] }

    @Test fun chainsFollowOnlyMarkedHops() {
        val nested = "\u0002[IRC-nERDs]\u0002 <~\u000306|\u0003> \u000313<yrdsb3222[m]/OFTC> \u000fIs their a discord"
        assertEquals(
            p("IRC-nERDs", "|", "\u000313<yrdsb3222[m]/OFTC> \u000fIs their a discord"),
            Relay.parseChain(nested, "", marks(emptyMap())),
        )
        assertEquals(
            p("IRC-nERDs", "yrdsb3222[m]/OFTC", "\u000fIs their a discord"),
            Relay.parseChain(nested, "", marks(mapOf("|" to ""))),
        )
        // A quoted nick looks exactly like an envelope: no mark, no unwrap.
        val quote = "\u0002[efnet]\u0002 <+\u000303raah\u0003> <\u000310ultros\u0003> blasphemer! and a heretic!"
        assertEquals("raah", Relay.parseChain(quote, "", marks(mapOf("|" to "")))?.nick)
    }

    @Test fun theInnermostSourceWins() {
        assertEquals(p("rizon", "Nsane", "Looks like the sequel bombed"), Relay.parseChain("<nR> [rizon] <Nsane> Looks like the sequel bombed", "", marks(mapOf("nR" to ""))))
        assertEquals(p("B", "alice", "hi"), Relay.parseChain("[A] <bridge> [B] <alice> hi", "", marks(mapOf("bridge" to ""))))
        assertEquals(p(null, "Nelluk", "many ghosts living and dead abound"), Relay.parseChain("<+DOMF> <+Nelluk> many ghosts living and dead abound", "", marks(mapOf("DOMF" to ""))))
        assertEquals(p("matrix", "alice", "hey"), Relay.parseChain("[net] <bridge> matrix » alice » hey", "", marks(mapOf("bridge" to "{source} » {nick} » {message}"))))
    }

    @Test fun chainsStopWhereTheyShould() {
        assertEquals(p("net", "bridge", "reconnected to matrix"), Relay.parseChain("[net] <bridge> reconnected to matrix", "", marks(mapOf("bridge" to ""))))
        val loop = Relay.parseChain("<loop> <loop> <loop> <loop> <loop> <loop> <loop> done", "", marks(mapOf("loop" to "")))
        assertEquals("loop", loop?.nick)
        assertEquals("<loop> <loop> <loop> done", loop?.text)
        assertNull(Relay.parseChain("just a line the bot said", "", marks(emptyMap())))
    }

    // ---- /relay ----------------------------------------------------------------

    @Test fun relayCommand() {
        assertEquals(RelayCommand.List, Relay.parseCommand(""))
        assertEquals(RelayCommand.List, Relay.parseCommand("ls"))
        assertEquals(RelayCommand.Add("relaybot", ""), Relay.parseCommand("add relaybot"))
        // The pattern is the raw rest of the line, spaces and all; quotes optional.
        assertEquals(RelayCommand.Add("bridge", "<{nick}> {message}"), Relay.parseCommand("add bridge <{nick}> {message}"))
        assertEquals(RelayCommand.Add("bot", "[{source}] <{nick}> {message}"), Relay.parseCommand("mark bot \"[{source}] <{nick}> {message}\""))
        assertEquals(RelayCommand.Remove("relaybot"), Relay.parseCommand("unmark relaybot"))
        assertTrue(Relay.parseCommand("add") is RelayCommand.Error)
        assertTrue(Relay.parseCommand("remove") is RelayCommand.Error)
        assertTrue(Relay.parseCommand("frob x") is RelayCommand.Error)
        assertEquals(ParsedInput.Relay(RelayCommand.Add("b", "")), Commands.parse("/relay add b", "#c", true))
    }

    // ---- /accept, metadata ---------------------------------------------------------

    private fun rawLine(input: String): String? =
        (Commands.parse(input, "#c", true) as? ParsedInput.Ops)?.ops?.singleOrNull()?.line

    @Test fun acceptJoinsNicksWithCommas() {
        assertEquals("ACCEPT alice,bob,-carol", rawLine("/accept alice bob -carol"))
        assertEquals("ACCEPT alice,bob", rawLine("/accept alice, bob"))
        assertEquals("ACCEPT *", rawLine("/accept"))
    }

    @Test fun metadataCommands() {
        assertEquals(ParsedInput.Metadata("METADATA * SET display-name :Alice A", true), Commands.parse("/displayname Alice A", "#c", true))
        assertEquals(ParsedInput.Metadata("METADATA * SET display-name", true), Commands.parse("/displayname off", "#c", true))
        assertEquals(ParsedInput.Metadata("METADATA * SET avatar :https://x.test/me.png", true), Commands.parse("/avatar https://x.test/me.png", "#c", true))
        assertTrue((Commands.parse("/avatar ftp://x/me.png", "#c", true) as ParsedInput.Local).isError)
        assertEquals(ParsedInput.Metadata("METADATA * SET pronouns :she/her", true), Commands.parse("/metadata set pronouns she/her", "#c", true))
        assertEquals(ParsedInput.Metadata("METADATA * SET status", true), Commands.parse("/metadata del status", "#c", true))
        assertEquals(ParsedInput.Metadata("METADATA * LIST", false), Commands.parse("/metadata list", "#c", true))
        assertEquals(ParsedInput.Metadata("METADATA alice LIST", false), Commands.parse("/metadata list alice", "#c", true))
        assertEquals(ParsedInput.Metadata("METADATA * CLEAR", true), Commands.parse("/metadata clear", "#c", true))
        assertTrue((Commands.parse("/metadata set Bad!Key x", "#c", true) as ParsedInput.Local).isError)
        assertTrue((Commands.parse("/metadata frob", "#c", true) as ParsedInput.Local).isError)
    }

    // ---- Copy quote, metadata records ---------------------------------------------

    @Test fun copyQuoteIsTheQuoteLineWithoutItsMark() {
        assertEquals("<bob> hi there", Quote.copyText("bob", "message", "hi\n  there"))
        assertEquals("* bob waves", Quote.copyText("bob", "action", "waves"))
        assertEquals("-bob- note", Quote.copyText("bob", "notice", "\u0002note\u0002"))
        assertEquals("<bob>", Quote.copyText("bob", "message", ""))
    }

    @Test fun userMetaParses() {
        val m = UserMeta.parse(JSONObject().put("displayName", "\u0002Al\u0002").put("pronouns", "they/them").put("avatarUrl", JSONObject.NULL))
        assertEquals("Al", m?.shownName)
        assertEquals("they/them", m?.pronouns)
        assertNull(m?.avatarUrl)
        assertNull(UserMeta.parse(JSONObject()))
        // A name that's only formatting shows as the nick.
        assertNull(UserMeta(displayName = "\u0002\u0002").shownName)
    }

    @Test fun jsonNullIsNotTheWordNull() {
        val o = JSONObject().put("topic", JSONObject.NULL).put("name", "x").put("empty", "")
        assertNull(o.str("topic"))
        assertNull(o.str("missing"))
        assertNull(o.str("empty"))
        assertEquals("x", o.str("name"))
    }
}
