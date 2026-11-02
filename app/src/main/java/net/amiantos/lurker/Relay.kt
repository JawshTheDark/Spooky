// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

/**
 * Relay/bridge bots (Lurker #277, #801). A relay bot posts other people's lines
 * on IRC — bridged in from Discord, Matrix, another network — wrapped in an
 * envelope like `[Discord] <alice> hello`. Once the user marks the bot's nick
 * (server-side, per network), the line is shown as alice's, with the source
 * beside her name. A port of Lurker's shared/parseRelay.ts, so a mark set on
 * the web reads the same lines the same way here.
 *
 * Templates are literal text with `{source}`, `{nick}` and `{message}`
 * placeholders; the literals are escaped and the placeholders compiled to
 * groups, so a user's pattern can't inject regex. `{nick}` and `{message}` are
 * required.
 */

/** A mark: [nick] in the server's casing, [pattern] "" for the built-in formats. */
data class RelayBot(val networkId: Int, val nick: String, val pattern: String)

/** The speaker inside an envelope, and the platform tag when the template has one. */
data class RelayParse(val source: String?, val nick: String, val text: String)

sealed interface RelayCommand {
    data object List : RelayCommand
    data class Add(val nick: String, val pattern: String) : RelayCommand
    data class Remove(val nick: String) : RelayCommand
    data class Error(val message: String) : RelayCommand
}

object Relay {
    /** Tried in order when a mark has no pattern of its own. */
    val DEFAULT_TEMPLATES = listOf("[{source}] <{nick}> {message}", "<{nick}> {message}")

    /** A chain of marked bots is followed this many envelopes deep. */
    private const val MAX_DEPTH = 4

    /** Lurker's server caps a pattern here. */
    const val MAX_PATTERN = 512

    // mIRC formatting: colour (\x03[fg][,bg]), hex colour (\x04…), the toggles.
    private val FORMAT = Regex(
        "\u0003(?:\\d{1,2}(?:,\\d{1,2})?)?|\u0004(?:[0-9A-Fa-f]{6}(?:,[0-9A-Fa-f]{6})?)?|[\u0002\u000f\u0011\u0016\u001d\u001e\u001f]",
    )

    // Channel-rank glyphs a bot carries over from the source channel (`<+FAST>`).
    private val MEMBER_PREFIX = Regex("^[~&@%+]+")

    private val PLACEHOLDER = Regex("\\{(source|nick|message)\\}")

    private class Compiled(val re: Regex, val slots: List<String>)

    private val cache = HashMap<String, Compiled?>()

    fun stripFormatting(text: String): String = FORMAT.replace(text, "")

    /** Where the [visibleOffset]th character of the stripped text sits in [raw]. */
    fun rawIndexForVisibleOffset(raw: String, visibleOffset: Int): Int {
        if (visibleOffset <= 0) return 0
        var visible = 0
        var i = 0
        while (i < raw.length) {
            if (visible >= visibleOffset) return i
            val m = FORMAT.matchAt(raw, i)
            if (m != null && m.value.isNotEmpty()) {
                i += m.value.length
            } else {
                visible++
                i++
            }
        }
        return raw.length
    }

    /** True when [template] has the placeholders a pattern needs. */
    fun isValidTemplate(template: String): Boolean = compile(template) != null

    @Synchronized
    private fun compile(template: String): Compiled? {
        if (cache.containsKey(template)) return cache[template]
        val slots = ArrayList<String>()
        val out = StringBuilder()
        var last = 0
        for (m in PLACEHOLDER.findAll(template)) {
            out.append(Regex.escape(template.substring(last, m.range.first)))
            val slot = m.groupValues[1]
            slots.add(slot)
            // {message} runs to the end of the line; {nick} and {source} are lazy
            // so the literals after them bound the match. A nick has no spaces.
            out.append(
                when (slot) {
                    "message" -> "(.*)"
                    "nick" -> "(\\S+?)"
                    else -> "(.+?)"
                },
            )
            last = m.range.last + 1
        }
        out.append(Regex.escape(template.substring(last)))
        val compiled = if ("nick" !in slots || "message" !in slots) {
            null
        } else {
            runCatching { Compiled(Regex("^$out$"), slots) }.getOrNull()
        }
        cache[template] = compiled
        return compiled
    }

