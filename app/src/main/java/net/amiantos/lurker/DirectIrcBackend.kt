// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0
package net.amiantos.lurker

import android.content.Context
import net.engio.mbassy.listener.Handler
import org.json.JSONObject
import org.kitteh.irc.client.library.Client
import org.kitteh.irc.client.library.event.channel.ChannelCtcpEvent
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent
import org.kitteh.irc.client.library.event.channel.ChannelNoticeEvent
import org.kitteh.irc.client.library.event.channel.ChannelPartEvent
import org.kitteh.irc.client.library.event.channel.ChannelTopicEvent
import org.kitteh.irc.client.library.event.channel.ChannelUsersUpdatedEvent
import org.kitteh.irc.client.library.event.capabilities.CapabilitiesSupportedListEvent
import org.kitteh.irc.client.library.event.helper.ServerMessageEvent
import org.kitteh.irc.client.library.event.client.ClientNegotiationCompleteEvent
import org.kitteh.irc.client.library.event.client.ClientReceiveCommandEvent
import org.kitteh.irc.client.library.event.client.ClientReceiveNumericEvent
import org.kitteh.irc.client.library.event.connection.ClientConnectionClosedEvent
import org.kitteh.irc.client.library.event.connection.ClientConnectionFailedEvent
import org.kitteh.irc.client.library.event.user.PrivateCtcpQueryEvent
import org.kitteh.irc.client.library.event.user.PrivateMessageEvent
import org.kitteh.irc.client.library.event.user.PrivateNoticeEvent
import org.kitteh.irc.client.library.event.user.UserNickChangeEvent
import org.kitteh.irc.client.library.event.user.UserQuitEvent
import org.kitteh.irc.client.library.feature.auth.SaslPlain
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Registration/auth-rejection numerics worth surfacing: bad password/token
 *  (464), banned (465/466), and SASL failures (904/905/906). */
private val AUTH_ERROR_NUMERICS = setOf(464, 465, 466, 904, 905, 906)

/**
 * Direct-IRC / bouncer backend. Subclasses [LurkerClient] to inherit all the
 * observable Compose state + buffer/roster/unread helpers, overriding only the
 * transport methods to speak raw IRC (via KICL). A bouncer (soju/ZNC) is just an
 * IRC server here — bind one upstream network with `user/network` in the
 * username/SASL field.
 */
class DirectIrcBackend(appContext: Context) : LurkerClient() {

    override val serverFeatures: Boolean get() = false

    private val store = DirectNetworkStore(appContext)
    private val manager = KiclManager()
    private val nextMsgId = AtomicLong(1)
    private val nextIgnoreId = AtomicLong(1)

    override fun start(prefs: Prefs) {
        loggedIn = true
        status = "Direct IRC mode"
        ignores.clear()
        ignores.addAll(store.loadIgnores())
        nextIgnoreId.set((ignores.maxOfOrNull { it.id } ?: 0L) + 1L)
        // Restore persisted message tails so buffers + history survive a restart
        // (direct networks have no server-side backlog; a bouncer re-sends its own).
        store.loadMessages().forEach { pb ->
            pb.networkId?.let { networkNames[it] = pb.networkName }
            val b = ensureBuffer(pb.networkId, pb.target)
            mergeInto(b.key, pb.msgs, replace = true)
        }
        nextMsgId.set(maxOf(nextMsgId.get(), (messagesByBuffer.values.flatten().maxOfOrNull { it.id } ?: 0L) + 1L))
        refreshConfigs()
        store.list().filter { it.autoconnect }.forEach { connectNetwork(it) }
    }

    private fun persistMessages() {
        val pbs = buffers.filter { it.networkId != null }.map { b ->
            DirectNetworkStore.PersistedBuffer(b.networkId, b.target, b.networkName, messagesByBuffer[b.key] ?: emptyList())
        }
        io.execute { runCatching { store.saveMessages(pbs) } }
    }

