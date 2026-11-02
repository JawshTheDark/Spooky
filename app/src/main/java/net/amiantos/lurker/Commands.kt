// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

/**
 * Slash-command parsing for the composer.
 *
 * The Lurker server accepts a small set of structured socket messages (`send`,
 * `action`, `notice`, `join`, `part`, `raw`, ...) and does the IRC translation
 * itself. Rather than teach the server a new dialect, this parser turns what the
 * user types into those existing wire ops — mapping the handful of commands with
 * special semantics (`/me`, `/msg`, `/join`, `/part`) explicitly and lowering
 * everything else to a `raw` IRC line, which is the most compatible path.
 *
 * Pure and free of Android types so the mapping is unit-tested on the JVM;
 * LurkerClient owns turning a [WireOp] into an actual socket frame.
 */

/** One thing to put on the wire. Fields not relevant to [type] are null. */
data class WireOp(
    val type: String, // send | action | notice | raw | join | part | clear | close
    val target: String? = null, // null = current buffer target
    val text: String? = null,
    val line: String? = null,
    val channel: String? = null,
    val reason: String? = null,
    /** send/action: the stored line this answers (an IRCv3 reply, Lurker 2.4+). */
    val replyTo: Long? = null,
    /** away: true = every network, false = this one, null = the user's
     *  `away.all_networks` setting decides (Lurker 2.4's /away semantics). */
    val all: Boolean? = null,
)

sealed interface ParsedInput {
    /** Ops to send. [openTarget] asks the UI to focus/open that buffer first. */
    data class Ops(val ops: List<WireOp>, val openTarget: String? = null) : ParsedInput
    /** A locally-rendered line (help text, or a usage error). */
    data class Local(val message: String, val isError: Boolean) : ParsedInput
    /** Open the channel browser (/list), optionally seeded with a filter [query]. */
    data class Browse(val query: String?) : ParsedInput
    /** A `/dcc` line (see [DccCommand]): REST, not a socket op. A chat's outcome
     *  lands as notices in the `=nick` buffer; a transfer's in the DCC screen. */
    data class Dcc(val cmd: DccCommand) : ParsedInput
    /** A `/xdcc` line: the server-side XDCC queue (Lurker 2.5). */
    data class Xdcc(val cmd: XdccCommand) : ParsedInput
    /** A `/relay` line: list, mark or unmark relay/bridge bots on this network. */
    data class Relay(val cmd: RelayCommand) : ParsedInput
    /** `/metadata`, `/avatar`, `/displayname` (Lurker 2.6): a raw METADATA [line].
     *  [write] = it changes your own record, which needs a connected network that
     *  keeps metadata (the server saves the row itself once it's accepted). */
    data class Metadata(val line: String, val write: Boolean) : ParsedInput
}

object Commands {
    private val EMPTY = ParsedInput.Ops(emptyList())
    private const val SLAP = "slaps %s around a bit with a large trout"

    fun isChannel(target: String): Boolean =
        target.isNotEmpty() && target[0] in "#&+!"

    /** `=nick` names a DCC chat buffer (irssi's convention, as the server uses). */
    fun isDccChat(target: String): Boolean = target.startsWith("=")

    /** The peer behind a `=nick` DCC chat; any other target unchanged. */
    fun dccPeer(target: String): String = if (isDccChat(target)) target.substring(1) else target

    /**
     * Expand a user alias once (single-pass, no loops — mirrors the web's
     * aliasResolver). `/name args…` where `name` matches an alias becomes the
     * expansion with `$1..$9` = positional args, `$2-` = 2nd arg onward, `$*` =
     * whole arg line, `$me` = own nick, `$chan` = current target. A template
     * with no `$` params gets the args appended. Returns [input] unchanged when
     * nothing matches — so this is a safe no-op when there are no aliases.
     */
    fun expandAlias(input: String, aliases: List<AliasEntry>, nick: String?, target: String): String {
        if (aliases.isEmpty() || !input.startsWith("/") || input.startsWith("//")) return input
        val space = input.indexOf(' ')
        val verb = (if (space == -1) input.substring(1) else input.substring(1, space)).lowercase()
        val alias = aliases.firstOrNull { it.name.equals(verb, true) } ?: return input
        val argLine = if (space == -1) "" else input.substring(space + 1).trim()
        val args = if (argLine.isEmpty()) emptyList() else argLine.split(Regex("\\s+"))
        var out = alias.expansion
        val hasParam = out.contains('$')
        out = out.replace(Regex("\\$([1-9])-")) { m ->
            args.drop(m.groupValues[1].toInt() - 1).joinToString(" ")
        }
        out = out.replace(Regex("\\$([1-9])")) { m -> args.getOrNull(m.groupValues[1].toInt() - 1) ?: "" }
        out = out.replace("$*", argLine).replace("\$me", nick ?: "").replace("\$chan", target)
        if (!hasParam && argLine.isNotEmpty()) out = "$out $argLine"
        return out.trim()
    }

