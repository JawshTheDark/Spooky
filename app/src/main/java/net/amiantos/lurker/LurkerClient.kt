// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import android.os.Handler
import android.os.Looper
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Transport + observable state for the Lurker WS/REST contract.
 *
 * Grown up from the prototype's single-shot proof into something usable day to
 * day: it restores a saved session and connects silently, keeps the socket alive
 * across drops with backoff + `?since=` resume, tracks unread counts, renders the
 * full spread of IRC event types, and drives the DCC and settings REST surfaces.
 *
 * Still no ViewModel/repository layer — state lives in Compose snapshot state and
 * the class is the single source of truth. That's a deliberate ceiling for a
 * native client this size; lurker#492's transport-adapter seam is the next step
 * if it grows further.
 */
/** WireOp types carrying user text, so a drop is worth retaining for a retry. */
internal val SENDABLE_OPS = setOf("send", "action", "notice")

/**
 * Which of [baseline] a connect burst closed elsewhere, given the keys it
 * enumerated in [seen] (lurker-ios#78).
 *
 * Empty when the burst enumerated nothing: that's a burst the server abandoned or
 * one we somehow missed entirely, and it says nothing about what's open — pruning
 * the whole baseline off it would wipe the buffer list.
 */
internal fun staleBufferKeys(baseline: Set<String>, seen: Set<String>): Set<String> =
    if (seen.isEmpty()) emptySet() else baseline - seen

/**
 * The event types the `chat.events` tiers treat as noise (lurker#672).
 *
 * Presence churn only. A kick, a topic change or an invite is something that
 * happened TO the channel and is never hidden — hiding those would lose real
 * information, not noise.
 */
internal val NOISE_EVENT_TYPES = setOf("join", "part", "quit", "nick", "chghost")

/**
 * Whether a message survives the `chat.events` tier (lurker#672).
 *
 * - `none` hides presence churn outright.
 * - `smart` keeps churn only for people who have actually said something in the
 *   buffer — the point being that a stranger joining and leaving is noise, while
 *   someone you were just talking to leaving is not.
 * - anything else (`all`, or an unknown value from a newer server) shows it.
 *
 * Your own lines are never hidden, whatever the tier: seeing your own join
 * confirms the channel worked.
 */
internal fun passesEventFilter(
    type: String,
    nick: String,
    self: Boolean,
    tier: String,
    speakers: Set<String>,
): Boolean {
    if (type !in NOISE_EVENT_TYPES || self) return true
    return when (tier) {
        "none" -> false
        "smart" -> nick.lowercase() in speakers
        else -> true
    }
}

/**
 * The nick a raw WHOIS line asks about, lowercased, or null if [line] isn't one.
 *
 * Both `WHOIS <nick>` and the server-targeted `WHOIS <server> <nick>` put the nick
 * last, so the last token is right for either. Used to match a broadcast
 * `whois_result` back to the request this client made.
 */
internal fun whoisTargetNick(line: String?): String? {
    val parts = line?.trim()?.split(Regex("\\s+")).orEmpty()
    if (parts.size < 2 || !parts[0].equals("WHOIS", true)) return null
    return parts.last().lowercase()
}

/**
 * On returning to the foreground, should we reuse the existing WebSocket (just
 * nudge presence) instead of force-cycling it? Reuse when it still reads
 * [connected] AND either the trip was a sub-[BACKGROUND_CYCLE_MS] task-switcher
 * hop OR a background anchor ([Prefs.backgroundConnect]) kept the process — and
 * thus okhttp's ping/pong — alive throughout, so a dead socket would already have
 * reconnected and there's no frozen-process zombie to reap. Cycling a live socket
 * opens a duplicate connection the server closes, which thrashes on reopen.
 */
internal fun shouldReuseSocketOnForeground(
    connected: Boolean,
    quickHop: Boolean,
    backgroundConnect: Boolean,
): Boolean = connected && (quickHop || backgroundConnect)

open class LurkerClient {
    /** True for the Lurker-server backend; DirectIrcBackend overrides to false to
     *  hide server-only surfaces (search, highlights, DCC, settings registry,
     *  uploads) that have no meaning on a raw IRC / bouncer connection. */
    open val serverFeatures: Boolean get() = true

    // ---- Observable state -------------------------------------------------
    var status by mutableStateOf<String?>(null)
        protected set
    var loggedIn by mutableStateOf(false)
        protected set
    var connected by mutableStateOf(false)
        protected set

    // Login-box state: a spinner flag and a human error (null while fine).
    var authBusy by mutableStateOf(false)
        protected set
    var authError by mutableStateOf<String?>(null)
        protected set

    val networks = mutableStateMapOf<Int, Network>()

    /** Per-network ISUPPORT mode vocabulary + server software, when the server
     *  advertises it (via the `server-info` frame / snapshot field). Absent =
     *  a vanilla Lurker server that doesn't send it yet — the mode UI then falls
     *  back to a curated default, so the app still works against an unpatched
     *  server. */
    val serverInfo = mutableStateMapOf<Int, ServerInfo>()
    val buffers = mutableStateListOf<Buffer>()

    /** bufferKey -> messages, in id order. */
    val messagesByBuffer = mutableStateMapOf<String, List<Msg>>()

    /** Buffer keys ever seen carrying E2E traffic — drives the sidebar lock even
     *  before a buffer's history loads. Seeded from Prefs, updated at ingestion. */
    val e2eSeen = mutableStateListOf<String>()

    /** bufferKey -> unread / highlight counts for badges. */
    val unread = mutableStateMapOf<String, Int>()
    val highlights = mutableStateMapOf<String, Int>()

    /** bufferKey -> channel roster, from names frames + incremental events. */
    val members = mutableStateMapOf<String, List<Member>>()

    /** bufferKey -> (nick -> expiry ms) of people currently typing there. */
    val typing = mutableStateMapOf<String, Map<String, Long>>()

    /** bufferKey -> server-synced draft text (persisted across devices). */
    val drafts = mutableStateMapOf<String, String>()
    private val draftPending = HashSet<String>() // keys with an unflushed local edit
    private val draftFlush = HashMap<String, Runnable>()

    /** bufferKey -> recent submitted lines (server-synced), oldest→newest. */
    val inputHistory = mutableStateMapOf<String, List<String>>()

    /** Server-synced custom aliases (name -> expansion). Fork-only feature. */
    val aliases = mutableStateListOf<AliasEntry>()

    /** Server-synced ignore rules (global + per-network), each carrying its scope. */
    val ignores = mutableStateListOf<IgnoreRule>()

    /**
     * True once a snapshot arrives from a server that supports the fork's
     * extended features (custom aliases, DCC send/chat, fserve). Inferred from
     * the presence of the fork-only `aliases` field — vanilla Lurker never sends
     * it. Gates the fork-only UI at runtime so one build works against any server:
     * the surfaces stay hidden on stock/hosted Lurker and light up on the fork.
     */
    var serverExtended by mutableStateOf(false)
        private set

    /** networkId -> pinned buffer targets, in user order. */
    val pins = mutableStateMapOf<Int, List<String>>()

    /** "networkId::nick" -> free-form nick note. */
    val nickNotes = mutableStateMapOf<String, String>()

    /** "networkId::#target" -> notify-always flag. */
    val notifyAlways = mutableStateMapOf<String, Boolean>()

    /** "networkId::#target" -> current channel topic. Fed by `channel-topic`
     *  (RPL_TOPIC sync on join) and live `topic` changes; drives the topic bar. */
    val topics = mutableStateMapOf<String, String>()

    /** "networkId::#target" -> current channel flag modes as a string ("mnt").
     *  Fed by `channel-modes` (RPL_CHANNELMODEIS on join + live MODE changes);
     *  drives the channel control panel's mode toggles/displays. */
    val channelModes = mutableStateMapOf<String, String>()

    /** Message ids the user has bookmarked (saved). Synced from the server. */
    val bookmarkIds = mutableStateSetOf<Long>()

    /**
     * The user's favorite buffers in the server's global order (Lurker 2.0+, which
     * removed contacts). Rendered as two kind-filtered sections — DMs as "Friends",
     * channels as "Favorites". Additive: a pre-2.0 server never sends the frame, so
     * an empty list simply means no favorites — never block waiting for it.
     */
    val favorites = mutableStateListOf<FavoriteEntry>()

    /** "networkId::nickLower" -> presence state (online/offline/away/back). */
    val presence = mutableStateMapOf<String, String>()

    /** One-shot: the UI opens+focuses this buffer. Set when we actually join a
     *  channel (channel-joined) — which a 470 forward can rename vs. the request. */
    var pendingOpen: Buffer? by mutableStateOf(null)

    /** networkId -> (requested channel, requestedAt) for a user join, so the real
     *  channel-joined can retarget focus + drop the ghost requested buffer. */
    protected val pendingJoin = mutableMapOf<Int, Pair<String, Long>>()

    /** True while the Activity is in the foreground (badge is enough; no notif). */
    protected var appForeground = true

    /** Guards [start] so a recreated Activity doesn't cycle the socket. */
    private var started = false

    /** Set by the app: invoked for a notify-worthy message while backgrounded. */
    var notificationSink: ((NotifiableEvent) -> Unit)? = null

    // Channel-list browser (/LIST) state for the currently-viewed network.
    val chanlistRows = mutableStateListOf<ChannelListing>()
    var chanlistLoading by mutableStateOf(false)
    var chanlistInProgress by mutableStateOf(false)
    var chanlistTotalCount by mutableStateOf(0)
    private var chanlistKey = "" // (query,sortBy,sortDir) guard
    private var chanlistNetworkId = -1
    // Last search args, so a live /LIST refresh (chanlist-progress/-end) can
    // re-poll the freshly-cached rows without the UI re-issuing the request.
    private var chanlistQuery = ""
    private var chanlistSortBy = "users"
    private var chanlistSortDir = "desc"

    /**
     * The server's advertised upload ceiling in BYTES (lurker#627 / PR #648), or 0
     * when the server is too old to advertise one.
     *
     * Advisory, not a contract: it's resolved for the default uploader at connect
     * time, so an operator change mid-session or a per-upload override is still
     * settled by the 413. The server has already subtracted its own multipart
     * envelope headroom, so compare a raw file size straight against it — don't
     * add another margin. Re-sent on the `settings` frame when the cap changes.
     */
    var maxUploadBytes by mutableStateOf(0L)
        private set

    /**
     * True once the server's connect burst has finished (lurker#635 / PR #640):
     * the terminal `backlog-complete` frame arrived, so every buffer it was going
     * to send has been sent.
     *
     * Absence of a buffer at this point means "not currently open", NOT "never
     * existed" — a closed buffer keeps its full persisted history, reachable with
     * open-buffer. So this must never drive discarding local history, drafts or
     * read positions; it only tells us to stop waiting. Suppressed by the server
     * if the burst errors midway, so it staying false is meaningful too.
     */
    var snapshotComplete by mutableStateOf(false)
        private set

    /**
     * Bumped once per connect burst. Keying retries on this rather than on
     * [connected] is what makes them fire after a SILENT socket replacement — the
     * old socket dies and a new one takes over without `connected` ever being
     * observed false, so anything watching connection state alone waits forever
     * (lurker-ios#78). A counter always changes value, so a burst that completes
     * inside a single snapshot frame can't be collapsed the way a boolean can.
     */
    var burstGeneration by mutableStateOf(0)
        private set

    // Buffer keys the CURRENT burst has enumerated, and the keys we already held
    // when it began. On backlog-complete, anything in the baseline the burst never
    // mentioned has been closed elsewhere — see reconcileBuffers.
    private val burstSeen = mutableSetOf<String>()
    private var burstBaseline = emptySet<String>()

    /** bufferKey -> whether older history exists beyond what's loaded. */
    val hasMoreOlder = mutableStateMapOf<String, Boolean>()

    /** bufferKey -> a history(before) request is in flight. */
    val loadingOlder = mutableStateMapOf<String, Boolean>()

    // Outstanding sends awaiting their send-result ack (clientId -> context).
    // The web client uses the same 8s deadline before declaring a send lost.
    private data class PendingSend(
        val bufferKey: String,
        val networkId: Int,
        val target: String,
        val type: String,
        val text: String,
    )
    private val pendingSends = mutableMapOf<String, PendingSend>()
    private var clientIdSeq = 0

    /** Undelivered sends, keyed by [FailedSend.id]. The chat renders each one as a
     *  row with Resend / Discard; entries live until the user acts on them. */
    val failedSends = mutableStateMapOf<String, FailedSend>()
    private var failedSeq = 0

    /**
     * Whois requests THIS client issued: "networkId::nickLower" -> (bufferKey, atMs).
     *
     * `whois_result` is published ephemerally to every one of the account's
     * connected clients and carries no attribution, so a whois run in Lurker web —
     * even on a different network — used to print into whatever buffer Spooky
     * happened to have open (freakyy85). Matching replies against our own
     * outstanding requests keeps someone else's whois out, and lands ours in the
     * buffer it was actually issued from.
     */
    private val pendingWhois = mutableMapOf<String, Pair<String, Long>>()

    /** bufferKey -> server read cursor (highest id the user has read). */
    private val lastRead = mutableMapOf<String, Long>()

    /**
     * bufferKey -> "New messages" divider anchor. Snapshotted from [lastRead]
     * when a buffer with unread is opened, so the divider stays put while the
     * user reads (the live cursor races to the tail the moment we mark-read).
     * Cleared when the buffer stops being active.
     */
    val dividerAfter = mutableStateMapOf<String, Long>()

    // DCC (receive side; send/chat scaffolded in DccScreen pending server API).
    val transfers = mutableStateMapOf<Int, DccTransfer>()

    /** "networkId::peerLower" of every live DCC CHAT session. Seeded from each
     *  snapshot's `dccChats` (a chat outlives its IRC link, so a disconnected
     *  network's count too) and kept current by `dcc-chat-state`. */
    val dccChatsLive = mutableStateSetOf<String>()

    /** Inbound DCC CHAT offers awaiting an answer, by [DccChatOffer.key]. Retired on
     *  `dcc-chat-offer-closed`, and reconciled against every snapshot's
     *  `dccChatOffers` — a missed close would otherwise leave an Accept button
     *  that quietly sends the peer a FRESH offer instead. */
    val dccChatOffers = mutableStateMapOf<String, DccChatOffer>()

    /**
     * IRCv3 reactions standing on each line we've seen, by message id (Lurker
     * 2.4+). Kept beside the rows rather than on them, as the web client does:
     * history pages and resumes replace whole lists, and a side map survives all
     * of that. A row that arrives is authoritative for itself (no `reactions` =
     * none stand now); a `reaction` frame adds or removes one live.
     */
    val reactions = mutableStateMapOf<Long, List<Reaction>>()

    /** networkId -> whether a reaction can go out there right now (`canReact`). */
    val networkCanReact = mutableStateMapOf<Int, Boolean>()

    /** bufferKey -> the reply that buffer's composer is writing (draft-synced). */
    val draftReplies = mutableStateMapOf<String, DraftReply>()

    /** The longest edge the server keeps of a static image (Lurker 2.4+), or 0
     *  when it doesn't say — then images upload as-is. */
    var maxStaticImageDimension by mutableStateOf(0)
        private set

    /** The current burst is a `?since=` resume: reactions changed while we were
     *  away don't ride it, so ask for them once it completes. */
    private var burstIsResume = false

    /** The server knows reactions (its snapshot carries `canReact`), so it
     *  understands `sync-reactions` — an older one would answer it with an error. */
    private var serverHasReactions = false
    var dccError by mutableStateOf<String?>(null)
    var dccEnabled by mutableStateOf(true)
        private set

    // Search (WS) + highlights (REST). One monotonic token drops stale results.
    val searchResults = mutableStateListOf<SearchResult>()
    var searchLoading by mutableStateOf(false)
    var searchHasMore by mutableStateOf(false)
    var searchError by mutableStateOf<String?>(null)
    private var searchToken = 0
    private var searchNextBefore: Long? = null
    private var lastSearchRaw: String = ""

    val highlightItems = mutableStateListOf<SearchResult>()
    var highlightsLoading by mutableStateOf(false)
    private var highlightsNextBefore: Long? = null
    var highlightsHasMore by mutableStateOf(false)

    // Bookmarks — saved messages, fetched from GET /api/bookmarks (same row shape
    // as highlights, so they reuse SearchResult + ResultRow).
    val bookmarkItems = mutableStateListOf<SearchResult>()
    var bookmarksLoading by mutableStateOf(false)
    private var bookmarksNextBefore: Long? = null
    var bookmarksHasMore by mutableStateOf(false)

    // Settings (registry-driven; empty if the server predates the endpoint).
    /**
     * The instance's advertised feature flags (`/api/config` → `features`).
     *
     * A MISSING flag means OFF — an older server that doesn't advertise a feature
     * doesn't have it — so a failed fetch can never conjure one the instance lacks.
     */
    val serverFeatureFlags = mutableStateMapOf<String, Boolean>()

    /** Whether this instance has [feature] turned on. Absent ⇒ false, deliberately. */
    fun hasFeature(feature: String): Boolean = serverFeatureFlags[feature] == true

    /** Whether a registry setting should be shown at all. Settings gated on a
     *  feature the instance doesn't have are HIDDEN, not merely disabled. */
    fun settingAvailable(option: SettingOption): Boolean =
        option.requiresFeature?.let { hasFeature(it) } ?: true

    val settingsRegistry = mutableStateListOf<SettingOption>()
    val settingsValues = mutableStateMapOf<String, Any?>()
    var settingsError by mutableStateOf<String?>(null)
    var settingsLoaded by mutableStateOf(false)
        protected set