    // Ignores are server-synced in Lurker mode; here they're local + persisted.
    override fun addIgnore(
        networkId: Int?, mask: String?, levels: List<String>, isExcept: Boolean,
        pattern: String?, channels: List<String>?,
    ) {
        ignores.add(IgnoreRule(nextIgnoreId.getAndIncrement(), networkId, mask, channels, pattern, "substr", levels, isExcept))
        store.saveIgnores(ignores.toList())
    }

    override fun removeIgnore(networkId: Int?, id: Long) {
        ignores.removeAll { it.id == id }
        store.saveIgnores(ignores.toList())
    }

    private fun refreshConfigs() = post {
        networkConfigs.clear()
        networkConfigs.addAll(store.list().map { it.toConfig() })
        // Seed a network row (disconnected) for each config so the sidebar shows it
        // even before it connects, and record its name so buffers group under it.
        store.list().forEach { n ->
            networkNames[n.id] = n.name
            if (networks[n.id] == null) networks[n.id] = Network(n.id, n.name, null, false)
        }
    }

    private fun buildClient(net: StoredNet): Client {
        val server = Client.builder()
            .nick(net.nick)
            .user(net.username?.ifBlank { null } ?: net.nick)
            .realName(net.realname ?: net.nick)
            .server()
            .host(net.host)
            .port(
                net.port,
                if (net.tls) Client.Builder.Server.SecurityType.SECURE
                else Client.Builder.Server.SecurityType.INSECURE,
            )
        net.serverPassword?.let { server.password(it) }
        val client = server.then().build()
        if (!net.saslAccount.isNullOrEmpty() && !net.saslPassword.isNullOrEmpty()) {
            client.authManager.addProtocol(SaslPlain(client, net.saslAccount, net.saslPassword))
        }
        return client
    }

    // Last error line surfaced per networkId, to collapse KICL's retry spam.
    private val lastSurfacedError = mutableMapOf<Int, String>()

    // Stable child networkId per (soju control id, upstream network name).
    private val sojuChildIds = mutableMapOf<String, Int>()
    private var nextChildId = 100_000

    private fun connectNetwork(net: StoredNet) {
        post {
            networks[net.id] = Network(net.id, net.name, null, false)
            status = "Connecting to ${net.name}…"
        }
        io.execute {
            runCatching {
                val client = buildClient(net)
                // A type="soju" config with no /network in its username is a soju
                // control connection — it discovers upstream networks. Children
                // (type="direct", username user/network) are normal connections.
                client.eventManager.registerEventListener(Listener(net.id, client, isSojuControl = net.type == "soju"))
                // Replace, never stack: a second live client for the same network
                // doubled every incoming line (both deliver) and kept the old
                // connection running unseen.
                manager.shutdown(net.id)
                manager.put(net.id, client)
                client.connect()
            }.onFailure { e ->
                post { status = "Couldn't connect to ${net.name}: ${e.message}" }
            }
        }
    }

    /** Open a per-upstream-network binding on a soju bouncer (username user/network).
     *  NOTE: soju.im/bouncer-networks discovery is implemented from spec and needs
     *  verification against a real soju instance. */
    private fun connectSojuChild(control: StoredNet, soju: SojuNetwork) {
        val key = "${control.id}/${soju.name}"
        val childId = sojuChildIds.getOrPut(key) { nextChildId++ }
        if (manager.get(childId) != null) return // already connected
        val baseUser = control.username?.ifBlank { null } ?: control.nick
        val child = control.copy(id = childId, name = soju.name, username = "$baseUser/${soju.name}", type = "direct")
        post {
            networkNames[childId] = soju.name
            networks[childId] = Network(childId, soju.name, null, false)
            if (networkConfigs.none { it.id == childId }) networkConfigs.add(child.toConfig())
        }
        connectNetwork(child)
    }

