// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lurker 2.5: the `/dcc` and `/xdcc` command lines (parsed as the web client
 * parses them), Quote, and the XDCC queue's rows.
 */
class Lurker25Test {
    // ---- /dcc ----------------------------------------------------------------

    @Test fun bareDccOpensTheScreen() {
        assertEquals(DccCommand.List, DccCommands.parseDcc(""))
        assertEquals(DccCommand.List, DccCommands.parseDcc("list"))
        assertEquals(DccCommand.Files, DccCommands.parseDcc("files"))
    }

    @Test fun transferVerbsTakeAnId() {
        assertEquals(DccCommand.ById("accept", 12), DccCommands.parseDcc("accept 12"))
        // irssi's `/dcc get` accepts an offer; download is its own word.
        assertEquals(DccCommand.ById("accept", 3), DccCommands.parseDcc("get 3"))
        assertEquals(DccCommand.ById("download", 3), DccCommands.parseDcc("save 3"))
        assertEquals(DccCommand.ById("reject", 4), DccCommands.parseDcc("deny 4"))
        assertEquals(DccCommand.ById("cancel", 5), DccCommands.parseDcc("stop 5"))
        assertEquals(DccCommand.ById("delete", 6), DccCommands.parseDcc("rm 6"))
        assertEquals(DccCommand.ById("retry", 7), DccCommands.parseDcc("retry 7"))
        for (bad in listOf("accept", "accept x", "accept 0", "accept -1", "accept 1 2")) {
            assertTrue(bad, DccCommands.parseDcc(bad) is DccCommand.Error)
        }
    }

    @Test fun clearTakesOneOrAll() {
        assertEquals(DccCommand.Clear(null), DccCommands.parseDcc("clear"))
        assertEquals(DccCommand.Clear(9), DccCommands.parseDcc("clear 9"))
        assertTrue(DccCommands.parseDcc("clear nope") is DccCommand.Error)
    }

    @Test fun sendTakesFlagsANickAndTheRestAsTheFile() {
        assertEquals(DccCommand.Send("bob", false, false, null), DccCommands.parseDcc("send bob"))
        assertEquals(
            DccCommand.Send("bob", passive = true, secure = true, file = "My Show - 01.mkv"),
            DccCommands.parseDcc("send -passive -secure bob My Show - 01.mkv"),
        )
        assertTrue(DccCommands.parseDcc("send -fast bob x") is DccCommand.Error)
        assertTrue(DccCommands.parseDcc("send #room x") is DccCommand.Error)
        assertTrue(DccCommands.parseDcc("send") is DccCommand.Error)
    }

    @Test fun chatAndCloseFollowIrssi() {
        assertEquals(DccCommand.Chat("bob", passive = false, secure = true), DccCommands.parseDcc("chat -secure bob"))
        assertEquals(DccCommand.ChatClose("bob"), DccCommands.parseDcc("close chat bob"))
        assertTrue(DccCommands.parseDcc("close bob") is DccCommand.Error)
        assertTrue(DccCommands.parseDcc("chat close bob") is DccCommand.Error)
        assertTrue(DccCommands.parseDcc("chat =bob") is DccCommand.Error)
        assertTrue(DccCommands.parseDcc("chat @#room") is DccCommand.Error)
        assertTrue(DccCommands.parseDcc("frobnicate") is DccCommand.Error)
    }

    // ---- /xdcc ---------------------------------------------------------------

    @Test fun addTakesAPackOrARange() {
        assertEquals(XdccCommand.Add("Bot", listOf(29)), DccCommands.parseXdcc("add Bot #29"))
        assertEquals(XdccCommand.Add("Bot", listOf(29, 30, 31, 32)), DccCommands.parseXdcc("add Bot 29-32"))
        assertTrue(DccCommands.parseXdcc("add Bot 29 30") is XdccCommand.Error)
    }

    @Test fun addlIsARangeEitherWayRound() {
        assertEquals(XdccCommand.Add("Bot", listOf(3, 4, 5)), DccCommands.parseXdcc("addl Bot 5 3"))
        assertTrue(DccCommands.parseXdcc("addl Bot 5") is XdccCommand.Error)
    }

    @Test fun addmTakesSeveralWithoutRepeats() {
        assertEquals(XdccCommand.Add("Bot", listOf(1, 3, 4, 5)), DccCommands.parseXdcc("addm Bot 1 #3 3-5"))
        assertTrue(DccCommands.parseXdcc("addm Bot 1 x") is XdccCommand.Error)
    }

    @Test fun tooManyPacksIsATypoNotAQueue() {
        assertTrue(DccCommands.parseXdcc("addl Bot 1 100000") is XdccCommand.Error)
        assertEquals(500, (DccCommands.parseXdcc("addl Bot 1 500") as XdccCommand.Add).packs.size)
    }

    @Test fun removeAndQueue() {
        assertEquals(XdccCommand.Remove("Bot", listOf(7)), DccCommands.parseXdcc("rmp Bot 7"))
        assertEquals(XdccCommand.Remove("Bot", null), DccCommands.parseXdcc("rmbot Bot"))
        assertEquals(XdccCommand.Queue(null), DccCommands.parseXdcc(""))
        assertEquals(XdccCommand.Queue("Bot"), DccCommands.parseXdcc("queue Bot"))
        // The pack sigil is `#`, but a bot is never a channel.
        assertTrue(DccCommands.parseXdcc("add #chan 1") is XdccCommand.Error)
        assertTrue(DccCommands.parseXdcc("purge") is XdccCommand.Error)
    }