    // ---- Internals --------------------------------------------------------
    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    /**
     * REST calls get their own timeouts. [http] is tuned for the long-lived
     * WebSocket (no read timeout), and every REST call runs one at a time on the
     * single [io] thread — so one request stuck on a dead connection (a network
     * switch mid-request) used to stall every call queued behind it: settings,
     * DCC, uploads, network actions. Shares [http]'s connection pool.
     */
    private val rest by lazy {
        http.newBuilder()
            .pingInterval(0, TimeUnit.MILLISECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** Uploads: the server answers only after handing the file on to the upload
     *  provider (or staging a DCC send), which can outlast [rest]'s 30s. */
    private val uploadHttp by lazy { rest.newBuilder().readTimeout(5, TimeUnit.MINUTES).build() }

    protected val main = Handler(Looper.getMainLooper())
    protected val io = Executors.newSingleThreadExecutor()
    private val json = "application/json; charset=utf-8".toMediaType()

    @Volatile private var ws: WebSocket? = null
    private var token: String? = null
    private var baseUrl: String = ""

    /** This instance's base URL, for resolving server-minted byte paths. */
    val serverBaseUrl: String get() = baseUrl

    /**
     * The session token, for byte requests aimed at THIS instance only.
     *
     * Named for its one legitimate caller rather than exposed as `token`, because
     * the mistake it guards against — attaching Authorization to a byte URL that
     * turned out to be an absolute CDN address — leaks the session into a third
     * party's access log once per image. See [previewMediaTarget].
     */
    val tokenForMedia: String? get() = token
    protected val networkNames = mutableMapOf<Int, String>()

    private var prefs: Prefs? = null
    protected var activeKey: String? = null

    /** Which buffer is focused, for the UI to check before re-activating one it
     *  restored. Rotation keeps this (the client is process-scoped) but a
     *  process-death restore doesn't — and re-running setActive needlessly would
     *  re-anchor the "New messages" divider to the tail. */
    val activeBufferKey: String? get() = activeKey

    /** Highest server message id seen — the `?since=` cursor on reconnect. */
    private var maxMessageId = 0L

    // Reconnect bookkeeping.
    private var intentionalClose = false
    private var reconnectScheduled = false
    private var reconnectRunnable: Runnable? = null
    private var connecting = false
    private var backoffMs = INITIAL_BACKOFF

    /** Last time any frame arrived — the only honest socket-liveness signal. */
    private var lastFrameAt = 0L

    /** When the app went to background (0 while foregrounded). */
    private var wentBackgroundAt = 0L

    protected fun post(block: () -> Unit) = main.post(block)

    // ---- Session lifecycle ------------------------------------------------

    /**
     * Wire up persistence and, if a saved session exists, connect straight away
     * without a sign-in screen. Called once from the Activity.
     */
    open fun start(prefs: Prefs) {
        // Idempotent: the client is process-scoped now, so a recreated Activity
        // (rotation, layout switch) calls start() again — don't cycle the socket.
        if (started) { this.prefs = prefs; return }
        started = true
        this.prefs = prefs
        // Seed known-E2E buffers so the sidebar lock shows immediately on launch.
        prefs.e2eBuffers.forEach { if (it !in e2eSeen) e2eSeen.add(it) }
        if (prefs.hasSession) {
            token = prefs.token
            baseUrl = prefs.serverUrl!!.trimEnd('/')
            post { status = "Connecting…"; loggedIn = true }
            io.execute {
                fetchNetworkNames()
                openSocket(null)
            }
            loadSettings() // chat rendering (font size) reads the registry
        }
    }

    /**
     * POST /api/auth/login/token — password in, bearer session token out. Blocking;
     * the caller runs it off the main thread.
     */
    /** Clear the login-box error (called as the user edits a field). */
    fun clearAuthError() { if (authError != null) authError = null }

    fun login(rawBase: String, username: String, password: String) {
        // The hosted lurker.chat deployment mints its token at the control plane
        // with an EMAIL, not a username, and then proxies all chat traffic through
        // app.lurker.chat (CLIENT_PROTOCOL §3.2). Self-hosted servers keep the
        // username → /api/auth/login/token flow. Detect which we're talking to
        // from the address the user typed and branch the request accordingly.
        val hosted = isHostedLurker(rawBase)
        val base = if (hosted) HOSTED_LURKER_BASE else normalizeServerUrl(rawBase)
        baseUrl = base
        post { authBusy = true; authError = null; status = "Signing in…" }
        try {
            val body = if (hosted) {
                // Control-plane app login: { email, password } → { token }.
                JSONObject().put("email", username.trim()).put("password", password)
            } else {
                JSONObject().put("username", username.trim()).put("password", password)
            }.toString().toRequestBody(json)
            val loginUrl = if (hosted) "$HOSTED_LURKER_BASE/_cp/auth/app/login" else "$base/api/auth/login/token"
            val req = Request.Builder().url(loginUrl).post(body).build()
            rest.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    // 401/403 = bad creds; other codes = server/proxy problem.
                    val msg = if (res.code == 401 || res.code == 403) {
                        if (hosted) "Wrong email or password." else "Wrong username or password."
                    } else "Sign-in failed (server said HTTP ${res.code})."
                    post { authError = msg; authBusy = false; status = null }
                    return
                }
                val tok = runCatching { JSONObject(text).optString("token") }.getOrNull()
                if (tok.isNullOrEmpty()) {
                    // A non-JSON 200 (usually an HTML page) means we hit a web page,
                    // not the Lurker API — almost always a wrong address.
                    post {
                        authError = "That address didn't return a Lurker login. Check the server URL."
                        authBusy = false; status = null
                    }
                    return
                }
                token = tok
            }
            // Persist the EFFECTIVE base (app.lurker.chat for hosted) so silent
            // resume reconnects to the right host, not the address the user typed.
            prefs?.saveSession(base, username.trim(), token!!)
            fetchNetworkNames()
            post {
                status = "Connecting…"
                authBusy = false
                loggedIn = true
            }
            openSocket(null)
            loadSettings() // chat rendering (font size) reads the registry
        } catch (e: Exception) {
            // Network-level failure (unreachable host, TLS, timeout).
            val why = e.message ?: "couldn't reach the server"
            post { authError = "Couldn't connect: $why"; authBusy = false; status = null }
        }
    }

    /** Trim, strip a trailing slash, and default to https:// when the user typed a
     *  bare host — the #1 cause of "no scheme" / HTML-instead-of-JSON sign-in errors. */
    private fun normalizeServerUrl(raw: String): String {
        var s = raw.trim().trimEnd('/')
        if (s.isNotEmpty() && !s.startsWith("http://", true) && !s.startsWith("https://", true)) {
            s = "https://$s"
        }
        return s
    }

    /** Tear down the session: close the socket, forget the token, reset state. */
    open fun signOut() {
        intentionalClose = true
        started = false
        ws?.close(1000, "sign out")
        ws = null
        // Capture before clearing: revokeSession runs later on the io thread, and
        // reading `token` there found it already null — so sign-out never actually
        // revoked the session server-side.
        val revokeToken = token
        val revokeBase = baseUrl
        io.execute { revokeSession(revokeToken, revokeBase) }
        prefs?.clearSession()
        token = null
        maxMessageId = 0
        post { resetSessionState() }
    }

    /**
     * Forget everything the previous session left in memory. The client is
     * process-scoped, so a sign-in after a sign-out (or after the server expired
     * the session) happens without process death — none of this may leak into
     * the next account. Main thread.
     */
    private fun resetSessionState() {
        // Wipe preview state with the session: an in-air response must not refill
        // the store we just emptied, which would put the previous account's
        // metadata back in cache and POST its URLs under the next account's token.
        ServerPreviews.store.reset()
        run {
            loggedIn = false
            connected = false
            status = null
            networks.clear()
            buffers.clear()
            messagesByBuffer.clear()
            unread.clear()
            highlights.clear()
            lastRead.clear()
            dividerAfter.clear()
            members.clear()
            typing.clear()
            hasMoreOlder.clear()
            loadingOlder.clear()
            pendingSends.clear()
            pendingWhois.clear()
            failedSends.clear()
            transfers.clear()
            dccChatsLive.clear()
            dccChatOffers.clear()
            reactions.clear()
            networkCanReact.clear()
            serverHasReactions = false
            draftReplies.clear()
            maxStaticImageDimension = 0
            activityFeed = false
            activityUnsupported = false
            activityNext = null
            drafts.clear()
            inputHistory.clear()
            aliases.clear()
            ignores.clear()
            pendingOpen = null
            pendingJoin.clear()
            serverExtended = false
            bookmarkIds.clear()
            favorites.clear()
            presence.clear()
            pins.clear()
            nickNotes.clear()
            notifyAlways.clear()
            topics.clear()
            channelModes.clear()
            chanlistRows.clear()
            searchResults.clear()
            highlightItems.clear()
            bookmarkItems.clear()
            bookmarksNextBefore = null
            bookmarksHasMore = false
            settingsRegistry.clear()
            serverFeatureFlags.clear()
            settingsValues.clear()
            settingsLoaded = false
            // The client is process-scoped, so sign-out→sign-in happens without
            // process death — none of this transient state may leak into the next
            // account. Cancel the reconnect timer so it can't fire against a fresh
            // login, and reset the connection/paging/UI flags to their defaults.
            cancelScheduledReconnect()
            connecting = false
            activeKey = null
            activeBuffer = null
            markReadQueued = false
            chanlistLoading = false
            chanlistInProgress = false
            chanlistTotalCount = 0
            chanlistKey = ""
            chanlistNetworkId = -1
            dccEnabled = true
            dccError = null
            networkConfigs.clear()
            networksError = null
            networkNames.clear()
            searchLoading = false
            searchHasMore = false
            searchError = null
            searchNextBefore = null
            lastSearchRaw = ""
            highlightsLoading = false
            highlightsNextBefore = null
            highlightsHasMore = false
            settingsError = null
            draftPending.clear()
            draftFlush.values.forEach(main::removeCallbacks)
            draftFlush.clear()
        }
    }

    // ---- Browser sign-in (OAuth, Lurker 2.3+) ------------------------------

    /** True from the moment the browser is opened until the redirect comes back
     *  or the user cancels: the login screen shows "finish in your browser". */
    var oauthWaiting by mutableStateOf(false)

    /**
     * Step 1 of browser sign-in. Discovers the server's OAuth endpoints,
     * registers this app there if it hasn't been, mints a PKCE pair, persists
     * the attempt, and returns the URL to open in the browser — or null after
     * setting [authError]. Blocking; the caller runs it off the main thread.
     *
     * Hosted (lurker.chat) is the same flow against app.lurker.chat (CLIENT_PROTOCOL
     * §3.2); only the password path needs the control-plane special case.
     */
    fun beginOAuth(rawBase: String): String? {
        val p = prefs ?: return null
        val base = if (isHostedLurker(rawBase)) HOSTED_LURKER_BASE else normalizeServerUrl(rawBase)
        post { authBusy = true; authError = null; status = null }
        fun fail(msg: String): String? {
            post { authError = msg; authBusy = false }
            return null
        }
        try {
            // RFC 8414 discovery. A pre-2.3 server has no such route and answers
            // with the web app's HTML (a 200 that isn't JSON) — same verdict as a
            // 404: no browser sign-in here, use the password form.
            val disco = getJson("$base/.well-known/oauth-authorization-server")
                ?: return fail("This server doesn't offer browser sign-in yet (it needs Lurker 2.3 or newer). Sign in with your username and password below.")
            val authorizeEp = disco.optString("authorization_endpoint")
            val tokenEp = disco.optString("token_endpoint")
            val registerEp = disco.optString("registration_endpoint")
            if (authorizeEp.isEmpty() || tokenEp.isEmpty() || registerEp.isEmpty()) {
                return fail("This server's sign-in setup is incomplete (no OAuth endpoints advertised).")
            }
            val redirect = OAuth.redirectUri(p.appId)
            val clientId = p.oauthClientId(base) ?: run {
                // RFC 7591 dynamic registration: public client, no secret.
                val body = JSONObject()
                    .put("client_name", p.appName.take(60))
                    .put("client_uri", OAuth.CLIENT_URI)
                    .put("redirect_uris", JSONArray().put(redirect))
                    .toString().toRequestBody(json)
                rest.newCall(Request.Builder().url(registerEp).post(body).build()).execute().use { res ->
                    val text = res.body?.string().orEmpty()
                    if (res.code == 429) {
                        return fail("The server is rate-limiting app registrations right now. Try again in a few minutes.")
                    }
                    val id = runCatching { JSONObject(text).optString("client_id") }.getOrNull()
                    if (!res.isSuccessful || id.isNullOrEmpty()) {
                        val why = runCatching { JSONObject(text).optString("error_description") }.getOrNull()
                        return fail("The server refused to register this app" + (why?.takeIf { it.isNotEmpty() }?.let { ": $it" } ?: " (HTTP ${res.code})."))
                    }
                    p.setOauthClientId(base, id)
                    id
                }
            }
            val verifier = OAuth.newVerifier()
            val state = OAuth.newState()
            p.oauthPending = OAuthPending(
                base = base, clientId = clientId, redirectUri = redirect,
                verifier = verifier, state = state, tokenEndpoint = tokenEp,
            ).toJson()
            baseUrl = base
            post { authBusy = false; oauthWaiting = true }
            return OAuth.authorizeUrl(authorizeEp, clientId, redirect, OAuth.challenge(verifier), state)
        } catch (e: Exception) {
            return fail("Couldn't connect: ${e.message ?: "couldn't reach the server"}")
        }
    }

    /**
     * Step 2: the browser redirected back to `<appId>:/oauth?code=…&state=…`.
     * Exchanges the code for the access token, then finishes exactly as a
     * password sign-in does. Blocking; runs off the main thread.
     */
    fun completeOAuth(redirectUri: String) {
        val p = prefs ?: return
        val pending = OAuthPending.fromJson(p.oauthPending)
        val r = OAuth.parseRedirect(redirectUri, p.appId)
        fun fail(msg: String) {
            post { authError = msg; authBusy = false; oauthWaiting = false; status = null }
        }
        if (r == null) return
        if (pending == null) return fail("That sign-in link is stale — tap Sign in with browser again.")
        if (r.state != pending.state) {
            // Not a reply to OUR request: ignore it rather than spend the code.
            return fail("That sign-in reply didn't match this app's request. Try again.")
        }
        p.oauthPending = null
        if (r.error != null) {
            return fail(
                if (r.error == "access_denied") "You declined the sign-in in the browser."
                else "The server refused the sign-in: ${r.error}",
            )
        }
        val code = r.code ?: return fail("The browser came back without a sign-in code. Try again.")
        post { authBusy = true; oauthWaiting = false; authError = null; status = "Signing in…" }
        try {
            val form = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", pending.clientId)
                .add("code", code)
                .add("redirect_uri", pending.redirectUri)
                .add("code_verifier", pending.verifier)
                .build()
            var tok = ""
            rest.newCall(Request.Builder().url(pending.tokenEndpoint).post(form).build()).execute().use { res ->
                val text = res.body?.string().orEmpty()
                val err = runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty()
                if (res.code == 401 || err == "invalid_client") {
                    // Rejects OUR client_id, not the member (OAUTH.md): the
                    // registration lapsed. Forget it so the next attempt re-registers.
                    p.setOauthClientId(pending.base, null)
                    return fail("This app's registration with the server lapsed. Tap Sign in with browser to try again.")
                }
                if (!res.isSuccessful) {
                    return fail(
                        if (err == "invalid_grant") "That sign-in code was rejected (expired or already used). Try again."
                        else "Sign-in failed (server said HTTP ${res.code}).",
                    )
                }
                tok = runCatching { JSONObject(text).optString("access_token") }.getOrNull().orEmpty()
                if (tok.isEmpty()) return fail("The server didn't return an access token. Try again.")
            }
            baseUrl = pending.base
            token = tok
            // The member never typed a name here; ask the server for it so the
            // login screen can prefill it next time, like the password path does.
            val username = getJson("$baseUrl/api/auth/me")
                ?.optJSONObject("user")?.optString("username").orEmpty()
                .ifEmpty { p.username.orEmpty() }
            p.saveSession(baseUrl, username, tok)
            fetchNetworkNames()
            post {
                status = "Connecting…"
                authBusy = false
                loggedIn = true
            }
            openSocket(null)
            loadSettings()
        } catch (e: Exception) {
            fail("Couldn't connect: ${e.message ?: "couldn't reach the server"}")
        }
    }

    /** The user gave up waiting for the browser. Also forgets the server's
     *  client_id: the one failure the app can't see is the approval page
     *  rejecting a lapsed registration, and a retry must not repeat it. */
    fun cancelOAuth(reason: String? = null) {
        val p = prefs
        OAuthPending.fromJson(p?.oauthPending)?.let { p?.setOauthClientId(it.base, null) }
        p?.oauthPending = null
        post { oauthWaiting = false; authBusy = false; authError = reason; status = null }
    }

    /** GET a JSON object, with the session bearer when we have one. Null on any
     *  non-2xx, network failure, or a body that isn't a JSON object. */
    private fun getJson(url: String): JSONObject? = try {
        val b = Request.Builder().url(url)
        token?.let { b.header("Authorization", "Bearer $it") }
        rest.newCall(b.build()).execute().use { res ->
            if (!res.isSuccessful) null
            else runCatching { JSONObject(res.body?.string().orEmpty()) }.getOrNull()
        }
    } catch (_: Exception) {
        null
    }

    private fun revokeSession(t: String?, base: String) {
        if (t == null || base.isEmpty()) return
        try {
            val req = Request.Builder()
                .url("$base/api/auth/logout")
                .header("Authorization", "Bearer $t")
                .post(ByteArray(0).toRequestBody(null))
                .build()
            rest.newCall(req).execute().close()
        } catch (_: Exception) {
            // Best-effort; the local token is already gone either way.
        }
    }

    /**
     * Called from the Activity's ON_RESUME. The hard part: after a long
     * background, the socket is often DEAD but nothing has told us yet — the
     * process was frozen when the NAT mapping / server reaped it, so `connected`
     * still reads true and no callback ever fired. Waiting for the next ping
     * cycle to notice reads as "spotty for a minute after opening the app".
     *
     * So: reconnect NOW (reset backoff, cancel any pending timer) whenever we're
     * not connected, and *cycle proactively* even when we look connected if the
     * app was backgrounded for a while and the socket has been silent — a
     * `?since=` resume is cheap, a zombie socket is not. A healthy socket just
     * gets a presence nudge.
     */
    open fun onForeground() {
        appForeground = true
        if (!loggedIn || token == null) return
        val now = System.currentTimeMillis()
        // Deterministic by design: any real background trip cycles the socket —
        // no liveness guessing, no zombie-socket class of bugs. ?since= resume
        // makes it cheap, and superseded-socket guards keep the UI steady. Only
        // sub-3s task-switcher hops skip the churn (if their socket died, the
        // normal failure path still reconnects instantly).
        val quickHop = wentBackgroundAt > 0 && now - wentBackgroundAt < BACKGROUND_CYCLE_MS
        wentBackgroundAt = 0
        // A background-connect session keeps the PROCESS alive the whole time the
        // app is backgrounded, so okhttp's ping/pong never paused — a socket that
        // had died would already have fired onFailure and reconnected. There's no
        // frozen-process zombie to reap. Force-cycling a still-live socket in that
        // case opens a SECOND connection for the same session: the server accepts
        // the upgrade (onOpen resets our backoff), spots the duplicate, and closes
        // it, so the two thrash green<->"Connecting…" for ~10s on every reopen
        // (freakyy85's report). So reuse the live socket unless a frozen process
        // (no background anchor) could have left a zombie behind.
        if (shouldReuseSocketOnForeground(connected, quickHop, prefs?.backgroundConnect == true)) {
            // Healthy socket. If it turns out dead after all, this send fails →
            // onFailure → the normal reconnect path takes over immediately.
            ws?.send(presenceFrame(true))
        } else {
            backoffMs = INITIAL_BACKOFF
            cancelScheduledReconnect()
            connecting = false
            openSocket(maxMessageId.takeIf { it > 0 })
        }
    }

    /**
     * The device's network came back, or the default network changed (Wi-Fi <->
     * mobile). Called by [NetworkMonitor] on the main thread.
     *
     * A socket rides the network it was opened on, so after a switch it's dead
     * even though nothing has said so yet — okhttp only notices when a ping goes
     * unanswered, and by then the backoff may be up to 30s. Reconnect now instead,
     * the way HexDroid and other Android IRC clients do. A network merely coming
     * back only matters if we're down.
     */
    open fun onNetworkAvailable(switched: Boolean) {
        if (!loggedIn || token == null || intentionalClose) return
        if (!switched && (connected || connecting)) return
        DebugLog.i("net", if (switched) "network changed — reconnecting" else "network back — reconnecting")
        backoffMs = INITIAL_BACKOFF
        cancelScheduledReconnect()
        connecting = false
        openSocket(maxMessageId.takeIf { it > 0 })
    }

    /** Called from the Activity's ON_STOP — remember when we left. */
    open fun onBackground() {
        appForeground = false
        wentBackgroundAt = System.currentTimeMillis()
        activeBuffer?.let { flushDraft(it) } // don't lose an unsynced draft
        ws?.send(presenceFrame(false))
    }

    private fun presenceFrame(visible: Boolean): String =
        JSONObject().put("type", "presence").put("visible", visible).toString()

    // ---- REST helpers -----------------------------------------------------

    private fun authed(path: String): Request.Builder =
        Request.Builder().url("$baseUrl$path").header("Authorization", "Bearer ${token!!}")

    /**
     * Resolve a batch of URLs server-side (`POST /api/link-preview/resolve`).
     *
     * [onDone] receives the descriptors, or null on a TRANSPORT failure — which the
     * store must treat as "no information about these URLs", never as a verdict.
     * Conflating the two is what strands links blank for a whole session.
     *
     * Max 20 URLs per request (extra are dropped from the response, so batch at
     * 20); 120 requests/min per account.
     */
    fun resolvePreviews(urls: List<String>, onDone: (List<ServerLinkPreview>?) -> Unit) {
        if (urls.isEmpty()) { onDone(emptyList()); return }
        io.execute {
            val out = runCatching {
                val body = JSONObject().put("urls", JSONArray(urls.take(20)))
                    .toString().toRequestBody(json)
                rest.newCall(authed("/api/link-preview/resolve").post(body).build()).execute().use { res ->
                    if (res.code == 429) {
                        DebugLog.w("preview", "rate limited; retry-after=${res.header("Retry-After")}")
                        return@runCatching null
                    }
                    if (!res.isSuccessful) return@runCatching null
                    val arr = JSONObject(res.body?.string().orEmpty()).optJSONArray("previews")
                        ?: return@runCatching emptyList()
                    // Drop an undecodable descriptor individually rather than
                    // failing the whole batch it arrived in.
                    (0 until arr.length()).mapNotNull { i ->
                        arr.optJSONObject(i)?.let { runCatching { parseServerPreview(it) }.getOrNull() }
                    }
                }
            }.getOrNull()
            post { onDone(out) }
        }
    }

    /**
     * Learn which optional features this instance actually mounted
     * (`GET /api/config`, unauthenticated). Refreshed on every connect, because an
     * operator can enable one without the app restarting.
     *
     * Best-effort and deliberately non-blocking: a flag that decides whether to
     * DECORATE a message must never delay GETTING the message. On failure the map
     * is left alone and everything gated on it stays hidden.
     */
    fun fetchServerConfig() = io.execute {
        runCatching {
            rest.newCall(Request.Builder().url("$baseUrl/api/config").build()).execute().use { res ->
                if (!res.isSuccessful) return@execute
                val feats = JSONObject(res.body?.string().orEmpty()).optJSONObject("features")
                    ?: return@execute
                val fresh = feats.keys().asSequence().associateWith { feats.optBoolean(it, false) }
                post { serverFeatureFlags.clear(); serverFeatureFlags.putAll(fresh) }
            }
        }
    }

    private fun fetchNetworkNames() {
        try {
            rest.newCall(authed("/api/networks").build()).execute().use { res ->
                if (!res.isSuccessful) return
                val arr = JSONObject(res.body?.string().orEmpty()).optJSONArray("networks") ?: return
                val configs = mutableListOf<NetworkConfig>()
                for (i in 0 until arr.length()) {
                    val n = arr.getJSONObject(i)
                    val id = n.getInt("id")
                    val name = n.optString("name", "network")
                    networkNames[id] = name
                    val net = Network(
                        id = id,
                        name = name,
                        nick = n.optString("nick").ifEmpty { null },
                        connected = n.optBoolean("connected", n.optString("state") == "connected"),
                    )
                    post { networks[id] = net }
                    parseNetworkConfig(n)?.let(configs::add)
                }
                // The REST order IS the user's chosen sidebar order — the buffer
                // list sections follow it.
                post {
                    networkConfigs.clear()
                    networkConfigs.addAll(configs)
                }
            }
        } catch (_: Exception) {
            // Names are cosmetic; the buffer list still works without them.
        }
    }

    /** Position of a network (by display name) in the user's chosen order. */
    fun networkOrder(name: String): Int {
        val i = networkConfigs.indexOfFirst { it.name.equals(name, true) }
        return if (i >= 0) i else Int.MAX_VALUE
    }

    /** Persist a full network ordering (must contain every network id). */
    open fun reorderNetworks(ids: List<Int>) = io.execute {
        try {
            val body = JSONObject().put("ids", org.json.JSONArray(ids)).toString().toRequestBody(json)
            rest.newCall(authed("/api/networks/reorder").post(body).build()).execute().use { res ->
                if (!res.isSuccessful) {
                    post { networksError = "reorder failed (HTTP ${res.code})" }
                }
                // Already on the io thread; refreshes names, states, and order.
                fetchNetworkNames()
            }
        } catch (e: Exception) {
            post { networksError = "reorder failed: ${e.message}" }
        }
    }

    // ---- WebSocket --------------------------------------------------------

    private fun openSocket(since: Long?) {
        intentionalClose = false
        connecting = true
        ws?.cancel()
        // ?v= announces the protocol we speak (#569); the server 426s a client
        // older than its minimum instead of feeding it frames it would mis-render.
        val wsBase = baseUrl.replaceFirst("http", "ws") + "/ws?v=$PROTOCOL_VERSION"
        val wsUrl = if (since != null && since > 0) "$wsBase&since=$since" else wsBase
        burstIsResume = since != null && since > 0
        val req = Request.Builder()
            .url(wsUrl)
            .header("Authorization", "Bearer ${token!!}")
            .build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== ws) return // superseded socket — ignore
                android.util.Log.i("LurkerWS", "open")
                DebugLog.i("ws", "connected")
                lastFrameAt = System.currentTimeMillis()
                webSocket.send(presenceFrame(true))
                post {
                    // A new socket means a new burst: don't let the previous
                    // connection's completion vouch for buffers not yet re-sent.
                    snapshotComplete = false
                    burstGeneration++
                    // Snapshot what we hold NOW, so a buffer opened while the burst
                    // is still arriving can't be mistaken for one the server omitted.
                    burstBaseline = buffers.map { it.key }.toSet()
                    burstSeen.clear()
                    connected = true
                    connecting = false
                    status = null
                    backoffMs = INITIAL_BACKOFF
                    fetchServerConfig() // which optional features this instance has
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== ws) return // superseded socket — ignore
                lastFrameAt = System.currentTimeMillis()
                val frame = try {
                    JSONObject(text)
                } catch (_: Exception) {
                    return
                }
                post { handleFrame(frame) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // The cancel() from a cycle fires this on the OLD socket, often
                // AFTER the new one opened — acting on it flipped `connected`
                // false and double-booked reconnects (the reopen "flutter").
                if (webSocket !== ws) return
                android.util.Log.w("LurkerWS", "failure http=${response?.code} ${t.javaClass.simpleName}: ${t.message}")
                DebugLog.e("ws", "failure http=${response?.code} ${t.javaClass.simpleName}: ${t.message}")
                val code = response?.code
                post {
                    connected = false
                    connecting = false
                    // A 401 means the saved token is dead — don't spin on it.
                    if (code == 401) {
                        forceSignOutLocally()
                        // `status` isn't shown anywhere once signed out; the sign-in
                        // screen shows authError, so say it there.
                        authError = "Your session expired — please sign in again."
                    } else if (code == 426) {
                        // Protocol handshake refused: this build is older than the
                        // server's minimum. Retrying can't help — say so legibly.
                        status = "This app is too old for the server — please update the app."
                    } else if (t is javax.net.ssl.SSLException) {
                        // TLS refused: the certificate didn't check out. In practice
                        // this is almost never the server — it's the NETWORK sitting
                        // in the middle (a captive portal / hotspot sign-in page, or
                        // an ISP walled garden presenting its own certificate). We
                        // correctly refuse to hand the session token to it, but the
                        // generic "Reconnecting…" left the user watching a spinner
                        // with no idea they had to sign in to the Wi-Fi first. Keep
                        // retrying — signing in fixes it without touching the app.
                        status = "Can't verify this server's certificate — if you're on Wi-Fi, " +
                            "sign in to the network first. Retrying…"
                        scheduleReconnect()
                    } else {
                        status = if (code != null) "Reconnecting… (HTTP $code)" else "Reconnecting…"
                        scheduleReconnect()
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket !== ws) return // superseded socket — ignore
                android.util.Log.w("LurkerWS", "closed code=$code reason=$reason")
                DebugLog.w("ws", "closed code=$code reason=${reason.ifEmpty { "-" }}${if (intentionalClose) " (intentional)" else ""}")
                post {
                    connected = false
                    connecting = false
                    if (!intentionalClose) scheduleReconnect()
                }
            }
        })
    }