    /** KICL dispatches on Netty threads; every handler hops to the main thread
     *  before touching Compose snapshot state (mirrors LurkerClient.post{}). */
    private inner class Listener(
        val networkId: Int,
        val client: Client,
        val isSojuControl: Boolean = false,
    ) {
        private fun netName() = networks[networkId]?.name ?: "network"
        private fun buf(target: String) = Buffer(networkId, target, netName())
        private fun myNick() = client.nick

        // soju.im/bouncer-networks: request the cap when the bouncer offers it.
        @Handler
        fun onCaps(e: CapabilitiesSupportedListEvent) {
            if (isSojuControl && e.supportedCapabilities.any { it.name == "soju.im/bouncer-networks" }) {
                e.addRequest("soju.im/bouncer-networks")
            }
        }

        // BOUNCER NETWORK <id> <attrs> — one per upstream network on the bouncer.
        @Handler
        fun onBouncer(e: ClientReceiveCommandEvent) {
            if (!isSojuControl || !e.command.equals("BOUNCER", ignoreCase = true)) return
            val soju = SojuBouncer.parseNetworkLine(e.parameters) ?: return
            post {
                val control = store.get(networkId) ?: return@post
                if (soju.removed) return@post
                connectSojuChild(control, soju)
            }
        }

        @Handler
        fun onReady(e: ClientNegotiationCompleteEvent) = post {
            networks[networkId] = Network(networkId, netName(), client.nick, true)
            connected = networks.values.any { it.connected }
            lastSurfacedError.remove(networkId) // fresh success — arm errors again
            status = "Connected to ${netName()}"
            // Network-aware modes: KICL has parsed this connection's ISUPPORT by
            // now, so build the same ServerInfo the Lurker backend gets from its
            // `server-info` frame. runCatching so a KICL surprise can't break the
            // connect flow.
            runCatching {
                val si = client.serverInfo
                fun sup(k: String) = si.getISupportParameter(k).flatMap { it.value }.orElse("")
                val chanModes = sup("CHANMODES")
                val prefix = sup("PREFIX")
                if (chanModes.isNotEmpty() || prefix.isNotEmpty()) {
                    serverInfo[networkId] = ServerInfo.parse(
                        software = software,
                        network = sup("NETWORK").ifEmpty { netName() },
                        chanModes = chanModes,
                        prefix = prefix,
                        chanTypes = sup("CHANTYPES"),
                    )
                }
            }
            if (isSojuControl) {
                // Discover the bouncer's upstream networks; each becomes its own row.
                io.execute { runCatching { client.sendRawLine("BOUNCER LISTNETWORKS") } }
                return@post
            }
            // Always give the network a server buffer — it's the place with a
            // composer where the user can /join a channel (and it holds server
            // notices). Without it a fresh network with no channels is a dead end.
            val server = ensureBuffer(networkId, ":server:$networkId")
            mergeInto(
                server.key,
                listOf(Msg(0, "notice", "*", "Connected to ${netName()} as ${client.nick}. Type /join #channel to join a channel.", self = false, system = true)),
                replace = false,
            )
            store.get(networkId)?.channels
                ?.split(',', ' ')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?.forEach { io.execute { runCatching { client.addChannel(it) } } }
        }

        @Handler
        fun onClosed(e: ClientConnectionClosedEvent) = post {
            networks[networkId]?.let { networks[networkId] = it.copy(connected = false) }
            connected = networks.values.any { it.connected }
            // Surface WHY, unless KICL is going to auto-reconnect (then it's just
            // a blip). Prefer the last-authoritative cause; fall back to generic.
            if (!e.willAttemptReconnect()) {
                val cause = e.cause.map { it.message ?: it.javaClass.simpleName }.orElse(null)
                surfaceError(if (cause != null) "Disconnected: $cause" else "Disconnected from ${netName()}")
                forgetIfDead()
            }
        }

        /** KICL won't retry this client: drop it from the manager. While it sat
         *  there, the return-to-app and network-change revivals (which only start
         *  networks with NO client) skipped it, so the network stayed down until a
         *  manual reconnect. A user disconnect already removed it — leave that alone. */
        private fun forgetIfDead() {
            if (manager.get(networkId) === client) manager.shutdown(networkId)
        }

        // KICL's connect() is async — a DNS/TLS/refused failure happens on a Netty
        // thread and never reaches connectNetwork's runCatching. Catch it here so a
        // bad host or dropped network shows a reason instead of a silent red dot.
        @Handler
        fun onFailed(e: ClientConnectionFailedEvent) = post {
            networks[networkId]?.let { networks[networkId] = it.copy(connected = false) }
            connected = networks.values.any { it.connected }
            val cause = e.cause.map { it.message ?: it.javaClass.simpleName }.orElse("connection failed")
            surfaceError("Couldn't connect to ${netName()}: $cause")
            if (!e.willAttemptReconnect()) forgetIfDead()
        }

        // Registration rejections: soju/ZNC send `464 :<reason>` (bad password/token)
        // — plus the neighbouring auth numerics — then close. Show the reason.
        // RPL_MYINFO (004): "<nick> <servername> <version> <umodes> <chanmodes>".
        // Grab the server software/version string so the mode UI can show it.
        private var software = ""

        @Handler
        fun onNumeric(e: ClientReceiveNumericEvent) {
            if (e.numeric == 4) {
                (e.parameters.getOrNull(2)?.takeIf { it.isNotBlank() }
                    ?: e.parameters.getOrNull(1))?.let { software = it }
                return
            }
            if (e.numeric !in AUTH_ERROR_NUMERICS) return
            val reason = e.parameters.lastOrNull()?.takeIf { it.isNotBlank() }
            post { surfaceError(reason ?: "Authentication failed (${e.numeric})") }
        }

        // A server `ERROR :<reason>` line (bouncer closeWithError, K-line, etc.).
        @Handler
        fun onServerError(e: ClientReceiveCommandEvent) {
            if (!e.command.equals("ERROR", ignoreCase = true)) return
            val reason = e.parameters.lastOrNull()?.takeIf { it.isNotBlank() } ?: return
            post { surfaceError(reason) }
        }

        /** Post an error to the network's server buffer + status so a failure is
         *  visible whether or not any channel buffer exists yet. KICL retries on a
         *  timer, so collapse an identical back-to-back error (e.g. a permanently
         *  bad host or wrong password) to one line instead of spamming the buffer. */
        private fun surfaceError(text: String) {
            status = text
            DebugLog.e("irc", "${netName()}: $text")
            if (lastSurfacedError[networkId] == text) return
            lastSurfacedError[networkId] = text
            val server = ensureBuffer(networkId, ":server:$networkId")
            mergeInto(
                server.key,
                listOf(Msg(0, "error", "*", text, self = false, system = true)),
                replace = false,
            )
        }

        @Handler
        fun onChannelMessage(e: ChannelMessageEvent) =
            addMessage(networkId, e.channel.name, "message", e.actor.nick, e.message, mask = e.actor.name, src = e)

        /** The DM buffer a private line belongs in: the sender's when it was sent
         *  TO us, otherwise its target. A bouncer replays (and relays from your
         *  other devices) the DMs YOU sent, as `:you PRIVMSG bob` — filing those
         *  under the sender put your half of every conversation in a buffer named
         *  after yourself. */
        private fun dmBuffer(toClient: Boolean, sender: String, target: String) =
            if (toClient) sender else target

        @Handler
        fun onPrivateMessage(e: PrivateMessageEvent) =
            addMessage(networkId, dmBuffer(e.isToClient, e.actor.nick, e.target), "message", e.actor.nick, e.message, dm = true, mask = e.actor.name, src = e)

        // /me in a DM arrives as a private CTCP ACTION, which nothing handled — DM
        // actions just vanished. (Other CTCP queries stay KICL's to answer.)
        @Handler
        fun onPrivateAction(e: PrivateCtcpQueryEvent) {
            val m = e.message
            if (m.startsWith("ACTION ")) {
                addMessage(networkId, dmBuffer(e.isToClient, e.actor.nick, e.target), "action", e.actor.nick, m.removePrefix("ACTION "), dm = true, mask = e.actor.name, src = e)
            }
        }

        @Handler
        fun onChannelAction(e: ChannelCtcpEvent) {
            val m = e.message
            if (m.startsWith("ACTION ")) {
                addMessage(networkId, e.channel.name, "action", e.actor.nick, m.removePrefix("ACTION "), mask = e.actor.name, src = e)
            }
        }

        @Handler
        fun onChannelNotice(e: ChannelNoticeEvent) =
            addMessage(networkId, e.channel.name, "notice", e.actor.nick, e.message, mask = e.actor.name, src = e)

        @Handler
        fun onPrivateNotice(e: PrivateNoticeEvent) =
            addMessage(networkId, dmBuffer(e.isToClient, e.actor.nick, e.target), "notice", e.actor.nick, e.message, dm = true, mask = e.actor.name, src = e)

        @Handler
        fun onJoin(e: ChannelJoinEvent) = post {
            ensureBuffer(networkId, e.channel.name)
            rebuildRoster(e.channel.name)
            if (e.user.nick.equals(myNick(), true)) {
                noteJoinRequest(networkId, e.channel.name)
                pendingOpen = buf(e.channel.name)
            } else {
                sysLine(e.channel.name, "${e.user.nick} joined")
            }
        }

        @Handler
        fun onPart(e: ChannelPartEvent) = post {
            rebuildRoster(e.channel.name)
            if (!e.user.nick.equals(myNick(), true)) {
                val reason = e.message.takeIf { it.isNotBlank() }
                sysLine(e.channel.name, "${e.user.nick} left" + (reason?.let { " ($it)" } ?: ""))
            }
        }

        // A quit shows in EVERY channel we share with that user (as real clients
        // do) — check our roster before rebuilding, since rebuild drops them.
        @Handler
        fun onQuit(e: UserQuitEvent) = post {
            val nick = e.user.nick
            if (nick.equals(myNick(), true)) return@post
            val reason = e.message.takeIf { it.isNotBlank() }
            val line = "$nick quit" + (reason?.let { " ($it)" } ?: "")
            buffers.filter { it.networkId == networkId && it.isChannel }.forEach { b ->
                // Only the channels that actually held this nick changed. Skipping
                // the rest avoids re-materialising + re-sorting the roster of every
                // channel on the network for one quit — an O(channels x users) hit
                // on netsplit quit bursts that also needlessly recomposed the UI.
                val wasHere = members[b.key]?.any { it.nick.equals(nick, true) } == true
                if (!wasHere) return@forEach
                rebuildRoster(b.target)
                sysLine(b.target, line)
            }
        }

        @Handler
        fun onNick(e: UserNickChangeEvent) = post {
            val oldNick = e.oldUser.nick
            val newNick = e.newUser.nick
            val line = "$oldNick is now known as $newNick"
            buffers.filter { it.networkId == networkId && it.isChannel }.forEach { b ->
                val wasHere = members[b.key]?.any { it.nick.equals(oldNick, true) } == true
                if (!wasHere) return@forEach // untouched channel — don't rebuild its roster
                rebuildRoster(b.target)
                if (!oldNick.equals(myNick(), true)) sysLine(b.target, line)
            }
        }

        @Handler
        fun onUsers(e: ChannelUsersUpdatedEvent) = post { rebuildRoster(e.channel.name) }

        @Handler
        fun onTopic(e: ChannelTopicEvent) = post {
            sysLine(e.channel.name, "Topic: ${e.topic.value.orElse("")}")
        }

        private fun rebuildRoster(channel: String) {
            val ch = client.getChannel(channel).orElse(null) ?: return
            val roster = ch.users.map { u ->
                val modes = ch.getUserModes(u).orElse(null)?.map { it.char.toString() } ?: emptyList()
                Member(u.nick, modes, away = false, host = u.host)
            }
            // Op/voice first (Member.rank), then alphabetical — same as Lurker mode.
            members["$networkId::$channel"] = roster.sortedWith(compareBy({ it.rank }, { it.nick.lowercase() }))
        }

        private fun sysLine(target: String, text: String) {
            val b = ensureBuffer(networkId, target)
            mergeInto(b.key, listOf(Msg(0, "system", "*", text, self = false, system = true)), replace = false)
        }
    }