    /**
     * The speaker inside [body], or null when no template matches (the line then
     * stays the bot's). A blank [customPattern] uses [DEFAULT_TEMPLATES].
     */
    fun parse(body: String?, customPattern: String? = null): RelayParse? {
        if (body.isNullOrEmpty()) return null
        // Bots colour the tag and nick, so match on the stripped text and map the
        // message back onto the raw body to keep its own formatting.
        val stripped = stripFormatting(body)
        val custom = customPattern.orEmpty().trim()
        val templates = if (custom.isNotEmpty()) listOf(custom) else DEFAULT_TEMPLATES
        for (template in templates) {
            val compiled = compile(template) ?: continue
            val match = compiled.re.find(stripped) ?: continue
            var source: String? = null
            var nick = ""
            var text = ""
            compiled.slots.forEachIndexed { i, slot ->
                val value = match.groupValues.getOrNull(i + 1).orEmpty()
                when (slot) {
                    "source" -> source = value
                    "nick" -> nick = value
                    else -> text = value
                }
            }
            nick = nick.replace(MEMBER_PREFIX, "")
            if (nick.isEmpty()) continue
            var display = text
            if (compiled.slots.last() == "message") {
                display = body.substring(rawIndexForVisibleOffset(body, stripped.length - text.length))
            }
            return RelayParse(source, nick, display)
        }
        return null
    }

    /**
     * [parse], then keep unwrapping while the speaker revealed is itself a marked
     * bot (a bridge relaying a bridge). [markFor] answers for a nick: null when
     * unmarked, else its pattern ("" for the defaults). The innermost speaker wins,
     * as does the innermost source tag actually present.
     */
    fun parseChain(body: String?, pattern: String?, markFor: (String) -> String?): RelayParse? {
        var parsed = parse(body, pattern) ?: return null
        for (depth in 1 until MAX_DEPTH) {
            val inner = markFor(parsed.nick) ?: break
            val next = parse(parsed.text, inner) ?: break
            parsed = RelayParse(next.source ?: parsed.source, next.nick, next.text)
        }
        return parsed
    }

    private val ADD = setOf("add", "mark", "set")
    private val REMOVE = setOf("remove", "rm", "del", "delete", "unmark")
    const val USAGE = "usage: /relay [list] · /relay add <nick> [pattern] · /relay remove <nick>"

    /** The /relay command, as the web client reads it: the pattern is the raw rest of the line. */
    fun parseCommand(argLine: String): RelayCommand {
        val trimmed = argLine.trim()
        if (trimmed.isEmpty()) return RelayCommand.List
        val (sub, rest) = peel(trimmed)
        val verb = sub.lowercase()
        if (verb == "list" || verb == "ls") return RelayCommand.List
        if (verb in ADD) {
            val (nick, pattern) = peel(rest)
            if (nick.isEmpty()) return RelayCommand.Error("usage: /relay add <nick> [pattern]")
            return RelayCommand.Add(nick, unquote(pattern.trim()))
        }
        if (verb in REMOVE) {
            val (nick, _) = peel(rest)
            if (nick.isEmpty()) return RelayCommand.Error("usage: /relay remove <nick>")
            return RelayCommand.Remove(nick)
        }
        return RelayCommand.Error("unknown subcommand \"$sub\". $USAGE")
    }

    private fun peel(s: String): Pair<String, String> {
        val m = Regex("^(\\S+)\\s*([\\s\\S]*)$").find(s.trimStart()) ?: return "" to ""
        return m.groupValues[1] to m.groupValues[2]
    }

    private fun unquote(s: String): String =
        if (s.length >= 2 && ((s.first() == '"' && s.last() == '"') || (s.first() == '\'' && s.last() == '\''))) {
            s.substring(1, s.length - 1)
        } else {
            s
        }
}
