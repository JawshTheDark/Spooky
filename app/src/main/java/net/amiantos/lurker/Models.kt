// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

/** A configured IRC network as the server describes it. */
data class Network(
    val id: Int,
    val name: String,
    val nick: String?,
    val connected: Boolean,
)

/**
 * The mode vocabulary a network's IRC server advertises via ISUPPORT (plus the
 * server software string from RPL_MYINFO), so the UI can offer exactly the
 * channel/user modes THIS ircd supports instead of a hardcoded guess.
 *
 * Absent (no entry in the map) for a vanilla Lurker server that doesn't send the
 * `server-info` frame yet — callers fall back to a curated default list, so the
 * app still works fully against an unpatched server.
 */
data class ServerInfo(
    /** Raw ircd/version string (e.g. "solanum-1.0-dev"); may be empty. */
    val software: String,
    /** ISUPPORT NETWORK token (e.g. "Libera.Chat"). */
    val network: String,
    /** ISUPPORT CHANTYPES (e.g. "#" or "#&"). */
    val chanTypes: String,
    /** CHANMODES group A — list modes (bans/exceptions); always take a param. */
    val typeA: Set<Char>,
    /** CHANMODES group B — always take a param when set/unset (e.g. key). */
    val typeB: Set<Char>,
    /** CHANMODES group C — take a param only when set (e.g. limit). */
    val typeC: Set<Char>,
    /** CHANMODES group D — paramless flag modes (moderated, invite-only, …). */
    val typeD: Set<Char>,
    /** PREFIX ladder high→low as (mode, symbol): e.g. o→@, v→+. */
    val prefixes: List<Pair<Char, Char>>,
) {
    /** Paramless flag modes — the toggle chips in the channel control panel. */
    val flagModes: Set<Char> get() = typeD

    /** True if [mode] takes a parameter (A always, B always, C when setting). */
    fun takesParam(mode: Char): Boolean = mode in typeA || mode in typeB || mode in typeC

    companion object {
        /** Build from the raw ISUPPORT strings the server/KICL hand us. */
        fun parse(
            software: String,
            network: String,
            chanModes: String,
            prefix: String,
            chanTypes: String,
        ): ServerInfo {
            val g = chanModes.split(',')
            fun grp(i: Int): Set<Char> = g.getOrNull(i)?.toSet() ?: emptySet()
            // PREFIX "(qaohv)~&@%+" -> [(q,~),(a,&),(o,@),(h,%),(v,+)].
            val open = prefix.indexOf('(')
            val close = prefix.indexOf(')')
            val prefixes = if (open == 0 && close in 2 until prefix.length) {
                val modes = prefix.substring(1, close)
                val syms = prefix.substring(close + 1)
                modes.zip(syms) { m, s -> m to s }
            } else {
                emptyList()
            }
            return ServerInfo(
                software = software,
                network = network,
                chanTypes = chanTypes.ifEmpty { "#" },
                typeA = grp(0),
                typeB = grp(1),
                typeC = grp(2),
                typeD = grp(3),
                prefixes = prefixes,
            )
        }
    }
}

/** A conversation: a channel, a DM, or the app-scoped system buffer. */
data class Buffer(
    /** null for the system buffer, which is read-only. */
    val networkId: Int?,
    val target: String,
    val networkName: String,
) {
    val key: String get() = "${networkId ?: "sys"}::$target"
    val isChannel: Boolean get() = Commands.isChannel(target)
    val isSystem: Boolean get() = networkId == null
    val isServerBuffer: Boolean get() = target.startsWith(":server:")

    /** A `=nick` DCC CHAT (the irssi convention). A buffer NAME, never an IRC
     *  target: the server routes anything sent here over the direct socket. */
    val isDccChat: Boolean get() = Commands.isDccChat(target)

    /** Who a DM or DCC chat is with — the peer's nick, without the `=`. */
    val peer: String get() = Commands.dccPeer(target)

    /** What the UI calls this buffer; raw pseudo-targets get friendly names. */
    val displayName: String get() = if (isServerBuffer) "Server" else target
}

/** A peer's inbound DCC CHAT offer awaiting our answer. Nothing is dialled until
 *  it's accepted; the server expires it after ten minutes. */
data class DccChatOffer(val networkId: Int, val nick: String, val passive: Boolean) {
    val key: String get() = "$networkId::${nick.lowercase()}"
}

/** A rendered line. System events (join/part/…) carry [system] = true. */
data class Msg(
    val id: Long,
    val type: String,
    val nick: String,
    val text: String,
    val self: Boolean,
    val time: String? = null,
    val system: Boolean = false,
    /** Log severity for :system: and E2E lines (info/warn/error), null elsewhere. */
    val level: String? = null,
    /** True when this message arrived end-to-end encrypted (server-decrypted). */
    val e2e: Boolean = false,
    /** True when this message matched a highlight rule (a mention / your nick) —
     *  drives the gold highlight background. */
    val matched: Boolean = false,
    /** Set on an undelivered-send line; keys the [FailedSend] holding the original
     *  text so the row can offer Resend / Discard. */
    val failedId: String? = null,
)

/** A message that never made it out — the socket was down, the server rejected it,
 *  or no ack arrived. Retained so the user can resend it verbatim. */
data class FailedSend(
    val id: String,
    val bufferKey: String,
    val networkId: Int,
    val target: String,
    /** The original WireOp type: "send" | "action" | "notice". */
    val type: String,
    val text: String,
    val reason: String,
)


