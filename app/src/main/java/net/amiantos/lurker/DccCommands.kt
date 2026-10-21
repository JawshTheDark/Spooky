// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

/**
 * The `/dcc` and `/xdcc` command lines, parsed the way Lurker's web client
 * parses them (vue_client/src/lib/commands/dcc.ts, xdcc.ts), so a habit carried
 * between the two does the same thing. Pure, so it unit-tests.
 *
 * The transfer verbs act on a transfer's id (as the DCC screen shows it) and are
 * account-wide; chat and send ride a network, so the caller needs one.
 */
sealed interface DccCommand {
    /** Open the DCC screen. */
    data object List : DccCommand
    /** List the kept files, with ids for `/dcc send`. */
    data object Files : DccCommand
    /** One transfer by id: accept, reject, cancel, download, delete or retry. */
    data class ById(val verb: String, val id: Int) : DccCommand
    /** One finished transfer off the list, or (null) every one. Files stay. */
    data class Clear(val id: Int?) : DccCommand
    /** [file] is a kept file's name or id; null to pick one. */
    data class Send(val nick: String, val passive: Boolean, val secure: Boolean, val file: String?) : DccCommand
    data class Chat(val nick: String, val passive: Boolean, val secure: Boolean) : DccCommand
    data class ChatClose(val nick: String) : DccCommand
    data class Error(val message: String) : DccCommand
}

sealed interface XdccCommand {
    data class Add(val bot: String, val packs: List<Int>) : XdccCommand
    /** [packs] null drops the bot's whole queue. */
    data class Remove(val bot: String, val packs: List<Int>?) : XdccCommand
    /** [bot] null for every bot's queue. */
    data class Queue(val bot: String?) : XdccCommand
    data class Error(val message: String) : XdccCommand
}

object DccCommands {
    /** The most packs one request may queue: a typo guard, not a quota (shared/xdcc.ts). */
    const val XDCC_MAX_PACKS = 500

    const val DCC_USAGE =
        "Usage: /dcc [list] · /dcc files · /dcc accept|reject|cancel|download|delete|retry <id> · " +
            "/dcc clear [<id>] · /dcc send [-passive] [-secure] <nick> [<file>] · " +
            "/dcc chat [-passive] [-secure] <nick> · /dcc close chat <nick>"

    const val XDCC_USAGE =
        "Usage: /xdcc add <bot> <pack> · /xdcc addl <bot> <first> <last> · " +
            "/xdcc addm <bot> <pack> <pack>… · /xdcc queue [<bot>] · " +
            "/xdcc rmp|rmpl|rmpm <bot> … · /xdcc rmbot <bot>"

    private val ACCEPT = setOf("accept", "ok", "yes", "get")
    private val REJECT = setOf("reject", "deny", "no")
    private val CANCEL = setOf("cancel", "abort", "stop")
    // Not `get`: irssi's `/dcc get` accepts an offer, and it does here too.
    private val DOWNLOAD = setOf("download", "save")
    private val DELETE = setOf("delete", "rm")

    /** A peer: one nick — never a `=nick` buffer name, a channel, a list or a mask. */
    private fun nick(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("=") && isSinglePeerTarget(it) }

    private fun id(raw: String?): Int? = raw?.toIntOrNull()?.takeIf { it > 0 }