    private fun addMessage(
        networkId: Int, target: String, type: String, nick: String, text: String,
        dm: Boolean = false, mask: String = nick, src: ServerMessageEvent? = null,
    ) {
        // Read the IRCv3 tags on KICL's thread, before hopping to main.
        val msgid = src?.getTag("msgid")?.orElse(null)?.value?.orElse(null)
        val serverTime = src?.getTag("time")?.orElse(null)?.value?.orElse(null)
        addMessageOnMain(networkId, target, type, nick, text, dm, mask, msgid, serverTime)
    }

    private fun addMessageOnMain(
        networkId: Int, target: String, type: String, nick: String, text: String,
        dm: Boolean, mask: String, msgid: String?, serverTime: String?,
    ) = post {
        val myNick = networks[networkId]?.nick
        val self = nick.equals(myNick, true)
        // Client-side ignore filtering (Lurker does this server-side). Never drop
        // our own lines.
        val ignore = if (self) IgnoreOutcome()
        else IgnoreMatch.evaluate(ignores, networkId, if (dm) null else target, dm, mask, type, text)
        if (ignore.drop) return@post
        val b = ensureBuffer(networkId, target)
        val msg = Msg(
            nextMsgId.getAndIncrement(), type, nick, text, self = self,
            time = serverTime ?: Instant.now().toString(), msgid = msgid,
        )
        // A bouncer replays its buffer on every reconnect (and after a restart,
        // on top of the tail we restored from disk). Without this, each replayed
        // line got a fresh local id and appeared again.
        if (isReplayDuplicate(messagesByBuffer[b.key].orEmpty(), msg)) return@post
        if (!mergeInto(b.key, listOf(msg), replace = false)) return@post
        val highlight = !ignore.suppressHighlight && (dm || (myNick != null && text.contains(myNick, true)))
        if (!ignore.suppressUnread) {
            countUnread(b.key, JSONObject().put("matched", highlight).put("dm", dm), msg)
        }
        if (!ignore.suppressNotify &&
            shouldNotify(true, b.key == activeKey, self, msg.system, msg.id > 0, appForeground)
        ) {
            val (nNet, nTarget, sameBuffer) = notifyTargetFor(b)
            notificationSink?.invoke(NotifiableEvent(nNet, nTarget, nick, text, dm, if (sameBuffer) msg.id else 0))
        }
    }