    fun parse(input: String, currentTarget: String, hasNetwork: Boolean): ParsedInput {
        val line = input.trimEnd()
        if (line.isEmpty()) return EMPTY

        // Plain text, or "//..." literal that happens to start with a slash.
        if (!line.startsWith("/") || line.startsWith("//")) {
            val body = if (line.startsWith("//")) line.substring(1) else line
            return ParsedInput.Ops(listOf(WireOp("send", text = body)))
        }

        val space = line.indexOf(' ')
        val verb = (if (space == -1) line.substring(1) else line.substring(1, space)).lowercase()
        val rest = if (space == -1) "" else line.substring(space + 1).trim()

        // Commands that touch a network need one. `/help` is the only exception, and
        // `/dcc`, whose transfer verbs are account-wide (chat and send check later).
        if (verb != "help" && verb != "commands" && verb != "dcc" && !hasNetwork) {
            return ParsedInput.Local("This buffer has no network.", isError = true)
        }

        fun raw(l: String) = ParsedInput.Ops(listOf(WireOp("raw", line = l)))
        fun err(msg: String) = ParsedInput.Local(msg, isError = true)

        return when (verb) {
            "help", "commands" -> ParsedInput.Local(HELP, isError = false)

            "me", "action" ->
                if (rest.isEmpty()) err("Usage: /me <action>")
                else ParsedInput.Ops(listOf(WireOp("action", text = rest)))

            "msg", "m" -> {
                val (who, body) = splitTargetAndBody(rest) ?: return err("Usage: /msg <target> <message>")
                ParsedInput.Ops(listOf(WireOp("send", target = who, text = body)))
            }

            "query", "q" -> {
                val (who, body) = splitTargetAndBody(rest) ?: run {
                    if (rest.isBlank()) return err("Usage: /query <nick> [message]")
                    return ParsedInput.Ops(emptyList(), openTarget = rest.trim())
                }
                ParsedInput.Ops(listOf(WireOp("send", target = who, text = body)), openTarget = who)
            }

            "notice" -> {
                val (who, body) = splitTargetAndBody(rest) ?: return err("Usage: /notice <target> <message>")
                ParsedInput.Ops(listOf(WireOp("notice", target = who, text = body)))
            }

            "join", "j" -> {
                if (rest.isEmpty()) return err("Usage: /join <#channel> [key]")
                val parts = rest.split(Regex("\\s+"), limit = 2)
                val chan = normalizeChannel(parts[0])
                // A key can't ride the structured join op, so fall to raw for that.
                if (parts.size == 2) raw("JOIN $chan ${parts[1]}")
                // No openTarget: don't optimistically open the requested channel — a
                // 470 forward can land us elsewhere. The join op focuses it (if already
                // joined) or channel-joined focuses the channel we actually land in.
                else ParsedInput.Ops(listOf(WireOp("join", channel = chan)))
            }

            "list", "channels" -> ParsedInput.Browse(rest.ifBlank { null })

            "part", "leave" -> {
                val parts = if (rest.isEmpty()) emptyList() else rest.split(Regex("\\s+"), limit = 2)
                val chan = parts.getOrNull(0)?.takeIf { isChannel(it) }?.let { normalizeChannel(it) }
                    ?: currentTarget
                val reason = when {
                    parts.isEmpty() -> null
                    isChannel(parts[0]) -> parts.getOrNull(1)
                    else -> rest
                }
                // An explicit /part leaves AND closes the buffer. close-buffer
                // sends the PART (with reason) server-side and removes the row —
                // /cycle keeps its bare "part" op to leave without closing.
                ParsedInput.Ops(listOf(WireOp("close", target = chan, reason = reason)))
            }

            "close" -> ParsedInput.Ops(listOf(WireOp("close", target = currentTarget)))
            "clear" -> ParsedInput.Ops(listOf(WireOp("clear", target = currentTarget)))

            "nick" ->
                if (rest.isEmpty()) err("Usage: /nick <newnick>") else raw("NICK $rest")

            "topic" -> {
                val (chan, text) = channelAndRest(rest, currentTarget)
                if (text.isEmpty()) raw("TOPIC $chan") else raw("TOPIC $chan :$text")
            }

            "kick" -> {
                if (rest.isEmpty()) return err("Usage: /kick <nick> [reason]")
                val (chan, tail) = channelAndRest(rest, currentTarget)
                val bits = tail.split(Regex("\\s+"), limit = 2)
                val who = bits.getOrNull(0).orEmpty()
                if (who.isEmpty()) return err("Usage: /kick <nick> [reason]")
                val reason = bits.getOrNull(1)
                raw(if (reason != null) "KICK $chan $who :$reason" else "KICK $chan $who")
            }

            "invite" -> {
                val bits = rest.split(Regex("\\s+"))
                if (bits.isEmpty() || bits[0].isEmpty()) return err("Usage: /invite <nick> [#channel]")
                val chan = bits.getOrNull(1)?.let { normalizeChannel(it) } ?: currentTarget
                raw("INVITE ${bits[0]} $chan")
            }

            "mode" -> if (rest.isEmpty()) err("Usage: /mode <target> <flags>") else raw("MODE $rest")

            "op" -> modeForNicks(rest, currentTarget, "+o") ?: err("Usage: /op <nick...>")
            "deop" -> modeForNicks(rest, currentTarget, "-o") ?: err("Usage: /deop <nick...>")
            "voice" -> modeForNicks(rest, currentTarget, "+v") ?: err("Usage: /voice <nick...>")
            "devoice" -> modeForNicks(rest, currentTarget, "-v") ?: err("Usage: /devoice <nick...>")
            "halfop" -> modeForNicks(rest, currentTarget, "+h") ?: err("Usage: /halfop <nick...>")
            "dehalfop" -> modeForNicks(rest, currentTarget, "-h") ?: err("Usage: /dehalfop <nick...>")
            "ban" -> modeForNicks(rest, currentTarget, "+b") ?: err("Usage: /ban <mask...>")
            "unban" -> modeForNicks(rest, currentTarget, "-b") ?: err("Usage: /unban <mask...>")

            // In a `=nick` DCC chat a bare /whois or /ping means the PEER — the
            // buffer name must never reach the IRC wire.
            "whois" -> when {
                rest.isNotEmpty() -> raw("WHOIS ${dccPeer(rest)}")
                isDccChat(currentTarget) -> raw("WHOIS ${dccPeer(currentTarget)}")
                else -> err("Usage: /whois <nick>")
            }
            "whowas" -> if (rest.isEmpty()) err("Usage: /whowas <nick>") else raw("WHOWAS $rest")

            "raw", "quote" -> if (rest.isEmpty()) err("Usage: /raw <line>") else raw(rest)

            "ns", "nickserv" -> if (rest.isEmpty()) err("Usage: /ns <command>") else raw("PRIVMSG NickServ :$rest")
            "cs", "chanserv" -> if (rest.isEmpty()) err("Usage: /cs <command>") else raw("PRIVMSG ChanServ :$rest")
            "ms", "memoserv" -> if (rest.isEmpty()) err("Usage: /ms <command>") else raw("PRIVMSG MemoServ :$rest")

            // Lurker 2.4: /away is per network unless `-all` (or the user's
            // away.all_networks setting); `-one` forces just this one. An empty
            // message is /back. Resolved by the backend, which knows the setting.
            "away", "back" -> {
                val words = rest.split(Regex("\\s+"), limit = 2)
                val flag = words[0].lowercase().takeIf { it == "-all" || it == "-one" }
                val message = if (verb == "back") "" else if (flag != null) words.getOrElse(1) { "" }.trim() else rest
                ParsedInput.Ops(listOf(WireOp("away", text = message, all = flag?.let { it == "-all" })))
            }

            "ctcp" -> {
                val (who, body) = splitTargetAndBody(rest) ?: return err("Usage: /ctcp <nick> <TYPE> [args]")
                // A CTCP rides IRC, so a `=nick` buffer name becomes its peer.
                raw("PRIVMSG ${dccPeer(who)} :${body.uppercase()}")
            }
            "ping" -> when {
                rest.isNotEmpty() -> raw("PRIVMSG ${dccPeer(rest)} :PING")
                isDccChat(currentTarget) -> raw("PRIVMSG ${dccPeer(currentTarget)} :PING")
                else -> err("Usage: /ping <nick>")
            }

            "dcc" -> when (val c = DccCommands.parseDcc(rest)) {
                is DccCommand.Error -> err(c.message)
                else -> ParsedInput.Dcc(c)
            }
            "xdcc" -> when (val c = DccCommands.parseXdcc(rest)) {
                is XdccCommand.Error -> err(c.message)
                else -> ParsedInput.Xdcc(c)
            }

            // Relay/bridge bots (Lurker #277). The pattern is the raw rest of the
            // line, spaces and all.
            "relay" -> when (val c = net.amiantos.lurker.Relay.parseCommand(rest)) {
                is RelayCommand.Error -> err("/relay: ${c.message}")
                else -> ParsedInput.Relay(c)
            }

            // Caller ID (+g): let these nicks' messages through. `/accept a b -c`
            // → ACCEPT a,b,-c; bare `/accept` lists who's accepted (ACCEPT *).
            "accept" -> raw("ACCEPT " + rest.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.joinToString(",").ifEmpty { "*" })

            // IRCv3 metadata: your display name, avatar and the rest (Lurker 2.6).
            "metadata" -> metadataCommand(rest)
            "avatar" -> when {
                rest.isEmpty() -> err("usage: /avatar <https://…/image.png> · /avatar off")
                rest.equals("off", true) -> metadataSet("avatar", null)
                !Regex("^https?://\\S+$", RegexOption.IGNORE_CASE).matches(rest) -> err("/avatar: the avatar must be an http(s) URL")
                else -> metadataSet("avatar", rest)
            }
            "displayname" -> when {
                rest.isEmpty() -> err("usage: /displayname <name> · /displayname off")
                rest.equals("off", true) -> metadataSet("display-name", null)
                else -> metadataSet("display-name", rest)
            }

            "slap" -> if (rest.isEmpty()) err("Usage: /slap <nick>")
                else ParsedInput.Ops(listOf(WireOp("action", text = SLAP.format(rest))))

            "quit" -> raw(if (rest.isEmpty()) "QUIT" else "QUIT :$rest")

            "cycle", "hop" -> {
                if (!isChannel(currentTarget)) return err("/cycle only works in a channel.")
                ParsedInput.Ops(
                    listOf(
                        WireOp("part", channel = currentTarget, reason = rest.ifEmpty { null }),
                        WireOp("join", channel = currentTarget),
                    ),
                    openTarget = currentTarget,
                )
            }

            // End-to-end encryption control (RPE2E). The server runs the whole
            // /e2e subcommand surface; we just pass the argument line through as
            // a dedicated {type:'e2e'} frame, scoped to this buffer.
            "e2e" -> ParsedInput.Ops(listOf(WireOp("e2e", target = currentTarget, line = rest)))

            else -> err("Unknown command: /$verb  (try /help, or /raw to send it literally)")
        }
    }