    fun parseDcc(argLine: String): DccCommand {
        val trimmed = argLine.trim()
        if (trimmed.isEmpty()) return DccCommand.List
        val parts = trimmed.split(Regex("\\s+"))
        val verb = parts[0].lowercase()
        when (verb) {
            "list", "ls" -> return DccCommand.List
            "files" -> return DccCommand.Files
            "clear" -> {
                if (parts.size == 1) return DccCommand.Clear(null)
                val i = id(parts[1])
                return if (i != null && parts.size == 2) DccCommand.Clear(i) else DccCommand.Error("Usage: /dcc clear [<id>]")
            }
            "chat" -> {
                val flags = parts.drop(1).filter { it.startsWith("-") }.map { it.lowercase() }
                val rest = parts.drop(1).filterNot { it.startsWith("-") }
                val usage = "Usage: /dcc chat [-passive] [-secure] <nick>"
                flags.firstOrNull { it != "-passive" && it != "-secure" }?.let {
                    return DccCommand.Error("Unknown option \"$it\". $usage")
                }
                // `/dcc chat close bob` read literally would OFFER a chat to "close".
                if (rest.size > 1) {
                    return DccCommand.Error(
                        if (rest[0].equals("close", true)) "To end a chat: /dcc close chat <nick>" else usage,
                    )
                }
                val n = nick(rest.getOrNull(0)) ?: return DccCommand.Error(usage)
                return DccCommand.Chat(n, passive = "-passive" in flags, secure = "-secure" in flags)
            }
            // irssi's `DCC SEND [-passive] <nick> <file>`, the file optional (then a
            // picker opens). The file is the rest of the line: no quotes needed.
            "send" -> {
                val usage = "Usage: /dcc send [-passive] [-secure] <nick> [<file>]"
                var rest = trimmed.substring(parts[0].length).trim()
                var passive = false
                var secure = false
                while (rest.startsWith("-")) {
                    val flag = rest.split(Regex("\\s+"), limit = 2)[0]
                    when (flag.lowercase()) {
                        "-passive" -> passive = true
                        "-secure" -> secure = true
                        else -> return DccCommand.Error("Unknown option \"$flag\". $usage")
                    }
                    rest = rest.substring(flag.length).trim()
                }
                val nickRaw = rest.split(Regex("\\s+"), limit = 2)[0]
                val n = nick(nickRaw) ?: return DccCommand.Error(usage)
                val file = rest.substring(nickRaw.length).trim()
                return DccCommand.Send(n, passive, secure, file.ifEmpty { null })
            }
            // irssi's type-first close. `/dcc close chat bob` has to close bob, not
            // a peer called "chat".
            "close" -> return when (parts.getOrNull(1)?.lowercase()) {
                "chat" -> {
                    val n = nick(parts.getOrNull(2))
                    if (n != null && parts.size == 3) DccCommand.ChatClose(n) else DccCommand.Error("Usage: /dcc close chat <nick>")
                }
                "send", "get" -> DccCommand.Error("File transfers close by id: /dcc list, then /dcc cancel <id>")
                else -> DccCommand.Error("Usage: /dcc close chat <nick>")
            }
        }
        val byId = when (verb) {
            in ACCEPT -> "accept"
            in REJECT -> "reject"
            in CANCEL -> "cancel"
            in DOWNLOAD -> "download"
            in DELETE -> "delete"
            "retry" -> "retry"
            else -> return DccCommand.Error(DCC_USAGE)
        }
        val i = id(parts.getOrNull(1))
        return if (i != null && parts.size == 2) DccCommand.ById(byId, i) else DccCommand.Error("Usage: /dcc $byId <id>")
    }

    private fun pack(raw: String?): Int? =
        raw?.trim()?.let { Regex("^#?(\\d{1,9})$").find(it) }?.groupValues?.get(1)?.toInt()

    // first..last, either way round; null past the limit, checked before building.
    private fun range(first: Int, last: Int): List<Int>? {
        val lo = minOf(first, last)
        val hi = maxOf(first, last)
        if (hi.toLong() - lo + 1 > XDCC_MAX_PACKS) return null
        return (lo..hi).toList()
    }

    private const val TOO_MANY = "at most $XDCC_MAX_PACKS packs at a time"

    /** `29`, `#29`, or a range `29-32` / `#29-#32`. */
    private fun packOrRange(raw: String): Result<List<Int>>? {
        Regex("^#?(\\d{1,9})-#?(\\d{1,9})$").find(raw.trim())?.let { m ->
            val r = range(m.groupValues[1].toInt(), m.groupValues[2].toInt())
            return if (r == null) Result.failure(IllegalArgumentException(TOO_MANY)) else Result.success(r)
        }
        return pack(raw)?.let { Result.success(listOf(it)) }
    }

    private fun packsFor(kind: String, args: List<String>): Result<List<Int>> {
        fun fail(m: String) = Result.failure<List<Int>>(IllegalArgumentException(m))
        if (kind == "range") {
            val a = pack(args.getOrNull(0))
            val b = pack(args.getOrNull(1))
            if (a == null || b == null || args.size != 2) return fail("needs <first> <last>")
            return range(a, b)?.let { Result.success(it) } ?: fail(TOO_MANY)
        }
        if (args.isEmpty()) return fail("needs a pack number")
        if (kind == "one" && args.size != 1) return fail("takes one pack; addm takes several")
        val packs = ArrayList<Int>()
        for (a in args) {
            val p = packOrRange(a) ?: return fail("\"$a\" is not a pack number")
            val list = p.getOrElse { return fail(it.message ?: TOO_MANY) }
            if (packs.size + list.size > XDCC_MAX_PACKS) return fail(TOO_MANY)
            packs += list
        }
        return Result.success(packs.distinct())
    }