    // ---- Sending: WireOps -> KICL -----------------------------------------
    override fun execute(buffer: Buffer, ops: List<WireOp>) {
        val networkId = buffer.networkId ?: return
        // Not connected to this network: retain any user text as a failed send the
        // user can retry, rather than dropping it silently.
        val client = manager.get(networkId) ?: run {
            post {
                for (op in ops) {
                    if (op.type !in SENDABLE_OPS) continue
                    val target = op.target ?: buffer.target
                    recordFailedSend(
                        "$networkId::$target", networkId, target, op.type, op.text.orEmpty(), "not connected",
                    )
                }
            }
            return
        }
        for (op in ops) {
            // KICL throws on CR/LF/NUL in a message (and on a space in a target),
            // and this runs on the main thread — so a pasted multi-line message, or
            // a sent Quote ("> …" + newline), crashed the app. IRC has no multi-line
            // message: send each line as its own, the way other clients do.
            // Lurker's server already splits the same way.
            fun guarded(block: () -> Unit) = try {
                block()
            } catch (e: Exception) {
                localNotice(buffer, "Couldn't send: ${e.message ?: e.javaClass.simpleName}")
            }
            when (op.type) {
                "send" -> {
                    val target = op.target ?: buffer.target
                    for (line in ircLines(op.text)) {
                        guarded {
                            client.sendMessage(target, line)
                            if (op.target == null) echoSelf(buffer, "message", line)
                        }
                    }
                }
                "action" -> for (line in ircLines(op.text)) {
                    guarded {
                        client.sendCtcpMessage(buffer.target, "ACTION $line")
                        echoSelf(buffer, "action", line)
                    }
                }
                "notice" -> for (line in ircLines(op.text)) {
                    guarded { client.sendNotice(op.target ?: buffer.target, line) }
                }
                "raw" -> for (line in ircLines(op.line)) guarded { client.sendRawLine(line) }
                "join" -> op.channel?.let { ch -> io.execute { runCatching { client.addChannel(ch) } } }
                "part" -> client.getChannel(op.target ?: buffer.target).ifPresent { it.part(op.reason ?: "") }
                "close" -> {
                    client.getChannel(buffer.target).ifPresent { it.part("") }
                    post { buffers.removeAll { it.key == buffer.key }; messagesByBuffer.remove(buffer.key) }
                }
                "clear" -> post { messagesByBuffer[buffer.key] = emptyList() }
                // /away: this network, or every connected one with -all.
                "away" -> {
                    val line = if (op.text.isNullOrBlank()) "AWAY" else "AWAY :${ircLines(op.text).joinToString(" ")}"
                    val targets = if (op.all == true) manager.entries().map { it.second } else listOf(client)
                    for (c in targets) guarded { c.sendRawLine(line) }
                }
                "e2e" -> localNotice(buffer, "End-to-end encryption isn't available in direct IRC mode.")
            }
        }
    }