    private val METADATA_KEY = Regex("^[a-z0-9_./-]+$")

    private fun metadataSet(key: String, value: String?): ParsedInput {
        if (!METADATA_KEY.matches(key)) {
            return ParsedInput.Local("invalid metadata key: $key (a-z, 0-9, _ . / -)", isError = true)
        }
        val clean = value?.replace(Regex("[\\r\\n\\u0000]"), "")?.trim().orEmpty()
        return ParsedInput.Metadata(if (clean.isEmpty()) "METADATA * SET $key" else "METADATA * SET $key :$clean", write = true)
    }

    /** `/metadata set|del|list|clear …`, as the web reads it. */
    private fun metadataCommand(argLine: String): ParsedInput {
        val trimmed = argLine.trim()
        val space = trimmed.indexOfFirst { it.isWhitespace() }
        val sub = (if (space < 0) trimmed else trimmed.substring(0, space)).lowercase()
        val rest = if (space < 0) "" else trimmed.substring(space + 1).trim()
        return when (sub) {
            "set" -> {
                val at = rest.indexOfFirst { it.isWhitespace() }
                if (rest.isEmpty() || at < 0) ParsedInput.Local("usage: /metadata set <key> <value>", isError = true)
                else metadataSet(rest.substring(0, at).lowercase(), rest.substring(at + 1))
            }
            "del", "unset", "delete" ->
                if (rest.isEmpty() || rest.any { it.isWhitespace() }) ParsedInput.Local("usage: /metadata del <key>", isError = true)
                else metadataSet(rest.lowercase(), null)
            "list" -> ParsedInput.Metadata("METADATA ${rest.split(Regex("\\s+"))[0].ifEmpty { "*" }} LIST", write = false)
            "clear" -> ParsedInput.Metadata("METADATA * CLEAR", write = true)
            else -> ParsedInput.Local(
                "usage: /metadata set <key> <value> · /metadata del <key> · /metadata list [nick|#chan] · /metadata clear",
                isError = true,
            )
        }
    }