    private val XDCC_VERBS = mapOf(
        "add" to (true to "one"), "addl" to (true to "range"), "addm" to (true to "many"),
        "rmp" to (false to "one"), "rmpl" to (false to "range"), "rmpm" to (false to "many"),
    )

    /** EasyXdcc's verbs (the HexChat plugin's), as the web client takes them. */
    fun parseXdcc(argLine: String): XdccCommand {
        val parts = argLine.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val verb = parts.getOrNull(0)?.lowercase() ?: return XdccCommand.Queue(null)
        val rest = parts.drop(1)
        if (verb == "queue" || verb == "list") {
            if (rest.size > 1) return XdccCommand.Error(XDCC_USAGE)
            if (rest.isEmpty()) return XdccCommand.Queue(null)
            return nick(rest[0])?.let { XdccCommand.Queue(it) } ?: XdccCommand.Error("\"${rest[0]}\" is not a nick")
        }
        if (verb == "rmbot") {
            val bot = nick(rest.getOrNull(0))
            if (bot == null || rest.size != 1) return XdccCommand.Error("rmbot needs a bot nick")
            return XdccCommand.Remove(bot, null)
        }
        val (add, kind) = XDCC_VERBS[verb] ?: return XdccCommand.Error(XDCC_USAGE)
        val bot = nick(rest.getOrNull(0))
            ?: return XdccCommand.Error(rest.getOrNull(0)?.let { "\"$it\" is not a nick" } ?: "$verb needs a bot nick")
        val packs = packsFor(kind, rest.drop(1)).getOrElse { return XdccCommand.Error("$verb ${it.message}") }
        return if (add) XdccCommand.Add(bot, packs) else XdccCommand.Remove(bot, packs)
    }

    /** `#31`, `#29–#32` for a run of three or more, else `#29, #31 and #35`. */
    fun describePacks(packs: List<Int>): String {
        val run = packs.size >= 3 && packs.indices.all { it == 0 || packs[it] == packs[it - 1] + 1 }
        if (run) return "#${packs.first()}–#${packs.last()}"
        val names = packs.map { "#$it" }
        if (names.size <= 1) return names.joinToString("")
        return names.dropLast(1).joinToString(", ") + " and " + names.last()
    }

    /** `/xdcc queue [bot]`: each bot's packs still to come, the one out first. */
    fun queueText(transfers: Collection<DccTransfer>, networkId: Int, bot: String?): String {
        val rows = transfers.filter {
            it.pack != null && it.networkId == networkId && !it.isFinished &&
                (bot == null || it.peerNick.equals(bot, ignoreCase = true))
        }
        if (rows.isEmpty()) return if (bot == null) "The XDCC queue is empty." else "Nothing queued from $bot."
        return rows.groupBy { it.peerNick }.entries.joinToString("\n") { (b, rs) ->
            "$b: " + rs.sortedWith(compareBy({ if (it.state == "queued") 1 else 0 }, { it.queueSeq ?: Int.MAX_VALUE }))
                .joinToString(", ") { t ->
                    "#${t.pack} " + when (t.state) {
                        "queued" -> "queued"
                        "requested" -> if (t.botAcked) "in the bot's queue" else "asked"
                        "receiving" -> if (t.total > 0) "${(t.progress * 100).toInt()}%" else "receiving"
                        else -> t.state.replace('_', ' ')
                    }
                }
        }
    }

    /** A queued pack's name until the bot's offer brings the real one. */
    fun isPlaceholderName(name: String, pack: Int?): Boolean = pack != null && name == "XDCC #$pack"

    /**
     * A kept file named on a `/dcc send` line: its id as `/dcc files` shows it, or
     * its name — exact first (case aside), then a name that starts with it, when
     * only one does. Null when nothing (or more than one thing) matches.
     */
    fun findFile(files: Collection<DccFile>, query: String): DccFile? {
        val q = query.trim().removePrefix("#")
        q.toIntOrNull()?.let { n -> files.firstOrNull { it.id == n }?.let { return it } }
        files.filter { it.name.equals(query.trim(), ignoreCase = true) }.let { if (it.size == 1) return it[0] }
        val starts = files.filter { it.name.startsWith(query.trim(), ignoreCase = true) }
        return starts.singleOrNull()
    }
}