    /** The server rejected our token (401). Same local teardown as [signOut] — the
     *  previous account's buffers and messages must not carry into the next
     *  sign-in — minus the revoke, which a dead token can't do. Main thread. */
    private fun forceSignOutLocally() {
        intentionalClose = true
        started = false
        ws = null
        prefs?.clearSession()
        token = null
        maxMessageId = 0
        resetSessionState()
    }

    private fun scheduleReconnect() {
        if (intentionalClose || reconnectScheduled || token == null) return
        reconnectScheduled = true
        val delay = backoffMs
        backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF)
        val runnable = Runnable {
            reconnectScheduled = false
            reconnectRunnable = null
            if (!intentionalClose && token != null) openSocket(maxMessageId.takeIf { it > 0 })
        }
        reconnectRunnable = runnable
        main.postDelayed(runnable, delay)
    }

    /** Drop a pending backoff timer so a foreground revival can connect NOW. */
    private fun cancelScheduledReconnect() {
        reconnectRunnable?.let(main::removeCallbacks)
        reconnectRunnable = null
        reconnectScheduled = false
    }

    // ---- Frame handling ---------------------------------------------------

    private fun handleFrame(frame: JSONObject) {
        when (frame.optString("kind")) {
            "snapshot" -> {
                bumpCursor(frame.optLong("cursor"))
                frame.optJSONArray("networks")?.let { applyNetworks(it) }
                if (frame.has("aliases")) {
                    serverExtended = true
                    applyAliases(frame.optJSONArray("aliases"))
                }
                if (frame.has("globalIgnores")) applyIgnoreScope(null, frame.optJSONArray("globalIgnores"))
                // 0 on an older server that doesn't advertise a cap — we then
                // upload unchecked exactly as before and let the 413 decide.
                if (frame.has("maxUploadBytes")) maxUploadBytes = frame.optLong("maxUploadBytes")
                maxStaticImageDimension = frame.optInt("maxStaticImageDimension", 0).coerceAtLeast(0)
            }

            // The burst's terminal frame. A fresh burst (reconnect, in-band resync,
            // fresh-network re-emission) re-arms this in onOpen. Logged because its
            // ABSENCE is the interesting case in a bug report: an old server, or a
            // burst the server abandoned midway.
            "backlog-complete" -> {
                snapshotComplete = true
                reconcileBuffers()
                if (burstIsResume) {
                    burstIsResume = false
                    syncReactions()
                }
                DebugLog.i("ws", "backlog-complete (${buffers.size} buffers)")
            }

            // Carries the recomputed effective cap when the user changes their
            // upload setting, so we don't keep sizing against a stale number for
            // the rest of the session.
            "settings" -> {
                if (frame.has("maxUploadBytes")) maxUploadBytes = frame.optLong("maxUploadBytes")
                if (frame.has("maxStaticImageDimension")) {
                    maxStaticImageDimension = frame.optInt("maxStaticImageDimension", 0).coerceAtLeast(0)
                }
            }

            // IRCv3 reactions (Lurker 2.4+): one added or taken back, live.
            "reaction" -> applyReactionFrame(frame)
            // The answer to sync-reactions: authoritative for every id asked about.
            "reactions-sync" -> {
                val ids = frame.optJSONArray("messageIds") ?: return
                val byId = frame.optJSONObject("reactions")
                for (i in 0 until ids.length()) {
                    val id = ids.optLong(i)
                    if (id <= 0) continue
                    val list = parseReactions(byId?.optJSONArray(id.toString()))
                    if (list.isEmpty()) reactions.remove(id) else reactions[id] = list
                }
            }

            "ignore-list-updated" -> {
                val networkId = if (frame.isNull("networkId")) null else frame.optInt("networkId")
                applyIgnoreScope(networkId, frame.optJSONArray("masks"))
            }

            "backlog" -> {
                val networkId = if (frame.isNull("networkId")) null else frame.getInt("networkId")
                val target = frame.optString("target")
                if (target.isEmpty()) return
                val buffer = ensureBuffer(networkId, target)
                burstSeen.add(buffer.key)
                val events = frame.optJSONArray("events")
                bumpCursorFromEvents(events)
                val reset = frame.optBoolean("reset", false)
                noteReactions(events, networkId)
                val parsed = parseEvents(events)
                mergeInto(buffer.key, parsed, replace = reset || messagesByBuffer[buffer.key] == null)
                seedUnread(buffer.key, frame)
                if (frame.has("lastReadId")) lastRead[buffer.key] = frame.optLong("lastReadId")
                if (frame.has("hasMoreOlder")) hasMoreOlder[buffer.key] = frame.optBoolean("hasMoreOlder")
                frame.optJSONArray("inputHistory")?.let { h ->
                    inputHistory[buffer.key] = (0 until h.length()).map { h.optString(it) }
                }
                // Reopening a buffer: once the fresh backlog hydrates, tell the
                // server we're caught up to what it just showed us.
                scheduleMarkRead(buffer.key)
            }

            "irc" -> {
                val networkId = if (frame.isNull("networkId")) null else frame.getInt("networkId")
                // WHOIS replies are ephemeral and targetless (the web renders a
                // modal); we print them into the buffer they were issued from.
                if (frame.optString("type") == "whois_result") {
                    handleWhoisResult(frame)
                    return
                }
                // A live /LIST refresh streams these targetless progress events
                // (server writes rows to its cache as it goes). Re-poll so the
                // browser fills in — and, critically, so switching to a network
                // that had no cached list actually shows results. Without this the
                // one immediate search after `list-channels` hits an empty cache
                // and never refreshes — the "always picks my first network" bug.
                when (frame.optString("type")) {
                    "chanlist-start" -> {
                        if (networkId == chanlistNetworkId) {
                            chanlistInProgress = true
                            chanlistTotalCount = 0
                        }
                        return
                    }
                    "chanlist-progress" -> {
                        if (networkId == chanlistNetworkId) {
                            chanlistInProgress = true
                            chanlistTotalCount = frame.optInt("total", chanlistTotalCount)
                            searchChannelList(chanlistQuery, chanlistSortBy, chanlistSortDir, 0)
                        }
                        return
                    }
                    "chanlist-end" -> {
                        if (networkId == chanlistNetworkId) {
                            chanlistInProgress = false
                            searchChannelList(chanlistQuery, chanlistSortBy, chanlistSortDir, 0)
                        }
                        return
                    }
                }
                // DCC CHAT lifecycle: ephemeral, aimed at the :server: pseudo-buffer
                // with the peer in `from` — never a chat row, never a buffer.
                when (frame.optString("type")) {
                    // Whether reactions can be sent on this network changed (2.4+).
                    "react-support" -> {
                        if (networkId != null) networkCanReact[networkId] = frame.optBoolean("canReact", false)
                        return
                    }
                    "dcc-chat-offer", "dcc-chat-offer-closed", "dcc-chat-state" -> {
                        if (networkId != null) applyDccChatEvent(networkId, frame)
                        return
                    }
                }
                val target = frame.optString("target")
                if (target.isEmpty()) return
                // Current-topic sync (RPL_TOPIC on join). Ephemeral, no chat row —
                // just update the topic bar. Live TOPIC *changes* arrive as a
                // "topic" system line (handled below) and also refresh the bar.
                if (frame.optString("type") == "channel-topic") {
                    if (networkId != null) {
                        val key = "$networkId::$target"
                        val t = frame.optString("topic")
                        if (t.isEmpty()) topics.remove(key) else topics[key] = t
                    }
                    return
                }
                // Current channel flag modes (RPL_CHANNELMODEIS on join + MODE
                // changes) — drives the channel control panel. Ephemeral, no row.
                if (frame.optString("type") == "channel-modes") {
                    if (networkId != null) {
                        channelModes["$networkId::$target"] = frame.optString("modes")
                    }
                    return
                }
                // ISUPPORT mode vocabulary + server software (patched server only;
                // vanilla never sends this, leaving serverInfo[net] null → curated
                // fallback in the UI). Ephemeral, no chat row.
                if (frame.optString("type") == "server-info") {
                    if (networkId != null) {
                        serverInfo[networkId] = ServerInfo.parse(
                            software = frame.optString("software"),
                            network = frame.optString("network"),
                            chanModes = frame.optString("chanModes"),
                            prefix = frame.optString("prefix"),
                            chanTypes = frame.optString("chanTypes"),
                        )
                    }
                    return
                }
                if (frame.optString("type") == "topic" && networkId != null) {
                    // A live change: keep the topic bar current (still falls through
                    // to render the "X set the topic" system line below).
                    val key = "$networkId::$target"
                    val t = frame.optString("text").ifEmpty { frame.optString("reason") }
                    if (t.isEmpty()) topics.remove(key) else topics[key] = t
                }
                // Typing is ephemeral and must not create buffers — handle it
                // before ensureBuffer and bail.
                if (frame.optString("type") == "typing") {
                    applyTyping("${networkId ?: "sys"}::$target", networkId, frame)
                    return
                }
                // Friend presence is ephemeral (server pseudo-target) — record it
                // and bail before ensureBuffer so it never spawns a buffer.
                if (frame.optString("type") == "peer-presence") {
                    val nick = frame.optString("nick")
                    if (networkId != null && nick.isNotEmpty()) {
                        val k = "$networkId::${nick.lowercase()}"
                        if (frame.isNull("state")) presence.remove(k) else presence[k] = frame.optString("state")
                    }
                    return
                }
                // The authoritative "you actually joined this channel" signal (per
                // amiantos). Create the buffer here — NOT on buffer-opened — and, if
                // it fulfils a join the user just asked for, focus it. A 470 forward
                // makes the joined channel differ from the request, so drop the ghost
                // requested buffer. Reconnect re-joins (no recent pendingJoin) just
                // ensure the buffer exists without stealing focus.
                if (frame.optString("type") == "channel-joined") {
                    val joined = ensureBuffer(networkId, target)
                    val pend = networkId?.let { pendingJoin[it] }
                    if (networkId != null && pend != null &&
                        System.currentTimeMillis() - pend.second < JOIN_FOCUS_WINDOW_MS
                    ) {
                        pendingJoin.remove(networkId)
                        if (!target.equals(pend.first, true)) {
                            val ghost = "$networkId::${pend.first}"
                            buffers.removeAll { it.key == ghost }
                            messagesByBuffer.remove(ghost)
                            unread.remove(ghost)
                            highlights.remove(ghost)
                        }
                        pendingOpen = joined
                    }
                    return
                }
                // A part/channel-parted for a channel we don't hold an open buffer
                // for (a FAILED join, or the leg of a forward we bailed on) must NOT
                // spawn a ghost buffer via ensureBuffer below (amiantos). Drop it —
                // you can't leave a channel you were never in. (pendingJoin is left
                // to expire; a real channel-joined still focuses where we land.)
                val partType = frame.optString("type")
                if ((partType == "channel-parted" || partType == "part") &&
                    buffers.none { it.key == "${networkId ?: "sys"}::$target" }
                ) {
                    return
                }
                bumpCursor(frame.optLong("id"))
                val buffer = ensureBuffer(networkId, target)
                // Roster updates ride the same stream; apply before the renderable
                // check (names/channel-parted produce no chat line at all).
                applyMemberEvent(buffer.key, frame)
                if (networkId != null && frame.optLong("id") > 0) noteReactionRow(frame)
                val msg = parseEvent(frame) ?: return
                // A re-delivered message (e.g. the boundary id on a ?since= resume)
                // is deduped by mergeInto — don't re-count its badge or re-notify.
                if (!mergeInto(buffer.key, listOf(msg), replace = false)) return
                countUnread(buffer.key, frame, msg)
                if (msg.id > 0) scheduleMarkRead(buffer.key)
                if (shouldNotify(frame.optBoolean("notify", false), buffer.key == activeKey, msg.self, msg.system, msg.id > 0, appForeground) &&
                    !isMutedForNotify(buffer.networkId, buffer.target, frame.optString("userhost"), msg.nick)
                ) {
                    val (nNet, nTarget, sameBuffer) = notifyTargetFor(buffer)
                    notificationSink?.invoke(
                        // Drop the message id when we retarget to a relay-linked
                        // pinned channel on another network — that copy has its own
                        // ids, so a scroll-to would miss; just open the buffer.
                        NotifiableEvent(nNet, nTarget, msg.nick, msg.text, frame.optBoolean("dm"), if (sameBuffer) msg.id else 0),
                    )
                }
            }

            "read-state" -> {
                val networkId = if (frame.isNull("networkId")) null else frame.optInt("networkId")
                val target = frame.optString("target")
                val key = "${networkId ?: "sys"}::$target"
                setBadge(unread, key, frame.optInt("unread", 0))
                setBadge(highlights, key, frame.optInt("highlights", 0))
                if (frame.has("lastReadId")) lastRead[key] = frame.optLong("lastReadId")
            }

            "buffer-reopened" -> {
                // A reopen of a previously-closed buffer is authoritative — recreate it.
                val networkId = if (frame.isNull("networkId")) null else frame.optInt("networkId")
                val target = frame.optString("target")
                if (target.isNotEmpty()) ensureBuffer(networkId, target)
            }

            "buffer-opened" -> {
                val networkId = if (frame.isNull("networkId")) null else frame.optInt("networkId")
                val target = frame.optString("target")
                if (target.isEmpty()) return
                // Per amiantos: buffer-opened is only a FOCUS HINT — sufficient where
                // nothing can fail (DMs, or a buffer we already have). A *new* channel
                // must NOT be created here: a 470 forward/redirect makes the joined
                // channel differ from the requested one, which is exactly what left the
                // ghost "#apple" buffer. New channels are created on channel-joined
                // (fresh join) or from the snapshot (already joined).
                val key = "${networkId ?: "sys"}::$target"
                if (buffers.any { it.key == key } || !Commands.isChannel(target)) {
                    ensureBuffer(networkId, target)
                }
            }

            "buffer-closed" -> {
                val networkId = if (frame.isNull("networkId")) null else frame.optInt("networkId")
                dropBuffer("${networkId ?: "sys"}::${frame.optString("target")}")
            }

            // Lurker 2.0: a buffer's target is a resolvable ATTRIBUTE, not an
            // identity — a DM follows its peer through /nick, and a CASEMAPPING
            // refold merges case-twin buffers. Every map here is keyed by
            // "networkId::target", so the rename has to carry all of it across.
            "buffer-renamed" -> {
                val networkId = if (frame.isNull("networkId")) null else frame.optInt("networkId")
                val from = frame.optString("from")
                val to = frame.optString("to")
                if (from.isEmpty() || to.isEmpty()) return
                renameBuffer(networkId, from, to, merged = frame.optBoolean("merged", false))
            }

            "search-result" -> {
                if (frame.optInt("token", -1) != searchToken) return // superseded
                searchLoading = false
                val arr = frame.optJSONArray("results")
                val rows = ArrayList<SearchResult>()
                if (arr != null) for (i in 0 until arr.length()) {
                    parseResultRow(arr.optJSONObject(i) ?: continue)?.let(rows::add)
                }
                if (frame.isNull("before")) searchResults.clear()
                val known = searchResults.mapTo(HashSet()) { it.id }
                searchResults.addAll(rows.filter { it.id !in known })
                searchHasMore = frame.optBoolean("hasMore", false)
                searchNextBefore = if (searchHasMore) searchResults.lastOrNull()?.id else null
            }

            "draft-snapshot" -> {
                drafts.clear()
                draftReplies.clear()
                frame.optJSONArray("drafts")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val d = arr.optJSONObject(i) ?: continue
                        val key = "${d.optInt("networkId")}::${d.optString("target")}"
                        val body = d.optString("body")
                        if (body.isNotEmpty()) drafts[key] = body
                        // A draft's reply follows the user across devices (2.4+).
                        parseDraftReply(d.optJSONObject("reply"))?.let { draftReplies[key] = it }
                    }
                }
            }
            "draft-updated" -> {
                val key = "${frame.optInt("networkId")}::${frame.optString("target")}"
                // Don't clobber an in-progress local edit with a remote echo.
                if (key !in draftPending) {
                    val body = frame.optString("body")
                    if (body.isEmpty()) drafts.remove(key) else drafts[key] = body
                    // Absent on a server that predates replies: leave ours alone.
                    if (frame.has("reply")) {
                        val r = parseDraftReply(frame.optJSONObject("reply"))
                        if (r == null) draftReplies.remove(key) else draftReplies[key] = r
                    }
                }
            }
            "input-history-added" -> {
                val key = "${frame.optInt("networkId")}::${frame.optString("target")}"
                val text = frame.optString("text")
                if (text.isNotEmpty()) inputHistory[key] = (inputHistory[key] ?: emptyList()) + text
            }
            "alias-list-updated" -> applyAliases(frame.optJSONArray("aliases"))

            "pins-changed" -> {
                val networkId = frame.optInt("networkId", -1)
                if (networkId >= 0) {
                    val p = frame.optJSONArray("pinned")
                    pins[networkId] = if (p == null) emptyList() else (0 until p.length()).map { p.optString(it) }
                }
            }
            "channel-notify-changed" -> {
                val key = "${frame.optInt("networkId")}::${frame.optString("target")}"
                if (frame.optBoolean("notifyAlways")) notifyAlways[key] = true else notifyAlways.remove(key)
            }
            "nick-note-updated" -> {
                val key = "${frame.optInt("networkId")}::${frame.optString("nick").lowercase()}"
                val note = frame.optString("note")
                if (note.isEmpty()) nickNotes.remove(key) else nickNotes[key] = note
            }

            "bookmark-ids-snapshot" -> {
                bookmarkIds.clear()
                frame.optJSONArray("ids")?.let { a ->
                    for (i in 0 until a.length()) bookmarkIds.add(a.optLong(i))
                }
            }
            "bookmark-updated" -> {
                val id = frame.optLong("messageId")
                if (frame.optBoolean("saved")) {
                    bookmarkIds.add(id)
                } else {
                    bookmarkIds.remove(id)
                    // Keep the open bookmarks list in sync when one is removed
                    // (from here or another device) — no full reload needed.
                    bookmarkItems.removeAll { it.id == id }
                }
            }

            // The whole ordered favorites list, replaced wholesale — deliberately the
            // same frame for the connect-burst seed and every later
            // favorite/unfavorite/reorder correction, so one handler covers both.
            "favorites-changed" -> {
                val arr = frame.optJSONArray("favorites")
                favorites.clear()
                if (arr != null) for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val target = o.optString("target")
                    if (target.isNotEmpty()) {
                        favorites.add(FavoriteEntry(o.optInt("networkId"), target, o.optInt("bufferId")))
                    }
                }
            }

            "chanlist-result" -> {
                val networkId = frame.optInt("networkId", -1)
                val key = "${frame.optString("query")}|${frame.optString("sortBy")}|${frame.optString("sortDir")}"
                if (networkId != chanlistNetworkId || key != chanlistKey) return // superseded
                chanlistLoading = false
                chanlistInProgress = frame.optBoolean("inProgress", false)
                chanlistTotalCount = frame.optInt("totalCount", 0)
                val rows = frame.optJSONArray("rows")
                val parsed = ArrayList<ChannelListing>()
                if (rows != null) for (i in 0 until rows.length()) {
                    val r = rows.optJSONObject(i) ?: continue
                    parsed.add(ChannelListing(r.optString("channel"), r.optInt("num_users"), r.optString("topic")))
                }
                if (frame.optInt("offset", 0) == 0) chanlistRows.clear()
                val known = chanlistRows.mapTo(HashSet()) { it.channel }
                chanlistRows.addAll(parsed.filter { it.channel !in known })
            }

            "dcc-transfer" -> frame.optJSONObject("transfer")?.let { applyTransfer(it) }

            "send-result" -> {
                val clientId = frame.optString("clientId")
                if (frame.optBoolean("ok", false)) {
                    pendingSends.remove(clientId)
                } else {
                    failSend(clientId, frame.optString("error").ifEmpty { "rejected by server" })
                }
            }

            // Reply to a history(before) page: prepend older events, dedup by id.
            "history" -> {
                val networkId = if (frame.isNull("networkId")) null else frame.optInt("networkId")
                val target = frame.optString("target")
                if (target.isEmpty()) return
                val key = "${networkId ?: "sys"}::$target"
                loadingOlder.remove(key)
                if (frame.has("hasMoreOlder")) hasMoreOlder[key] = frame.optBoolean("hasMoreOlder")
                // A mode:'latest' hydrate carries the buffer's recall list, and it's
                // the only frame that does — a reloaded client gets its up-arrow
                // history back here or not at all (lurker#671).
                frame.optJSONArray("inputHistory")?.let { h ->
                    inputHistory[key] = (0 until h.length()).map { h.optString(it) }
                }
                noteReactions(frame.optJSONArray("events"), networkId)
                val parsed = parseEvents(frame.optJSONArray("events"))
                if (parsed.isEmpty()) return
                val existing = messagesByBuffer[key] ?: emptyList()
                val known = existing.mapNotNull { m -> m.id.takeIf { it > 0 } }.toHashSet()
                val older = parsed.filter { it.id <= 0 || it.id !in known }
                if (older.isNotEmpty()) messagesByBuffer[key] = ensureOrdered(older + existing)
            }

            "error" -> status = frame.optString("text")
        }
    }

    /** Forget a buffer entirely — the row and everything keyed to it. */
    private fun dropBuffer(key: String) {
        buffers.removeAll { it.key == key }
        messagesByBuffer.remove(key)
        unread.remove(key)
        highlights.remove(key)
    }

    /** Move one map entry from [from] to [to], folding into whatever is already
     *  there via [combine] (called as combine(surviving, incoming)). */
    private fun <V> moveEntry(map: MutableMap<String, V>, from: String, to: String, combine: (V, V) -> V) {
        val incoming = map.remove(from) ?: return
        val surviving = map[to]
        map[to] = if (surviving == null) incoming else combine(surviving, incoming)
    }

    /**
     * Carry a renamed buffer's entire state from `from` to `to` (Lurker 2.0's
     * `buffer-renamed`; see MIGRATION_2_0.md). [merged] means `from` folded INTO an
     * existing `to` — a CASEMAPPING case-twin merge — so both sides combine instead
     * of the mover simply landing on an empty key.
     *
     * Everything keyed by "networkId::target" has to move together. Miss one and it
     * orphans silently: messages stop appending, badges freeze, the E2E lock and the
     * per-buffer appearance overrides detach, in-flight sends ack into a dead key,
     * and the peer's next line opens a SECOND buffer.
     */
    private fun renameBuffer(networkId: Int?, from: String, to: String, merged: Boolean) {
        val old = "${networkId ?: "sys"}::$from"
        val new = "${networkId ?: "sys"}::$to"
        if (old == new) return
        val idx = buffers.indexOfFirst { it.key == old }
        if (idx < 0 && messagesByBuffer[old] == null) return // nothing of ours under that name
        DebugLog.i("ws", "buffer renamed $from -> $to${if (merged) " (merged)" else ""}")

        val wasActive = activeKey == old
        // The Buffer row: `target` is a val, so replace the entry. When the
        // destination row already exists (merge), drop the old row instead.
        if (idx >= 0) {
            if (buffers.any { it.key == new }) buffers.removeAt(idx)
            else buffers[idx] = buffers[idx].copy(target = to)
        } else {
            ensureBuffer(networkId, to)
        }

        // Messages: concatenate and re-order, dropping ids the survivor already has.
        messagesByBuffer.remove(old)?.let { incoming ->
            val surviving = messagesByBuffer[new]
            messagesByBuffer[new] = if (surviving == null) incoming else {
                val seen = surviving.mapNotNullTo(HashSet()) { it.id.takeIf { id -> id > 0 } }
                ensureOrdered(surviving + incoming.filter { it.id <= 0 || seen.add(it.id) })
            }
        }
        moveEntry(unread, old, new) { a, b -> a + b }
        moveEntry(highlights, old, new) { a, b -> a + b }
        moveEntry(members, old, new) { a, _ -> a }        // survivor's roster is current
        moveEntry(typing, old, new) { a, b -> a + b }
        moveEntry(drafts, old, new) { a, b -> if (a.isBlank()) b else a }
        moveEntry(draftReplies, old, new) { a, _ -> a }
        moveEntry(inputHistory, old, new) { a, _ -> a }
        moveEntry(topics, old, new) { a, _ -> a }
        moveEntry(channelModes, old, new) { a, _ -> a }
        moveEntry(hasMoreOlder, old, new) { a, b -> a || b }
        moveEntry(loadingOlder, old, new) { a, b -> a || b }
        moveEntry(notifyAlways, old, new) { a, b -> a || b }
        moveEntry(dividerAfter, old, new) { a, b -> minOf(a, b) }
        moveEntry(lastRead, old, new) { a, b -> maxOf(a, b) }
        moveEntry(typingSentAt, old, new) { a, b -> maxOf(a, b) }
        if (draftPending.remove(old)) draftPending.add(new)
        // Cancel rather than re-point: a queued flush closes over the old key. The
        // text survives in `drafts`, and the next edit re-arms the timer.
        draftFlush.remove(old)?.let { main.removeCallbacks(it) }
        if (e2eSeen.remove(old) && new !in e2eSeen) e2eSeen.add(new)
        // Burst bookkeeping — if a rename lands mid-burst and these still say the
        // old key, reconcileBuffers drops the renamed buffer as "closed elsewhere".
        if (burstSeen.remove(old)) burstSeen.add(new)
        if (old in burstBaseline) burstBaseline = burstBaseline - old + new

        // Structures keyed by something else whose VALUES embed the target/key.
        networkId?.let { nid ->
            pins[nid]?.let { list ->
                if (list.any { it.equals(from, true) }) {
                    pins[nid] = list.map { if (it.equals(from, true)) to else it }.distinct()
                }
            }
        }
        pendingSends.filterValues { it.bufferKey == old }.forEach { (id, p) ->
            pendingSends[id] = p.copy(bufferKey = new, target = to)
        }
        failedSends.filterValues { it.bufferKey == old }.forEach { (id, f) ->
            failedSends[id] = f.copy(bufferKey = new, target = to)
        }
        pendingWhois.filterValues { it.first == old }.forEach { (nickKey, v) ->
            pendingWhois[nickKey] = v.copy(first = new)
        }
        // A DM that followed its peer's /nick takes the peer's nick-keyed state too.
        if (networkId != null && !Commands.isChannel(from)) {
            val oldNick = "$networkId::${from.lowercase()}"
            val newNick = "$networkId::${to.lowercase()}"
            nickNotes.remove(oldNick)?.let { if (newNick !in nickNotes) nickNotes[newNick] = it }
            presence.remove(oldNick)?.let { if (newNick !in presence) presence[newNick] = it }
        }

        // Device-local, buffer-keyed: the in-memory Ui mirror AND its persisted Prefs
        // copy must both move, or the stale one wins on next launch.
        val ui = net.amiantos.lurker.ui.theme.Ui
        ui.compactOverrides.remove(old)?.let { if (new !in ui.compactOverrides) ui.compactOverrides[new] = it }
        // containsKey distinguishes the "" ("no background here") sentinel from absent.
        if (ui.backgroundOverrides.containsKey(old)) {
            val v = ui.backgroundOverrides.remove(old)!!
            if (!ui.backgroundOverrides.containsKey(new)) ui.backgroundOverrides[new] = v
        }
        if (ui.multichan.remove(old) && new !in ui.multichan) ui.multichan.add(new)
        if (ui.translateRead.remove(old) && new !in ui.translateRead) ui.translateRead.add(new)
        ui.translateOutgoing.remove(old)?.let { if (new !in ui.translateOutgoing) ui.translateOutgoing[new] = it }
        prefs?.let { p ->
            p.compactOverrides.takeIf { it.containsKey(old) }?.let { m ->
                p.compactOverrides = m - old + (new to m.getValue(old))
            }
            p.backgroundOverrides.takeIf { it.containsKey(old) }?.let { m ->
                p.backgroundOverrides = m - old + (new to m.getValue(old))
            }
            if (old in p.multichanKeys) p.multichanKeys = p.multichanKeys - old + new
            if (old in p.e2eBuffers) p.e2eBuffers = p.e2eBuffers - old + new
            if (old in p.translateRead) p.translateRead = p.translateRead - old + new
            p.translateOutgoing.takeIf { it.containsKey(old) }?.let { m ->
                p.translateOutgoing = m - old + (new to m.getValue(old))
            }
        }

        // Follow the rename in the UI: the open chat holds a Buffer VALUE copy, so
        // it keeps rendering the dead key until pendingOpen re-points it.
        if (wasActive) {
            activeKey = new
            buffers.firstOrNull { it.key == new }?.let {
                activeBuffer = it
                pendingOpen = it
            }
        }
    }

    /**
     * Drop buffers the just-finished burst never mentioned (lurker-ios#78).
     *
     * A `buffer-closed` frame only reaches a client that's connected, so a channel
     * closed from the web while we were backgrounded used to linger here until the
     * next relaunch. The burst is the server's own statement of what's open, so
     * anything it skipped has been closed elsewhere.
     *
     * Scoped to what we held when the burst STARTED, so a buffer opened while it
     * was still arriving is never mistaken for an omission. System buffers are
     * exempt: they're app-scoped rather than per-network and aren't enumerated the
     * same way, so their absence proves nothing.
     *
     * This drops the local row, not server-side history — a closed buffer keeps its
     * full backlog and comes back with everything intact when reopened, which is
     * exactly the "not currently open" vs "never existed" distinction lurker#640
     * draws.
     *
     * Runs once per socket: the baseline is cleared afterwards, so the later
     * backlog-completes that an in-band resync or a fresh-network re-emission also
     * produce can't prune against a stale picture.
     */
    private fun reconcileBuffers() {
        val dropped = staleBufferKeys(burstBaseline, burstSeen).filter { key ->
            // System buffers are app-scoped rather than per-network and aren't
            // enumerated the same way, so their absence proves nothing.
            buffers.firstOrNull { it.key == key }?.isSystem == false
        }
        dropped.forEach(::dropBuffer)
        burstBaseline = emptySet()
        if (dropped.isNotEmpty()) DebugLog.i("ws", "closed elsewhere: ${dropped.joinToString()}")
    }

    /** Ask for the page of history older than what's currently loaded. */
    fun loadOlder(buffer: Buffer) {
        val networkId = buffer.networkId ?: return
        val key = buffer.key
        if (loadingOlder[key] == true) return
        val oldest = messagesByBuffer[key]?.firstOrNull { it.id > 0 }?.id ?: return
        loadingOlder[key] = true
        ws?.send(
            JSONObject()
                .put("type", "history")
                .put("networkId", networkId)
                .put("target", buffer.target)
                .put("mode", "before")
                .put("before", oldest)
                .put("limit", 100)
                .put("countBy", pageCountUnit())
                .toString(),
        )
    }

    private fun failSend(clientId: String, reason: String) {
        val pending = pendingSends.remove(clientId) ?: return
        recordFailedSend(pending.bufferKey, pending.networkId, pending.target, pending.type, pending.text, reason)
    }

    /**
     * Surface an undelivered message: retain the original text under a [FailedSend]
     * and drop an inline row in the buffer offering Resend / Discard. Called for a
     * server rejection, a missing ack, and — the common case on a flaky connection —
     * a send attempted with no live socket at all.
     */
    protected fun recordFailedSend(
        bufferKey: String,
        networkId: Int,
        target: String,
        type: String,
        text: String,
        reason: String,
    ) {
        val id = "f${++failedSeq}"
        failedSends[id] = FailedSend(id, bufferKey, networkId, target, type, text, reason)
        val preview = if (text.length > 80) text.take(77) + "…" else text
        mergeInto(
            bufferKey,
            listOf(
                Msg(
                    id = 0, type = "send-failed", nick = "",
                    text = "⚠ Not delivered ($reason): “$preview”",
                    self = false, system = true, failedId = id,
                ),
            ),
            replace = false,
        )
    }

    /** Drop a failed send's record and its inline row from the buffer. */
    private fun clearFailed(failed: FailedSend) {
        failedSends.remove(failed.id)
        messagesByBuffer[failed.bufferKey]
            ?.filterNot { it.failedId == failed.id }
            ?.let { messagesByBuffer[failed.bufferKey] = it }
    }

    /** Retry an undelivered message verbatim. The row disappears; if the send fails
     *  again a fresh one takes its place (with the new reason). */
    fun resendFailed(id: String) {
        val failed = failedSends[id] ?: return
        clearFailed(failed)
        val buffer = buffers.firstOrNull { it.key == failed.bufferKey }
            ?: Buffer(failed.networkId, failed.target, networkNames[failed.networkId] ?: "network")
        execute(buffer, listOf(WireOp(failed.type, target = failed.target, text = failed.text)))
    }

    /** Give up on an undelivered message — removes the row and the retained text. */
    fun discardFailed(id: String) {
        failedSends[id]?.let(::clearFailed)
    }

    private fun applyNetworks(arr: JSONArray) {
        for (i in 0 until arr.length()) {
            val n = arr.optJSONObject(i) ?: continue
            // The snapshot blob keys the network as `networkId` (REST uses `id`).
            val id = n.optInt("networkId", n.optInt("id", -1))
            if (id < 0) continue
            val name = n.optString("name").ifEmpty { networkNames[id] ?: "network" }
            networkNames[id] = name
            networks[id] = Network(
                id = id,
                name = name,
                nick = n.optString("nick").ifEmpty { null },
                connected = n.optBoolean("connected", n.optString("state") == "connected"),
            )
            // ISUPPORT vocabulary carried in the snapshot (patched server); null
            // on a vanilla server so the UI falls back to a curated mode list.
            n.optJSONObject("serverInfo")?.let { si ->
                serverInfo[id] = ServerInfo.parse(
                    software = si.optString("software"),
                    network = si.optString("network"),
                    chanModes = si.optString("chanModes"),
                    prefix = si.optString("prefix"),
                    chanTypes = si.optString("chanTypes"),
                )
            }
            // Whether a reaction can go out here (Lurker 2.4+). Absent on a
            // disconnected network: treat as false.
            networkCanReact[id] = n.optBoolean("canReact", false)
            if (n.has("canReact")) serverHasReactions = true
            // DCC CHAT state rides every snapshot: replace this network's slice.
            n.optJSONArray("dccChats")?.let { live ->
                dccChatsLive.removeAll { it.startsWith("$id::") }
                for (j in 0 until live.length()) {
                    val nk = live.optString(j)
                    if (nk.isNotEmpty()) dccChatsLive.add("$id::${nk.lowercase()}")
                }
            }
            n.optJSONArray("dccChatOffers")?.let { offers ->
                val pending = (0 until offers.length()).map { offers.optString(it) }.filter { it.isNotEmpty() }
                val keys = pending.map { "$id::${it.lowercase()}" }.toSet()
                dccChatOffers.keys.filter { it.startsWith("$id::") && it !in keys }.forEach { dccChatOffers.remove(it) }
                // Keep a live-event entry (it knows `passive`); add any we missed.
                for (nk in pending) {
                    val offer = DccChatOffer(id, nk, passive = false)
                    if (offer.key !in dccChatOffers) dccChatOffers[offer.key] = offer
                }
            }
            // Per-network pinned buffers, in the user's chosen order.
            n.optJSONArray("pinned")?.let { p ->
                pins[id] = (0 until p.length()).map { p.optString(it) }
            }
            // Nick notes: {nick, note} rows keyed by (networkId, nick).
            n.optJSONArray("nickNotes")?.let { notes ->
                for (i in 0 until notes.length()) {
                    val nt = notes.optJSONObject(i) ?: continue
                    val nk = nt.optString("nick"); val body = nt.optString("note")
                    if (nk.isNotEmpty() && body.isNotEmpty()) nickNotes["$id::${nk.lowercase()}"] = body
                }
            }
            // channelNotify: { "#target": { notifyAlways } }.
            n.optJSONObject("channelNotify")?.let { cn ->
                for (t in cn.keys()) {
                    if (cn.optJSONObject(t)?.optBoolean("notifyAlways") == true) notifyAlways["$id::$t"] = true
                }
            }
            // peerPresence: { "nicklower": { nick, state, stateAt } } — the current
            // online/away state of tracked friends/DM peers. Without applying this,
            // a friend who was ALREADY online when we connected shows a grey dot
            // until they next toggle (no live peer-presence event fires on connect),
            // while the web client — which applies the snapshot — shows them online.
            n.optJSONObject("peerPresence")?.let { pp ->
                for (nk in pp.keys()) {
                    val st = pp.optJSONObject(nk)?.optString("state")
                    if (!st.isNullOrEmpty()) presence["$id::${nk.lowercase()}"] = st
                }
            }
            // The snapshot carries each channel's member roster inline.
            n.optJSONArray("channels")?.let { chans ->
                for (c in 0 until chans.length()) {
                    val ch = chans.optJSONObject(c) ?: continue
                    val chName = ch.optString("name")
                    if (chName.isEmpty()) continue
                    val key = "$id::$chName"
                    ch.optJSONArray("members")?.let { members[key] = parseMembers(it) }
                    // The snapshot already carries each channel's current topic and
                    // flag-mode string — capture them so the control panel shows the
                    // active modes (and topic) immediately, instead of waiting for
                    // the on-open MODE/TOPIC self-heal to round-trip.
                    ch.optString("topic").takeIf { it.isNotEmpty() }?.let { topics[key] = it }
                    ch.optString("modes").takeIf { it.isNotEmpty() }?.let { channelModes[key] = it }
                }
            }
        }
    }

    // ---- Channel members ----------------------------------------------------

    protected fun parseMembers(arr: JSONArray): List<Member> =
        (0 until arr.length()).mapNotNull { i ->
            val m = arr.optJSONObject(i) ?: return@mapNotNull null
            val nick = m.optString("nick")
            if (nick.isEmpty()) return@mapNotNull null
            Member(
                nick = nick,
                modes = m.optJSONArray("modes")?.let { md ->
                    (0 until md.length()).map { md.optString(it) }
                } ?: emptyList(),
                away = m.optBoolean("away", false),
                host = m.optString("host").ifEmpty { null },
            )
        }

    /**
     * Keep per-buffer rosters current. `names` frames replace wholesale; the
     * join/part/quit/kick/nick stream is applied incrementally (the server does
     * NOT re-send names for those — mirrors the web client's store).
     */
    protected fun applyMemberEvent(key: String, frame: JSONObject) {
        when (frame.optString("type")) {
            "names" -> frame.optJSONArray("members")?.let { members[key] = parseMembers(it) }
            "join" -> {
                val nick = frame.optString("nick")
                val cur = members[key] ?: return
                if (nick.isNotEmpty() && cur.none { it.nick.equals(nick, true) }) {
                    members[key] = cur + Member(nick)
                }
            }
            "part", "quit" -> removeMember(key, frame.optString("nick"))
            "kick" -> removeMember(key, frame.optString("kicked"))
            "nick" -> {
                val to = frame.optString("newNick")
                val from = frame.optString("nick")
                val cur = members[key] ?: return
                if (to.isEmpty()) return
                members[key] = cur.map { if (it.nick.equals(from, true)) it.copy(nick = to) else it }
            }
            "channel-parted" -> members.remove(key)
        }
    }

    // ---- Typing indicators --------------------------------------------------

    /** Apply an inbound typing event: active adds (with TTL), paused/done remove. */
    private fun applyTyping(key: String, networkId: Int?, frame: JSONObject) {
        val nick = frame.optString("nick")
        if (nick.isEmpty()) return
        // Never show our own echo.
        if (networkId != null && networks[networkId]?.nick?.equals(nick, true) == true) return
        val cur = typing[key].orEmpty().toMutableMap()
        if (frame.optString("state") == "active") {
            cur[nick] = System.currentTimeMillis() + TYPING_TTL
            main.postDelayed({ sweepTyping(key) }, TYPING_TTL + 250)
        } else {
            cur.remove(nick)
        }
        if (cur.isEmpty()) typing.remove(key) else typing[key] = cur
    }

    /** Drop typers whose `active` refresh stopped arriving (they went quiet). */
    private fun sweepTyping(key: String) {
        val now = System.currentTimeMillis()
        val cur = typing[key] ?: return
        val alive = cur.filterValues { it > now }
        if (alive.size != cur.size) {
            if (alive.isEmpty()) typing.remove(key) else typing[key] = alive
        }
    }

    private val typingSentAt = mutableMapOf<String, Long>()

    /**
     * Broadcast that we're composing in [buffer] (throttled to every 3s, the
     * IRCv3 cadence). Gated by chat.send_typing_notifications; commands are
     * never broadcast.
     */
    fun notifyTyping(buffer: Buffer, active: Boolean) {
        val networkId = buffer.networkId ?: return
        // A DCC chat is a direct socket; there's no IRC target to TAGMSG.
        if (buffer.isDccChat) return
        if (!settingBool("chat.send_typing_notifications", true)) return
        val now = System.currentTimeMillis()
        if (active && now - (typingSentAt[buffer.key] ?: 0) < 3_000) return
        typingSentAt[buffer.key] = if (active) now else 0
        ws?.send(
            JSONObject()
                .put("type", "typing")
                .put("networkId", networkId)
                .put("target", buffer.target)
                .put("state", if (active) "active" else "done")
                .toString(),
        )
    }

    /** Effective bool value of a registry setting: override ?? default ?? fallback. */
    fun settingBool(key: String, fallback: Boolean): Boolean {
        (settingsValues[key] as? Boolean)?.let { return it }
        (settingsRegistry.firstOrNull { it.key == key }?.default as? Boolean)?.let { return it }
        return fallback
    }

    fun settingString(key: String, fallback: String): String {
        (settingsValues[key] as? String)?.takeIf { it.isNotEmpty() }?.let { return it }
        (settingsRegistry.firstOrNull { it.key == key }?.default as? String)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        return fallback
    }

    /**
     * How much join/part/quit noise to show: `all` / `smart` / `none` (lurker#672).
     *
     * The mobile variant wins where the server offers one — a phone has far less
     * room for noise than a desktop, which is the whole reason it exists. Falls
     * back to `all`, matching the server's default, so a server too old to
     * advertise either key behaves exactly as before.
     */
    val eventFilterTier: String
        get() = settingString("chat.events.mobile", "").ifEmpty { settingString("chat.events", "all") }

    /**
     * What a history page should be counted in (lurker#670).
     *
     * While we're hiding presence churn, a page measured in stored rows can arrive
     * almost entirely invisible — a screenful that renders as nothing, and a "Load
     * older" that appears to do nothing. Asking for `renderable` sizes the page by
     * lines that will actually be drawn. Older servers ignore the field, so this is
     * safe to send unconditionally.
     */
    private fun pageCountUnit(): String =
        if (eventFilterTier == "all") "event" else "renderable"

    /**
     * Render a whois_result — but only one we asked for, into the buffer we asked
     * from. The event is broadcast to all of the account's clients with no sender
     * attribution, so an unmatched reply belongs to another client (Lurker web, a
     * second device) and is dropped here. Nothing is lost by dropping it: the
     * server still writes the raw whois numerics to that network's Server buffer.
     */
    private fun handleWhoisResult(frame: JSONObject) {
        val w = frame.optJSONObject("whois") ?: return
        val networkId = if (frame.isNull("networkId")) null else frame.optInt("networkId")
        val nick = w.optString("nick").lowercase()
        if (nick.isEmpty()) return
        val (key, askedAt) = pendingWhois.remove("$networkId::$nick") ?: return
        if (System.currentTimeMillis() - askedAt > WHOIS_REPLY_WINDOW) return
        val msg = Msg(
            id = 0,
            type = "whois",
            nick = "",
            text = formatWhois(w),
            self = false,
            time = frame.optString("time").ifEmpty { null },
            system = true,
        )
        mergeInto(key, listOf(msg), replace = false)
    }

    private fun formatWhois(w: JSONObject): String = buildString {
        val nick = w.optString("nick", "?")
        if (w.optString("error") == "not_found") {
            append("WHOIS $nick — no such nick")
            return@buildString
        }
        append("WHOIS $nick")
        val ident = w.optString("ident")
        val host = w.optString("hostname")
        if (ident.isNotEmpty() || host.isNotEmpty()) append(" ($ident@$host)")
        w.optString("real_name").takeIf { it.isNotEmpty() }?.let { append("\nname      $it") }
        w.optString("account").takeIf { it.isNotEmpty() }?.let { append("\naccount   $it") }
        w.optString("server").takeIf { it.isNotEmpty() }?.let {
            append("\nserver    $it")
            w.optString("server_info").takeIf { s -> s.isNotEmpty() }?.let { s -> append(" ($s)") }
        }
        w.optString("channels").takeIf { it.isNotEmpty() }?.let { append("\nchannels  $it") }
        val idle = w.optLong("idle", -1)
        if (idle >= 0) append("\nidle      ${formatIdle(idle)}")
        w.optString("away").takeIf { it.isNotEmpty() }?.let { append("\naway      $it") }
        val actual = listOf(w.optString("actual_hostname"), w.optString("actual_ip"))
            .filter { it.isNotEmpty() }.distinct()
        if (actual.isNotEmpty()) append("\nactually  ${actual.joinToString(" ")}")
        if (w.optBoolean("secure")) append("\nsecure connection")
        if (w.optBoolean("operator")) append("\nIRC operator")
    }

    private fun formatIdle(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    protected fun removeMember(key: String, nick: String) {
        if (nick.isEmpty()) return
        members[key]?.let { cur -> members[key] = cur.filterNot { it.nick.equals(nick, true) } }
    }

    /** The signed-in user's own membership row in [buffer], for op-gating menus. */
    fun myMember(buffer: Buffer): Member? {
        val nick = buffer.networkId?.let { networks[it]?.nick } ?: return null
        return members[buffer.key]?.firstOrNull { it.nick.equals(nick, true) }
    }

    protected fun bumpCursor(id: Long) {
        if (id > maxMessageId) maxMessageId = id
    }

    private fun bumpCursorFromEvents(arr: JSONArray?) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            bumpCursor(arr.optJSONObject(i)?.optLong("id") ?: 0L)
        }
    }

    protected fun ensureBuffer(networkId: Int?, target: String): Buffer {
        val key = "${networkId ?: "sys"}::$target"
        buffers.firstOrNull { it.key == key }?.let { return it }
        val buffer = Buffer(
            networkId = networkId,
            target = target,
            networkName = networkId?.let { networkNames[it] } ?: "system",
        )
        buffers.add(buffer)
        return buffer
    }

    /** Returns whether anything was actually added (false = every id was a dup),
     *  so the live path can avoid double-counting a re-delivered message. */
    /** Remember (and persist) that a buffer carries E2E, so its sidebar lock
     *  survives a restart / shows before history loads. */
    protected fun markE2e(key: String) {
        if (key in e2eSeen) return
        e2eSeen.add(key)
        prefs?.let { it.e2eBuffers = it.e2eBuffers + key }
    }

    protected fun mergeInto(key: String, newMsgs: List<Msg>, replace: Boolean): Boolean {
        if (newMsgs.any { it.e2e }) markE2e(key)
        if (replace) {
            messagesByBuffer[key] = newMsgs
            return true
        }
        val existing = messagesByBuffer[key] ?: emptyList()
        if (existing.isEmpty()) {
            messagesByBuffer[key] = newMsgs
            return true
        }
        val ids = existing.mapNotNull { if (it.id > 0) it.id else null }.toHashSet()
        val add = newMsgs.filter { it.id <= 0 || ids.add(it.id) }
        if (add.isEmpty()) return false
        var merged = ensureOrdered(existing + add)
        // Nothing used to trim a buffer, so a busy channel left running all day
        // held every line it ever received — and each new line copies the whole
        // list. Cap the buffers you're NOT looking at (trimming the open one would
        // yank the scroll position); with a server behind us the trimmed history
        // is one "Load older" away.
        if (merged.size > MAX_BUFFER_MESSAGES && key != activeKey) {
            merged = merged.takeLast(KEEP_BUFFER_MESSAGES)
            if (serverFeatures) hasMoreOlder[key] = true
        }
        messagesByBuffer[key] = merged
        return true
    }

    protected fun ensureOrdered(msgs: List<Msg>): List<Msg> = orderMessagesById(msgs)

    // ---- Unread accounting ------------------------------------------------

    protected fun seedUnread(key: String, frame: JSONObject) {
        if (key == activeKey) return
        if (frame.has("unread")) setBadge(unread, key, frame.optInt("unread", 0))
        if (frame.has("highlights")) setBadge(highlights, key, frame.optInt("highlights", 0))
    }

    protected fun countUnread(key: String, frame: JSONObject, msg: Msg) {
        if (key == activeKey || msg.self || msg.system) return
        if (msg.type !in COUNTABLE) return
        unread[key] = (unread[key] ?: 0) + 1
        if (frame.optBoolean("matched", false) || frame.optBoolean("dm", false)) {
            highlights[key] = (highlights[key] ?: 0) + 1
        }
    }

    protected fun setBadge(map: MutableMap<String, Int>, key: String, value: Int) {
        if (value <= 0) map.remove(key) else map[key] = value
    }

    /** Mark a buffer focused: clears its badge and tells the server we've read it. */
    fun setActive(buffer: Buffer?) {
        val prev = activeKey
        activeKey = buffer?.key
        activeBuffer = buffer
        if (prev != null && prev != buffer?.key) dividerAfter.remove(prev)
        val key = buffer?.key ?: return
        // Anchor the New-messages divider before the badge and cursor move.
        val cursor = lastRead[key] ?: 0L
        if ((unread[key] ?: 0) > 0 && cursor > 0) dividerAfter[key] = cursor
        unread.remove(key)
        highlights.remove(key)
        markRead(buffer)
    }

    private var activeBuffer: Buffer? = null
    private var markReadQueued = false

    /**
     * Keep the server's read cursor at our heels while a buffer is focused:
     * every persisted event that lands in the active buffer (fresh backlog on
     * reopen included) queues a mark-read, batched to at most one every 1.5s so
     * a busy channel doesn't chatter the socket. Without this, only the moment
     * of opening was marked — everything watched afterwards stayed "unread" on
     * the server and every other device.
     */
    protected fun scheduleMarkRead(key: String) {
        if (key != activeKey || markReadQueued) return
        markReadQueued = true
        main.postDelayed({
            markReadQueued = false
            activeBuffer?.takeIf { it.key == activeKey }?.let(::markRead)
        }, 1_500)
    }

    // ---- Drafts + input history --------------------------------------------

    /** Update a buffer's draft locally and debounce-sync it (500ms, like web). */
    fun setDraftLocal(buffer: Buffer, text: String) {
        if (buffer.networkId == null) return
        val key = buffer.key
        if (text.isEmpty()) drafts.remove(key) else drafts[key] = text
        scheduleDraftFlush(buffer)
    }

    /** Start (or cancel, with null) a reply in [buffer]'s composer. Part of the
     *  draft, so it syncs to the account's other devices like the text does. */
    fun setDraftReply(buffer: Buffer, reply: DraftReply?) {
        if (buffer.networkId == null) return
        if (reply == null) draftReplies.remove(buffer.key) else draftReplies[buffer.key] = reply
        scheduleDraftFlush(buffer)
    }

    /** Drop the reply without syncing — the send that used it clears the whole
     *  draft server-side anyway. */
    fun consumeDraftReply(buffer: Buffer): DraftReply? = draftReplies.remove(buffer.key)

    private fun scheduleDraftFlush(buffer: Buffer) {
        val key = buffer.key
        draftPending.add(key)
        draftFlush.remove(key)?.let(main::removeCallbacks)
        val r = Runnable { flushDraft(buffer) }
        draftFlush[key] = r
        main.postDelayed(r, 500)
    }

    /** Send the pending draft now (called on buffer switch, background, send). */
    fun flushDraft(buffer: Buffer) {
        val networkId = buffer.networkId ?: return
        val key = buffer.key
        draftFlush.remove(key)?.let(main::removeCallbacks)
        if (key !in draftPending) return
        draftPending.remove(key)
        val body = drafts[key].orEmpty()
        val reply = draftReplies[key]
        ws?.send(
            JSONObject().put("networkId", networkId).put("target", buffer.target).apply {
                if (body.isEmpty() && reply == null) {
                    put("type", "draft-clear")
                } else {
                    put("type", "draft-set").put("body", body)
                    // Always say it: `reply: null` clears one set elsewhere. A
                    // pre-2.4 server ignores the field.
                    put(
                        "reply",
                        reply?.let { JSONObject().put("messageId", it.messageId).put("addressed", it.addressed) }
                            ?: JSONObject.NULL,
                    )
                }
            }.toString(),
        )
    }

    /** Record a submitted line in the per-buffer history (server-synced + local). */
    fun addInputHistory(buffer: Buffer, text: String) {
        val networkId = buffer.networkId ?: return
        if (text.isBlank()) return
        inputHistory[buffer.key] = (inputHistory[buffer.key] ?: emptyList()) + text
        ws?.send(
            JSONObject().put("type", "input-history-add")
                .put("networkId", networkId).put("target", buffer.target).put("text", text)
                .toString(),
        )
    }

    // ---- Aliases (fork-only) ------------------------------------------------

    private fun applyAliases(arr: JSONArray?) {
        if (arr == null) return
        val list = (0 until arr.length()).mapNotNull { i ->
            val a = arr.optJSONObject(i) ?: return@mapNotNull null
            AliasEntry(a.optInt("id"), a.optString("name"), a.optString("expansion"))
        }
        aliases.clear(); aliases.addAll(list)
    }

    fun addAlias(name: String, expansion: String) {
        ws?.send(JSONObject().put("type", "add-alias").put("name", name).put("expansion", expansion).toString())
    }

    /** A network's display name, for synthesizing a buffer row we don't hold yet. */
    fun networkName(id: Int): String = networkNames[id] ?: "network"

    /** Whether this buffer is in the user's favorites (Friends if a DM, Favorites
     *  if a channel — one server-side flag behind both labels). */
    fun isFavorite(buffer: Buffer): Boolean = isFavorite(buffer.networkId, buffer.target)

    fun isFavorite(networkId: Int?, target: String): Boolean =
        networkId != null && favorites.any { it.networkId == networkId && it.target.equals(target, true) }

    /**
     * Favorite / unfavorite a buffer. The server replies with the whole
     * `favorites-changed` list, so we don't touch local state here — and because
     * one buffer holds one placement, favoriting also drops any pin (the server
     * follows up with `pins-changed`). Server/system pseudo-buffers and closed
     * buffers are refused server-side, so the UI only offers this on real ones.
     */
    fun toggleFavorite(networkId: Int?, target: String) {
        val nid = networkId ?: return
        val verb = if (isFavorite(nid, target)) "unfavorite-buffer" else "favorite-buffer"
        ws?.send(JSONObject().put("type", verb).put("networkId", nid).put("target", target).toString())
    }

    /** Rewrite the global favorites order. Id-form ONLY — favorites span networks,
     *  so bare names can't address them. May be a subset: unmentioned favorites keep
     *  their relative order after the supplied ones, which lets one kind-filtered
     *  section (Friends or Favorites) reorder without disturbing the other. */
    fun reorderFavorites(bufferIds: List<Int>) {
        if (bufferIds.isEmpty()) return
        ws?.send(JSONObject().put("type", "reorder-favorites").put("bufferIds", JSONArray(bufferIds)).toString())
    }

    /** Raw presence state for a nick ("online" / "away" / "offline" / "back"), or
     *  null when we've never heard — which the UI renders as grey "unknown". */
    fun presenceState(networkId: Int?, nick: String): String? =
        presence["${networkId}::${nick.lowercase()}"]

    /** True when a nick is known to be present (online/away/back — anything but
     *  an explicit offline). Used to sort the Friends buffer-list section. */
    fun isPresent(networkId: Int?, nick: String): Boolean {
        val st = presenceState(networkId, nick)
        return st != null && st != "offline"
    }

    /**
     * Add a peer to Friends: favorite their DM buffer. The server refuses a CLOSED
     * buffer, so mint it first — `open` and the favorite ride the same socket in
     * order, which makes the pair safe without waiting for a round trip.
     */
    fun addFriend(networkId: Int, nick: String) {
        if (isFavorite(networkId, nick)) return
        ws?.send(JSONObject().put("type", "open-buffer").put("networkId", networkId).put("target", nick).toString())
        ws?.send(JSONObject().put("type", "favorite-buffer").put("networkId", networkId).put("target", nick).toString())
    }

    /** Ask the server to refresh a nick's presence (e.g. when opening their DM). */
    fun probePresence(networkId: Int, nick: String) {
        ws?.send(JSONObject().put("type", "probe-presence").put("networkId", networkId).put("nick", nick).toString())
    }

    fun removeAlias(id: Int) {
        ws?.send(JSONObject().put("type", "remove-alias").put("id", id).toString())
    }

    protected fun markRead(buffer: Buffer) {
        val networkId = buffer.networkId ?: return
        val lastId = messagesByBuffer[buffer.key]?.lastOrNull { it.id > 0 }?.id ?: return
        ws?.send(
            JSONObject()
                .put("type", "mark-read")
                .put("networkId", networkId)
                .put("target", buffer.target)
                .put("messageId", lastId)
                .toString(),
        )
    }

    // ---- Event parsing ----------------------------------------------------

    private fun parseEvents(arr: JSONArray?): List<Msg> {
        if (arr == null) return emptyList()
        val out = ArrayList<Msg>(arr.length())
        for (i in 0 until arr.length()) {
            parseEvent(arr.optJSONObject(i) ?: continue)?.let(out::add)
        }
        return out
    }

    private fun parseEvent(e: JSONObject): Msg? {
        // The server flags ignored-sender lines; we drop them (client-filtered).
        if (e.optBoolean("fromIgnored", false)) return null
        val type = e.optString("type")
        val id = e.optLong("id")
        val nick = eventNick(e)
        val text = e.optString("text")
        // Automated TAGMSG/BATCH "unknown command" bounces: a typing notification
        // (client-only tag carried over TAGMSG) that a network without the
        // message-tags cap rejects with 421. The user never typed it, so drop the
        // bounce instead of showing a red error line. Structured field first, with
        // a text fallback for servers that don't tag it ("unknown_command TAGMSG …").
        val unknownCmd = e.optString("unknownCommand")
        if (unknownCmd == "TAGMSG" || unknownCmd == "BATCH") return null
        if (type == "error" && text.startsWith("unknown_command ") &&
            (text.contains("TAGMSG") || text.contains("BATCH"))
        ) {
            return null
        }
        return when (type) {
            "message", "action", "notice" -> Msg(
                id = id, type = type, nick = nick, text = text,
                self = e.optBoolean("self", false), time = e.optString("time").ifEmpty { null },
                // Persisted flag: the message rode E2E (server already decrypted it).
                e2e = e.optBoolean("e2e", false),
                // Highlight rule hit (a mention / your nick) — gold background. A
                // reply to one of your lines is one too (the server stamps both).
                matched = e.optBoolean("matched", false) || e.optBoolean("replyToSelf", false),
                msgid = if (e.isNull("msgid")) null else e.optString("msgid").ifEmpty { null },
                replyTo = parseReplyRef(e.optJSONObject("replyTo")),
                replyToSelf = e.optBoolean("replyToSelf", false),
            )
            // The server's own voice: a 401 routed into a channel or DM ("gnat
            // isn't on this network."), a rejected command, an ircd error. It has
            // no author, so it is a system line, never a bubble with a sender
            // header — the shape DirectIrcBackend already uses for its error rows,
            // and how Scully draws them (a `!!` marker, not the nick column).
            "error" -> Msg(
                id = id, type = type, nick = nick, text = text,
                self = false, time = e.optString("time").ifEmpty { null },
                system = true,
            )
            // Ephemeral E2E status/handshake line: rendered as a tagged system
            // line (green info / red warn), never persisted. No crypto involved.
            "e2e" -> Msg(
                id = id, type = "e2e", nick = "", text = text,
                self = false, time = e.optString("time").ifEmpty { null },
                system = true,
                level = e.optString("level").ifEmpty { "info" },
            )
            // The :system: buffer's log lines (#355): type 'system', nick null,
            // with level/scope/source riding alongside.
            "system" -> Msg(
                id = id, type = "system", nick = "",
                text = buildString {
                    val source = e.optString("source")
                    if (source.isNotEmpty()) append("[").append(source).append("] ")
                    append(text)
                },
                self = false, time = e.optString("time").ifEmpty { null },
                system = true,
                level = e.optString("level").ifEmpty { null },
            )
            in SYSTEM_TYPES -> Msg(
                id = id, type = type, nick = "", text = systemLine(type, nick, text, e),
                self = e.optBoolean("self", false), time = e.optString("time").ifEmpty { null },
                system = true,
            )
            else -> null // ephemeral (names/typing/state/…) — nothing to render inline
        }
    }

    /** Best-effort one-line rendering of a structural IRC event. */
    protected fun systemLine(type: String, nick: String, text: String, e: JSONObject): String {
        val reason = text.takeIf { it.isNotBlank() }
        return when (type) {
            "join" -> "→ $nick joined"
            "part" -> "← $nick left" + (reason?.let { " ($it)" } ?: "")
            "quit" -> "⇐ $nick quit" + (reason?.let { " ($it)" } ?: "")
            "kick" -> {
                // The victim rides "kicked" (server) — "target" is the CHANNEL, which
                // is why kick lines used to read "X kicked #channel" (d3fc0n1).
                val who = e.optString("kicked").ifEmpty { e.optString("victim") }
                "$nick kicked ${who.ifEmpty { "someone" }}" + (reason?.let { " ($it)" } ?: "")
            }
            "nick" -> {
                val to = e.optString("newNick").ifEmpty { text }
                "$nick is now known as $to"
            }
            "mode" -> "$nick set mode" + (reason?.let { " $it" } ?: "")
            "topic" -> if (reason != null) "$nick set the topic: $reason" else "$nick cleared the topic"
            "invite" -> inviteLine(nick, e)
            else -> reason ?: type
        }
    }

    // ---- Sending ----------------------------------------------------------

    /** Hydrate a buffer's history on open (shells arrive empty). */
    open fun open(buffer: Buffer) {
        val networkId = buffer.networkId ?: return
        ws?.send(
            JSONObject()
                .put("type", "open-buffer")
                .put("networkId", networkId)
                .put("target", buffer.target)
                .toString(),
        )
        // Self-healing roster: the server only pushes `names` on join/NAMES/WHO,
        // so if we somehow have no roster for a joined channel, ask for one.
        if (buffer.isChannel && members[buffer.key].isNullOrEmpty()) {
            ws?.send(
                JSONObject()
                    .put("type", "raw")
                    .put("networkId", networkId)
                    .put("line", "NAMES ${buffer.target}")
                    .toString(),
            )
        }
        // Self-healing topic: the server only pushes the topic on join (RPL_TOPIC)
        // and on live changes, so a channel we were already in before connecting
        // never has one — the topic bar stays blank (d3fc0n1). Query it once; the
        // RPL_TOPIC reply comes back as a `channel-topic` event and fills the bar.
        if (buffer.isChannel && topics[buffer.key].isNullOrBlank()) {
            ws?.send(
                JSONObject()
                    .put("type", "raw")
                    .put("networkId", networkId)
                    .put("line", "TOPIC ${buffer.target}")
                    .toString(),
            )
        }
        // Same self-heal for channel modes — query them once so the control panel
        // shows the current flags for a channel we were already in (RPL_CHANNELMODEIS).
        if (buffer.isChannel && channelModes[buffer.key] == null) {
            ws?.send(
                JSONObject()
                    .put("type", "raw")
                    .put("networkId", networkId)
                    .put("line", "MODE ${buffer.target}")
                    .toString(),
            )
        }
    }

    /** True when the channel has flag mode [mode] set (e.g. 'm', 't', 'i'). */
    fun hasChannelMode(buffer: Buffer, mode: Char): Boolean =
        channelModes[buffer.key]?.contains(mode) == true

    /** Set/clear a simple channel flag mode via a raw MODE command. The server
     *  (IRC) enforces op-only; a non-op's attempt just bounces with a notice. */
    fun setChannelMode(buffer: Buffer, mode: Char, on: Boolean) {
        val networkId = buffer.networkId ?: return
        ws?.send(
            JSONObject()
                .put("type", "raw")
                .put("networkId", networkId)
                .put("line", "MODE ${buffer.target} ${if (on) "+" else "-"}$mode")
                .toString(),
        )
    }

    /** Set (or clear) the channel topic via a raw TOPIC command. Op-only when the
     *  channel is +t; a non-op's attempt just bounces with a notice. An empty
     *  string clears the topic. */
    fun setTopic(buffer: Buffer, topic: String) {
        val networkId = buffer.networkId ?: return
        ws?.send(
            JSONObject()
                .put("type", "raw")
                .put("networkId", networkId)
                .put("line", "TOPIC ${buffer.target} :$topic")
                .toString(),
        )
    }

    /** Ensure a DM/channel buffer exists, hydrate it, and return it for focusing. */
    fun focusTarget(networkId: Int, target: String): Buffer {
        // Check BEFORE ensureBuffer, which would create it locally either way.
        val held = buffers.any { it.networkId == networkId && it.target.equals(target, true) }
        val buffer = ensureBuffer(networkId, target)
        // A buffer we already hold only needs its contents — that's a read now.
        // One we don't hold may be closed server-side, so it needs the write.
        if (held) hydrate(buffer) else open(buffer)
        return buffer
    }

    /**
     * Fetch a buffer's newest page — the read-only hydration path (lurker#671).
     *
     * `open-buffer` used to serve double duty as both read and write; it's now the
     * write alone, and it isn't free — it (re)opens the buffer server-side and
     * JOINs a channel as a side effect, and it announces to the account's other
     * devices. So anything that just wants contents asks for them instead.
     *
     * The reply also carries `inputHistory`, which is the only way a reloaded
     * client gets its per-buffer up-arrow recall back.
     */
    fun hydrate(buffer: Buffer) {
        val networkId = buffer.networkId ?: return
        ws?.send(
            JSONObject()
                .put("type", "history")
                .put("mode", "latest")
                .put("networkId", networkId)
                .put("target", buffer.target)
                .put("countBy", pageCountUnit())
                .toString(),
        )
    }

    /** Append a locally-generated line (command help / usage errors) to a buffer. */
    fun localNotice(buffer: Buffer, text: String) {
        mergeInto(
            buffer.key,
            listOf(Msg(id = 0, type = "notice", nick = "*", text = text, self = false, system = true)),
            replace = false,
        )
    }

    /** Execute the parsed composer input against [buffer]. */
    /** Record a user-initiated join so channel-joined can focus the channel we
     *  actually land in (a 470 forward can rename it) and drop the ghost request. */
    protected fun noteJoinRequest(networkId: Int, channel: String) {
        pendingJoin[networkId] = channel to System.currentTimeMillis()
    }

    /** Join a channel WITHOUT optimistically creating its buffer (per amiantos —
     *  channels are pending until channel-joined). The transient Buffer only
     *  carries the networkId to execute; it's never added to the buffer list. */
    open fun join(networkId: Int, channel: String) {
        execute(
            Buffer(networkId, channel, networks[networkId]?.name ?: ""),
            listOf(WireOp("join", channel = channel)),
        )
    }

    open fun execute(buffer: Buffer, ops: List<WireOp>) {
        val networkId = buffer.networkId ?: return
        // No live socket (offline / mid-reconnect): a message would otherwise vanish
        // without a trace. Retain it as a failed send the user can retry instead.
        val socket = ws ?: run {
            for (op in ops) {
                if (op.type !in SENDABLE_OPS) continue
                val target = op.target ?: buffer.target
                recordFailedSend(
                    "$networkId::$target", networkId, target, op.type, op.text.orEmpty(),
                    if (connected) "connecting" else "offline",
                )
            }
            return
        }
        for (op in ops) {
            val target = op.target ?: buffer.target
            val frame = JSONObject().put("networkId", networkId)
            when (op.type) {
                "send", "action", "notice" -> {
                    frame.put("type", op.type).put("target", target).put("text", op.text)
                    // An IRCv3 reply (2.4+): the server tags the first line out, or
                    // sends it as a plain line when the network can't carry it.
                    if (op.type != "notice") op.replyTo?.let { frame.put("replyTo", it) }
                    // Correlate with the send-result ack so a rejected or lost
                    // message is surfaced instead of silently vanishing.
                    val clientId = "a${++clientIdSeq}"
                    frame.put("clientId", clientId)
                    val key = "${networkId}::$target"
                    pendingSends[clientId] = PendingSend(key, networkId, target, op.type, op.text.orEmpty())
                    main.postDelayed({ failSend(clientId, "no acknowledgement (timeout)") }, SEND_ACK_TIMEOUT)
                }
                "raw" -> {
                    frame.put("type", "raw").put("line", op.line)
                    // Note a whois we're asking for, so its broadcast reply can be
                    // told apart from another client's and land back here. Covers
                    // both entry points (/whois and the nick sheet) since each
                    // lowers to a raw WHOIS line. Last token is the nick in both
                    // "WHOIS nick" and the server-targeted "WHOIS server nick".
                    whoisTargetNick(op.line)?.let { nick ->
                        val now = System.currentTimeMillis()
                        // Drop requests that never got an answer, so a flaky
                        // network can't grow this map without bound.
                        pendingWhois.values.retainAll { now - it.second <= WHOIS_REPLY_WINDOW }
                        pendingWhois["$networkId::$nick"] = buffer.key to now
                    }
                }
                // {type:'e2e', networkId, target, args} — server runs the subcommand.
                "e2e" -> frame.put("type", "e2e").put("target", target).put("args", op.line.orEmpty())
                "join" -> {
                    frame.put("type", "join").put("channel", op.channel)
                    op.channel?.let { ch ->
                        val existing = buffers.firstOrNull { it.key == "$networkId::$ch" }
                        // Already joined: just focus it. Otherwise note the request so
                        // channel-joined focuses the channel we actually land in (a 470
                        // forward can rename it) — the buffer isn't created until then.
                        if (existing != null) pendingOpen = existing else noteJoinRequest(networkId, ch)
                    }
                }
                "part" -> {
                    frame.put("type", "part").put("channel", op.channel)
                    op.reason?.let { frame.put("reason", it) }
                }
                "close" -> {
                    frame.put("type", "close-buffer").put("target", target)
                    op.reason?.let { frame.put("reason", it) } // /part's leave message
                }
                "clear" -> frame.put("type", "clear-buffer").put("target", target)
                "away" -> {
                    val message = op.text.orEmpty()
                    if (op.all ?: settingBool("away.all_networks", false)) {
                        // Every network: the user-scoped verb with no networkId,
                        // which every Lurker version reads as "all of them".
                        frame.remove("networkId")
                        if (message.isEmpty()) frame.put("type", "back")
                        else frame.put("type", "away").put("message", message)
                        frame.put("all", true)
                    } else {
                        // Just this network: a plain AWAY, which means exactly that
                        // on any server version.
                        frame.put("type", "raw").put("line", if (message.isEmpty()) "AWAY" else "AWAY :$message")
                    }
                }
                else -> continue
            }
            // okhttp refuses (false) once the socket has failed or closed, and the
            // frame is gone — surface a user message now rather than after the
            // ack timeout.
            if (!socket.send(frame.toString())) {
                frame.optString("clientId").takeIf { it.isNotEmpty() }?.let { failSend(it, "offline") }
            }
        }
    }

    // ---- Uploads ------------------------------------------------------------

    var uploading by mutableStateOf(false)

    /**
     * Upload a file through the account's configured provider (Zipline/Chibisafe/
     * local/…) and hand back its public URL. Multipart field is `image` for
     * historical reasons — any file type goes through it.
     */
    fun uploadFile(filename: String, fileBody: okhttp3.RequestBody, onDone: (String?, String?) -> Unit) {
        post { uploading = true }
        io.execute {
            try {
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("image", filename, fileBody)
                    .build()
                uploadHttp.newCall(authed("/api/uploads").post(body).build()).execute().use { res ->
                    val text = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        val err = runCatching { JSONObject(text).optString("error") }.getOrNull()
                        post { uploading = false; onDone(null, err?.ifEmpty { null } ?: "upload failed (HTTP ${res.code})") }
                        return@execute
                    }
                    val url = JSONObject(text).optString("url")
                    post { uploading = false; onDone(url.ifEmpty { null }, null) }
                }
            } catch (e: Exception) {
                post { uploading = false; onDone(null, "upload failed: ${e.message}") }
            }
        }
    }

    // ---- Network management (REST) ------------------------------------------

    val networkConfigs = mutableStateListOf<NetworkConfig>()
    var networksError by mutableStateOf<String?>(null)

    open fun loadNetworkConfigs() = io.execute {
        try {
            rest.newCall(authed("/api/networks").build()).execute().use { res ->
                if (!res.isSuccessful) {
                    post { networksError = "Failed to load networks (HTTP ${res.code})" }
                    return@execute
                }
                val arr = JSONObject(res.body?.string().orEmpty()).optJSONArray("networks")
                val parsed = mutableListOf<NetworkConfig>()
                if (arr != null) for (i in 0 until arr.length()) {
                    parseNetworkConfig(arr.optJSONObject(i) ?: continue)?.let(parsed::add)
                }
                post {
                    networksError = null
                    networkConfigs.clear()
                    networkConfigs.addAll(parsed)
                }
            }
        } catch (e: Exception) {
            post { networksError = "Failed to load networks: ${e.message}" }
        }
    }

    private fun parseNetworkConfig(o: JSONObject): NetworkConfig? {
        val id = o.optInt("id", -1)
        if (id < 0) return null
        return NetworkConfig(
            id = id,
            name = o.optString("name"),
            host = o.optString("host"),
            port = o.optInt("port", 6697),
            tls = o.optBoolean("tls", true),
            nick = o.optString("nick"),
            username = o.optString("username").ifEmpty { null },
            realname = o.optString("realname").ifEmpty { null },
            autoconnect = o.optBoolean("autoconnect", false),
            hasPassword = o.optBoolean("has_password", false),
            hasSaslPassword = o.optBoolean("has_sasl_password", false),
            saslAccount = o.optString("sasl_account").ifEmpty { null },
            blocked = o.optBoolean("blocked", false),
        )
    }

    /** Create (id == null) or update (id != null) a network; refreshes the list. */
    open fun saveNetwork(id: Int?, fields: Map<String, Any?>, onDone: (String?) -> Unit) = io.execute {
        try {
            val payload = JSONObject()
            for ((k, v) in fields) if (v != null) payload.put(k, v)
            val body = payload.toString().toRequestBody(json)
            val req = if (id == null) {
                authed("/api/networks").post(body)
            } else {
                authed("/api/networks/$id").patch(body)
            }.build()
            rest.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    val err = runCatching {
                        JSONObject(res.body?.string().orEmpty()).optString("error")
                    }.getOrNull()
                    post { onDone(err?.ifEmpty { null } ?: "save failed (HTTP ${res.code})") }
                    return@execute
                }
                loadNetworkConfigs()
                // Also refresh the id→name map + live network states so a freshly
                // added network is labelled correctly straight away (otherwise its
                // buffers fall back to "network"/"system" and scatter, #buffers-funky).
                fetchNetworkNames()
                post { onDone(null) }
            }
        } catch (e: Exception) {
            post { onDone("save failed: ${e.message}") }
        }
    }

    open fun deleteNetwork(id: Int, onDone: (String?) -> Unit) = io.execute {
        try {
            rest.newCall(authed("/api/networks/$id").delete().build()).execute().use { res ->
                if (!res.isSuccessful) {
                    post { onDone("delete failed (HTTP ${res.code})") }
                    return@execute
                }
                loadNetworkConfigs()
                // Also refresh the id→name map + live network states so a freshly
                // added network is labelled correctly straight away (otherwise its
                // buffers fall back to "network"/"system" and scatter, #buffers-funky).
                fetchNetworkNames()
                post { onDone(null) }
            }
        } catch (e: Exception) {
            post { onDone("delete failed: ${e.message}") }
        }
    }

    /** connect | disconnect | reconnect. Live state lands via the WS snapshot. */
    open fun networkAction(id: Int, action: String) = io.execute {
        try {
            rest.newCall(
                authed("/api/networks/$id/$action").post(ByteArray(0).toRequestBody(null)).build(),
            ).execute().use { res ->
                if (!res.isSuccessful) {
                    post { networksError = "$action failed (HTTP ${res.code})" }
                }
            }
        } catch (e: Exception) {
            post { networksError = "$action failed: ${e.message}" }
        }
    }

    // ---- DCC (receive) ----------------------------------------------------

    fun loadDcc() = io.execute {
        try {
            rest.newCall(authed("/api/dcc?limit=100").build()).execute().use { res ->
                if (res.code == 403) {
                    post { dccEnabled = false; dccError = null }
                    return@execute
                }
                if (!res.isSuccessful) {
                    post { dccError = "Failed to load transfers (HTTP ${res.code})" }
                    return@execute
                }
                val arr = JSONObject(res.body?.string().orEmpty()).optJSONArray("transfers")
                post {
                    dccEnabled = true
                    dccError = null
                    if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(::applyTransfer)
                }
            }
        } catch (e: Exception) {
            post { dccError = "Failed to load transfers: ${e.message}" }
        }
    }

    fun dccAccept(id: Int) = dccAction(id, "accept")
    fun dccReject(id: Int) = dccAction(id, "reject")
    fun dccCancel(id: Int) = dccAction(id, "cancel")

    private fun dccAction(id: Int, action: String) = io.execute {
        try {
            val req = authed("/api/dcc/$id/$action")
                .post(ByteArray(0).toRequestBody(null))
                .build()
            rest.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    post { dccError = "DCC $action failed (HTTP ${res.code})" }
                    return@execute
                }
                JSONObject(res.body?.string().orEmpty()).optJSONObject("transfer")?.let {
                    post { applyTransfer(it) }
                }
            }
        } catch (e: Exception) {
            post { dccError = "DCC $action failed: ${e.message}" }
        }
    }

    /**
     * Offer a file to [nick] over DCC SEND. Bytes go up as multipart; the server
     * stages them in its DCC dir and CTCP-offers the peer (active or passive per
     * server config + dcc.prefer_passive). Progress arrives as dcc-transfer frames.
     */
    fun dccSendFile(networkId: Int, nick: String, filename: String, fileBody: okhttp3.RequestBody) = io.execute {
        try {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("networkId", networkId.toString())
                .addFormDataPart("nick", nick)
                .addFormDataPart("file", filename, fileBody)
                .build()
            uploadHttp.newCall(authed("/api/dcc/send").post(body).build()).execute().use { res ->
                val bodyText = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    val err = runCatching { JSONObject(bodyText).optString("error") }.getOrNull()
                    post { dccError = err?.ifEmpty { null } ?: "DCC send failed (HTTP ${res.code})" }
                    return@execute
                }
                JSONObject(bodyText).optJSONObject("transfer")?.let {
                    post { dccEnabled = true; dccError = null; applyTransfer(it) }
                }
            }
        } catch (e: Exception) {
            post { dccError = "DCC send failed: ${e.message}" }
        }
    }

    /**
     * Open (or close) a DCC chat with [nick]. The chat lives in the "=nick" buffer.
     * Opening a peer's pending offer ACCEPTS it; closing one DECLINES it (the
     * server's doubling, same as irssi's `/dcc chat`). Returns as soon as the
     * offer is away — the handshake's outcome arrives as notices in `=nick`.
     * A failure lands in [reportTo] when given (the chat the user is looking at),
     * else in [dccError] for the DCC screen. [focus] opens the `=nick` buffer once
     * the server has taken the request.
     */
    fun dccChat(
        networkId: Int,
        nick: String,
        open: Boolean,
        passive: Boolean = false,
        reportTo: Buffer? = null,
        focus: Boolean = false,
    ) = io.execute {
        val offerKey = "$networkId::${nick.lowercase()}"
        fun fail(msg: String) {
            post { if (reportTo != null) localNotice(reportTo, msg) else dccError = msg }
        }
        try {
            val path = if (open) "/api/dcc/chat" else "/api/dcc/chat/close"
            val body = JSONObject().put("networkId", networkId).put("nick", nick)
                .apply { if (open && passive) put("passive", true) }
                .toString().toRequestBody(json)
            rest.newCall(authed(path).post(body).build()).execute().use { res ->
                if (!res.isSuccessful) {
                    val err = runCatching {
                        JSONObject(res.body?.string().orEmpty()).optString("error")
                    }.getOrNull()
                    fail(err?.ifEmpty { null } ?: "DCC chat failed (HTTP ${res.code})")
                    return@use
                }
                post {
                    // Either way the offer has been answered; the server's
                    // offer-closed confirms it, but don't leave the banner up.
                    dccChatOffers.remove(offerKey)
                    if (open && focus) pendingOpen = ensureBuffer(networkId, "=$nick")
                }
            }
        } catch (e: Exception) {
            fail("DCC chat failed: ${e.message}")
        }
    }

    /** True when the `=nick` [buffer] has a live DCC session behind it. */
    fun isDccChatLive(buffer: Buffer): Boolean {
        val networkId = buffer.networkId ?: return false
        return "$networkId::${buffer.peer.lowercase()}" in dccChatsLive
    }

    /** The pending inbound offer from [buffer]'s peer, if any. */
    fun dccOfferFor(buffer: Buffer): DccChatOffer? {
        val networkId = buffer.networkId ?: return null
        return dccChatOffers["$networkId::${buffer.peer.lowercase()}"]
    }

    private fun applyDccChatEvent(networkId: Int, frame: JSONObject) {
        val from = frame.optString("from")
        if (from.isEmpty()) return
        val key = "$networkId::${from.lowercase()}"
        when (frame.optString("type")) {
            "dcc-chat-offer" -> {
                dccChatOffers[key] = DccChatOffer(networkId, from, frame.optBoolean("passive", false))
                // A peer is waiting on an answer — worth a notification when we're
                // not on screen. Routed to the server buffer, where the server also
                // prints the offer.
                if (!appForeground) {
                    val server = buffers.firstOrNull { it.networkId == networkId && it.isServerBuffer }
                    notificationSink?.invoke(
                        NotifiableEvent(
                            networkId, server?.target ?: ":server:$networkId", from,
                            "wants to start a DCC chat", isDm = true,
                        ),
                    )
                }
            }
            "dcc-chat-offer-closed" -> dccChatOffers.remove(key)
            "dcc-chat-state" ->
                if (frame.optBoolean("live", false)) dccChatsLive.add(key) else dccChatsLive.remove(key)
        }
    }

    // ---- Reactions + replies (Lurker 2.4+) ------------------------------------

    /** Whether [msg] can be reacted to here: a stored message/action carrying a
     *  msgid, on a connected network that can send the tags, never E2E. */
    fun canReact(buffer: Buffer, msg: Msg): Boolean {
        val nid = buffer.networkId ?: return false
        return serverFeatures && networkCanReact[nid] == true && networks[nid]?.connected == true &&
            msg.id > 0 && msg.msgid != null && (msg.type == "message" || msg.type == "action") &&
            !msg.e2e && buffer.key !in e2eSeen &&
            !buffer.isServerBuffer && !buffer.isDccChat
    }

    /** Whether a reply to [msg] can name it (it needs a msgid). Where it can't,
     *  Reply falls back to the plain "nick, " address. */
    fun canReplyTo(buffer: Buffer, msg: Msg): Boolean =
        serverFeatures && buffer.networkId != null && msg.id > 0 && msg.msgid != null &&
            msg.type in setOf("message", "action", "notice") &&
            !msg.e2e && buffer.key !in e2eSeen &&
            !buffer.isServerBuffer && !buffer.isDccChat

    /** React with [value] on a line, or take ours back if it's already there.
     *  Never optimistic: the server's `reaction` echo is what lights it up, and a
     *  refusal is silence. */
    fun react(messageId: Long, value: String) {
        val v = value.trim()
        if (messageId <= 0 || !isValidReaction(v)) return
        val mine = reactions[messageId].orEmpty().any { it.self && it.value == v }
        ws?.send(
            JSONObject().put("type", "react").put("messageId", messageId).put("value", v)
                .put("remove", mine).toString(),
        )
    }

    private fun parseReactions(arr: JSONArray?): List<Reaction> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val r = arr.optJSONObject(i) ?: return@mapNotNull null
            val value = r.optString("value")
            if (value.isEmpty()) null
            else Reaction(if (r.isNull("nick")) "*" else r.optString("nick", "*"), value, r.optBoolean("self", false))
        }
    }

    /** A row is authoritative for itself: no `reactions` means none stand now. */
    private fun noteReactionRow(e: JSONObject) {
        val id = e.optLong("id")
        if (id <= 0) return
        val list = parseReactions(e.optJSONArray("reactions"))
        if (list.isEmpty()) reactions.remove(id) else reactions[id] = list
    }

    /** Reconcile against a page of rows. The system buffer's ids are a separate
     *  sequence that overlaps this one, so it's skipped. */
    private fun noteReactions(events: JSONArray?, networkId: Int?) {
        if (events == null || networkId == null) return
        for (i in 0 until events.length()) events.optJSONObject(i)?.let(::noteReactionRow)
    }

    private fun applyReactionFrame(f: JSONObject) {
        val id = f.optLong("messageId")
        val value = f.optString("value")
        if (id <= 0 || value.isEmpty()) return
        val nick = f.optString("nick")
        val self = f.optBoolean("self", false)
        val cur = reactions[id].orEmpty()
        if (f.optBoolean("remove", false)) {
            // Ours goes by `self`, not nick: we may have reacted under an older nick.
            val list = cur.filterNot { r ->
                if (self) r.self && r.value == value else r.value == value && r.nick.equals(nick, true)
            }
            if (list.isEmpty()) reactions.remove(id) else reactions[id] = list
            // A reaction to your line taken back leaves the activity feed too.
            if (f.optBoolean("toSelf", false) && !self) {
                highlightItems.removeAll { it.id == id && it.reaction == value && it.nick.equals(nick, true) }
            }
        } else if (cur.none { it.value == value && it.nick.equals(nick, true) }) {
            // Appended, so a chip keeps its place and a new one goes last.
            reactions[id] = cur + Reaction(nick, value, self)
        }
    }

    /**
     * After a resume, ask what stands now on the lines we already hold: a
     * `reaction` frame only reaches a connected socket, and `?since=` ships only
     * new rows. The newest 200 lines of each buffer, within the server's 5000 cap.
     */
    private fun syncReactions() {
        if (!serverHasReactions) return
        val ids = ArrayList<Long>()
        for (b in buffers) {
            if (b.networkId == null) continue
            val msgs = messagesByBuffer[b.key] ?: continue
            var n = 0
            for (i in msgs.indices.reversed()) {
                val id = msgs[i].id
                if (id <= 0) continue
                ids.add(id)
                if (++n >= 200) break
            }
            if (ids.size >= 5_000) break
        }
        if (ids.isEmpty()) return
        ws?.send(JSONObject().put("type", "sync-reactions").put("messageIds", JSONArray(ids.take(5_000))).toString())
    }

    private fun parseReplyParent(o: JSONObject?): ReplyParent? {
        if (o == null) return null
        val id = o.optLong("id")
        if (id <= 0) return null
        return ReplyParent(
            id = id,
            nick = if (o.isNull("nick")) "*" else o.optString("nick", "*"),
            type = o.optString("type", "message"),
            text = o.optString("text"),
            self = o.optBoolean("self", false),
        )
    }

    private fun parseReplyRef(o: JSONObject?): ReplyRef? {
        if (o == null) return null
        val msgid = o.optString("msgid")
        if (msgid.isEmpty()) return null
        return ReplyRef(msgid, parseReplyParent(o.optJSONObject("parent")))
    }

    private fun parseDraftReply(o: JSONObject?): DraftReply? {
        if (o == null) return null
        val id = o.optLong("messageId")
        if (id <= 0) return null
        return DraftReply(id, o.optBoolean("addressed", false), parseReplyParent(o.optJSONObject("parent")))
    }

    // ---- Search + highlights ------------------------------------------------

    /** Run a fresh search from raw `from:/in:/on: text` input. */
    fun runSearch(raw: String) {
        lastSearchRaw = raw
        val q = Search.parse(raw)
        // A search needs at least free text OR a structured filter.
        if (q.text.isBlank() && q.from.isEmpty() && q.inTarget.isBlank()) {
            searchResults.clear(); searchHasMore = false; searchLoading = false
            return
        }
        val token = ++searchToken
        searchNextBefore = null
        searchLoading = true
        searchError = null
        searchResults.clear()
        sendSearch(q, token, before = null)
    }

    /** Fetch the next page of the current search. */
    fun searchMore() {
        val before = searchNextBefore ?: return
        if (searchLoading) return
        searchLoading = true
        sendSearch(Search.parse(lastSearchRaw), searchToken, before)
    }

    private fun sendSearch(q: Search.Query, token: Int, before: Long?) {
        val networkId = if (q.onNetwork.isNotBlank()) {
            networks.values.firstOrNull { it.name.equals(q.onNetwork, true) }?.id
        } else null
        val frame = JSONObject()
            .put("type", "search")
            .put("token", token)
            .put("limit", 50)
        if (q.text.isNotBlank()) frame.put("query", q.text)
        if (q.from.isNotEmpty()) frame.put("nicks", JSONArray(q.from))
        if (q.inTarget.isNotBlank()) frame.put("target", q.inTarget)
        if (networkId != null) frame.put("networkId", networkId)
        if (before != null) frame.put("before", before)
        ws?.send(frame.toString())
    }

    private fun parseResultRow(o: JSONObject): SearchResult? {
        val id = o.optLong("id", -1)
        if (id < 0) return null
        return SearchResult(
            id = id,
            networkId = o.optInt("networkId", -1),
            target = o.optString("target"),
            nick = o.optString("nick", "*"),
            // Search rows use DB columns (body/createdAt); fall back to live-frame names.
            body = o.optString("body").ifEmpty { o.optString("text") },
            createdAt = o.optString("createdAt").ifEmpty { o.optString("time").ifEmpty { null } },
            replyTo = parseReplyRef(o.optJSONObject("replyTo"))?.parent,
        )
    }

    /** True once the server answered `/api/activity` (Lurker 2.4+): the tab reads
     *  "Activity" and reaction items appear beside highlights. */
    var activityFeed by mutableStateOf(false)
        private set
    private var activityUnsupported = false
    private var activityNext: JSONObject? = null

    /**
     * The highlights tab. On Lurker 2.4+ it's the activity feed — highlights plus
     * other people's reactions to your lines — paged by an opaque `next` cursor;
     * an older server 404s that and gets `/api/highlights`, as before.
     */
    fun loadHighlights(fresh: Boolean) {
        if (highlightsLoading) return
        if (fresh) { highlightItems.clear(); highlightsNextBefore = null; activityNext = null }
        val before = highlightsNextBefore
        val next = activityNext
        if (!fresh && activityFeed && next == null) return
        post { highlightsLoading = true }
        io.execute {
            if (!activityUnsupported && loadActivity(next)) return@execute
            try {
                val path = "/api/highlights?limit=50" + (before?.let { "&before=$it" } ?: "")
                rest.newCall(authed(path).build()).execute().use { res ->
                    if (!res.isSuccessful) {
                        post { highlightsLoading = false }
                        return@execute
                    }
                    val obj = JSONObject(res.body?.string().orEmpty())
                    val arr = obj.optJSONArray("items")
                    val next = if (obj.isNull("nextBefore")) null else obj.optLong("nextBefore")
                    val rows = ArrayList<SearchResult>()
                    if (arr != null) for (i in 0 until arr.length()) {
                        parseResultRow(arr.optJSONObject(i) ?: continue)?.let(rows::add)
                    }
                    post {
                        highlightItems.addAll(rows)
                        highlightsNextBefore = next
                        highlightsHasMore = next != null
                        highlightsLoading = false
                    }
                }
            } catch (_: Exception) {
                post { highlightsLoading = false }
            }
        }
    }

    /** One page of `/api/activity`. False when the server doesn't have it (older
     *  than 2.4), so the caller falls back to highlights. */
    private fun loadActivity(cursor: JSONObject?): Boolean {
        try {
            val q = StringBuilder("/api/activity?limit=50")
            cursor?.keys()?.forEach { k ->
                val v = cursor.opt(k)
                if (v != null && v != JSONObject.NULL) q.append('&').append(k).append('=').append(v)
            }
            rest.newCall(authed(q.toString()).build()).execute().use { res ->
                if (res.code == 404) {
                    activityUnsupported = true
                    return false
                }
                if (!res.isSuccessful) {
                    post { highlightsLoading = false }
                    return true
                }
                // Not JSON (a proxy's page, an old server's HTML): no feed here.
                val obj = runCatching { JSONObject(res.body?.string().orEmpty()) }.getOrNull()
                    ?: run { activityUnsupported = true; return false }
                val arr = obj.optJSONArray("items")
                val nextCursor = obj.optJSONObject("next")
                val rows = ArrayList<SearchResult>()
                if (arr != null) for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.optString("kind") == "reaction") {
                        val id = o.optLong("id", -1)
                        val value = o.optString("value")
                        if (id < 0 || value.isEmpty()) continue
                        rows.add(
                            SearchResult(
                                id = id,
                                networkId = o.optInt("networkId", -1),
                                target = o.optString("target"),
                                nick = if (o.isNull("nick")) "*" else o.optString("nick", "*"),
                                body = o.optString("text"),
                                createdAt = o.optString("time").ifEmpty { null },
                                reaction = value,
                            ),
                        )
                    } else {
                        parseResultRow(o)?.let(rows::add)
                    }
                }
                post {
                    activityFeed = true
                    highlightItems.addAll(rows)
                    activityNext = nextCursor
                    highlightsHasMore = nextCursor != null
                    highlightsLoading = false
                }
                return true
            }
        } catch (_: Exception) {
            post { highlightsLoading = false }
            return true
        }
    }

    /** GET /api/bookmarks — paginated saved messages (REST), newest first. */
    fun loadBookmarks(fresh: Boolean) {
        if (bookmarksLoading) return
        if (fresh) { bookmarkItems.clear(); bookmarksNextBefore = null }
        val before = bookmarksNextBefore
        post { bookmarksLoading = true }
        io.execute {
            try {
                val path = "/api/bookmarks?limit=50" + (before?.let { "&before=$it" } ?: "")
                rest.newCall(authed(path).build()).execute().use { res ->
                    if (!res.isSuccessful) {
                        post { bookmarksLoading = false }
                        return@execute
                    }
                    val obj = JSONObject(res.body?.string().orEmpty())
                    val arr = obj.optJSONArray("items")
                    val next = if (obj.isNull("nextBefore")) null else obj.optLong("nextBefore")
                    val rows = ArrayList<SearchResult>()
                    if (arr != null) for (i in 0 until arr.length()) {
                        parseResultRow(arr.optJSONObject(i) ?: continue)?.let(rows::add)
                    }
                    post {
                        bookmarkItems.addAll(rows)
                        bookmarksNextBefore = next
                        bookmarksHasMore = next != null
                        bookmarksLoading = false
                    }
                }
            } catch (_: Exception) {
                post { bookmarksLoading = false }
            }
        }
    }

    // ---- Pins, mark-all-read, channel list ---------------------------------

    fun isPinned(buffer: Buffer): Boolean =
        buffer.networkId?.let { pins[it]?.any { t -> t.equals(buffer.target, true) } } == true

    /** Which buffer a highlight notification for [buffer] should open, plus whether
     *  that's the same buffer the message arrived on. When a relayed copy of a
     *  channel highlights you on a network where you HAVEN'T pinned it, but you
     *  HAVE pinned the same-named channel on another network (your favourited
     *  "home"), prefer that — so tapping the notification lands in the original,
     *  not the relay mirror (freakyy85). Same-name match only; differently-named
     *  relays can't be linked without explicit metadata. Falls back to [buffer]. */
    fun notifyTargetFor(buffer: Buffer): Triple<Int?, String, Boolean> {
        val net = buffer.networkId ?: return Triple(buffer.networkId, buffer.target, true)
        val other = relayNotifyNetwork(net, buffer.target, buffer.isChannel, pins)
        return if (other != null) Triple(other, buffer.target, false) else Triple(net, buffer.target, true)
    }

    fun togglePin(buffer: Buffer) {
        val networkId = buffer.networkId ?: return
        val type = if (isPinned(buffer)) "unpin-buffer" else "pin-buffer"
        ws?.send(JSONObject().put("type", type).put("networkId", networkId).put("target", buffer.target).toString())
    }

    fun markAllRead() {
        ws?.send(JSONObject().put("type", "mark-all-read").toString())
    }

    // ---- Notify, notes, ignore ---------------------------------------------

    fun nickNote(networkId: Int?, nick: String): String? =
        networkId?.let { nickNotes["$it::${nick.lowercase()}"] }

    fun setNickNote(networkId: Int, nick: String, note: String) {
        val key = "$networkId::${nick.lowercase()}"
        if (note.isBlank()) nickNotes.remove(key) else nickNotes[key] = note
        ws?.send(JSONObject().put("type", "set-nick-note").put("networkId", networkId).put("nick", nick).put("note", note).toString())
    }

    fun isNotifyAlways(buffer: Buffer): Boolean =
        buffer.networkId?.let { notifyAlways["$it::${buffer.target}"] } == true

    fun setNotifyAlways(buffer: Buffer, on: Boolean) {
        val networkId = buffer.networkId ?: return
        if (on) notifyAlways["$networkId::${buffer.target}"] = true else notifyAlways.remove("$networkId::${buffer.target}")
        ws?.send(JSONObject().put("type", "set-channel-notify-always").put("networkId", networkId).put("target", buffer.target).put("notifyAlways", on).toString())
    }

    // ---- Per-channel notification mute ------------------------------------
    // A channel mute is a NONOTIFY ignore rule scoped to the channel (issue #359
    // folded the old `muted` flag into the ignore engine). The server bakes mutes
    // into web-push, but the `notify` flag it sends live clients does NOT encode
    // them — so a native client that trusts `notify` (see shouldNotify) would
    // still buzz on a muted channel (freakyy85's relayed-channel report). We
    // apply the mute locally: honor it in the notify path AND expose a toggle.

    /** The channel-scope NONOTIFY/ALL mute rule for [buffer], if one exists. */
    private fun channelMuteRule(buffer: Buffer): IgnoreRule? {
        val nid = buffer.networkId ?: return null
        return ignores.firstOrNull { r ->
            !r.isExcept &&
                r.mask == null &&
                (r.levels.contains("NONOTIFY") || r.levels.contains("ALL")) &&
                (r.networkId == null || r.networkId == nid) &&
                r.channels?.any { it.equals(buffer.target, true) } == true
        }
    }

    fun isNotifyMuted(buffer: Buffer): Boolean = channelMuteRule(buffer) != null

    /** Toggle "mute notifications" for a channel. On = add a channel-scoped
     *  NONOTIFY rule (keeps messages visible, just silences pings); off = remove it. */
    fun setNotifyMuted(buffer: Buffer, on: Boolean) {
        val networkId = buffer.networkId ?: return
        if (on) {
            if (channelMuteRule(buffer) == null) {
                addIgnore(networkId, mask = null, levels = listOf("NONOTIFY"), channels = listOf(buffer.target))
            }
        } else {
            channelMuteRule(buffer)?.let { removeIgnore(it.networkId, it.id) }
        }
    }

    // ---- Notification-action entry points (wrist / lockscreen replies) --------
    // Resolve the live buffer if we hold it, else synthesize a transient one that
    // just carries the (networkId, target) needed to act.

    private fun bufferFor(networkId: Int, target: String): Buffer =
        buffers.firstOrNull { it.key == "$networkId::$target" }
            ?: Buffer(networkId, target, networks[networkId]?.name ?: "")

    /** Send a reply typed into a notification (RemoteInput). No-op if offline. */
    fun replyFromNotification(networkId: Int, target: String, text: String) {
        if (text.isBlank()) return
        val buffer = bufferFor(networkId, target)
        execute(buffer, listOf(WireOp("send", text = text)))
        markRead(buffer)
    }

    /** Mark a buffer read from a notification action. */
    fun markReadFromNotification(networkId: Int, target: String) {
        markRead(bufferFor(networkId, target))
    }

    /** Mute a buffer from a notification action. */
    fun muteFromNotification(networkId: Int, target: String) {
        setNotifyMuted(bufferFor(networkId, target), true)
    }

    /** True when a synced ignore rule silences NOTIFICATIONS for this event — a
     *  channel-scope mute (mask null) or a sender-specific NONOTIFY/ALL rule. The
     *  local notifier must apply this itself; the server only honors it in push. */
    private fun isMutedForNotify(networkId: Int?, target: String, userhost: String?, nick: String?): Boolean {
        if (ignores.isEmpty()) return false
        return ignores.any { r ->
            if (r.isExcept) return@any false
            if (!(r.levels.contains("NONOTIFY") || r.levels.contains("ALL"))) return@any false
            if (r.networkId != null && r.networkId != networkId) return@any false
            if (r.channels != null && r.channels.none { it.equals(target, true) }) return@any false
            // Null mask = whole-scope mute (channel/network); else match the sender.
            r.mask == null || ircMaskMatches(r.mask, userhost, nick)
        }
    }

    /** IRC glob match (`*` any run, `?` one char, case-insensitive). A bare-nick
     *  mask (no ! or @) matches the nick; a full mask matches nick!user@host. */
    // Compiled masks memoized by pattern — a mute rule is re-tested against many
    // messages, so we compile each glob once instead of per candidate line.
    private val maskRegexCache = HashMap<String, Regex?>()

    private fun ircMaskMatches(mask: String, userhost: String?, nick: String?): Boolean {
        val rx = maskRegexCache.getOrPut(mask) {
            runCatching {
                Regex(
                    "(?i)" + mask.map {
                        when (it) { '*' -> ".*"; '?' -> "."; else -> Regex.escape(it.toString()) }
                    }.joinToString(""),
                )
            }.getOrNull()
        } ?: return false
        val bareNick = !mask.contains('!') && !mask.contains('@')
        val subject = if (bareNick) (nick ?: userhost) else (userhost ?: nick)
        return subject != null && rx.matches(subject)
    }

    fun isBookmarked(id: Long): Boolean = id in bookmarkIds

    /** Save/unsave a message. Optimistic; the server echoes `bookmark-updated`. */
    fun toggleBookmark(id: Long) {
        if (id <= 0) return
        val save = id !in bookmarkIds
        if (save) bookmarkIds.add(id) else bookmarkIds.remove(id)
        ws?.send(JSONObject().put("type", if (save) "set-bookmark" else "unset-bookmark").put("messageId", id).toString())
    }

    /** Add a simple ALL-level ignore for [mask] (bare-mask rule the server accepts). */
    /** Add an ignore rule. [networkId] null = global (every network); empty
     *  [levels] means ALL. e.g. levels=["NOHIGHLIGHT"] ignores only a sender's
     *  highlights without hiding their messages. */
    open fun addIgnore(
        networkId: Int?,
        mask: String?,
        levels: List<String> = listOf("ALL"),
        isExcept: Boolean = false,
        pattern: String? = null,
        channels: List<String>? = null,
    ) {
        val rule = JSONObject()
            .put("mask", mask ?: JSONObject.NULL)
            .put("channels", channels?.let { JSONArray(it) } ?: JSONObject.NULL)
            .put("pattern", pattern ?: JSONObject.NULL)
            .put("patternKind", "substr")
            .put("levels", JSONArray(levels))
            .put("isExcept", isExcept)
        ws?.send(
            JSONObject().put("type", "add-ignore")
                .put("networkId", networkId ?: JSONObject.NULL)
                .put("rule", rule)
                .toString(),
        )
    }

    open fun removeIgnore(networkId: Int?, id: Long) {
        ws?.send(
            JSONObject().put("type", "remove-ignore")
                .put("networkId", networkId ?: JSONObject.NULL)
                .put("id", id)
                .toString(),
        )
    }

    private fun applyIgnoreScope(networkId: Int?, arr: JSONArray?) {
        ignores.removeAll { it.networkId == networkId }
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val channels = o.optJSONArray("channels")?.let { a -> (0 until a.length()).map { a.optString(it) } }
            val levels = o.optJSONArray("levels")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            ignores.add(
                IgnoreRule(
                    id = o.optLong("id"),
                    networkId = networkId,
                    mask = if (o.isNull("mask")) null else o.optString("mask").takeIf { it.isNotEmpty() },
                    channels = channels,
                    pattern = if (o.isNull("pattern")) null else o.optString("pattern").takeIf { it.isNotEmpty() },
                    patternKind = o.optString("patternKind").ifEmpty { "substr" },
                    levels = levels,
                    isExcept = o.optBoolean("isExcept", false),
                ),
            )
        }
    }

    /** Kick a fresh /LIST refresh on a network and load its cached rows. */
    fun openChannelList(networkId: Int) {
        chanlistNetworkId = networkId
        chanlistRows.clear()
        ws?.send(JSONObject().put("type", "list-channels").put("networkId", networkId).toString())
        searchChannelList("", "users", "desc", 0)
    }

    fun searchChannelList(query: String, sortBy: String, sortDir: String, offset: Int) {
        val networkId = chanlistNetworkId
        if (networkId < 0) return
        chanlistKey = "$query|$sortBy|$sortDir"
        chanlistQuery = query
        chanlistSortBy = sortBy
        chanlistSortDir = sortDir
        if (offset == 0) chanlistLoading = true
        ws?.send(
            JSONObject().put("type", "chanlist-search").put("networkId", networkId)
                .put("query", query).put("sortBy", sortBy).put("sortDir", sortDir)
                .put("offset", offset).put("limit", 200).toString(),
        )
    }

    private fun applyTransfer(o: JSONObject) {
        val id = o.optInt("id", -1)
        if (id < 0) return
        transfers[id] = DccTransfer(
            id = id,
            peerNick = o.optString("peer_nick", "?"),
            filename = o.optString("filename", "file"),
            state = o.optString("state", "unknown"),
            received = o.optLong("received_bytes", 0),
            total = o.optLong("advertised_size", 0),
            direction = o.optString("direction", "recv"),
            error = o.optString("error").ifEmpty { null },
        )
    }

    // ---- Settings ---------------------------------------------------------

    open fun loadSettings() = io.execute {
        try {
            rest.newCall(authed("/api/settings/bootstrap").build()).execute().use { res ->
                if (!res.isSuccessful) {
                    post { settingsError = "Settings unavailable (HTTP ${res.code})"; settingsLoaded = true }
                    return@execute
                }
                val obj = JSONObject(res.body?.string().orEmpty())
                val reg = obj.optJSONArray("registry")
                val vals = obj.optJSONObject("values")
                post {
                    settingsRegistry.clear()
                    settingsValues.clear()
                    if (reg != null) for (i in 0 until reg.length()) {
                        reg.optJSONObject(i)?.let(::parseOption)?.let(settingsRegistry::add)
                    }
                    if (vals != null) for (k in vals.keys()) {
                        settingsValues[k] = jsonToValue(vals.get(k))
                    }
                    settingsError = null
                    settingsLoaded = true
                }
            }
        } catch (e: Exception) {
            post { settingsError = "Settings unavailable: ${e.message}"; settingsLoaded = true }
        }
    }

    fun patchSetting(key: String, value: Any?) = io.execute {
        try {
            val changes = JSONObject().put(key, toJson(value))
            val body = JSONObject().put("changes", changes).toString().toRequestBody(json)
            val req = authed("/api/settings/").patch(body).build()
            rest.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    post { settingsError = "Couldn't save $key (HTTP ${res.code})" }
                    return@execute
                }
                applyValues(res)
            }
        } catch (e: Exception) {
            post { settingsError = "Couldn't save $key: ${e.message}" }
        }
    }

    fun resetSetting(key: String) = io.execute {
        try {
            rest.newCall(authed("/api/settings/$key").delete().build()).execute().use { res ->
                if (res.isSuccessful) applyValues(res)
            }
        } catch (_: Exception) {
        }
    }

    /** Effective int value of a registry setting: override ?? default ?? fallback. */
    fun settingInt(key: String, fallback: Int): Int {
        (settingsValues[key] as? Number)?.let { return it.toInt() }
        (settingsValues[key] as? String)?.toIntOrNull()?.let { return it }
        (settingsRegistry.firstOrNull { it.key == key }?.default as? Number)?.let { return it.toInt() }
        return fallback
    }

    private fun applyValues(res: Response) {
        val vals = JSONObject(res.body?.string().orEmpty()).optJSONObject("values") ?: return
        post {
            // The response is the FULL override map — replace, don't merge, so a
            // reset (key removed server-side) also disappears locally.
            settingsValues.clear()
            for (k in vals.keys()) settingsValues[k] = jsonToValue(vals.get(k))
            settingsError = null
        }
    }

    private fun parseOption(o: JSONObject): SettingOption? {
        val key = o.optString("key").ifEmpty { return null }
        val choices = o.optJSONArray("choices")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()
        return SettingOption(
            key = key,
            label = o.optString("label").ifEmpty { key.substringAfterLast('.') },
            type = o.optString("type", "string"),
            group = o.optString("group").ifEmpty { key.substringBefore('.') },
            category = o.optString("category"),
            description = o.optString("description"),
            default = jsonToValue(o.opt("default")),
            choices = choices,
            min = if (o.has("min")) o.optInt("min") else null,
            max = if (o.has("max")) o.optInt("max") else null,
            requiresFeature = o.optString("requiresFeature").ifEmpty { null },
        )
    }

    private fun jsonToValue(v: Any?): Any? = when {
        v == null || v == JSONObject.NULL -> null
        v is JSONArray -> (0 until v.length()).map { v.optString(it) }
        else -> v
    }

    private fun toJson(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is List<*> -> JSONArray(value)
        else -> value
    }

    private companion object {
        const val PROTOCOL_VERSION = 1 // mirror server/protocol.ts
        const val INITIAL_BACKOFF = 1_000L
        const val MAX_BACKOFF = 30_000L
        const val SEND_ACK_TIMEOUT = 8_000L // matches the web client's deadline

        /** How long a whois we issued stays eligible to claim a broadcast reply.
         *  Generous — replies are near-instant, but a lagging server shouldn't
         *  silently swallow the answer. */
        const val WHOIS_REPLY_WINDOW = 60_000L

        /** How long an `active` typing signal lives without a refresh. */
        const val TYPING_TTL = 6_000L

        /** Backgrounds shorter than this (task-switcher hops) skip the cycle. */
        const val BACKGROUND_CYCLE_MS = 3_000L

        /** A channel-joined within this of the join request focuses that channel. */
        const val JOIN_FOCUS_WINDOW_MS = 15_000L

        /** A background buffer past this many lines is trimmed to [KEEP_BUFFER_MESSAGES]. */
        const val MAX_BUFFER_MESSAGES = 1_500
        const val KEEP_BUFFER_MESSAGES = 1_000
        val COUNTABLE = setOf("message", "action", "notice")
        val SYSTEM_TYPES = setOf("join", "part", "quit", "nick", "kick", "mode", "topic", "invite")
    }
}