    /** Split "target rest of message" into (target, body); null if no body. */
    private fun splitTargetAndBody(s: String): Pair<String, String>? {
        val i = s.indexOf(' ')
        if (i == -1) return null
        val target = s.substring(0, i).trim()
        val body = s.substring(i + 1).trim()
        if (target.isEmpty() || body.isEmpty()) return null
        return target to body
    }

    /** If [s] begins with a channel token, peel it off; else use [fallback]. */
    private fun channelAndRest(s: String, fallback: String): Pair<String, String> {
        val i = s.indexOf(' ')
        val first = if (i == -1) s else s.substring(0, i)
        return if (isChannel(first)) {
            normalizeChannel(first) to (if (i == -1) "" else s.substring(i + 1).trim())
        } else {
            fallback to s
        }
    }

    private fun modeForNicks(s: String, currentTarget: String, flag: String): ParsedInput? {
        val (chan, tail) = channelAndRest(s, currentTarget)
        val nicks = tail.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (nicks.isEmpty()) return null
        val sign = flag[0]
        val letter = flag.substring(1)
        val flags = sign + letter.repeat(nicks.size)
        return ParsedInput.Ops(listOf(WireOp("raw", line = "MODE $chan $flags ${nicks.joinToString(" ")}")))
    }

    private fun normalizeChannel(name: String): String =
        if (isChannel(name)) name else "#$name"