    @Test fun packsReadAsAPersonWouldSayThem() {
        assertEquals("#31", DccCommands.describePacks(listOf(31)))
        assertEquals("#29–#32", DccCommands.describePacks(listOf(29, 30, 31, 32)))
        assertEquals("#29 and #31", DccCommands.describePacks(listOf(29, 31)))
        assertEquals("#29, #31 and #35", DccCommands.describePacks(listOf(29, 31, 35)))
    }

    private fun file(id: Int, name: String) = DccFile(id, name, 1, "received", null, "bob", 1)

    @Test fun aSendNamesAFileByIdOrName() {
        val files = listOf(file(3, "Show - 01.mkv"), file(4, "Show - 02.mkv"), file(5, "notes.txt"))
        assertEquals(5, DccCommands.findFile(files, "5")?.id)
        assertEquals(5, DccCommands.findFile(files, "#5")?.id)
        assertEquals(4, DccCommands.findFile(files, "show - 02.mkv")?.id)
        assertEquals(5, DccCommands.findFile(files, "notes")?.id)
        // Two start with "Show": ambiguous, so neither.
        assertNull(DccCommands.findFile(files, "Show"))
        assertNull(DccCommands.findFile(files, "nothing"))
    }

    private fun row(id: Int, pack: Int, state: String, seq: Int? = null, bot: String = "Bot") =
        DccTransfer(id, bot, "XDCC #$pack", state, 0, 0, "recv", null, networkId = 1, pack = pack, queueSeq = seq)

    @Test fun theQueueListsWhatsOutThenWhatWaits() {
        val rows = listOf(row(1, 30, "queued", 2), row(2, 29, "requested"), row(3, 31, "queued", 3), row(4, 9, "completed"))
        assertEquals("Bot: #29 asked, #30 queued, #31 queued", DccCommands.queueText(rows, 1, null))
        assertEquals("Nothing queued from Other.", DccCommands.queueText(rows, 1, "Other"))
        assertEquals("The XDCC queue is empty.", DccCommands.queueText(rows, 2, null))
    }

    @Test fun aQueuedPacksPlaceholderNameIsNotItsName() {
        assertTrue(DccCommands.isPlaceholderName("XDCC #29", 29))
        assertFalse(DccCommands.isPlaceholderName("Show.mkv", 29))
        assertFalse(DccCommands.isPlaceholderName("XDCC #29", null))
    }

    @Test fun packRowsKnowWhatTheyCanDo() {
        assertTrue(row(1, 2, "queued").isQueuedPack)
        assertTrue(row(1, 2, "queued").isActive)
        assertTrue(row(1, 2, "failed").canRetry)
        assertFalse(row(1, 2, "cancelled").canRetry)
        val partial = DccTransfer(1, "bob", "f", "failed", 50, 100, "recv", "dropped", hasFile = true)
        assertTrue(partial.hasPartial)
        assertFalse(partial.copy(hasFile = false).hasPartial)
    }

    // ---- Quote ---------------------------------------------------------------

    @Test fun aQuoteWritesTheLineTheWayIrcDoes() {
        assertEquals("> <alice> what she said", Quote.lineFor("alice", "message", "what she said"))
        assertEquals("> * alice waves", Quote.lineFor("alice", "action", "waves"))
        assertEquals("> -NickServ- you are identified", Quote.lineFor("NickServ", "notice", "you are identified"))
    }

    @Test fun aQuoteIsProse() {
        // Formatting out, lines folded.
        assertEquals("> <a> bold red two lines", Quote.lineFor("a", "message", "\u0002bold\u0002 \u000304red\u0003 two\nlines"))
        assertEquals("> <a>", Quote.lineFor("a", "message", ""))
    }

    @Test fun aLongQuoteIsClippedToOneLine() {
        val q = Quote.lineFor("alice", "message", "é".repeat(400))
        assertTrue(q.endsWith("…"))
        assertTrue(q.toByteArray(Charsets.UTF_8).size <= Quote.TEXT_BUDGET)
        // Never mid-character: what's left is still whole é's.
        assertTrue(q.removePrefix("> <alice> ").removeSuffix("…").all { it == 'é' })
    }

    @Test fun aNewQuoteGoesUnderTheQuotesAboveTheAnswer() {
        assertEquals("> <a> one\n", Quote.intoDraft("", "> <a> one"))
        assertEquals("> <a> one\n> <b> two\nmy answer", Quote.intoDraft("> <a> one\nmy answer", "> <b> two"))
        assertEquals("> <a> one\n" to "answer", Quote.split("> <a> one\nanswer"))
        assertEquals("" to "no quote here", Quote.split("no quote here"))
    }

    @Test fun onlyLinesThatOpenWithTheMarkAreQuotes() {
        assertEquals(listOf(0 until 8), Quote.quoteLines("> <a> hi\nreply"))
        assertEquals(listOf(6 until 7), Quote.quoteLines("first\n>"))
        assertTrue(Quote.quoteLines(">no space").isEmpty())
        assertTrue(Quote.quoteLines("a > b").isEmpty())
    }

    // ---- DCC screen ----------------------------------------------------------

    @Test fun timeLeftReadsPlainly() {
        assertEquals("10 s left", etaText(1_000, 100))
        assertEquals("2 min left", etaText(120_000, 1_000))
        assertEquals("1 h 30 min left", etaText(5_400_000, 1_000))
        assertNull(etaText(100, 0))
        assertNull(etaText(0, 100))
    }

    @Test fun newStatesReadPlainly() {
        assertEquals("checking the checksum", dccStateLabel("verifying"))
        assertEquals("asked the bot", dccStateLabel("requested"))
        assertEquals("queued", dccStateLabel("queued"))
    }
}