/** The managed, multi-tenant lurker.chat deployment. Its login is different from a
 *  self-hosted server (email + control-plane mint, then proxied), so the client
 *  branches on it — see [LurkerClient.login] and CLIENT_PROTOCOL §3.2. */
const val HOSTED_LURKER_BASE = "https://app.lurker.chat"

/** True when the typed address is the hosted lurker.chat app (bare host, with a
 *  scheme, with a path, or with a port all normalize the same). Callers use this
 *  to sign in with an email at the control plane instead of a username. */
fun isHostedLurker(raw: String): Boolean {
    val host = raw.trim()
        .removePrefix("https://").removePrefix("http://")
        .substringBefore('/').substringBefore(':')
        .lowercase()
    return host == "app.lurker.chat" || host == "lurker.chat"
}

/**
 * Pure decision for whether an incoming message should raise a system
 * notification. Plain booleans (no Android/JSON deps) so the truth table is
 * unit-testable. Only notify while backgrounded, for a real inbound message the
 * server flagged `notify` (its union of highlight / DM / notify-always) that
 * isn't in the currently-open buffer.
 */
/**
 * Pure relay-notification retarget. When a channel highlights you on a network
 * where you HAVEN'T pinned it, but you HAVE pinned the same-named channel on a
 * DIFFERENT network (your favourited "home" of a relayed pair), return that other
 * network so the notification opens the original — else null to keep the source.
 * Same-name match only; no Android/state deps so it's unit-testable.
 */