    private val HELP = buildString {
        appendLine("Commands:")
        appendLine("/me, /msg, /query, /notice, /join, /part, /close, /clear")
        appendLine("/list [filter], /nick, /topic, /kick, /invite, /mode, /whois")
        appendLine("/op, /deop, /voice, /devoice, /ban, /unban")
        appendLine("/away [-all|-one] [message], /back [-all], /ctcp, /ping, /slap, /cycle, /quit")
        appendLine("/ns, /cs, /ms, /raw <line>")
        appendLine("/dcc [list], /dcc files, /dcc accept|reject|cancel|download|delete|retry <id>, /dcc clear [<id>]")
        appendLine("/dcc send [-passive] [-secure] <nick> [<file>], /dcc chat [-passive] [-secure] <nick>, /dcc close chat <nick>")
        appendLine("/xdcc add|addl|addm <bot> <packs>, /xdcc queue [<bot>], /xdcc rmp|rmpl|rmpm <bot> <packs>, /xdcc rmbot <bot>")
        appendLine("/relay [list], /relay add <nick> [pattern], /relay remove <nick> — e.g. /relay add bridge <{nick}> {message}")
        appendLine("/accept [nick…] (caller ID), /displayname <name>|off, /avatar <url>|off, /metadata set|del|list|clear")
        append("Prefix a literal slash with // to send it as a message.")
    }
}
