// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

/**
 * Nick / command completion candidate ranking — pure so it's JVM-testable.
 * Mirrors the web client's nickCompletion: recent speakers first (newest-first),
 * then the rest of the roster alphabetically; self excluded; case-insensitive
 * prefix match.
 */
object Completion {
    /** The word being typed around [cursor] in [text], and its start index. */
    data class Word(val text: String, val start: Int)

    /** Extract the whitespace-delimited word the cursor sits in (empty if none). */
    fun wordAt(text: String, cursor: Int): Word {
        val c = cursor.coerceIn(0, text.length)
        var start = c
        while (start > 0 && !text[start - 1].isWhitespace()) start--
        var end = c
        while (end < text.length && !text[end].isWhitespace()) end++
        return Word(text.substring(start, end), start)
    }

    /**
     * Nick candidates for [prefix]: recents (newest-first) ahead of the rest of
     * [members], both prefix-filtered case-insensitively, self dropped, deduped.
     */
    fun nicks(prefix: String, recents: List<String>, members: List<String>, self: String?): List<String> {
        val p = prefix.lowercase()
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        fun consider(nick: String) {
            if (nick.isEmpty()) return
            if (self != null && nick.equals(self, true)) return
            if (!nick.lowercase().startsWith(p)) return
            if (seen.add(nick.lowercase())) out.add(nick)
        }
        recents.forEach(::consider)
        members.sortedBy { it.lowercase() }.forEach(::consider)
        return out
    }

    /**
     * Channel candidates for a "#…" prefix: case-insensitive prefix match over
     * [known] channel names (open buffers + any /LIST results), deduped,
     * alphabetical. The prefix includes its leading "#" (or "&", etc.).
     */
    fun channels(prefix: String, known: List<String>): List<String> {
        val p = prefix.lowercase()
        val seen = HashSet<String>()
        return known.asSequence()
            .filter { it.lowercase().startsWith(p) && seen.add(it.lowercase()) }
            .sortedBy { it.lowercase() }
            .toList()
    }

    /** Command candidates for a "/verb" prefix (built-ins + any user aliases). */
    fun commands(prefix: String, verbs: List<String>): List<String> {
        val p = prefix.removePrefix("/").lowercase()
        // Case-insensitive on the candidate too — user aliases can be mixed-case.
        return verbs.filter { it.lowercase().startsWith(p) }.sorted().map { "/$it" }
    }

    /** The built-in slash verbs the app understands (for command completion). */
    val VERBS = listOf(
        "me", "msg", "query", "notice", "join", "part", "close", "clear", "nick",
        "topic", "kick", "invite", "mode", "op", "deop", "voice", "devoice",
        "halfop", "dehalfop", "ban", "unban", "whois", "whowas", "raw", "quote",
        "ns", "cs", "ms", "away", "back", "ctcp", "ping", "slap", "quit", "cycle", "dcc",
        "hop", "e2e", "list", "channels", "help",
    )
}