    /** No echo-message cap yet → optimistically append our own line. */
    private fun echoSelf(buffer: Buffer, type: String, text: String) = post {
        val nick = networks[buffer.networkId]?.nick ?: "me"
        val msg = Msg(nextMsgId.getAndIncrement(), type, nick, text, self = true, time = Instant.now().toString())
        mergeInto(buffer.key, listOf(msg), replace = false)
    }

    override fun open(buffer: Buffer) {} // no server history to hydrate; buffer is local

    override fun onForeground() {
        appForeground = true
        // Recover any autoconnect network whose connection was lost (e.g. dropped
        // while backgrounded, or a Wi-Fi↔cellular switch). Only where we hold no
        // live client, to avoid racing KICL's own reconnection.
        store.list().filter { it.autoconnect && manager.get(it.id) == null }.forEach { connectNetwork(it) }
    }

    override fun onBackground() {
        appForeground = false
        persistMessages()
    }

    /**
     * See [LurkerClient.onNetworkAvailable]. KICL notices a dead socket only by
     * its own ping timeout, minutes later — so after a switch, reconnect every
     * live client now (soju children included), and after an outage reconnect the
     * ones that are down. Autoconnect networks with no client at all are started.
     */
    override fun onNetworkAvailable(switched: Boolean) {
        for ((id, c) in manager.entries()) {
            if (switched || networks[id]?.connected != true) {
                io.execute { runCatching { c.reconnect("Network changed") } }
            }
        }
        store.list().filter { it.autoconnect && manager.get(it.id) == null }.forEach { connectNetwork(it) }
    }