/** An inbound DCC file transfer, mirrored from the server's transfer rows. */
data class DccTransfer(
    val id: Int,
    val peerNick: String,
    val filename: String,
    val state: String,
    val received: Long,
    val total: Long,
    val direction: String,
    val error: String?,
) {
    val progress: Float
        get() = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else 0f
    val isPending: Boolean get() = state == "pending_approval"
    val isSend: Boolean get() = direction == "send"
    val isActive: Boolean
        get() = state in setOf(
            "connecting", "receiving", "stalled", "verifying", "requested",
            "offering", "sending", // outgoing-send states
        )
    val isTerminal: Boolean
        get() = state in setOf("completed", "failed", "rejected", "cancelled")
}

/**
 * A message worth notifying about (a highlight, DM, or notify-always channel),
 * flattened for the Notifier. [target] is the buffer to open on tap.
 */
data class NotifiableEvent(
    val networkId: Int?,
    val target: String,
    val nick: String,
    val text: String,
    val isDm: Boolean,
    /** Id of the message that triggered this — lets a tapped notification jump
     *  straight to it (0 if unknown). */
    val msgId: Long = 0,
)

/**
 * One entry in the user's server-owned favorites list — the contacts successor
 * (Lurker 2.0 removed contacts entirely).
 *
 * The server owns ONE global ordered list spanning networks; `favorites-changed`
 * ships it wholesale. The UI renders it as two kind-filtered sections: DMs under
 * "Friends", channels under "Favorites" — one flag, two labels. [bufferId] is the
 * stable identity (it survives renames) and the only form `reorder-favorites`
 * accepts, since names can't address across networks.
 */
data class FavoriteEntry(val networkId: Int, val target: String, val bufferId: Int) {
    val key: String get() = "$networkId::$target"
    val isChannel: Boolean get() = Commands.isChannel(target)
}

/** A server-synced custom slash alias: /name expands to expansion with $1..$9 params. */
data class AliasEntry(val id: Int, val name: String, val expansion: String)

/**
 * An irssi-style ignore rule (issue #301). AND-ed dimensions: [mask] (who),
 * [channels] (where), [pattern] (what text), [levels] (which event types + the
 * special modifiers NOHIGHLIGHT/NONOTIFY/NOUNREAD/ALL). [isExcept] inverts it
 * (an un-ignore). [networkId] null = a global rule (every network).
 */
data class IgnoreRule(
    val id: Long,
    val networkId: Int?,
    val mask: String?,
    val channels: List<String>?,
    val pattern: String?,
    val patternKind: String,
    val levels: List<String>,
    val isExcept: Boolean,
) {
    val who: String get() = mask?.takeIf { it.isNotBlank() } ?: "anyone"
    val levelsLabel: String get() = if (levels.isEmpty()) "all" else levels.joinToString(", ") { it.lowercase() }
}

/** A row in the /LIST channel browser. */
data class ChannelListing(val channel: String, val users: Int, val topic: String)

/** One search / highlights result row (decorated DB message: body + createdAt). */
data class SearchResult(
    val id: Long,
    val networkId: Int,
    val target: String,
    val nick: String,
    val body: String,
    val createdAt: String?,
)

/**
 * A channel member from `names` frames. `modes` are IRC mode letters
 * (q owner / a admin / o op / h halfop / v voice); the glyph mapping is
 * client-side, mirroring the web client (the server doesn't read ISUPPORT
 * PREFIX yet).
 */
data class Member(
    val nick: String,
    val modes: List<String> = emptyList(),
    val away: Boolean = false,
    val host: String? = null,
) {
    val prefix: String
        get() = when {
            "q" in modes -> "~"
            "a" in modes -> "&"
            "o" in modes -> "@"
            "h" in modes -> "%"
            "v" in modes -> "+"
            else -> ""
        }

    /** Sort rank: owner first, plain members last. */
    val rank: Int
        get() = when {
            "q" in modes -> 0
            "a" in modes -> 1
            "o" in modes -> 2
            "h" in modes -> 3
            "v" in modes -> 4
            else -> 5
        }

    /** Can this member moderate (op-gated menu actions)? Mirrors the web's MODERATE_MODES. */
    val canModerate: Boolean get() = modes.any { it == "q" || it == "a" || it == "o" || it == "h" }

    /** Ban mask, host-based when the host is known (mirrors the web client). */
    val banMask: String get() = host?.let { "*!*@$it" } ?: "$nick!*@*"
}

/**
 * A network row as `/api/networks` REST returns it (config, not live state —
 * live connected/nick state rides the WS snapshot into [LurkerClient.networks]).
 */
data class NetworkConfig(
    val id: Int,
    val name: String,
    val host: String,
    val port: Int,
    val tls: Boolean,
    val nick: String,
    val username: String?,
    val realname: String?,
    val autoconnect: Boolean,
    val hasPassword: Boolean,
    val hasSaslPassword: Boolean,
    val saslAccount: String?,
    val blocked: Boolean,
    /** Direct-mode endpoint kind ("direct" | "soju" | "znc"); ignored in Lurker mode. */
    val type: String = "direct",
)

/**
 * One entry from the settings registry (`/api/settings/bootstrap`). The screen
 * renders generically off [type], so the whole registry is editable without the
 * client hard-coding any particular key.
 */
data class SettingOption(
    val key: String,
    val label: String,
    val type: String, // bool | int | string | secret | color | enum | string-list
    val group: String,
    val category: String = "",
    val description: String = "",
    val default: Any? = null,
    val choices: List<String> = emptyList(),
    val min: Int? = null,
    val max: Int? = null,
    /**
     * Instance feature this setting depends on (matched against `/api/config`'s
     * `features`), or null when it always applies.
     *
     * The server does NOT filter these out of the bootstrap payload, so a
     * registry-driven settings screen renders authentic-looking toggles for
     * features the instance never mounted — you'd flip one and get silence.
     * Hiding is the client's job; see [LurkerClient.settingAvailable].
     */
    val requiresFeature: String? = null,
)