internal fun relayNotifyNetwork(
    net: Int,
    target: String,
    isChannel: Boolean,
    pinsByNet: Map<Int, List<String>>,
): Int? {
    if (!isChannel) return null
    if (pinsByNet[net]?.any { it.equals(target, true) } == true) return null // already pinned here
    for ((pinnedNet, targets) in pinsByNet) {
        if (pinnedNet == net) continue
        if (targets.any { it.equals(target, true) }) return pinnedNet
    }
    return null
}

/**
 * The sender of an event row, `*` when it has none.
 *
 * Android's org.json turns a PRESENT-but-null value into the string "null";
 * `optString`'s fallback only covers an ABSENT key. Server-voice rows — a
 * persisted `error` above all — come back from history with `nick: null`, so
 * "gnat isn't on this network." rendered under a sender called "null"
 * (stephanos, #lurker). `isNull` is true for absent and null alike.
 */
/**
 * An invite's system line. The server sends two shapes: a channel line
 * (`nick` = inviter, `invited` = who — our own /invite, or one an op sees via
 * invite-notify) and the invite TO US, which names the inviter in `from` and the
 * channel in `channel` and has no `nick` at all. Reading neither field was the
 * mangled "Jawsh invited you" in the channel you'd just invited someone to.
 */
internal fun inviteLine(nick: String, e: JSONObject): String {
    val who = nick.takeIf { it.isNotEmpty() && it != "*" } ?: e.optString("from").ifEmpty { "Someone" }
    val invited = e.optString("invited")
    if (invited.isNotEmpty()) return "$who invited $invited"
    val channel = e.optString("channel").ifEmpty { e.optString("text") }
    return "$who invited you" + (if (channel.isNotBlank()) " to $channel" else "")
}