    override fun signOut() {
        manager.shutdownAll()
        super.signOut()
    }

    // Direct mode has no server settings registry — mark it loaded + empty so the
    // Settings screen shows the device-local prefs (theme, media, biometric,
    // background connect) instead of a scary "Settings unavailable" fetch error.
    override fun loadSettings() {
        post {
            settingsRegistry.clear()
            settingsValues.clear()
            settingsError = null
            settingsLoaded = true
        }
    }

    // ---- Network CRUD -> local store + KiclManager ------------------------
    override fun loadNetworkConfigs() { refreshConfigs() }

    override fun saveNetwork(id: Int?, fields: Map<String, Any?>, onDone: (String?) -> Unit) {
        val newId = store.upsert(id, fields)
        refreshConfigs()
        // (Re)connect if it should be live.
        store.get(newId)?.let { net ->
            if (net.autoconnect || manager.get(newId) != null) {
                manager.shutdown(newId)
                connectNetwork(net)
            }
        }
        post { onDone(null) }
    }

    override fun deleteNetwork(id: Int, onDone: (String?) -> Unit) {
        manager.shutdown(id)
        store.delete(id)
        post {
            networks.remove(id)
            buffers.removeAll { it.networkId == id }
            refreshConfigs()
            onDone(null)
        }
    }

