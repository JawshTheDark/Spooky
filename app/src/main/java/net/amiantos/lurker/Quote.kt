// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle

/**
 * Quote (Lurker 2.5): Reply's plain-text sibling, for people whose clients never
 * see a reply tag. It puts `> <alice> what she said` above the draft on its own
 * line; the answer goes under it, and the two go out as two messages that read
 * the same everywhere. On the way in, any line that starts with `> ` — whoever
 * wrote it — is drawn as a block quote. Mirrors vue_client/src/utils/replyText.ts.
 */
object Quote {
    /** What a line may carry when the network hasn't said (shared/wireBudget.ts). */
    const val TEXT_BUDGET = 350

    /** How IRC writes the author: `<nick>`, `* nick` for an action, `-nick-` for a notice. */
    private fun marks(type: String): Pair<String, String> = when (type) {
        "action" -> "* " to ""
        "notice" -> "-" to "-"
        else -> "<" to ">"
    }

    private fun bytes(s: String) = s.toByteArray(Charsets.UTF_8).size

    /** [s] cut to at most [max] UTF-8 bytes, never mid-character. */
    private fun clip(s: String, max: Int): String {
        if (bytes(s) <= max) return s
        val out = StringBuilder()
        var used = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val n = bytes(String(Character.toChars(cp)))
            if (used + n > max) break
            out.appendCodePoint(cp)
            used += n
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    /**
     * The Quote action's line for a message, as prose: formatting codes out, new
     * lines folded to spaces, clipped with an ellipsis to one wire line so the
     * quote alone never splits in two.
     */
    fun lineFor(nick: String, type: String, text: String, maxBytes: Int = TEXT_BUDGET): String {
        val (open, close) = marks(type)
        val head = "> $open$nick$close"
        val flat = Mirc.strip(text).replace(Regex("\\s*\\n\\s*"), " ").trim()
        if (flat.isEmpty()) return head
        val full = "$head $flat"
        if (bytes(full) <= maxBytes) return full
        // The space and the ellipsis (three bytes) come out of the text's room.
        val room = maxBytes - bytes(head) - 1 - 3
        if (room <= 0) return head
        return "$head ${clip(flat, room).trimEnd()}…"
    }

    /** What Copy quote copies (Lurker 2.6): the Quote line without the `> ` or the
     *  wire budget — `<alice> what she said`, read by the same rules. */
    fun copyText(nick: String, type: String, text: String): String {
        val (open, close) = marks(type)
        val head = "$open$nick$close"
        val flat = Mirc.strip(text).replace(Regex("\\s*\\n\\s*"), " ").trim()
        return if (flat.isEmpty()) head else "$head $flat"
    }

    /** A draft's leading quote lines (each `> …` and complete) and the answer under them. */
    fun split(draft: String): Pair<String, String> {
        val quotes = Regex("^(?:> [^\\n]*\\n)*").find(draft)?.value.orEmpty()
        return quotes to draft.substring(quotes.length)
    }

    /** Where a new quote goes: under the quotes already there, above the answer. */
    fun intoDraft(draft: String, line: String): String {
        val (quotes, body) = split(draft)
        return quotes + line + "\n" + body
    }

    /** Whether one line of a message opens a quote: `> …`, or the mark alone (a quoted picture). */
    fun opensQuote(line: String): Boolean = line == ">" || line.startsWith("> ")

    /** The [start, end) of each line in [text] that opens a quote. */
    fun quoteLines(text: String): List<IntRange> {
        val out = ArrayList<IntRange>()
        var start = 0
        while (start <= text.length) {
            val nl = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            if (opensQuote(text.substring(start, nl))) out += start until nl
            start = nl + 1
        }
        return out
    }

    /**
     * [body] with each quoting line drawn as a quote: a bar where the `> ` was and
     * the rest faded and italic. The line's own links and colours stay.
     */
    fun styled(body: AnnotatedString, faded: Color, bar: Color): AnnotatedString {
        val lines = quoteLines(body.text)
        if (lines.isEmpty()) return body
        return buildAnnotatedString {
            var at = 0
            for (r in lines) {
                append(body.subSequence(at, r.first))
                withStyle(SpanStyle(color = bar)) { append("▎ ") }
                val textStart = minOf(r.first + 2, r.last + 1)
                withStyle(SpanStyle(color = faded, fontStyle = FontStyle.Italic)) {
                    append(body.subSequence(textStart, r.last + 1))
                }
                at = r.last + 1
            }
            append(body.subSequence(at, body.length))
        }
    }
}