internal fun eventNick(e: JSONObject): String =
    if (e.isNull("nick")) "*" else e.optString("nick", "*")

internal fun shouldNotify(
    notify: Boolean,
    isActiveBuffer: Boolean,
    self: Boolean,
    system: Boolean,
    hasId: Boolean,
    foreground: Boolean,
): Boolean =
    notify && !foreground && !self && !system && hasId && !isActiveBuffer

/**
 * Order chat rows by server id. Out-of-order live delivery (two sources racing —
 * e.g. an MCP post + an app send — or a delayed E2E decrypt) would otherwise
 * strand a lower-id message at the tail. Fast-paths the already-sorted common
 * case (no allocation). When a reorder IS needed, client-injected rows (id<=0:
 * local notices, whois blocks, E2E status) stay anchored right after the real
 * message they were injected below, rather than jumping to the end.
 */
internal fun orderMessagesById(msgs: List<Msg>): List<Msg> {
    var last = 0L
    for (m in msgs) if (m.id > 0) { if (m.id < last) { last = -1; break }; last = m.id }
    if (last != -1L) return msgs // already in order
    var anchor = 0L
    return msgs.map { m ->
        val sortKey = if (m.id > 0) { anchor = m.id; m.id * 2 } else anchor * 2 + 1
        sortKey to m
    }.sortedBy { it.first }.map { it.second }
}