    override fun networkAction(id: Int, action: String) {
        val net = store.get(id) ?: return
        when (action) {
            // Already up: connecting again would only replace a working link.
            "connect" -> if (manager.get(id) == null || networks[id]?.connected != true) connectNetwork(net)
            "disconnect" -> {
                manager.shutdown(id)
                post { networks[id]?.let { networks[id] = it.copy(connected = false) } }
            }
            "reconnect" -> { manager.shutdown(id); connectNetwork(net) }
        }
    }

    override fun reorderNetworks(ids: List<Int>) {
        store.reorder(ids)
        refreshConfigs()
    }
}

/** Holds the live KICL clients keyed by local networkId. Touched from the main
 *  thread, the io executor and KICL's Netty threads, hence concurrent. */
class KiclManager {
    private val clients = ConcurrentHashMap<Int, Client>()
    fun put(id: Int, client: Client) { clients[id] = client }
    fun get(id: Int): Client? = clients[id]
    fun entries(): List<Pair<Int, Client>> = clients.entries.map { it.key to it.value }
    fun shutdown(id: Int) { clients.remove(id)?.let { runCatching { it.shutdown() } } }
    fun shutdownAll() { clients.values.forEach { runCatching { it.shutdown() } }; clients.clear() }
}

/** Composer text as IRC lines: split on any line break, NULs dropped (KICL
 *  rejects both), blank lines skipped. */
internal fun ircLines(text: String?): List<String> =
    text.orEmpty().replace("\u0000", "").split(Regex("\r\n|\r|\n")).filter { it.isNotBlank() }

/** How far a replayed copy of OUR OWN line may sit from the optimistic copy we
 *  appended at send time: those are stamped by our clock, the replay by the
 *  server's, so allow for skew. Other people's lines compare server-time exactly. */
private const val SELF_ECHO_WINDOW_MS = 60_000L

/** Only the recent tail can hold a replay's original; bouncers replay the tail. */
private const val REPLAY_SCAN = 500

/**
 * True when [incoming] is a bouncer replay of a line [existing] already holds.
 * Matched on IRCv3 `msgid` when the server sends one; otherwise on the same
 * type, nick and text at the same `server-time`. A line with neither tag can't
 * be told apart from someone genuinely repeating themselves, so it's kept.
 */
internal fun isReplayDuplicate(existing: List<Msg>, incoming: Msg): Boolean {
    val tail = if (existing.size > REPLAY_SCAN) existing.subList(existing.size - REPLAY_SCAN, existing.size) else existing
    incoming.msgid?.let { id -> if (tail.any { it.msgid == id }) return true }
    val at = parseInstant(incoming.time) ?: return false
    val window = if (incoming.self) SELF_ECHO_WINDOW_MS else 0L
    return tail.any { m ->
        !m.system && m.type == incoming.type && m.text == incoming.text &&
            m.nick.equals(incoming.nick, ignoreCase = true) &&
            parseInstant(m.time)?.let { Duration.between(it, at).abs().toMillis() <= window } == true
    }
}

private fun parseInstant(iso: String?): Instant? = iso?.let { runCatching { Instant.parse(it) }.getOrNull() }
