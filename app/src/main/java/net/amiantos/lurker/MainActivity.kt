// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.WindowManager
import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material3.Slider
import androidx.compose.ui.graphics.toArgb
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import net.amiantos.lurker.ui.theme.AccentBlue
import net.amiantos.lurker.ui.theme.AlertRed
import net.amiantos.lurker.ui.theme.AppTheme
import net.amiantos.lurker.ui.theme.CanvasBlack
import net.amiantos.lurker.ui.theme.GlassBorder
import net.amiantos.lurker.ui.theme.LurkerTheme
import net.amiantos.lurker.ui.theme.TextPrimary
import net.amiantos.lurker.ui.theme.Ui
import androidx.compose.ui.graphics.luminance
import net.amiantos.lurker.ui.theme.HighlightGold
import net.amiantos.lurker.ui.theme.NoticeAmber
import net.amiantos.lurker.ui.theme.OnlineGreen
import net.amiantos.lurker.ui.theme.PillGray
import net.amiantos.lurker.ui.theme.SurfaceDark
import net.amiantos.lurker.ui.theme.SurfaceRaised
import net.amiantos.lurker.ui.theme.TextSecondary
import net.amiantos.lurker.ui.theme.formatTime
import net.amiantos.lurker.ui.theme.formatTimeWithSeconds
import net.amiantos.lurker.ui.theme.nickColor
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.imageLoader
import coil.compose.AsyncImage
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/** The frosted-pane recipe every glass surface shares. */
@Composable
private fun glassStyle(): HazeStyle = HazeStyle(
    backgroundColor = CanvasBlack,
    tints = listOf(HazeTint(SurfaceDark.copy(alpha = 0.55f))),
    blurRadius = 22.dp,
    noiseFactor = 0.02f,
)

/** Where the app is: the buffer list, a chat, settings, DCC, or networks. */
internal sealed interface Screen {
    data object Buffers : Screen
    data class Chat(val buffer: Buffer, val scrollToMsgId: Long? = null) : Screen
    data object Settings : Screen
    data object Dcc : Screen
    data object Networks : Screen
    data object Search : Screen
    data object Friends : Screen
    data object Ignores : Screen
    data object Multichan : Screen
    data class ChannelList(val query: String? = null) : Screen
    data class NetworkEdit(val config: NetworkConfig?) : Screen
}

// Rotating the phone recreates the Activity, which used to reset the nav state and
// dump you back on the buffer list, losing the channel you were reading (d3fc0n).
// Screens are saved instead, so a rotate — or the system killing the app for memory
// — puts you back where you were, back stack included.
//
// Encoded as ONE string per screen: the saved-instance Bundle takes a plain
// ArrayList<String> without any nesting, which keeps this robust. U+0001 is the
// separator because it can't appear in a channel name, nick, or network name.
internal const val SCREEN_SEP = '\u0001'

internal fun encodeScreen(s: Screen): String = when (s) {
    Screen.Buffers -> "buffers"
    Screen.Settings -> "settings"
    Screen.Dcc -> "dcc"
    Screen.Networks -> "networks"
    Screen.Search -> "search"
    Screen.Friends -> "friends"
    Screen.Ignores -> "ignores"
    Screen.Multichan -> "multichan"
    is Screen.Chat -> listOf(
        "chat",
        s.buffer.networkId?.toString().orEmpty(), // empty = the :system: buffer
        s.buffer.target,
        s.buffer.networkName,
        s.scrollToMsgId?.toString().orEmpty(),
    ).joinToString(SCREEN_SEP.toString())
    is Screen.ChannelList -> "channelList$SCREEN_SEP${s.query.orEmpty()}"
    // Deliberately restores to the network LIST: the config is a whole object and
    // the half-filled form wouldn't survive regardless, so the list is the honest
    // place to land rather than a blank editor.
    is Screen.NetworkEdit -> "networks"
}

internal fun decodeScreen(raw: String): Screen {
    val p = raw.split(SCREEN_SEP)
    return when (p.getOrNull(0)) {
        "chat" -> if (p.size >= 4) {
            Screen.Chat(
                Buffer(p[1].toIntOrNull(), p[2], p[3]),
                p.getOrNull(4)?.toLongOrNull(),
            )
        } else {
            Screen.Buffers
        }
        "channelList" -> Screen.ChannelList(p.getOrNull(1)?.ifEmpty { null })
        "settings" -> Screen.Settings
        "dcc" -> Screen.Dcc
        "networks" -> Screen.Networks
        "search" -> Screen.Search
        "friends" -> Screen.Friends
        "ignores" -> Screen.Ignores
        "multichan" -> Screen.Multichan
        else -> Screen.Buffers
    }
}

private val ScreenStateSaver: Saver<Screen, String> =
    Saver(save = { encodeScreen(it) }, restore = { decodeScreen(it) })

private val BackStackSaver = listSaver<SnapshotStateList<Screen>, String>(
    save = { stack -> stack.map { encodeScreen(it) } },
    restore = { saved -> saved.map { decodeScreen(it) }.toMutableStateList() },
)

class MainActivity : FragmentActivity() {
    // Process-scoped: the socket + buffer state outlive Activity recreation
    // (rotation, the tablet/phone layout switch) and are shared with the
    // background service, instead of a fresh client per Activity.
    private val client: LurkerClient get() = (application as LurkerApp).client
    private val prefs by lazy { Prefs(this) }

    /** True while the biometric lock is covering the UI (see [promptUnlock]). */
    private var locked by mutableStateOf(false)

    /** Set once the user has biometrically unlocked E2E channels this foreground
     *  session (prefs.e2eBiometricLock). Reset on background so switching away
     *  re-locks them (amiantos). Only relevant when the toggle is on. */
    private var e2eUnlocked by mutableStateOf(false)
    /** True while an E2E-unlock prompt is already showing, so we don't stack them. */
    private var e2ePrompting = false

    /** A file arriving via the system share sheet, waiting for a buffer pick. */
    private var sharedUri by mutableStateOf<Uri?>(null)

    /** (networkId, target) to open from a tapped notification, once composed. */
    // networkId, target, and the message id to jump to (0 = just open the buffer).
    private var pendingDeepLink by mutableStateOf<Triple<Int, String, Long>?>(null)

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* declining is fine — the socket + badges still work, just no notifications */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Cover the UI immediately if the lock is on and we haven't unlocked this
        // process; onResume runs the prompt.
        locked = prefs.biometricLock && !(application as LurkerApp).unlocked
        Ui.theme = AppTheme.from(prefs.theme)
        Ui.inlineMedia = prefs.inlineMedia
        Ui.linkPreviews = prefs.linkPreviews
        Ui.youtubeDescriptions = prefs.youtubeDescriptions
        Ui.translateBackend = when (prefs.translateBackend) {
            "libretranslate" -> TranslateBackend.LIBRETRANSLATE
            "openai" -> TranslateBackend.OPENAI
            else -> TranslateBackend.OFF
        }
        Ui.translateEndpoint = prefs.translateEndpoint
        Ui.translateApiKey = prefs.translateApiKey
        Ui.translateModel = prefs.translateModel
        Ui.translateTargetLang = prefs.translateTargetLang
        Ui.translateRead.clear()
        Ui.translateRead.addAll(prefs.translateRead.distinct())
        Ui.translateOutgoing.putAll(prefs.translateOutgoing)
        Ui.chatTextScale = prefs.chatTextScale
        Ui.clock24h = prefs.clock24h
        Ui.highlightColor = prefs.highlightColor
        Ui.compact = prefs.compactMessages
        Ui.compactOverrides.putAll(prefs.compactOverrides)
        Ui.accentColor = prefs.accentColor
        Ui.fontFamily = prefs.fontFamily
        Ui.density = prefs.density
        Ui.nickColors = prefs.nickColors
        Ui.backgroundUri = prefs.backgroundUri
        Ui.backgroundDim = prefs.backgroundDim
        Ui.backgroundOverrides.putAll(prefs.backgroundOverrides)
        Ui.seedMultichan(prefs.multichanKeys)
        Ui.multichanCompact = prefs.multichanCompact
        // Don't start a backend until a mode is settled. Existing users have a
        // saved Lurker session (clientMode still null on first update) — treat them
        // as Lurker mode and start immediately; only a genuinely fresh install
        // (no mode, no session) waits for the ModePicker.
        if (prefs.clientMode != null || prefs.hasSession) client.start(prefs)
        Notifier.ensureChannels(this)
        // Notify only while backgrounded; posts from the WS thread (thread-safe).
        client.notificationSink = { Notifier.post(applicationContext, it) }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        consumeShareIntent(intent)
        consumeNotificationIntent(intent)
        consumeOAuthIntent(intent)
        // Opt-in always-on: revive the liveness anchor on launch (from the
        // foreground, so the background-start rule never bites).
        if (wantsBackgroundConnection()) LurkerConnectionService.start(this)
        setContent {
            LurkerTheme {
                // Hoisted above the lock gate: a relock returns early before this
                // line, so declaring it here (below the gate) would forget the open
                // chat on every unlock. Keeping it up top preserves it.
                // rememberSaveable, not remember: a rotation recreates the Activity,
                // and plain remember dumped you back on the buffer list mid-conversation.
                var screen by rememberSaveable(stateSaver = ScreenStateSaver) {
                    mutableStateOf<Screen>(Screen.Buffers)
                }
                // Back stack, so leaving a menu (or a nested channel) returns to
                // where you were — not always the buffer list. Capped so a long
                // session can't grow it without bound. Saved too, or a rotate would
                // strand you with a Back that jumps straight to the buffer list.
                val backStack = rememberSaveable(saver = BackStackSaver) { mutableStateListOf<Screen>() }
                // One pump for the whole app: pacing is a global property, so a
                // per-row pump would be twenty pumps racing the same rate limit.
                ServerPreviewPump(client)
                // Re-focus a chat we RESTORED (rotation or a process-death relaunch).
                // Guarded on the client's current focus: rotation keeps it already
                // set, and re-running setActive would re-anchor the "New messages"
                // divider to the tail and lose your place in the unread run.
                LaunchedEffect(Unit) {
                    (screen as? Screen.Chat)?.buffer
                        ?.takeIf { it.key != client.activeBufferKey }
                        ?.let { client.setActive(it) }
                }
                fun go(dest: Screen) {
                    if (dest == screen) return
                    backStack.add(screen)
                    if (backStack.size > 32) backStack.removeAt(0)
                    screen = dest
                }
                fun back() {
                    val prev = if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex) else Screen.Buffers
                    screen = prev
                    // Re-activate the buffer we're returning to (null = not a chat).
                    client.setActive((prev as? Screen.Chat)?.buffer)
                }

                // A dropped session (WS 401 → forceSignOutLocally) flips loggedIn
                // false and clears the token without going through the UI sign-out,
                // so stop the background service here too — otherwise its "staying
                // connected" notification lingers over a dead session.
                LaunchedEffect(client.loggedIn) {
                    if (!client.loggedIn && !prefs.hasSession) {
                        LurkerConnectionService.stop(this@MainActivity)
                    }
                }

                if (locked) {
                    LockScreen(onUnlock = { promptUnlock() })
                    return@LurkerTheme
                }

                // Per-channel E2E biometric gate (prefs.e2eBiometricLock): cover a
                // known-secure channel with a lock until the user authenticates.
                // Re-locks on background (onStop). Single-pane nav only for now.
                val e2eChat = (screen as? Screen.Chat)?.buffer
                if (prefs.e2eBiometricLock && !e2eUnlocked &&
                    e2eChat != null && e2eChat.key in client.e2eSeen
                ) {
                    LaunchedEffect(e2eChat.key) { promptE2eUnlock() }
                    LockScreen(onUnlock = { promptE2eUnlock() }, e2e = true)
                    return@LurkerTheme
                }

                // First run: choose Lurker-server mode or direct-IRC/bouncer mode.
                // The chosen backend is picked in LurkerApp by clientMode, and the
                // client is created lazily, so applying a choice restarts the app.
                // Only genuinely fresh installs see the picker — an existing Lurker
                // session means clientMode is effectively "lurker".
                if (prefs.clientMode == null && !prefs.hasSession) {
                    ModePickerScreen(onPick = { mode -> prefs.clientMode = mode; restartApp() })
                    return@LurkerTheme
                }

                if (!client.loggedIn) {
                    LoginScreen(client, prefs)
                    return@LurkerTheme
                }

                // A tapped notification opens its buffer once we're logged in.
                LaunchedEffect(pendingDeepLink) {
                    pendingDeepLink?.let { (nid, tgt, mid) ->
                        val buffer = client.focusTarget(nid, tgt)
                        client.setActive(buffer)
                        screen = Screen.Chat(buffer, scrollToMsgId = mid.takeIf { it > 0 })
                        pendingDeepLink = null
                    }
                }

                // The channel we actually joined (channel-joined) — or an
                // already-open channel we asked to /join — opens itself.
                LaunchedEffect(client.pendingOpen) {
                    client.pendingOpen?.let { buffer ->
                        client.hydrate(buffer)
                        client.setActive(buffer)
                        screen = Screen.Chat(buffer)
                        client.pendingOpen = null
                    }
                }

                // Direct mode with nothing configured yet: drop the user on the
                // Networks screen to add their first IRC network / bouncer.
                LaunchedEffect(prefs.clientMode) {
                    if (prefs.clientMode == "direct" && client.networkConfigs.isEmpty()) {
                        client.loadNetworkConfigs()
                        if (client.networkConfigs.isEmpty()) screen = Screen.Networks
                    }
                }

                BoxWithConstraints(Modifier.fillMaxSize()) {
                    // Width tiers. THREE-pane (>=1100dp, e.g. landscape tablet):
                    // list | chat | roster, all persistent. TWO-pane (840–1100dp,
                    // e.g. tablet portrait): chat | roster — the channel list is
                    // the full-screen master reached with Back (like the phone), so
                    // the chat pairs with the nicklist, not the list. Below 840:
                    // the unchanged phone flow.
                    val threePane = maxWidth >= 1100.dp
                    val twoPane = maxWidth >= 840.dp && !threePane
                    val active = screen
                    val onSelectBuffer: (Buffer) -> Unit = { buffer ->
                        // Materialize a synthesized Friends row before selecting (see the
                        // phone onOpen note); focusTarget returns held buffers unchanged.
                        val b = buffer.networkId?.let { client.focusTarget(it, buffer.target) }
                            ?: buffer.also { client.open(it) }
                        client.setActive(b); screen = Screen.Chat(b)
                    }
                    if (threePane && (active == Screen.Buffers || active is Screen.Chat)) {
                        TabletHome(
                            client = client,
                            selectedBuffer = (active as? Screen.Chat)?.buffer,
                            scrollToMsgId = (active as? Screen.Chat)?.scrollToMsgId,
                            sharedUri = sharedUri,
                            onShareConsumed = { sharedUri = null },
                            sharePending = sharedUri != null,
                            onSelect = onSelectBuffer,
                            onDeselect = { client.setActive(null); screen = Screen.Buffers },
                            onSettings = { client.loadSettings(); screen = Screen.Settings },
                            onDcc = { client.loadDcc(); screen = Screen.Dcc },
                            onNetworks = { client.loadNetworkConfigs(); screen = Screen.Networks },
                            onSearch = { screen = Screen.Search },
                            onFriends = { screen = Screen.Friends },
                            onIgnores = { screen = Screen.Ignores },
                            onBrowse = { screen = Screen.ChannelList() },
                            onOpenMultichan = { screen = Screen.Multichan },
                            onSignOut = { LurkerConnectionService.stop(this@MainActivity); client.signOut() },
                            showList = true,
                            showRoster = true,
                        )
                        return@BoxWithConstraints
                    }
                    if (twoPane && active is Screen.Chat) {
                        // Chat + nicklist; Back drops the list back into view.
                        TabletHome(
                            client = client,
                            selectedBuffer = active.buffer,
                            scrollToMsgId = active.scrollToMsgId,
                            sharedUri = sharedUri,
                            onShareConsumed = { sharedUri = null },
                            sharePending = sharedUri != null,
                            onSelect = onSelectBuffer,
                            onDeselect = { client.setActive(null); screen = Screen.Buffers },
                            // Two-pane hides the rail (so its overflow actions are
                            // stubbed), but the chat's top-bar gear still needs Settings.
                            onSettings = { client.loadSettings(); screen = Screen.Settings },
                            onDcc = {}, onNetworks = {},
                            onSearch = {}, onFriends = {}, onIgnores = {}, onBrowse = {}, onSignOut = {},
                            showList = false,
                            showRoster = true,
                        )
                        return@BoxWithConstraints
                    }
                when (val s = screen) {
                    Screen.Buffers -> BufferListScreen(
                        client = client,
                        sharePending = sharedUri != null,
                        onOpen = { buffer ->
                            // A synthesized Friends row isn't in client.buffers yet —
                            // materialize it (ensureBuffer + open-buffer) so Chat opens
                            // a real, hydrating buffer instead of an orphan. focusTarget
                            // returns the existing buffer for ones we already hold; it
                            // also sends open-buffer, so no separate open() call here.
                            val b = buffer.networkId?.let { client.focusTarget(it, buffer.target) }
                                ?: buffer.also { client.open(it) }
                            client.setActive(b)
                            go(Screen.Chat(b))
                        },
                        onSettings = { client.loadSettings(); go(Screen.Settings) },
                        onDcc = { client.loadDcc(); go(Screen.Dcc) },
                        onNetworks = { client.loadNetworkConfigs(); go(Screen.Networks) },
                        onSearch = { go(Screen.Search) },
                        onFriends = { go(Screen.Friends) },
                        onIgnores = { go(Screen.Ignores) },
                        onBrowse = { go(Screen.ChannelList()) },
                        onOpenMultichan = { go(Screen.Multichan) },
                        onSignOut = { LurkerConnectionService.stop(this@MainActivity); client.signOut() },
                    )
                    // Keyed per buffer: Chat -> Chat (a /query, a DCC accept, a
                    // notification) otherwise REUSES the composable, carrying the old
                    // buffer's scroll state and measured bar heights into the new one.
                    is Screen.Chat -> key(s.buffer.key) {
                        ChatScreen(
                            client = client,
                            buffer = s.buffer,
                            scrollToMsgId = s.scrollToMsgId,
                            sharedUri = sharedUri,
                            onShareConsumed = { sharedUri = null },
                            onBack = { back() },
                            onOpenBuffer = { buffer ->
                                client.setActive(buffer)
                                go(Screen.Chat(buffer))
                            },
                            onBrowse = { q -> go(Screen.ChannelList(q)) },
                            onSettings = { client.loadSettings(); go(Screen.Settings) },
                        )
                    }
                    Screen.Settings -> SettingsScreen(client, prefs) { back() }
                    Screen.Dcc -> DccScreen(
                        client = client,
                        onOpenBuffer = { buffer ->
                            client.setActive(buffer)
                            go(Screen.Chat(buffer))
                        },
                        onBack = { back() },
                    )
                    Screen.Networks -> NetworksScreen(
                        client = client,
                        onEdit = { go(Screen.NetworkEdit(it)) },
                        onAdd = { go(Screen.NetworkEdit(null)) },
                        onBack = { back() },
                    )
                    Screen.Search -> SearchScreen(
                        client = client,
                        onOpenResult = { networkId, target ->
                            val buffer = client.focusTarget(networkId, target)
                            client.setActive(buffer)
                            go(Screen.Chat(buffer))
                        },
                        onBack = { back() },
                    )
                    Screen.Friends -> FriendsScreen(
                        client = client,
                        onOpenBuffer = { buffer ->
                            client.setActive(buffer)
                            go(Screen.Chat(buffer))
                        },
                        onBack = { back() },
                    )
                    Screen.Ignores -> IgnoresScreen(client) { back() }
                    Screen.Multichan -> MultichanScreen(
                        client = client,
                        onBack = { back() },
                        onOpenSource = { buffer, msgId ->
                            val b = buffer.networkId?.let { client.focusTarget(it, buffer.target) }
                                ?: buffer.also { client.open(it) }
                            client.setActive(b)
                            go(Screen.Chat(b, scrollToMsgId = msgId.takeIf { it > 0 }))
                        },
                    )
                    is Screen.ChannelList -> ChannelListScreen(
                        client = client,
                        initialQuery = (screen as Screen.ChannelList).query,
                        onJoin = { networkId, chan ->
                            // Don't optimistically open — channel-joined focuses the
                            // channel we actually land in (avoids the forwarded ghost).
                            client.join(networkId, chan)
                        },
                        onBack = { back() },
                    )
                    is Screen.NetworkEdit -> NetworkEditScreen(
                        client = client,
                        config = s.config,
                        onBack = { back() },
                    )
                }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeShareIntent(intent)
        consumeNotificationIntent(intent)
        consumeOAuthIntent(intent)
    }

    /** The browser's OAuth redirect, forwarded by [OAuthRedirectActivity]:
     *  `<applicationId>:/oauth?code=…`. Finishes sign-in off the main thread. */
    private fun consumeOAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW || !data.scheme.equals(packageName, ignoreCase = true)) return
        intent.data = null // consume so a config change doesn't replay (and re-spend) the code
        val uri = data.toString()
        Thread { runCatching { client.completeOAuth(uri) } }.start()
    }

    /** Relaunch the app in a fresh process so LurkerApp re-creates its lazy
     *  [LurkerClient] for the newly-chosen clientMode (mode switch = restart). */
    private fun restartApp() = restartAppProcess(this)

    /** A tapped notification carries the buffer to open; stash it for the UI. */
    private fun consumeNotificationIntent(intent: Intent?) {
        val target = intent?.getStringExtra(Notifier.EXTRA_TARGET) ?: return
        val nid = intent.getIntExtra(Notifier.EXTRA_NETWORK_ID, -1)
        val mid = intent.getLongExtra(Notifier.EXTRA_MESSAGE_ID, 0L)
        if (nid >= 0) pendingDeepLink = Triple(nid, target, mid)
        intent.removeExtra(Notifier.EXTRA_TARGET) // consume so a config change won't replay
        intent.removeExtra(Notifier.EXTRA_MESSAGE_ID)
    }

    /**
     * The share target is exported, and the upload path reads whatever URI it's
     * handed WITH THIS APP'S PERMISSIONS. A `file://` URI — or one on our own
     * FileProvider — let any app make Spooky upload its own private files (the
     * session token's prefs file included) to the user's upload host and paste
     * the public link into the composer. Accept only another app's content URI.
     */
    private fun isShareableUri(uri: Uri): Boolean =
        uri.scheme.equals("content", ignoreCase = true) &&
            uri.authority?.startsWith(packageName) != true

    private fun consumeShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null && isShareableUri(uri)) sharedUri = uri
            intent.action = null // consume so a config change doesn't replay it
        }
    }

    // The bouncer holds our place while we're away; on return, revive a dropped
    // socket and resume from the last message id we saw.
    override fun onResume() {
        super.onResume()
        client.onForeground()
        // Revive the background anchor on every foreground, not just a cold start:
        // Android 15+ stops it when the dataSync daily budget runs out, and simply
        // resuming a still-alive Activity wouldn't otherwise restart it. Safe to
        // call when it's already running (idempotent) or still out of budget (the
        // service catches the refused start and stands down without crashing).
        if (wantsBackgroundConnection()) LurkerConnectionService.start(this)
        // Re-lock only after a real trip away (>2s), so a rotation/layout switch
        // doesn't re-prompt. First launch: backgroundedAt is 0 → treated as away.
        if (prefs.biometricLock) {
            val app = application as LurkerApp
            val awayMs = if (app.backgroundedAt > 0) SystemClock.elapsedRealtime() - app.backgroundedAt else Long.MAX_VALUE
            if (!app.unlocked || awayMs > RELOCK_GRACE_MS) {
                app.unlocked = false
                locked = true
                promptUnlock()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        client.onBackground()
        (application as LurkerApp).backgroundedAt = SystemClock.elapsedRealtime()
        // Switching away re-locks E2E channels (amiantos) — next open re-prompts.
        e2eUnlocked = false
    }

    /** Biometric gate for opening an E2E channel (prefs.e2eBiometricLock). Same
     *  fall-open behaviour as the app-open lock: if the device can't authenticate,
     *  don't strand the user. On success, E2E channels open until next background. */
    private fun promptE2eUnlock() {
        if (e2ePrompting) return
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            e2eUnlocked = true
            return
        }
        e2ePrompting = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    e2ePrompting = false
                    e2eUnlocked = true
                }
                override fun onAuthenticationError(code: Int, err: CharSequence) {
                    e2ePrompting = false
                    when (code) {
                        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                        BiometricPrompt.ERROR_HW_NOT_PRESENT,
                        BiometricPrompt.ERROR_HW_UNAVAILABLE,
                        BiometricPrompt.ERROR_NO_BIOMETRICS,
                        -> e2eUnlocked = true
                    }
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock encrypted channel")
            .setSubtitle("Confirm it's you to view secure chats")
            .setAllowedAuthenticators(authenticators)
            .build()
        try {
            prompt.authenticate(info)
        } catch (_: Exception) {
            e2ePrompting = false
            e2eUnlocked = true
        }
    }

    /** Show the system biometric / device-credential prompt; unlock on success. */
    private fun promptUnlock() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        // If the device can't authenticate at all (no biometric enrolled AND no
        // PIN/pattern/password), don't strand the user behind a prompt that will
        // only ever error — just let them in. (canAuthenticate is also checked
        // before the toggle is even offered.)
        if (BiometricManager.from(this).canAuthenticate(authenticators) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            unlock()
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = unlock()
                // Unrecoverable errors (no hardware, nothing enrolled, permanent
                // lockout, user canceled) arrive here ASYNCHRONOUSLY — the old
                // try/catch never saw them, which could hard-lock the app. Fall
                // open on the terminal ones; transient errors leave the lock up so
                // the "Unlock" button can retry.
                override fun onAuthenticationError(code: Int, err: CharSequence) {
                    when (code) {
                        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                        BiometricPrompt.ERROR_HW_NOT_PRESENT,
                        BiometricPrompt.ERROR_HW_UNAVAILABLE,
                        BiometricPrompt.ERROR_NO_BIOMETRICS,
                        -> unlock()
                    }
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Lurker")
            .setSubtitle("Confirm it's you to open your chats")
            .setAllowedAuthenticators(authenticators)
            .build()
        try {
            prompt.authenticate(info)
        } catch (_: Exception) {
            unlock() // no usable authenticator — don't trap the user out
        }
    }

    /** "Stay connected" is on and there's something to stay connected TO. Direct
     *  mode has no Lurker session token — gating on `hasSession` alone meant the
     *  anchor was never revived there after a restart, only by the toggle itself. */
    private fun wantsBackgroundConnection(): Boolean =
        prefs.backgroundConnect && (prefs.hasSession || prefs.clientMode == "direct")

    private fun unlock() {
        (application as LurkerApp).unlocked = true
        locked = false
    }

    private companion object {
        const val RELOCK_GRACE_MS = 2000L
    }
}

/**
 * Tablet / landscape three-pane home (EXPANDED width): a persistent buffer-list
 * rail | the selected buffer's chat | the channel roster. The phone keeps its
 * screen-by-screen flow untouched; this is only reached above the width gate.
 * Settings/Search/Networks/etc. still open as full-screen overlays through the
 * same handlers the rail invokes.
 */
@Composable
private fun TabletHome(
    client: LurkerClient,
    selectedBuffer: Buffer?,
    scrollToMsgId: Long? = null,
    sharedUri: Uri?,
    onShareConsumed: () -> Unit,
    sharePending: Boolean,
    onSelect: (Buffer) -> Unit,
    onDeselect: () -> Unit,
    onSettings: () -> Unit,
    onDcc: () -> Unit,
    onNetworks: () -> Unit,
    onSearch: () -> Unit,
    onFriends: () -> Unit,
    onIgnores: () -> Unit,
    onBrowse: () -> Unit,
    onOpenMultichan: () -> Unit = {},
    onSignOut: () -> Unit,
    // Three-pane keeps the buffer-list rail; two-pane drops it (the chat pairs
    // with the roster and Back returns to the full-screen list).
    showList: Boolean,
    showRoster: Boolean,
) {
    val context = LocalContext.current
    // DCC send from the roster pane: hold the target across the system picker.
    var dccNick by remember { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val nick = dccNick
        dccNick = null
        val networkId = selectedBuffer?.networkId
        if (uri != null && nick != null && networkId != null) {
            val upload = readUpload(context, uri)
            if (upload == null) client.dccError = "Couldn't read that file."
            else client.dccSendFile(networkId, nick, upload.first, upload.second)
        }
    }
    Row(Modifier.fillMaxSize().background(CanvasBlack)) {
        // Left rail (three-pane only) — the full buffer list, overflow menu and all.
        if (showList) {
            Box(Modifier.width(340.dp).fillMaxHeight()) {
                BufferListScreen(
                    client = client,
                    sharePending = sharePending,
                    onOpen = onSelect,
                    onSettings = onSettings,
                    onDcc = onDcc,
                    onNetworks = onNetworks,
                    onSearch = onSearch,
                    onFriends = onFriends,
                    onIgnores = onIgnores,
                    onBrowse = onBrowse,
                    onOpenMultichan = onOpenMultichan,
                    onSignOut = onSignOut,
                )
            }
            VerticalDivider(color = GlassBorder, thickness = 0.5.dp)
        }
        // Middle — the selected conversation.
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (selectedBuffer != null) key(selectedBuffer.key) {
                ChatScreen(
                    client = client,
                    buffer = selectedBuffer,
                    scrollToMsgId = scrollToMsgId,
                    sharedUri = sharedUri,
                    onShareConsumed = onShareConsumed,
                    onBack = onDeselect,
                    onOpenBuffer = onSelect,
                    onBrowse = { onBrowse() },
                    onSettings = onSettings,
                    // Three-pane: the rail is the persistent nav, so no back arrow.
                    // Two-pane: show the back arrow (Back returns to the list).
                    embedded = showList,
                    // No room for a roster column → keep the modal-sheet button.
                    showMembersButton = !showRoster,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a conversation", color = TextSecondary, fontSize = 15.sp)
                }
            }
        }
        // Right — the roster, for channels only (DMs have none) when there's room.
        if (showRoster && selectedBuffer?.isChannel == true) {
            VerticalDivider(color = GlassBorder, thickness = 0.5.dp)
            Box(Modifier.width(260.dp).fillMaxHeight()) {
                MemberPane(
                    client = client,
                    buffer = selectedBuffer,
                    onOpenBuffer = onSelect,
                    onPickFileFor = { nick -> dccNick = nick; filePicker.launch("*/*") },
                )
            }
        }
    }
}

/** Common toggleable (paramless) channel flag modes, shown as chips in the
 *  control panel. Exact support varies by IRCd — toggling one the server
 *  doesn't know just draws a harmless error notice in the channel/server
 *  buffer; the chip reflects whatever the live +modes string actually reports. */
private val CHANNEL_MODE_FLAGS = listOf(
    'm' to "Moderated",
    't' to "Topic locked",
    'i' to "Invite only",
    'n' to "No external",
    's' to "Secret",
    'p' to "Private",
    'c' to "No colors",
    'C' to "No CTCP",
    'r' to "Registered only",
    'g' to "Free invite",
)

/** Friendly labels for channel-mode letters across common ircds (solanum,
 *  InspIRCd, UnrealIRCd, ngIRCd…). Used when the network advertised its ISUPPORT
 *  and we render exactly its supported flag modes; unknown letters render as
 *  "+x". Only paramless (type-D) modes actually surface as toggles. */
private val CHANNEL_MODE_LABELS: Map<Char, String> = mapOf(
    'm' to "Moderated", 't' to "Topic locked", 'i' to "Invite only",
    'n' to "No external", 's' to "Secret", 'p' to "Private",
    'c' to "No colors", 'C' to "No CTCP", 'r' to "Registered only",
    'g' to "Free invite", 'z' to "SSL only", 'S' to "SSL only",
    'R' to "Registered only", 'M' to "Reg. moderated", 'T' to "No notices",
    'N' to "No nick changes", 'D' to "Delayed join", 'u' to "Hide unregistered",
    'O' to "Oper only", 'A' to "Admin only", 'Q' to "No kicks",
    'K' to "No knock", 'P' to "Permanent", 'G' to "Word filter",
    'F' to "Free forward", 'L' to "Large ban list",
)

/** Friendly names for the per-user rank (PREFIX) modes, used by the member
 *  action sheet's grant/revoke rows. */
private val RANK_LABELS: Map<Char, String> = mapOf(
    'q' to "owner", 'a' to "admin", 'o' to "op", 'h' to "halfop", 'v' to "voice",
)

/**
 * Glassmorphic channel control panel that slides down from the channel-name pill.
 * Shows the topic, toggleable flag modes (server enforces op-only), and the
 * per-channel notification controls. Replaces the always-on topic bar (d3fc0n1
 * didn't want it always visible; amiantos's iOS app puts this behind the pill).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelControlPanel(
    client: LurkerClient,
    buffer: Buffer,
    hazeState: HazeState,
    topInsetPx: Int,
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenLink: (String) -> Unit,
    onMembers: () -> Unit,
) {
    val density = LocalDensity.current
    val topDp = with(density) { topInsetPx.toDp() }
    val topic = client.topics[buffer.key]
    val modes = client.channelModes[buffer.key].orEmpty()
    // ISUPPORT for this network (patched server / Direct mode). Null on a vanilla
    // Lurker server → the mode chips fall back to a curated list.
    val si = buffer.networkId?.let { client.serverInfo[it] }
    val e2e = buffer.key in client.e2eSeen ||
        (client.messagesByBuffer[buffer.key]?.asReversed()?.take(150)?.any { it.e2e } == true)
    val memberCount = client.members[buffer.key]?.size ?: 0
    var editingTopic by remember(buffer.key) { mutableStateOf(false) }
    var topicDraft by remember(buffer.key) { mutableStateOf("") }
    // Leaving the panel cancels an in-progress topic edit so it doesn't linger.
    LaunchedEffect(visible) { if (!visible) editingTopic = false }

    Box(Modifier.fillMaxSize()) {
        // Scrim — tap outside to dismiss.
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onDismiss() },
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(
                Modifier
                    .padding(top = topDp + 6.dp, start = 10.dp, end = 10.dp)
                    .fillMaxWidth()
                    // Cap to the space between the top bar and the composer, and
                    // scroll internally — so the panel never runs off the bottom or
                    // hides its lower controls under the composer on shorter phones
                    // (or when a network advertises a long mode list). ~104dp clears
                    // a typical composer + nav inset.
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp.dp - topDp - 104.dp).coerceAtLeast(200.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .hazeEffect(hazeState, style = glassStyle())
                    .background(SurfaceDark, RoundedCornerShape(20.dp))
                    .border(0.5.dp, GlassBorder, RoundedCornerShape(18.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                // Header.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (e2e) {
                        LockGlyph(color = OnlineGreen, size = 14.dp)
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(buffer.displayName, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("$memberCount", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    MembersGlyph(color = TextSecondary, size = 14.dp)
                }

                // Topic — tap Edit to change it (raw TOPIC; op-only when +t).
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("TOPIC", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (buffer.isChannel && !editingTopic) {
                            Text(
                                "Edit",
                                color = AccentBlue,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        topicDraft = topic.orEmpty()
                                        editingTopic = true
                                    }
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (editingTopic) {
                        OutlinedTextField(
                            value = topicDraft,
                            onValueChange = { topicDraft = it },
                            placeholder = { Text("Channel topic", fontSize = 13.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 104.dp),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            TextButton(onClick = { editingTopic = false }) {
                                Text("Cancel", color = TextSecondary, fontSize = 13.sp)
                            }
                            TextButton(onClick = {
                                client.setTopic(buffer, topicDraft.trim())
                                editingTopic = false
                            }) {
                                Text("Save", color = AccentBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else if (topic.isNullOrBlank()) {
                        Text("No topic set.", color = TextSecondary, fontSize = 13.sp)
                    } else {
                        Text(
                            // No inner scroll — the whole panel scrolls now, and a
                            // nested same-direction scroll would fight it. A long
                            // topic just extends and the panel handles the overflow.
                            mircAnnotated(topic, AccentBlue, onOpenLink),
                            color = TextPrimary,
                            fontSize = 12.5.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }

                // Flag-mode chips (tap toggles; the server enforces op-only). When
                // the server advertised its ISUPPORT (si != null) we show exactly
                // the paramless (type-D) modes THIS ircd supports; otherwise we fall
                // back to a curated cross-ircd list so a vanilla server still works.
                val flagModes: List<Pair<Char, String>> =
                    if (si != null && si.flagModes.isNotEmpty()) {
                        si.flagModes.sorted().map { it to (CHANNEL_MODE_LABELS[it] ?: "+$it") }
                    } else {
                        CHANNEL_MODE_FLAGS
                    }
                // A long ISUPPORT mode list (solanum advertises ~18) would dominate
                // the panel, so collapse to the active modes by default with a
                // "+N more" expander. Active modes sort first so the current state
                // reads at a glance.
                var modesExpanded by remember(buffer.key) { mutableStateOf(false) }
                val orderedModes = flagModes.sortedByDescending { it.first in modes }
                val collapsible = orderedModes.size > 6
                val shownModes = if (!collapsible || modesExpanded) {
                    orderedModes
                } else {
                    orderedModes.filter { it.first in modes }.ifEmpty { orderedModes.take(4) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("CHANNEL MODES", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        // Server software, when the network told us (read directly
                        // off ISUPPORT/004) — otherwise nothing.
                        if (!si?.software.isNullOrBlank()) {
                            Text("  ·  ${si!!.software}", color = TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        shownModes.forEach { (flag, label) ->
                            val on = flag in modes
                            ModeChip(label, on) { client.setChannelMode(buffer, flag, !on) }
                        }
                        if ('k' in modes) ModeChip("Key set", true, onClick = null)
                        if ('l' in modes) ModeChip("Limited", true, onClick = null)
                        if (collapsible) {
                            Text(
                                if (modesExpanded) "Show less" else "+${orderedModes.size - shownModes.size} more",
                                color = AccentBlue,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { modesExpanded = !modesExpanded }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                            )
                        }
                    }
                }

                HorizontalDivider(color = GlassBorder)

                // Notifications.
                PanelToggleRow("Notify on every message", client.isNotifyAlways(buffer)) {
                    client.setNotifyAlways(buffer, it)
                }
                PanelToggleRow("Mute notifications", client.isNotifyMuted(buffer)) {
                    client.setNotifyMuted(buffer, it)
                }
                // Per-channel compact layout (overrides the global default).
                val panelCtx = LocalContext.current
                PanelToggleRow("Compact messages", Ui.compactFor(buffer.key)) { on ->
                    Ui.compactOverrides[buffer.key] = on
                    Prefs(panelCtx).compactOverrides = Ui.compactOverrides.toMap()
                }
                // Mirror this channel into the Multi-channel firehose view.
                PanelToggleRow("Add to Multi-channel", Ui.inMultichan(buffer.key)) { on ->
                    // removeAll, not remove: strip every copy, so turning a channel
                    // back off always sticks even if the list ever held duplicates.
                    if (on) { if (buffer.key !in Ui.multichan) Ui.multichan.add(buffer.key) }
                    else Ui.multichan.removeAll { it == buffer.key }
                    Prefs(panelCtx).multichanKeys = Ui.multichan.toSet()
                }

                // Per-channel background image (overrides the global one for THIS
                // channel; "None here" hides it even if a global one is set).
                val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) {
                        val safe = buffer.key.replace(Regex("[^A-Za-z0-9]"), "_")
                        saveBackgroundPick(panelCtx, uri, "ch_bg_${safe}_")?.let {
                            Ui.backgroundOverrides[buffer.key] = it
                            Prefs(panelCtx).backgroundOverrides = Ui.backgroundOverrides.toMap()
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("CHANNEL BACKGROUND", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    fun persistBg() { Prefs(panelCtx).backgroundOverrides = Ui.backgroundOverrides.toMap() }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PanelActionChip("Set image / video") {
                            bgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }
                        PanelActionChip("None here") { Ui.backgroundOverrides[buffer.key] = ""; persistBg() }
                        if (buffer.key in Ui.backgroundOverrides) {
                            PanelActionChip("Use default") { Ui.backgroundOverrides.remove(buffer.key); persistBg() }
                        }
                    }
                }

                TextButton(
                    onClick = onMembers,
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("View members", color = AccentBlue, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, on: Boolean, onClick: (() -> Unit)?) {
    Text(
        label,
        color = if (on) Color.White else TextSecondary,
        fontSize = 11.5.sp,
        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) AccentBlue else PillGray, RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun PanelToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        // Drop the 48dp min touch target so the row is only as tall as the
        // (scaled-down) switch — keeps the panel compact.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                modifier = Modifier.scale(0.8f),
            )
        }
    }
}

/** A small tappable action pill used in the channel control panel. */
@Composable
private fun PanelActionChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = AccentBlue,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PillGray, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Full-bleed cover shown while the biometric lock is engaged. [e2e] switches the
 *  copy/colour to the per-channel encrypted-channel gate. */
@Composable
private fun LockScreen(onUnlock: () -> Unit, e2e: Boolean = false) {
    Box(Modifier.fillMaxSize().background(CanvasBlack), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LockGlyph(color = if (e2e) OnlineGreen else TextSecondary, size = 44.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                if (e2e) "Encrypted channel locked" else "Lurker is locked",
                color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onUnlock) {
                Text("Unlock", color = AccentBlue, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LoginScreen(client: LurkerClient, prefs: Prefs) {
    // Prefill the server + username from the last sign-in; password is never stored.
    var server by remember { mutableStateOf(prefs.serverUrl ?: "") }
    var username by remember { mutableStateOf(prefs.username ?: "") }
    var password by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val canSubmit = server.isNotBlank() && username.isNotBlank() && password.isNotBlank() &&
        !client.authBusy && !client.oauthWaiting
    fun submit() {
        if (!canSubmit) return
        scope.launch { withContext(Dispatchers.IO) { client.login(server, username, password) } }
    }

    Box(
        Modifier.fillMaxSize().background(CanvasBlack).imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LockGlyph(color = AccentBlue, size = 40.dp)
            Spacer(Modifier.height(16.dp))
            Text("Sign in to Lurker", color = TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Connect to your Lurker server. Your session stays signed in across launches.",
                color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(26.dp))

            // The hosted lurker.chat app signs in with an email at the control
            // plane; a self-hosted server uses a username. Adapt the field labels
            // as soon as we recognise the hosted address (CLIENT_PROTOCOL §3.2).
            val hosted = isHostedLurker(server)
            FormField("Server address", server) { server = it; client.clearAuthError() }
            Text(
                if (hosted) "Hosted Lurker — sign in with your lurker.chat email below."
                else "e.g. chat.irc.so — https:// is added automatically.",
                color = TextSecondary, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 3.dp),
            )
            Spacer(Modifier.height(18.dp))

            // Browser sign-in (Lurker 2.3+ OAuth): the server's own page does the
            // authenticating — password or passkey — and the app only ever holds
            // the resulting token. Preferred; the password form below stays for
            // older servers, which beginOAuth detects and says so.
            var viaBrowser by remember { mutableStateOf(false) }
            if (client.oauthWaiting) {
                Text(
                    "Finish signing in in your browser, then come back here.",
                    color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                CircularProgressIndicator(Modifier.size(20.dp), color = AccentBlue, strokeWidth = 2.dp)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { client.cancelOAuth() }) {
                    Text("Cancel", color = AccentBlue, fontSize = 14.sp)
                }
            } else {
                Button(
                    onClick = {
                        viaBrowser = true
                        scope.launch {
                            val url = withContext(Dispatchers.IO) { client.beginOAuth(server) }
                            if (url != null) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (_: ActivityNotFoundException) {
                                    client.cancelOAuth("No web browser is installed to sign in with.")
                                }
                            }
                        }
                    },
                    enabled = server.isNotBlank() && !client.authBusy,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    if (client.authBusy && viaBrowser) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Sign in with browser", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    "Opens your server's sign-in page. Works with passkeys, and your password never enters the app.",
                    color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = GlassBorder, modifier = Modifier.fillMaxWidth(0.6f))
            Spacer(Modifier.height(8.dp))
            Text("Or sign in with a password", color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            FormField(if (hosted) "Email" else "Username", username) { username = it; viaBrowser = false; client.clearAuthError() }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; client.clearAuthError() },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                trailingIcon = {
                    TextButton(onClick = { showPw = !showPw }) {
                        Text(if (showPw) "Hide" else "Show", color = AccentBlue, fontSize = 13.sp)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            client.authError?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = AlertRed, fontSize = 13.sp, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = { submit() },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (client.authBusy && !viaBrowser) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Sign in", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "No account? Ask your Lurker server's admin to create one.",
                color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = GlassBorder, modifier = Modifier.fillMaxWidth(0.6f))
            Spacer(Modifier.height(10.dp))
            Text("Don't use Lurker?", color = TextSecondary, fontSize = 12.sp)
            TextButton(onClick = { prefs.clientMode = "direct"; restartAppProcess(context) }) {
                Text("Connect directly to IRC or a bouncer", color = AccentBlue, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ModePickerScreen(onPick: (String) -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("How do you want to connect?", style = MaterialTheme.typography.headlineSmall)
            Text(
                "You can change this later in Settings (it restarts the app).",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            ModeCard(
                title = "Lurker server",
                body = "Connect to a Lurker instance (always-on, history & sync, push). Best experience — needs a Lurker account.",
                onClick = { onPick("lurker") },
            )
            ModeCard(
                title = "Direct IRC / bouncer",
                body = "Connect straight to IRC networks, or to a bouncer (soju, ZNC). No Lurker needed. Note: without a bouncer, a phone can't stay connected in the background.",
                onClick = { onPick("direct") },
            )
        }
    }
}

@Composable
private fun ModeCard(title: String, body: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = SurfaceDark,
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = TextSecondary, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BufferListScreen(
    client: LurkerClient,
    sharePending: Boolean,
    onOpen: (Buffer) -> Unit,
    onSettings: () -> Unit,
    onDcc: () -> Unit,
    onNetworks: () -> Unit,
    onSearch: () -> Unit,
    onFriends: () -> Unit,
    onIgnores: () -> Unit,
    onBrowse: () -> Unit,
    onOpenMultichan: () -> Unit,
    onSignOut: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    val hazeState = remember { HazeState() }
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableStateOf(0) }

    Box(Modifier.fillMaxSize().background(CanvasBlack)) {
        AmbientBackground(Modifier.hazeSource(hazeState, zIndex = 0f))
        val rows = remember(
            client.buffers.toList(),
            client.unread.toMap(),
            client.networks.toMap(),
            client.networkConfigs.toList(),
            client.pins.toMap(),
            client.favorites.toList(),
            client.presence.toMap(),
        ) { buildBufferSections(client) }

        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (client.connected) "No buffers yet." else "Connecting…", color = TextSecondary)
            }
        } else {
            BufferListBody(
                client = client,
                rows = rows,
                sharePending = sharePending,
                topPadding = with(density) { topBarHeightPx.toDp() },
                hazeState = hazeState,
                onOpen = onOpen,
                onOpenMultichan = onOpenMultichan,
            )
        }

        // Frosted top bar the list scrolls beneath.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { topBarHeightPx = it.height }
                .hazeEffect(hazeState, style = glassStyle()),
        ) {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConnectionDot(client.connected)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (client.connected) "Buffers" else "Connecting…",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                actions = {
                    // Search is a server-side feature — hidden in direct IRC mode.
                    if (client.serverFeatures) {
                        TextButton(onClick = onSearch) { SearchGlyph() }
                    }
                    TextButton(onClick = { menuOpen = true }) {
                        Text("⋯", color = TextSecondary, fontSize = 22.sp)
                    }
                    AppDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        // Server-only surfaces (server /LIST cache, server-synced
                        // contacts, server DCC) don't apply to a raw IRC/bouncer link.
                        if (client.serverFeatures) {
                            DropdownMenuItem(text = { Text("Browse channels (/LIST)") }, onClick = { menuOpen = false; onBrowse() })
                        }
                        DropdownMenuItem(text = { Text("Mark all read") }, onClick = { menuOpen = false; client.markAllRead() })
                        if (client.serverFeatures) {
                            DropdownMenuItem(text = { Text("Friends") }, onClick = { menuOpen = false; onFriends() })
                        }
                        DropdownMenuItem(text = { Text("Ignore list") }, onClick = { menuOpen = false; onIgnores() })
                        DropdownMenuItem(text = { Text("Networks") }, onClick = { menuOpen = false; onNetworks() })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; onSettings() })
                        if (client.serverFeatures) {
                            DropdownMenuItem(text = { Text("DCC transfers") }, onClick = { menuOpen = false; onDcc() })
                        }
                        DropdownMenuItem(text = { Text("Sign out") }, onClick = { menuOpen = false; onSignOut() })
                    }
                },
            )
        }
    }
}

@Composable
private fun BufferListBody(
    client: LurkerClient,
    rows: List<BufferSection>,
    sharePending: Boolean,
    topPadding: androidx.compose.ui.unit.Dp,
    hazeState: HazeState,
    onOpen: (Buffer) -> Unit,
    onOpenMultichan: () -> Unit = {},
) {
    LazyColumn(
        // Clip the scroll viewport above the nav bar (padding, not just
        // contentPadding, so rows never render under it mid-scroll) while the
        // ambient background behind still fills edge-to-edge.
        Modifier.fillMaxSize().navigationBarsPadding().hazeSource(hazeState, zIndex = 1f),
        contentPadding = PaddingValues(top = topPadding + 4.dp, bottom = 8.dp),
    ) {
            if (sharePending) {
                item {
                    Text(
                        "Sharing a file — pick a conversation to attach it to.",
                        color = AccentBlue,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp, 8.dp)
                            .background(SurfaceDark, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                    )
                }
            }
            if (client.serverFeatures && client.dccChatOffers.isNotEmpty()) {
                item { DccChatBanner(client, current = null) }
            }
            // The Multi-channel firehose: a single pinned entry above every network
            // that merges the timelines of all toggled channels. Shown only once at
            // least one channel opts in (from its control panel).
            if (Ui.multichan.isNotEmpty()) {
                item {
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, GlassBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 6.dp)
                            .clickable(onClick = onOpenMultichan),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Text("⊞", color = AccentBlue, fontSize = 20.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Multi-channel", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${Ui.multichan.size} channel${if (Ui.multichan.size == 1) "" else "s"} merged",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                            Text("›", color = TextSecondary, fontSize = 20.sp)
                        }
                    }
                }
            }
            items(rows.size) { index ->
                val section = rows[index]
                Text(
                    section.network,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth().padding(28.dp, 18.dp, 16.dp, 6.dp),
                )
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Column {
                        section.buffers.forEachIndexed { i, buffer ->
                            // Friend rows are identity rows synthesized from contacts:
                            // they carry a presence dot, are tagged by network (they
                            // span networks), and are NOT closeable/pinnable here (a
                            // friend buffer is unclosable — closing would just collapse
                            // it back to this same row).
                            val isFriendRow = section.network == "Friends"
                            // Cache the E2E history scan (up to 150 msgs) per row, so a
                            // buffer-list recompose (unread/pin/presence ticks) doesn't
                            // re-scan every buffer's history every frame. Recomputes only
                            // when this buffer's message list or its known-E2E flag moves.
                            val bufMsgs = client.messagesByBuffer[buffer.key]
                            val bufSeenE2e = buffer.key in client.e2eSeen
                            val e2eFlag = remember(bufMsgs, bufSeenE2e) {
                                bufMsgs?.takeIf { it.isNotEmpty() }
                                    ?.let { m -> m.asReversed().take(150).any { it.e2e } }
                                    ?: bufSeenE2e
                            }
                            BufferRow(
                                buffer = buffer,
                                unread = client.unread[buffer.key] ?: 0,
                                highlight = (client.highlights[buffer.key] ?: 0) > 0,
                                hasDraft = !client.drafts[buffer.key].isNullOrBlank(),
                                pinned = client.isPinned(buffer),
                                notifyAll = client.isNotifyAlways(buffer),
                                muted = client.isNotifyMuted(buffer),
                                showNetwork = section.network == "★ Pinned" || isFriendRow ||
                                    section.network == "💬 New queries",
                                // Green lock, matching the chat header (see e2eFlag above).
                                e2e = e2eFlag,
                                // "" (not null) when unknown: the row still shows a
                                // dot, greyed, rather than dropping it entirely.
                                presence = when {
                                    // A DCC peer has no IRC presence — the socket is the
                                    // whole story: green while the chat is live.
                                    buffer.isDccChat -> if (client.isDccChatLive(buffer)) "online" else ""
                                    isFriendRow -> client.presenceState(buffer.networkId, buffer.target).orEmpty()
                                    else -> null
                                },
                                // Favorites carry no display name (2.0 dropped the
                                // named-contact model) — the row reads the nick.
                                label = null,
                                networkName = buffer.networkId?.let { client.networkName(it) },
                                onTogglePin = if (isFriendRow || buffer.isSystem || buffer.isServerBuffer) null else {
                                    { client.togglePin(buffer) }
                                },
                                // Server refuses system/server pseudo-buffers, so
                                // don't offer it there.
                                favorite = client.isFavorite(buffer),
                                onToggleFavorite = if (buffer.isSystem || buffer.isServerBuffer || buffer.isDccChat) null else {
                                    { client.toggleFavorite(buffer.networkId, buffer.target) }
                                },
                                onToggleNotify = if (buffer.isChannel) {
                                    { client.setNotifyAlways(buffer, !client.isNotifyAlways(buffer)) }
                                } else null,
                                onToggleMute = if (buffer.isChannel) {
                                    { client.setNotifyMuted(buffer, !client.isNotifyMuted(buffer)) }
                                } else null,
                                onClick = { onOpen(buffer) },
                                onClose = if (isFriendRow || buffer.isSystem || buffer.isServerBuffer) null else {
                                    { client.execute(buffer, listOf(WireOp("close"))) }
                                },
                            )
                            if (i < section.buffers.lastIndex) {
                                HorizontalDivider(
                                    color = SurfaceRaised,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
}

/** Unwrap a Compose context to its host Activity (for window-level flags like
 *  FLAG_SECURE). Compose hands you a ContextWrapper, not the Activity directly. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private data class BufferSection(val network: String, val buffers: List<Buffer>)

/**
 * Group buffers by network. Sections follow the user's chosen network order
 * (the /api/networks sidebar order, rearrangeable on the Networks screen);
 * within a section the Server row leads, then unread, channels, DMs.
 */
private fun buildBufferSections(client: LurkerClient): List<BufferSection> {
    val out = mutableListOf<BufferSection>()
    // A "Pinned" section leads, intersecting pins with actually-open buffers.
    val pinned = client.buffers.filter { client.isPinned(it) }
    if (pinned.isNotEmpty()) out.add(BufferSection("★ Pinned", pinned.sortedBy { it.target.lowercase() }))

    // "Friends" and "Favorites" are two kind-filtered views of ONE server-owned
    // favorites list (Lurker 2.0's contacts replacement): DMs surface as Friends
    // with a presence dot, channels as Favorites. Rows are synthesized
    // INDEPENDENTLY of whether the buffer is open server-side — per amiantos,
    // close-state is irrelevant here (the web sidebar is f(buffers ∪ favorites ∪ …)).
    // Reuse the open buffer when we hold one (real casing / shared unread key),
    // else a transient row that materializes on tap via open-buffer. Both are then
    // hidden from the per-network lists below so nothing shows twice.
    //
    // The server's order IS the user's chosen order, so it's preserved verbatim
    // rather than re-sorted (presence still shows as the dot colour).
    val favRows = client.favorites.map { f ->
        client.buffers.firstOrNull { it.key == f.key }
            ?: Buffer(f.networkId, f.target, client.networkName(f.networkId))
    }
    val friendKeys = favRows.map { it.key }.toSet()
    val friendRows = favRows.filterNot { it.isChannel }
    val favoriteChannels = favRows.filter { it.isChannel }
    if (friendRows.isNotEmpty()) out.add(BufferSection("Friends", friendRows))
    if (favoriteChannels.isNotEmpty()) out.add(BufferSection("⭐ Favorites", favoriteChannels))

    // A query someone just opened is the most time-sensitive thing in the list, but
    // it used to sit inside its network's group — potentially several screens down,
    // so you had to scroll to notice it at all (freakyy85). Surface unread DMs in
    // their own section up top. Friend DMs are skipped: they're already near the
    // top in Friends, with a presence dot. The section only exists while something
    // is unread, so it vanishes once you've caught up.
    val queryRows = client.buffers.filter {
        !it.isChannel && !it.isSystem && !it.isServerBuffer &&
            it.key !in friendKeys && !client.isPinned(it) &&
            (client.unread[it.key] ?: 0) > 0
    }
    val queryKeys = queryRows.map { it.key }.toSet()
    if (queryRows.isNotEmpty()) {
        out.add(BufferSection("💬 New queries", queryRows.sortedBy { it.target.lowercase() }))
    }

    // Pinned + friend-primary buffers live only in their own sections — drop them
    // from the network group so they don't show twice (matches the web client).
    // Group by stable network id, NOT the buffer's stored networkName. That name
    // is captured when the buffer is first created; if the network's name isn't
    // known yet it falls back ("network"/"system"), and since the field never
    // updates, one network's rows scatter across mismatched sections (the "two
    // Server rows / funky sections" bug). Grouping by id keeps a network's rows
    // together and resolves the header label live, so a late name-fetch fixes it.
    val byNetwork = client.buffers
        .filterNot { client.isPinned(it) }
        .filterNot { it.key in friendKeys }
        .filterNot { it.key in queryKeys }
        .groupBy { it.networkId }
    byNetwork.entries
        .sortedWith(
            compareBy(
                { it.key == null }, // the app-scoped :system: buffer sorts last
                { it.key?.let { id -> client.networkOrder(client.networkName(id)) } ?: Int.MAX_VALUE },
                { it.key?.let { id -> client.networkName(id).lowercase() } ?: "system" },
            ),
        )
        .forEach { (networkId, bufs) ->
            out.add(
                BufferSection(
                    network = networkId?.let { client.networkName(it) } ?: "System",
                    buffers = bufs.sortedWith(
                        compareByDescending<Buffer> { it.isServerBuffer } // Server row leads
                            .thenByDescending { (client.unread[it.key] ?: 0) > 0 }
                            .thenByDescending { it.isChannel }
                            .thenBy { it.target.lowercase() },
                    ),
                ),
            )
        }
    return out
}

@Composable
private fun BufferRow(
    buffer: Buffer,
    unread: Int,
    highlight: Boolean,
    hasDraft: Boolean = false,
    pinned: Boolean = false,
    notifyAll: Boolean = false,
    muted: Boolean = false,
    showNetwork: Boolean = false,
    e2e: Boolean = false,
    /** Raw presence state for a Friends row ("online"/"away"/"offline"/…), "" when
     *  unknown, or null for a row that shows no presence dot at all. */
    presence: String? = null,
    label: String? = null,
    /** Network name to show under the row (Pinned/Friends/queries), resolved LIVE
     *  by the caller — buffer.networkName is frozen at creation and reads "network"
     *  for a buffer made before its network name arrived. Falls back to that field. */
    networkName: String? = null,
    onTogglePin: (() -> Unit)? = null,
    /** Favorite/unfavorite this buffer — "Friends" for a DM, "Favorites" for a
     *  channel, one server-side flag behind both labels. */
    favorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onToggleNotify: (() -> Unit)? = null,
    onToggleMute: (() -> Unit)? = null,
    onClick: () -> Unit,
    onClose: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    if (onClose != null || onTogglePin != null || onToggleMute != null || onToggleFavorite != null) {
        AppDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (onToggleFavorite != null) {
                val what = if (buffer.isChannel) "Favorites" else "Friends"
                DropdownMenuItem(
                    text = { Text(if (favorite) "Remove from $what" else "Add to $what") },
                    onClick = { menu = false; onToggleFavorite() },
                )
            }
            if (onTogglePin != null) {
                DropdownMenuItem(
                    text = { Text(if (pinned) "Unpin" else "Pin to top") },
                    onClick = { menu = false; onTogglePin() },
                )
            }
            if (onToggleNotify != null) {
                DropdownMenuItem(
                    text = { Text(if (notifyAll) "Notify: highlights only" else "Notify on every message") },
                    onClick = { menu = false; onToggleNotify() },
                )
            }
            if (onToggleMute != null) {
                DropdownMenuItem(
                    text = { Text(if (muted) "Unmute notifications" else "Mute notifications") },
                    onClick = { menu = false; onToggleMute() },
                )
            }
            if (onClose != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (buffer.isChannel) "Close (leaves ${buffer.target})" else "Close conversation",
                            color = AlertRed,
                        )
                    },
                    onClick = { menu = false; onClose() },
                )
            }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { if (onClose != null) menu = true })
            .padding(16.dp, 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Presence dot for Friends rows, on the same colour guide as the Friends
        // screen: green online, yellow away, red offline, grey unknown.
        if (presence != null) {
            Box(
                Modifier
                    .padding(end = 10.dp)
                    .size(9.dp)
                    .background(presenceColor(presence.ifEmpty { null }), CircleShape),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                label ?: buffer.displayName,
                // Explicit: the glass screens sit outside a Scaffold, so there is no
                // implicit Material content color to inherit.
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal,
            )
            // The Pinned section flattens across networks, so a bare "#foo" is
            // ambiguous when the same channel is joined on several networks —
            // tag each pinned row with its network.
            val netLabel = (networkName ?: buffer.networkName).takeIf { it.isNotBlank() && it != "network" }
            if (showNetwork && netLabel != null) {
                Text(
                    netLabel,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        // E2E indicator — trailing (right) side so it doesn't disrupt the name's
        // left alignment; same green lock as the chat header.
        if (e2e) {
            LockGlyph(color = OnlineGreen, size = 13.dp)
            Spacer(Modifier.width(8.dp))
        }
        if (hasDraft) {
            Text("✎", color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
        }
        if (unread > 0) {
            UnreadBadge(unread, highlight)
            Spacer(Modifier.width(8.dp))
        }
        Text("›", color = TextSecondary, fontSize = 20.sp)
    }
}

@Composable
private fun UnreadBadge(count: Int, highlight: Boolean) {
    Box(
        Modifier
            .background(if (highlight) AlertRed else PillGray, RoundedCornerShape(11.dp))
            .padding(horizontal = 9.dp, vertical = 2.dp),
    ) {
        Text(
            if (count > 999) "999+" else "$count",
            color = if (highlight) Color.White else TextPrimary,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ConnectionDot(connected: Boolean) {
    Box(Modifier.size(9.dp).background(if (connected) OnlineGreen else AlertRed, CircleShape))
}

// ---- Chat ------------------------------------------------------------------

/** One rendered row in the chat list, after grouping and divider insertion. */
private sealed interface ChatRow {
    data class Bubble(val msg: Msg, val first: Boolean, val last: Boolean) : ChatRow
    data class Action(val msg: Msg) : ChatRow
    data class SystemLine(val msg: Msg) : ChatRow
    data object NewMessages : ChatRow
}

/** All the app's popup menus, styled once: rounded corners, the raised surface
 *  colour, a hairline border and a soft shadow — instead of the stock sharp box. */
@Composable
private fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(16.dp),
        containerColor = SurfaceRaised,
        border = BorderStroke(0.5.dp, GlassBorder),
        tonalElevation = 0.dp,
        shadowElevation = 14.dp,
        content = content,
    )
}

/** The message id backing a row (-1 for the New-messages divider). */
private fun rowMsgId(r: ChatRow): Long = when (r) {
    is ChatRow.Bubble -> r.msg.id
    is ChatRow.Action -> r.msg.id
    is ChatRow.SystemLine -> r.msg.id
    ChatRow.NewMessages -> -1L
}

/**
 * Group consecutive messages from the same sender iMessage-style: each message
 * keeps ITS OWN bubble (a full merge made message boundaries invisible), but a
 * group stacks tight — nick label once, tightened corners along the shared
 * edge, timestamp on the last. The "New messages" divider breaks a group.
 */
private fun buildChatRows(messages: List<Msg>, dividerAfter: Long?): List<ChatRow> {
    val out = ArrayList<ChatRow>(messages.size + 1)
    var dividerPending = dividerAfter != null && messages.any { it.id > dividerAfter && !it.self }
    var prevBubble: Msg? = null
    for (msg in messages) {
        var groupBroke = false
        if (dividerPending && dividerAfter != null && msg.id > dividerAfter && !msg.self) {
            out.add(ChatRow.NewMessages)
            dividerPending = false
            groupBroke = true
        }
        when {
            msg.system -> { out.add(ChatRow.SystemLine(msg)); prevBubble = null }
            msg.type == "action" -> { out.add(ChatRow.Action(msg)); prevBubble = null }
            else -> {
                val sameGroup = !groupBroke && prevBubble != null &&
                    prevBubble.nick == msg.nick && prevBubble.self == msg.self &&
                    prevBubble.type == msg.type
                if (sameGroup) {
                    // The row just before this one is always the previous bubble
                    // (anything else resets prevBubble): it's no longer last.
                    val prev = out.last() as ChatRow.Bubble
                    out[out.lastIndex] = prev.copy(last = false)
                }
                out.add(ChatRow.Bubble(msg, first = !sameGroup, last = true))
                prevBubble = msg
            }
        }
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    client: LurkerClient,
    buffer: Buffer,
    scrollToMsgId: Long? = null,
    sharedUri: Uri? = null,
    onShareConsumed: () -> Unit = {},
    onBack: () -> Unit,
    onOpenBuffer: (Buffer) -> Unit,
    // Opens the channel browser (from /list); the arg pre-seeds the search filter.
    onBrowse: (String?) -> Unit = {},
    // Opens Settings from the top-bar gear.
    onSettings: () -> Unit = {},
    // The middle pane of the tablet layout: the buffer list is a persistent rail,
    // so this hides the back arrow and yields the back gesture to the host.
    embedded: Boolean = false,
    // Whether to show the roster button that opens the modal member sheet. In the
    // full three-pane layout the roster is its own pane, so this is turned off.
    showMembersButton: Boolean = true,
) {
    BackHandler(enabled = !embedded, onBack = onBack)
    // Seed the composer from the server-synced draft when the buffer opens. The
    // text itself already survives a rotation (every keystroke writes through to
    // the process-scoped client), but seeding with a bare TextFieldValue parked the
    // caret at position 0 — so a rotate mid-sentence, or reopening a buffer with a
    // draft, dropped you at the START of your own text. Seed the caret at the end.
    var draft by remember(buffer.key) {
        val seed = client.drafts[buffer.key] ?: ""
        mutableStateOf(TextFieldValue(seed, TextRange(seed.length)))
    }
    // Flush the draft when leaving this buffer.
    DisposableEffect(buffer.key) { onDispose { client.flushDraft(buffer) } }
    // If this buffer is closed out from under us (/part, /close, or a close from
    // another device), leave the chat view rather than stranding an empty one.
    val stillOpen = client.buffers.any { it.key == buffer.key }
    LaunchedEffect(stillOpen) { if (!stillOpen && client.buffers.isNotEmpty()) onBack() }
    // Prefix the composer with "nick: " — the shared reply seam for the
    // long-press action sheet and the swipe-to-reply gesture.
    fun addressNick(m: Msg) {
        // "nick, " — the same ping form nick-completion uses at line start, not ":".
        val t = "${m.nick}, " + draft.text
        draft = TextFieldValue(t, TextRange(t.length))
        client.setDraftLocal(buffer, t)
    }
    // Reply: an IRCv3 reply when the server can name the line (Lurker 2.4+ and a
    // msgid), shown quoted above the sent message; otherwise the plain address.
    // The quote carries the context, so a DM needs no "nick, " — a channel still
    // gets one so the line reads as addressed to them.
    fun replyTo(m: Msg) {
        if (!client.canReplyTo(buffer, m)) { addressNick(m); return }
        val addressed = buffer.isChannel && !m.self && !draft.text.startsWith("${m.nick}, ")
        if (addressed) addressNick(m)
        client.setDraftReply(
            buffer,
            DraftReply(m.id, addressed, ReplyParent(m.id, m.nick, m.type, m.text.take(300), m.self)),
        )
    }
    // Cancelling also takes back the "nick, " the reply put in, if it's still there.
    fun cancelReply() {
        val r = client.draftReplies[buffer.key] ?: return
        val prefix = r.parent?.nick?.let { "$it, " }
        if (r.addressed && prefix != null && draft.text.startsWith(prefix)) {
            val t = draft.text.removePrefix(prefix)
            draft = TextFieldValue(t, TextRange(t.length))
            client.setDraftLocal(buffer, t)
        }
        client.setDraftReply(buffer, null)
    }
    var showMembers by remember { mutableStateOf(false) }
    var showE2e by remember { mutableStateOf(false) }
    var showTranslate by remember { mutableStateOf(false) }
    // Outgoing-translation cycle. translateOriginal holds what the user TYPED while
    // the composer shows the translation awaiting approval (rendered as "was: …");
    // translateFailed makes the next press post as typed rather than trapping the
    // message behind a broken translator.
    var translateOriginal by remember(buffer.key) { mutableStateOf<String?>(null) }
    var translateFailed by remember(buffer.key) { mutableStateOf(false) }
    var translating by remember(buffer.key) { mutableStateOf(false) }
    var showChannelPanel by remember { mutableStateOf(false) }
    // Message whose long-press action sheet is open, if any.
    var actionMsg by remember(buffer.key) { mutableStateOf<Msg?>(null) }
    val messages = client.messagesByBuffer[buffer.key] ?: emptyList()
    // Self-heal: if we land on an empty buffer (e.g. a notification opened it while
    // the socket was still reconnecting, so the initial open() was dropped),
    // re-request its history once we're connected.
    //
    // Prefer waiting for the connect burst to finish (lurker#635's
    // backlog-complete) rather than racing it: open-buffer JOINs a channel as a
    // side effect, so asking for a buffer whose frame was still in flight isn't
    // free. Empty-once-complete means "not currently open" — never "gone" — so
    // this only re-asks, and touches no local history, draft or read position.
    // A server too old to send the frame falls back to a short grace period, so a
    // dropped open() still self-heals there as it always did.
    // Keyed on burstGeneration, not just `connected`: a socket can die and be
    // replaced without `connected` ever being observed false, which leaves anything
    // watching connection state alone waiting forever (lurker-ios#78). The counter
    // changes on every burst, so the retry always re-arms.
    LaunchedEffect(client.burstGeneration, client.snapshotComplete, buffer.key, messages.isEmpty()) {
        if (!client.connected || messages.isNotEmpty() || buffer.networkId == null) return@LaunchedEffect
        if (!client.snapshotComplete) delay(2_000)
        // Re-read: the buffer may have arrived while we waited.
        if (client.connected && client.messagesByBuffer[buffer.key].isNullOrEmpty()) client.hydrate(buffer)
    }
    // No "channel is encrypted" frame exists; infer from recent E2E traffic.
    val e2eActive = remember(messages.size) { messages.takeLast(200).any { it.e2e } }
    val divider = client.dividerAfter[buffer.key]
    // Event-noise tier (lurker#672). Speakers are derived from what's loaded rather
    // than tracked separately, so "smart" stays consistent with what's on screen
    // and works in direct mode too. Only the RENDER is filtered — tail-following,
    // paging and unread still reason about every message.
    val eventTier = client.eventFilterTier
    val visible = remember(messages, eventTier) {
        if (eventTier == "all") {
            messages
        } else {
            val speakers = messages.asSequence()
                .filter { it.type == "message" || it.type == "action" || it.type == "notice" }
                .map { it.nick.lowercase() }
                .toSet()
            messages.filter { passesEventFilter(it.type, it.nick, it.self, eventTier, speakers) }
        }
    }
    val rows = remember(visible, divider) { buildChatRows(visible, divider) }
    val listState = rememberLazyListState()
    // iMessage-style swipe-to-reveal timestamps (amiantos): timestamps are hidden;
    // a left-drag on any bubble pulls ALL bubbles over via this one shared offset,
    // sliding each message's time in from the right. Springs back on release.
    val revealScope = rememberCoroutineScope()
    val revealAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    // Wide enough for the full "11:47:32 PM" timestamp at the larger reveal font.
    val revealMaxPx = with(LocalDensity.current) { 108.dp.toPx() }

    // Upload → provider URL → splice into the draft (attach button + share sheet).
    val context = LocalContext.current
    // Encrypted (E2E) channels: block screenshots, screen-recording, and the
    // recents-preview thumbnail while one is on screen (FLAG_SECURE). Uses the
    // persisted known-E2E set too, so protection is on before history loads.
    // Cleared on leave so normal buffers stay screenshot-able.
    val secureBuffer = e2eActive || buffer.key in client.e2eSeen
    DisposableEffect(secureBuffer) {
        val window = context.findActivity()?.window
        if (secureBuffer) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { context.findActivity()?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    fun uploadIntoDraft(uri: Uri) {
        val upload = readUpload(context, uri, shrinkToPx = client.maxStaticImageDimension)
        if (upload == null) {
            client.localNotice(buffer, "Couldn't read that file.")
            return
        }
        // Refuse before sending anything: the server advertises its real ceiling
        // now, so an oversized pick fails instantly instead of streaming the whole
        // file up over cellular to earn a 413 (lurker#627).
        uploadTooLarge(upload.second.contentLength(), client.maxUploadBytes)?.let { why ->
            client.localNotice(buffer, why)
            return
        }
        client.uploadFile(upload.first, upload.second) { url, err ->
            if (url != null) {
                val sep = if (draft.text.isEmpty() || draft.text.endsWith(" ")) "" else " "
                val text = draft.text + sep + url + " "
                draft = TextFieldValue(text, TextRange(text.length))
            } else {
                client.localNotice(buffer, "Upload failed: ${err ?: "unknown error"}")
            }
        }
    }
    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uploadIntoDraft(uri)
    }
    // Camera capture: hand the camera app a FileProvider write URI, then push the
    // resulting photo through the exact same upload path as a picked file.
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingPhotoUri
        pendingPhotoUri = null
        if (ok && uri != null) uploadIntoDraft(uri)
    }
    fun launchCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhotoUri = uri
        cameraLauncher.launch(uri)
    }
    // A share-sheet file lands here once a buffer is chosen.
    LaunchedEffect(sharedUri) {
        val uri = sharedUri ?: return@LaunchedEffect
        onShareConsumed()
        uploadIntoDraft(uri)
    }

    // DCC send: remember who we're sending to across the system file picker.
    var dccNick by remember { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val nick = dccNick
        dccNick = null
        val networkId = buffer.networkId
        if (uri != null && nick != null && networkId != null) {
            val upload = readUpload(context, uri)
            if (upload == null) {
                client.dccError = "Couldn't read that file."
            } else {
                client.dccSendFile(networkId, nick, upload.first, upload.second)
            }
        }
    }

    // hasMoreOlder doubles as the server's "unhydrated shell" marker (it ships
    // true on a buffer whose backlog hasn't been fetched yet), so it stays true on
    // a brand-new query that turns out to have no history at all — leaving a
    // "Load older messages" button with nothing behind it (freakyy85). Require a
    // real message to page back FROM, which is exactly when loadOlder() can act:
    // it early-returns without an oldest id.
    val showLoadOlder = client.hasMoreOlder[buffer.key] == true &&
        buffer.networkId != null &&
        messages.any { it.id > 0 }
    val loadingOlder = client.loadingOlder[buffer.key] == true
    val typers = client.typing[buffer.key]?.keys?.sorted() ?: emptyList()
    var joinPrompt by remember { mutableStateOf<String?>(null) }
    // Tapping a #channel in any message offers to join it (freakyy85). Null on
    // the :system: buffer, which has no network to join on.
    val chanTap: ((String) -> Unit)? = remember(buffer.networkId != null) {
        if (buffer.networkId != null) ({ chan: String -> joinPrompt = chan }) else null
    }
    var viewerUrl by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current
    // Route taps: images open the in-app viewer, av media goes to the system
    // player picker, everything else to the browser. Remembered so it's a stable
    // instance across recompositions — every message row takes it as a param, and a
    // fresh lambda each recompose would needlessly re-run them.
    val openLink: (String) -> Unit = remember(uriHandler) {
        { url ->
            val clean = url.substringBefore('?').substringBefore('#').lowercase()
            if ((IMAGE_EXTS + MEDIA_EXTS).any { clean.endsWith(it) }) {
                viewerUrl = url
            } else {
                uriHandler.openUri(url)
            }
        }
    }

    viewerUrl?.let { url -> MediaViewerDialog(url, onClose = { viewerUrl = null }) }

    // Reveal the ghost bubble only when already reading the tail.
    LaunchedEffect(typers.isNotEmpty()) {
        if (typers.isNotEmpty() && rows.isNotEmpty() && !listState.canScrollForward) {
            listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    joinPrompt?.let { chan ->
        AlertDialog(
            onDismissRequest = { joinPrompt = null },
            containerColor = SurfaceRaised,
            title = { Text("Join $chan?", color = TextPrimary) },
            text = { Text("Join the channel on ${buffer.networkId?.let { client.networkName(it) } ?: buffer.networkName}?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    joinPrompt = null
                    buffer.networkId?.let { networkId -> client.join(networkId, chan) }
                }) { Text("Join", color = AccentBlue, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { joinPrompt = null }) { Text("Cancel", color = TextSecondary) }
            },
        )
    }
    val oldestId = messages.firstOrNull { it.id > 0 }?.id
    // Measured heights of the frosted top bar and composer overlays (the list's
    // content padding). Declared up here so the tail-pin effects can key on them.
    var topBarHeightPx by remember { mutableStateOf(0) }
    var bottomBarHeightPx by remember { mutableStateOf(0) }
    // Set when a load-older is requested: the id whose row we re-anchor to once
    // the prepend lands, so the viewport doesn't jump.
    var anchorId by remember(buffer.key) { mutableStateOf<Long?>(null) }
    val headerCount = if (showLoadOlder) 1 else 0
    // Bumped whenever an inline image finishes loading and resizes its card, so
    // the tail-follow effect below can re-pin the bottom instead of drifting up.
    var mediaTick by remember(buffer.key) { mutableStateOf(0) }

    // Reading position is sacred: follow the tail ONLY when the user is already
    // at (or within a couple rows of) the bottom. The -2 slack also absorbs the
    // index shift of the append itself. Scrolled up = stay put, no fighting.
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            // -3 (not -2): one extra row of slack absorbs the brief gap between a
            // message appending and the follow-scroll catching up, so a busy
            // channel doesn't flash "N new" while you're actually at the bottom.
            lastVisible >= info.totalItemsCount - 3
        }
    }
    // The jump chip's count is DERIVED, not incremented: snapshot the newest id
    // when the reader leaves the bottom, then count real messages beyond it —
    // exact even when a burst lands in one recomposition.
    var awaySinceId by remember(buffer.key) { mutableStateOf<Long?>(null) }
    LaunchedEffect(atBottom) {
        awaySinceId = if (atBottom) null else (messages.lastOrNull { it.id > 0 }?.id ?: 0L)
    }
    // Cache this O(n) scan so it doesn't run on every ChatScreen recompose (typing,
    // media ticks, …). `messages` is a plain local re-derived each recompose (not
    // snapshot state), so derivedStateOf would capture a stale ref — key on cheap
    // signals instead: the count only moves when a message arrives (size grows) or
    // the away-anchor flips.
    val missed = remember(messages.size, awaySinceId) {
        awaySinceId?.let { since -> messages.count { it.id > since && !it.system && !it.self } } ?: 0
    }

    // Whether the reader is parked on the newest row. Tracked from scroll gestures
    // (and set by our own tail scrolls) rather than derived from layout, because a
    // PADDING change — the composer measuring in after the first frame, the
    // keyboard sliding up — pushes the tail under the composer without any scroll
    // happening. By the time layout reflects that, "at bottom" already reads false
    // (or, with the atBottom slack, falsely true), so nothing re-pinned and a
    // channel opened with the keyboard up landed several messages short.
    var pinnedToTail by remember(buffer.key) { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) pinnedToTail = !listState.canScrollForward
        }
    }
    // Opening a buffer lands at the newest message ONCE (regardless of scroll
    // position), the moment its rows populate. After that, live messages only
    // follow the tail if you're already at the bottom — so you never fight it.
    var openScrollDone by remember(buffer.key) { mutableStateOf(false) }
    // Armed when YOU send a message: your own line echoes back from the server a
    // beat later, and the plain at-bottom tail-follow can miss it (the echo lands
    // on a frame where atBottom reads false), leaving your message below the fold
    // behind the composer while the view sits on the last OTHER message. This
    // one-shot forces the scroll to your line when it arrives, then disarms.
    var followSelfSend by remember(buffer.key) { mutableStateOf(false) }
    // A tapped notification asks to jump to a specific message; flash it briefly so
    // the eye lands on it. Reset when the buffer or requested id changes.
    var scrolledToTarget by remember(buffer.key, scrollToMsgId) { mutableStateOf(false) }
    var flashMsgId by remember(buffer.key) { mutableStateOf<Long?>(null) }
    LaunchedEffect(flashMsgId) { if (flashMsgId != null) { delay(2400); flashMsgId = null } }
    LaunchedEffect(buffer.key, rows.size, scrollToMsgId) {
        if (rows.isEmpty()) return@LaunchedEffect
        val targetIdx = if (scrollToMsgId != null && !scrolledToTarget)
            rows.indexOfFirst { rowMsgId(it) == scrollToMsgId } else -1
        when {
            // Jump straight to the notification's message once it's loaded.
            targetIdx >= 0 -> {
                listState.scrollToItem(targetIdx + headerCount)
                openScrollDone = true
                pinnedToTail = false
                scrolledToTarget = true
                flashMsgId = scrollToMsgId
            }
            // Normal open: land at the newest message, once.
            !openScrollDone -> {
                listState.scrollToItem(rows.lastIndex + headerCount)
                openScrollDone = true
                pinnedToTail = true
            }
        }
    }
    // Where the viewport goes after YOU send something. This used to be purely the
    // client author's call — Spooky kept your position, the web client re-pinned —
    // so the server now owns it as `chat.keep_position_on_send` (lurker#628 / PR
    // #667) and every client agrees. Server default is off: re-pin to the newest
    // message. Absent on a server too old to register the key, where the same
    // default applies. Once the server ships it the toggle appears in Settings on
    // its own, since that screen is driven by the settings registry.
    val keepPositionOnSend = client.settingBool("chat.keep_position_on_send", false)
    val chatScope = rememberCoroutineScope()
    // Re-pin to the bottom. Scrolls against the CURRENT rows, which is one short of
    // the line being sent — that's fine and self-correcting: it lands us inside the
    // atBottom slack, so the tail-follow below catches the new row when it arrives.
    fun repinToTail() {
        pinnedToTail = true
        chatScope.launch {
            listState.scrollToItem((rows.lastIndex + headerCount).coerceAtLeast(0))
        }
    }
    // Tap a reply's quote: go to the line it answers, and flash it.
    fun jumpTo(id: Long) {
        val idx = rows.indexOfFirst { rowMsgId(it) == id }
        if (idx < 0) {
            android.widget.Toast.makeText(context, "That message isn't loaded — try Load older.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        pinnedToTail = false
        chatScope.launch { listState.animateScrollToItem(idx + headerCount) }
        flashMsgId = id
    }
    // A line's reaction chips, and whether tapping one can send a reaction.
    fun reactionsOf(m: Msg): List<ReactionGroup> =
        if (m.id > 0 && buffer.networkId != null) groupReactions(client.reactions[m.id]) else emptyList()
    fun reactOn(m: Msg): ((String) -> Unit)? =
        if (client.canReact(buffer, m)) { v: String -> client.react(m.id, v) } else null
    /**
     * Actually put the composer contents on the wire. Split out of the send
     * lambda so the outgoing-translation cycle can reach it: an already-in-
     * target-language message sends straight from the translate coroutine
     * instead of demanding a second press.
     */
    fun dispatchSend(raw: String) {
        client.notifyTyping(buffer, active = false)
        client.addInputHistory(buffer, raw)
        val myNick = buffer.networkId?.let { client.networks[it]?.nick }
        val text = Commands.expandAlias(raw, client.aliases, myNick, buffer.target)
        when (val parsed = Commands.parse(text, buffer.target, buffer.networkId != null)) {
            is ParsedInput.Ops -> {
                // The reply rides the first line going to THIS buffer; a command
                // that sends nothing here (/join, /whois…) leaves it in place.
                val reply = client.draftReplies[buffer.key]
                var usedReply = false
                val ops = if (reply == null) parsed.ops else parsed.ops.map { op ->
                    if (!usedReply && op.target == null && (op.type == "send" || op.type == "action")) {
                        usedReply = true
                        op.copy(replyTo = reply.messageId)
                    } else op
                }
                if (usedReply) client.consumeDraftReply(buffer)
                client.execute(buffer, ops)
                parsed.openTarget?.let { target ->
                    buffer.networkId?.let { onOpenBuffer(client.focusTarget(it, target)) }
                }
                // Only a plain message (or /me, /notice) is subject to
                // the keep-position preference. A command always re-pins:
                // its reply comes back as id-less rows that the stay-put
                // logic can't count, so it would land silently below the
                // fold with nothing to say it arrived.
                val wasMessage = parsed.ops.all { it.type in SENDABLE_OPS }
                if (!keepPositionOnSend || !wasMessage) repinToTail()
                // A sent message echoes back asynchronously; arm the
                // one-shot so the scroll lands on YOUR line once it does.
                // (Commands append synchronously, so repinToTail already
                // caught them — no echo to wait for.)
                if (wasMessage && !keepPositionOnSend) followSelfSend = true
            }
            // Local command output (/help and friends) is id-less too.
            is ParsedInput.Local -> { client.localNotice(buffer, parsed.message); repinToTail() }
            is ParsedInput.DccChat -> {
                val networkId = buffer.networkId
                if (!client.serverFeatures || networkId == null) {
                    client.localNotice(buffer, "DCC chat needs a Lurker server.")
                } else {
                    client.dccChat(
                        networkId, parsed.nick, open = !parsed.close, passive = parsed.passive,
                        reportTo = buffer, focus = !parsed.close,
                    )
                }
                repinToTail()
            }
            is ParsedInput.Browse ->
                // Lurker has a rich channel browser; direct mode falls back
                // to a raw LIST (results land in the server buffer).
                if (client.serverFeatures) onBrowse(parsed.query)
                else client.execute(
                    buffer,
                    listOf(WireOp("raw", line = "LIST" + (parsed.query?.let { " $it" } ?: ""))),
                )
        }
        draft = TextFieldValue("")
        client.setDraftLocal(buffer, "") // clears + syncs draft-clear
    }


    // Follow the tail only when the tail itself changed — a prepend of older
    // history grows the list without moving the newest message.
    val tailSig = messages.lastOrNull()?.let { it.id to it.text.length }
    LaunchedEffect(tailSig) {
        // Closing/parting a buffer empties the rows: lastIndex is then -1 and
        // scrollToItem(-1) throws. Bail before any pinning when there's nothing
        // to pin to (also disarms a pending self-send follow so it can't fire
        // against the next buffer).
        if (rows.isEmpty()) { followSelfSend = false; return@LaunchedEffect }
        // Your own just-sent line: always follow it to the bottom, regardless of
        // the atBottom slack, then disarm. This is the deterministic half of the
        // send scroll — repinToTail handles the instant jump, this catches the
        // async echo so your message is never stranded under the composer.
        if (followSelfSend) {
            listState.scrollToItem(rows.lastIndex + headerCount)
            followSelfSend = false
            return@LaunchedEffect
        }
        // Never follow the tail while the user is actively scrolling — otherwise a
        // fling that's still within the atBottom slack gets yanked back down by the
        // next append, which on a busy channel repeats and traps them at the bottom.
        if (openScrollDone && anchorId == null && atBottom && !listState.isScrollInProgress) {
            listState.scrollToItem(rows.lastIndex + headerCount)
        }
    }
    // The composer (and the keyboard under it) or the top bar changed height: a
    // reader parked on the tail stays on it. Runs on every frame of the IME
    // animation, which is what keeps the newest line glued above the keyboard.
    LaunchedEffect(bottomBarHeightPx, topBarHeightPx) {
        if (rows.isEmpty() || !openScrollDone || anchorId != null) return@LaunchedEffect
        if (pinnedToTail && !listState.isScrollInProgress) {
            listState.scrollToItem(maxOf(0, listState.layoutInfo.totalItemsCount - 1, rows.lastIndex + headerCount))
        }
    }
    // An image resizing its card mustn't shove the tail out from under a reader
    // who's sitting at the bottom — re-pin when one loads (but not mid-scroll).
    LaunchedEffect(mediaTick) {
        if (rows.isEmpty()) return@LaunchedEffect
        if (mediaTick > 0 && openScrollDone && anchorId == null && atBottom && !listState.isScrollInProgress) {
            listState.scrollToItem(rows.lastIndex + headerCount)
        }
    }
    // Returning to the app must not strand a reader who was at the tail: some
    // resumes trigger a history re-sync that shifts the list, drifting the view
    // upward. Re-pin to the newest row on resume — but only if we were already
    // at the bottom, so a deliberately scrolled-up reader is left alone.
    val resumeScope = rememberCoroutineScope()
    LifecycleResumeEffect(buffer.key) {
        if (openScrollDone && anchorId == null && atBottom) {
            resumeScope.launch {
                delay(80) // let any resume-time re-sync settle before pinning
                val n = listState.layoutInfo.totalItemsCount
                if (n > 0) listState.scrollToItem(n - 1)
            }
        }
        onPauseOrDispose { }
    }
    // Older page landed: put the previously-oldest row back under the finger.
    LaunchedEffect(oldestId) {
        val anchor = anchorId ?: return@LaunchedEffect
        if (oldestId != null && oldestId < anchor) {
            val idx = rows.indexOfFirst { r ->
                val id = when (r) {
                    is ChatRow.Bubble -> r.msg.id
                    is ChatRow.Action -> r.msg.id
                    is ChatRow.SystemLine -> r.msg.id
                    ChatRow.NewMessages -> -1L
                }
                id >= anchor
            }
            if (idx >= 0) listState.scrollToItem(idx + headerCount)
            anchorId = null
        }
    }

    // Real glassmorphism: the message list is the haze SOURCE and scrolls
    // edge-to-edge; the top bar and composer are frosted panes that blur
    // whatever passes beneath them.
    val hazeState = remember { HazeState() }
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize().background(CanvasBlack)) {
        // A custom chat background image (device-local) replaces the ambient wash
        // as the base haze source, dimmed for text legibility; otherwise the
        // animated ambient wash is the base. Either way it's zIndex 0 so the top
        // bar and composer blur it, and messages layer on top at zIndex 1.
        val bgPath = Ui.backgroundFor(buffer.key)
        if (bgPath != null) {
            ChatBackground(bgPath, Modifier.fillMaxSize().hazeSource(hazeState, zIndex = 0f))
            Box(Modifier.fillMaxSize().background(CanvasBlack.copy(alpha = Ui.backgroundDim)))
        } else {
            AmbientBackground(Modifier.hazeSource(hazeState, zIndex = 0f))
        }
        // Chat text follows the synced look.font.size.mobile setting (web px
        // ~ sp); +2 keeps the historical default (14 -> 16sp). Ui.chatTextScale
        // is the device-local bump (tablets read tiny at the mobile default).
        val baseSize = (client.settingInt("look.font.size.mobile", 14) + 2 + Ui.chatTextScale).coerceIn(11, 30)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(hazeState, zIndex = 1f),
            contentPadding = PaddingValues(
                top = with(density) { topBarHeightPx.toDp() } + 4.dp,
                // Composer overlay height when present (it already includes the
                // nav-bar inset); bare nav-bar inset otherwise (:system: has no
                // composer and its lines were sliding under the task bar).
                bottom = maxOf(
                    with(density) { bottomBarHeightPx.toDp() },
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ) + 6.dp,
            ),
        ) {
            if (showLoadOlder) {
                item {
                    TextButton(
                        onClick = { anchorId = oldestId; client.loadOlder(buffer) },
                        enabled = !loadingOlder,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (loadingOlder) "Loading…" else "Load older messages",
                            color = if (loadingOlder) TextSecondary else AccentBlue,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            // Stable keys so "load older" (a PREPEND) doesn't renumber every row and
            // force a full re-layout of the viewport. Real messages key on their id;
            // the divider and any not-yet-acked optimistic/local rows (id <= 0) fall
            // back to position, which stays unique and can't collide with an "m<id>".
            items(rows.size, key = { i -> rowMsgId(rows[i]).let { if (it > 0) "m$it" else "row-$i" } }) { i ->
                val compact = Ui.compactFor(buffer.key)
                when (val row = rows[i]) {
                    is ChatRow.Bubble -> if (compact) {
                        CompactMessageRow(
                            row.msg, baseSize, openLink,
                            translateKey = buffer.key,
                            onAction = { actionMsg = it },
                            onMediaLoaded = { mediaTick++ },
                            flash = row.msg.id == flashMsgId,
                            // The nick tap stays the quick "nick, " address.
                            onNickTap = { addressNick(row.msg) },
                            onChannel = chanTap,
                            reactions = reactionsOf(row.msg),
                            onReact = reactOn(row.msg),
                            onJumpTo = ::jumpTo,
                        )
                    } else MessageBubble(
                        row.msg, row.first, row.last, baseSize, openLink,
                        translateKey = buffer.key,
                        onMediaLoaded = { mediaTick++ },
                        onAction = { actionMsg = it },
                        onSwipeReply = { replyTo(it) },
                        onChannel = chanTap,
                        flash = row.msg.id == flashMsgId,
                        reactions = reactionsOf(row.msg),
                        onReact = reactOn(row.msg),
                        onJumpTo = ::jumpTo,
                        revealProvider = { revealAnim.value },
                        revealMaxPx = revealMaxPx,
                        onReveal = { d ->
                            revealScope.launch {
                                revealAnim.snapTo((revealAnim.value + d).coerceIn(-revealMaxPx, 0f))
                            }
                        },
                        onRevealEnd = { revealScope.launch { revealAnim.animateTo(0f) } },
                    )
                    is ChatRow.Action -> Column {
                        if (Ui.compact) CompactActionRow(row.msg, baseSize, openLink) else ActionLine(row.msg, baseSize, openLink)
                        ReactionChips(
                            reactionsOf(row.msg), reactOn(row.msg), alignEnd = false,
                            modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
                        )
                    }
                    is ChatRow.SystemLine -> {
                        // An undelivered message gets its text back plus Resend /
                        // Discard, instead of a dead-end notice.
                        val failed = row.msg.failedId?.let { client.failedSends[it] }
                        if (failed != null) {
                            FailedSendRow(
                                failed = failed,
                                baseSize = baseSize,
                                onResend = { client.resendFailed(failed.id) },
                                onDiscard = { client.discardFailed(failed.id) },
                                onCopyToComposer = {
                                    val t = draft.text.ifEmpty { "" } + failed.text
                                    draft = TextFieldValue(t, TextRange(t.length))
                                    client.setDraftLocal(buffer, t)
                                    client.discardFailed(failed.id)
                                },
                            )
                        } else SystemLine(
                            row.msg,
                            onJoin = if (buffer.networkId != null) {
                                { chan -> joinPrompt = chan }
                            } else null,
                        )
                    }
                    ChatRow.NewMessages -> NewMessagesDivider()
                }
            }
            if (typers.isNotEmpty()) {
                item { TypingBubble(typers) }
            }
        }

        // Frosted top bar (app bar + optional topic bar stacked; the column's
        // measured height feeds the list's top content padding).
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { topBarHeightPx = it.height }
                .hazeEffect(hazeState, style = glassStyle()),
        ) {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    if (!embedded) {
                        TextButton(onClick = onBack) { Text("‹", color = AccentBlue, fontSize = 26.sp) }
                    }
                },
                title = {
                    var titleMenu by remember { mutableStateOf(false) }
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(SurfaceDark, RoundedCornerShape(18.dp))
                                .border(0.5.dp, GlassBorder, RoundedCornerShape(18.dp))
                                .combinedClickable(
                                    // Tap the pill → channel control panel (topic,
                                    // modes, notifications). DMs/system buffers have
                                    // no panel, so they fall back to back-nav.
                                    onClick = {
                                        if (buffer.isChannel) {
                                            showChannelPanel = true
                                        } else if (!embedded) {
                                            onBack()
                                        }
                                    },
                                    onLongClick = {
                                        if (!buffer.isSystem && !buffer.isServerBuffer) titleMenu = true
                                    },
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            ConnectionDot(client.connected)
                            Spacer(Modifier.width(7.dp))
                            if (e2eActive) {
                                LockGlyph(color = OnlineGreen, size = 13.dp)
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                buffer.displayName,
                                color = TextPrimary,
                                // Capped: the title slot is only ~168dp between the
                                // back arrow, member count and gear, so at full scale
                                // barely three characters survive the ellipsis.
                                fontSize = cappedSp(15f, 1.3f),
                                fontWeight = FontWeight.SemiBold,
                                // The pill is centred between the back arrow, member
                                // count and gear, so at a large font scale a long name
                                // gets squeezed and would break mid-word
                                // ("#compu / tertech"), doubling the bar's height.
                                // Truncate instead; the full name is in the panel.
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("⌄", color = TextSecondary, fontSize = 13.sp)
                        }
                        AppDropdownMenu(expanded = titleMenu, onDismissRequest = { titleMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Translate", color = AccentBlue) },
                                onClick = { titleMenu = false; showTranslate = true },
                            )
                            // E2E rides IRC; a DCC chat is already a direct socket.
                            if (!buffer.isDccChat) DropdownMenuItem(
                                text = { Text("Encryption (E2E)", color = OnlineGreen) },
                                onClick = { titleMenu = false; showE2e = true; client.execute(buffer, listOf(WireOp("e2e", target = buffer.target, line = "status"))) },
                            )
                            val dccNet = buffer.networkId
                            if (buffer.isDccChat && dccNet != null && client.serverFeatures) {
                                val live = client.isDccChatLive(buffer)
                                DropdownMenuItem(
                                    text = { Text(if (live) "End DCC chat" else "Start DCC chat again", color = AccentBlue) },
                                    onClick = {
                                        titleMenu = false
                                        client.dccChat(dccNet, buffer.peer, open = !live, reportTo = buffer)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when {
                                            buffer.isChannel -> "Close (leaves ${buffer.target})"
                                            buffer.isDccChat -> "Close (ends the chat)"
                                            else -> "Close conversation"
                                        },
                                        color = AlertRed,
                                    )
                                },
                                onClick = {
                                    titleMenu = false
                                    client.execute(buffer, listOf(WireOp("close")))
                                    onBack()
                                },
                            )
                        }
                    }
                },
                actions = {
                    if (buffer.isChannel && showMembersButton) {
                        val count = client.members[buffer.key]?.size ?: 0
                        TextButton(onClick = { showMembers = true }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MembersGlyph(color = TextSecondary, size = 16.dp)
                                if (count > 0) {
                                    Spacer(Modifier.width(5.dp))
                                    Text("$count", color = TextSecondary, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                    // Quick Settings access without going back to the buffer list.
                    IconButton(onClick = onSettings) {
                        SettingsGlyph(color = TextSecondary, size = 20.dp)
                    }
                },
            )
            // Pending DCC chat offers, and this `=nick` chat's own connection state.
            // Inside the measured top column so the list's padding follows it.
            if (client.serverFeatures) DccChatBanner(client, current = buffer)
        }

        // Channel control panel — tap the channel name pill to slide it out.
        // Topic, flag-mode toggles, and notification controls, on frosted glass.
        if (buffer.isChannel) {
            ChannelControlPanel(
                client = client,
                buffer = buffer,
                hazeState = hazeState,
                topInsetPx = topBarHeightPx,
                visible = showChannelPanel,
                onDismiss = { showChannelPanel = false },
                onOpenLink = openLink,
                onMembers = { showChannelPanel = false; showMembers = true },
            )
        }

        // Frosted composer, hugging the keyboard.
        if (!buffer.isSystem) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { bottomBarHeightPx = it.height }
                    .hazeEffect(hazeState, style = glassStyle())
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                // Completion: the word under the cursor drives a suggestion strip —
                // nicks in channels, /commands at line start. No Tab key on mobile.
                val word = Completion.wordAt(draft.text, draft.selection.start)
                val replying = client.draftReplies[buffer.key]
                val suggestions = remember(
                    draft.text, draft.selection.start,
                    client.members[buffer.key], client.buffers.size, client.chanlistRows.size,
                ) {
                    when {
                        word.text.startsWith("/") && word.start == 0 ->
                            Completion.commands(word.text, Completion.VERBS)
                        word.text.length >= 2 && (word.text.startsWith("#") || word.text.startsWith("&")) -> {
                            // Channels you're in first, then anything the /LIST browser pulled in.
                            val known = client.buffers.filter { it.isChannel }.map { it.target } +
                                client.chanlistRows.map { it.channel }
                            Completion.channels(word.text, known).take(12)
                        }
                        word.text.length >= 1 && !word.text.startsWith("/") -> {
                            val recents = (client.messagesByBuffer[buffer.key] ?: emptyList())
                                .asReversed().asSequence()
                                .filter { !it.system && it.nick.isNotBlank() }
                                .map { it.nick }.distinct().take(30).toList()
                            val roster = client.members[buffer.key]?.map { it.nick } ?: emptyList()
                            val self = buffer.networkId?.let { client.networks[it]?.nick }
                            Completion.nicks(word.text, recents, roster, self).take(12)
                        }
                        else -> emptyList()
                    }
                }
                fun applySuggestion(pick: String): TextFieldValue {
                    // The "nick, " ping form only applies to a nick opening the line —
                    // not commands or channel names.
                    val isChannel = pick.startsWith("#") || pick.startsWith("&")
                    val atLineStart = word.start == 0 && !pick.startsWith("/") && !isChannel
                    val insert = pick + if (atLineStart) ", " else " "
                    val before = draft.text.substring(0, word.start)
                    val after = draft.text.substring((word.start + word.text.length).coerceAtMost(draft.text.length))
                    val newText = before + insert + after
                    return TextFieldValue(newText, TextRange(before.length + insert.length))
                }
                Composer(
                    draft = draft,
                    onChange = { new ->
                        val hadText = draft.text.isNotBlank()
                        draft = new
                        client.setDraftLocal(buffer, new.text)
                        val hasText = new.text.isNotBlank()
                        // Commands never broadcast composing state.
                        if (hasText && !new.text.startsWith("/")) {
                            client.notifyTyping(buffer, active = true)
                        } else if (hadText && !hasText) {
                            client.notifyTyping(buffer, active = false)
                        }
                    },
                    target = buffer.displayName,
                    uploading = client.uploading,
                    onPickFile = { uploadPicker.launch("*/*") },
                    onTakePhoto = { launchCamera() },
                    suggestions = suggestions,
                    onPickSuggestion = { draft = applySuggestion(it); client.setDraftLocal(buffer, draft.text) },
                    // Long-press send = post exactly what's typed, no translation.
                    onSendVerbatim = {
                        val raw = draft.text.trim()
                        if (raw.isNotEmpty()) {
                            translateOriginal = null
                            translateFailed = false
                            dispatchSend(raw)
                        }
                    },
                    banner = if (translating || translateOriginal != null || replying != null) {
                        {
                            replying?.let { ReplyComposeBanner(it, onCancel = { cancelReply() }) }
                            if (translating || translateOriginal != null) Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (translating) {
                                    Text("Translating…", color = TextSecondary, fontSize = 12.sp)
                                } else {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Send again to post this translation",
                                            color = AccentBlue,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            "was: ${translateOriginal.orEmpty()}",
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    TextButton(onClick = {
                                        // Back to what they typed; the cycle restarts.
                                        val orig = translateOriginal.orEmpty()
                                        translateOriginal = null
                                        draft = TextFieldValue(orig, TextRange(orig.length))
                                        client.setDraftLocal(buffer, orig)
                                    }) { Text("Undo", color = TextSecondary, fontSize = 12.sp) }
                                }
                            }
                        }
                    } else null,
                ) {
                    val raw = draft.text.trim()
                    if (raw.isEmpty()) return@Composer
                    // Outgoing translation: translate -> preview -> approve. The user
                    // must SEE what they're about to say in a language they can't
                    // read, so the translation goes back in the composer and the next
                    // press sends it verbatim. Commands and /me pass straight through,
                    // and noise ("lol") skips the round trip entirely — waiting on a
                    // translator to send "lol" reads as "it just doesn't send".
                    val outLang = Translator.outgoingLang(buffer.key)
                    if (outLang != null && !raw.startsWith("/") &&
                        translateOriginal == null && !translateFailed && !isSkippable(raw)
                    ) {
                        translating = true
                        chatScope.launch {
                            val t = runCatching { Translator.translateNow(raw, outLang) }.getOrNull()
                            translating = false
                            when {
                                // A broken translator must never hold a message
                                // hostage: say so, and let the next press post as typed.
                                t == null -> {
                                    translateFailed = true
                                    client.localNotice(
                                        buffer,
                                        "Translation failed — press send again to post as typed.",
                                    )
                                }
                                // Already in that language: send now, no second press.
                                !isMeaningful(raw, t.text) -> dispatchSend(raw)
                                else -> {
                                    translateOriginal = raw
                                    draft = TextFieldValue(t.text, TextRange(t.text.length))
                                    client.setDraftLocal(buffer, t.text)
                                }
                            }
                        }
                        return@Composer
                    }
                    translateOriginal = null
                    translateFailed = false
                    dispatchSend(raw)
                }
            }
        }

        // Jump back to the tail after reading history. A compact circular button
        // (not a wide pill) with a count badge, so it covers as little chat as
        // possible — matters most in the dense compact layout (amiantos's note).
        if (!atBottom && rows.isNotEmpty()) {
            val scope = rememberCoroutineScope()
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 14.dp,
                        bottom = with(density) { bottomBarHeightPx.toDp() } + 14.dp,
                    ),
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(SurfaceRaised, CircleShape)
                        .border(0.5.dp, GlassBorder, CircleShape)
                        .clickable {
                            pinnedToTail = true
                            scope.launch {
                                listState.scrollToItem(maxOf(0, listState.layoutInfo.totalItemsCount - 1))
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("↓", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                }
                if (missed > 0) {
                    Text(
                        if (missed > 99) "99+" else "$missed",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 5.dp, y = (-5).dp)
                            .background(AccentBlue, CircleShape)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }

    if (showMembers) {
        MemberSheet(
            client = client,
            buffer = buffer,
            onDismiss = { showMembers = false },
            onOpenBuffer = { showMembers = false; onOpenBuffer(it) },
            onPickFileFor = { nick ->
                dccNick = nick
                showMembers = false
                filePicker.launch("*/*")
            },
        )
    }

    if (showTranslate) {
        TranslateSheet(
            bufferKey = buffer.key,
            bufferName = buffer.displayName,
            onDismiss = { showTranslate = false },
        )
    }
    if (showE2e) {
        E2eSheet(
            client = client,
            buffer = buffer,
            messages = messages,
            onDismiss = { showE2e = false },
        )
    }

    actionMsg?.let { m ->
        MessageActions(
            msg = m,
            client = client,
            onReact = reactOn(m)?.let { send -> { v: String -> send(v); actionMsg = null } },
            myReactions = client.reactions[m.id].orEmpty().filter { it.self }.map { it.value }.toSet(),
            onReply = {
                replyTo(m)
                actionMsg = null
            },
            onQuote = {
                val t = "> ${m.text}\n" + draft.text
                draft = TextFieldValue(t, TextRange(t.length))
                client.setDraftLocal(buffer, t)
                actionMsg = null
            },
            onWhois = if (buffer.networkId != null) {
                {
                    client.execute(buffer, listOf(WireOp("raw", line = "WHOIS ${m.nick}")))
                    actionMsg = null
                }
            } else null,
            onDismiss = { actionMsg = null },
        )
    }
}

/**
 * Encryption panel: the E2E scrollback (status/handshake lines) for this buffer
 * plus quick-action buttons that drive the server's /e2e subcommands. All crypto
 * is server-side; this is a control + status surface only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun E2eSheet(
    client: LurkerClient,
    buffer: Buffer,
    messages: List<Msg>,
    onDismiss: () -> Unit,
) {
    val lines = remember(messages.size) { messages.filter { it.type == "e2e" }.takeLast(60) }
    fun run(args: String) = client.execute(buffer, listOf(WireOp("e2e", target = buffer.target, line = args)))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = false,
        // Keep a touch of translucency but not so much the chat bleeds through and
        // hurts readability — only lifts the see-through Dark theme (~0.70), leaves
        // the already-near-opaque Oled/Light surfaces alone.
        containerColor = SurfaceDark.copy(alpha = SurfaceDark.alpha.coerceAtLeast(0.94f)),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                LockGlyph(color = OnlineGreen, size = 15.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Encryption — ${buffer.displayName}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
            Text(
                "End-to-end encryption is handled by your Lurker server. These are the handshake and status lines for this buffer.",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            // Four buttons need ~390dp at 2x in a 328dp column — wrap them.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { run("status") }) { Text("Status", fontSize = 13.sp) }
                TextButton(onClick = { run("list") }) { Text("Peers", fontSize = 13.sp) }
                TextButton(onClick = { run("fingerprint") }) { Text("My key", fontSize = 13.sp) }
                TextButton(onClick = { run("help") }) { Text("Help", fontSize = 13.sp) }
            }
            Text(
                "Type /e2e handshake <nick>, /e2e accept <nick>, /e2e verify <nick>, /e2e on in chat.",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            HorizontalDivider(color = SurfaceRaised)
            if (lines.isEmpty()) {
                Text(
                    "No E2E activity yet. Tap Status, or start a handshake from chat.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 380.dp).padding(top = 8.dp)) {
                    items(lines.size) { i ->
                        val m = lines[i]
                        Text(
                            m.text,
                            color = if (m.level == "warn") AlertRed else TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberSheet(
    client: LurkerClient,
    buffer: Buffer,
    onDismiss: () -> Unit,
    onOpenBuffer: (Buffer) -> Unit,
    onPickFileFor: (String) -> Unit,
) {
    var selected by remember { mutableStateOf<Member?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Fully expanded, and no drag-to-dismiss: a fast fling in the roster
        // list used to spill leftover velocity into the sheet gesture and yank
        // it closed. Close via scrim tap or back.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = false,
        // Keep a touch of translucency but not so much the chat bleeds through and
        // hurts readability — only lifts the see-through Dark theme (~0.70), leaves
        // the already-near-opaque Oled/Light surfaces alone.
        containerColor = SurfaceDark.copy(alpha = SurfaceDark.alpha.coerceAtLeast(0.94f)),
    ) {
        val roster = remember(client.members[buffer.key]) {
            (client.members[buffer.key] ?: emptyList())
                .sortedWith(compareBy({ it.rankFor(client.serverInfo[buffer.networkId ?: -1]?.prefixes) }, { it.nick.lowercase() }))
        }
        val sel = selected
        if (sel == null) {
            Text(
                "${buffer.displayName} — ${roster.size} member${if (roster.size == 1) "" else "s"}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )
            if (roster.isEmpty()) {
                Text(
                    "No member list yet — the server sends it when the channel is joined.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
            RosterList(roster, Modifier.heightIn(max = 460.dp), client.serverInfo[buffer.networkId ?: -1]?.prefixes) { selected = it }
        } else {
            MemberActions(
                client = client,
                buffer = buffer,
                member = sel,
                canModerate = client.myMember(buffer)?.canModerate == true,
                onBack = { selected = null },
                onDone = onDismiss,
                onOpenBuffer = onOpenBuffer,
                onPickFileFor = onPickFileFor,
            )
        }
    }
}

/** The channel roster rows, shared by the phone member sheet and the tablet
 *  right pane. Rank-prefix glyph, nick (dimmed when away), and an away tag. */
@Composable
private fun RosterList(
    roster: List<Member>,
    modifier: Modifier = Modifier,
    /** The network's PREFIX ladder, when it advertised one. */
    ladder: List<Pair<Char, Char>>? = null,
    onSelect: (Member) -> Unit,
) {
    LazyColumn(modifier) {
        items(roster.size) { i ->
            val m = roster[i]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(m) }
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    m.prefixFor(ladder).ifEmpty { " " },
                    // widthIn, not width: a two-symbol prefix ("@+") in a hard 20dp
                    // box wraps one symbol per line at a large font scale.
                    maxLines = 1,
                    softWrap = false,
                    color = if (m.canModerate) NoticeAmber else OnlineGreen,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.widthIn(min = 20.dp),
                )
                Text(
                    m.nick,
                    color = nickColor(m.nick).copy(alpha = if (m.away) 0.45f else 1f),
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                if (m.away) Text("away", color = TextSecondary, fontSize = 12.sp)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Persistent right-hand roster for the tablet three-pane layout. Same roster +
 *  nick-actions flow as [MemberSheet], but always-on rather than a modal sheet. */
@Composable
private fun MemberPane(
    client: LurkerClient,
    buffer: Buffer,
    onOpenBuffer: (Buffer) -> Unit,
    onPickFileFor: (String) -> Unit,
) {
    var selected by remember(buffer.key) { mutableStateOf<Member?>(null) }
    val roster = remember(client.members[buffer.key]) {
        (client.members[buffer.key] ?: emptyList())
            .sortedWith(compareBy({ it.rankFor(client.serverInfo[buffer.networkId ?: -1]?.prefixes) }, { it.nick.lowercase() }))
    }
    Column(
        Modifier.fillMaxSize()
            .background(SurfaceDark.copy(alpha = SurfaceDark.alpha.coerceAtLeast(0.94f)))
            .navigationBarsPadding()
            .padding(top = 12.dp),
    ) {
        val sel = selected
        if (sel == null) {
            Text(
                "${roster.size} member${if (roster.size == 1) "" else "s"}",
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp),
            )
            if (roster.isEmpty()) {
                Text(
                    "No member list yet — the server sends it when the channel is joined.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                )
            }
            RosterList(roster, Modifier.fillMaxSize(), client.serverInfo[buffer.networkId ?: -1]?.prefixes) { selected = it }
        } else {
            MemberActions(
                client = client,
                buffer = buffer,
                member = sel,
                canModerate = client.myMember(buffer)?.canModerate == true,
                onBack = { selected = null },
                onDone = { selected = null },
                onOpenBuffer = onOpenBuffer,
                onPickFileFor = onPickFileFor,
            )
        }
    }
}

@Composable
private fun MemberActions(
    client: LurkerClient,
    buffer: Buffer,
    member: Member,
    canModerate: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenBuffer: (Buffer) -> Unit,
    onPickFileFor: (String) -> Unit,
) {
    val networkId = buffer.networkId ?: return
    val nick = member.nick
    val chan = buffer.target
    var editNote by remember { mutableStateOf(false) }
    var noteText by remember(nick) { mutableStateOf(client.nickNote(networkId, nick) ?: "") }
    fun raw(line: String) {
        client.execute(buffer, listOf(WireOp("raw", line = line)))
        onDone()
    }

    if (editNote) {
        AlertDialog(
            onDismissRequest = { editNote = false },
            containerColor = SurfaceRaised,
            title = { Text("Note on $nick", color = TextPrimary) },
            text = {
                OutlinedTextField(noteText, { noteText = it }, placeholder = { Text("e.g. lives in Berlin") }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = { client.setNickNote(networkId, nick, noteText); editNote = false }) {
                    Text("Save", color = AccentBlue, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { editNote = false }) { Text("Cancel", color = TextSecondary) } },
        )
    }

    Column(Modifier.padding(bottom = 24.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp),
        ) {
            TextButton(onClick = onBack) { Text("‹", color = AccentBlue, fontSize = 22.sp) }
            Text(
                "${member.prefixFor(client.serverInfo[networkId]?.prefixes)}$nick",
                color = nickColor(nick),
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                maxLines = 1,
                softWrap = false,
            )
            member.host?.let {
                Spacer(Modifier.width(8.dp))
                // Hostmasks are long; at a large font scale the nick takes the row
                // and the host would wrap a character at a time. Truncate it.
                Text(
                    it,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        client.nickNote(networkId, nick)?.let { note ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 4.dp),
            ) {
                NoteGlyph(color = TextSecondary, size = 13.dp)
                Spacer(Modifier.width(6.dp))
                Text(note, color = TextSecondary, fontSize = 13.sp)
            }
        }
        SheetAction("Whois") { raw("WHOIS $nick") }
        SheetAction("Send a message") {
            onOpenBuffer(client.focusTarget(networkId, nick))
        }
        SheetAction(if (client.nickNote(networkId, nick) != null) "Edit note" else "Add note") { editNote = true }
        if (client.isFavorite(networkId, nick)) {
            SheetAction("Remove from Friends") { client.toggleFavorite(networkId, nick); onDone() }
        } else {
            SheetAction("Add to Friends") { client.addFriend(networkId, nick); onDone() }
        }
        // Mute this nick's mentions of you without hiding their messages — handy
        // for a bot that pings your name. A global NOHIGHLIGHT rule on their mask.
        SheetAction("Ignore highlights") {
            client.addIgnore(null, member.banMask, levels = listOf("NOHIGHLIGHT")); onDone()
        }
        SheetAction("Ignore (everything)", danger = true) {
            client.addIgnore(networkId, member.banMask, levels = listOf("ALL")); onDone()
        }
        // FORK-ONLY (outgoing DCC SEND): only when the server supports it.
        if (client.serverExtended) {
            SheetAction("DCC: send a file…") { onPickFileFor(nick) }
        }
        // DCC CHAT is upstream Lurker (/api/dcc/chat); the server answers 403 with
        // a clear reason when it isn't enabled for the account.
        if (client.serverFeatures) {
            SheetAction("DCC: start a chat") {
                client.dccChat(networkId, nick, open = true, focus = true)
                onDone()
            }
        }
        if (canModerate) {
            HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(vertical = 6.dp))
            // Rank grant/revoke driven by the network's PREFIX ladder — so a server
            // with halfop/admin/owner shows those too. Falls back to op+voice on a
            // vanilla server that didn't advertise ISUPPORT.
            val ranks: List<Char> = client.serverInfo[networkId]?.prefixes?.map { it.first }
                ?: listOf('o', 'v')
            ranks.forEach { m ->
                val has = m.toString() in member.modes
                val label = RANK_LABELS[m] ?: "+$m"
                SheetAction(if (has) "Take $label" else "Give $label") {
                    raw("MODE $chan ${if (has) "-" else "+"}$m $nick")
                }
            }
            SheetAction("Kick", danger = true) { raw("KICK $chan $nick") }
            SheetAction("Ban", danger = true) { raw("MODE $chan +b ${member.banMask}") }
            SheetAction("Kick + ban", danger = true) {
                client.execute(
                    buffer,
                    listOf(
                        WireOp("raw", line = "MODE $chan +b ${member.banMask}"),
                        WireOp("raw", line = "KICK $chan $nick"),
                    ),
                )
                onDone()
            }
        }
    }
}

@Composable
private fun SheetAction(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (danger) AlertRed else TextPrimary,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 12.dp),
    )
}

/** Long-press action sheet for a single message: copy / reply / quote / share / bookmark. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActions(
    msg: Msg,
    client: LurkerClient,
    onReply: () -> Unit,
    onQuote: () -> Unit,
    // Null on the :system: buffer, where there's no network to ask.
    onWhois: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    // IRCv3 reactions (Lurker 2.4+); null where this line can't take one.
    onReact: ((String) -> Unit)? = null,
    myReactions: Set<String> = emptySet(),
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var customReaction by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceDark,
    ) {
        Text(
            msg.text.replace("\n", " ").take(90).ifBlank { msg.nick },
            color = TextSecondary,
            fontSize = 13.sp,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 6.dp),
        )
        HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(vertical = 4.dp))
        if (onReact != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QUICK_REACTIONS.forEach { e ->
                    val mine = e in myReactions
                    Text(
                        e,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (mine) AccentBlue.copy(alpha = 0.35f) else Color.Transparent, CircleShape)
                            .clickable { onReact(e) }
                            .padding(7.dp),
                    )
                }
                Text(
                    "＋",
                    fontSize = 22.sp,
                    color = AccentBlue,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { customReaction = if (customReaction == null) "" else null }
                        .padding(7.dp),
                )
            }
            // Any short text works too — IRCv3 reactions aren't limited to emoji.
            customReaction?.let { cur ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = cur,
                        onValueChange = { customReaction = it.replace("\n", "") },
                        placeholder = { Text("React with anything…", fontSize = 14.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { onReact(cur.trim()) },
                        enabled = isValidReaction(cur.trim()),
                    ) { Text("React", color = AccentBlue, fontWeight = FontWeight.SemiBold) }
                }
            }
            HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(vertical = 4.dp))
        }
        SheetAction("Copy text") {
            clipboard.setText(AnnotatedString(msg.text))
            onDismiss()
        }
        SheetAction("Reply") { onReply() }
        SheetAction("Quote") { onQuote() }
        // Long-press → whois the sender; reply stays a short press (freakyy85).
        // Also covers "whois the person I'm in a query with", since their lines
        // are the ones you'd long-press there.
        if (onWhois != null && !msg.self && msg.nick.isNotBlank()) {
            SheetAction("Whois ${msg.nick}") { onWhois() }
        }
        SheetAction("Share…") {
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, msg.text)
            }
            context.startActivity(android.content.Intent.createChooser(send, null))
            onDismiss()
        }
        if (msg.id > 0) {
            SheetAction(if (client.isBookmarked(msg.id)) "Remove bookmark" else "Bookmark") {
                client.toggleBookmark(msg.id)
                onDismiss()
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Max file we'll buffer for a DCC send (the server's own ceiling is higher). */
/**
 * Resolve a content Uri to (displayName, streaming RequestBody). No size cap
 * and no buffering — the body streams straight from the content provider into
 * the socket, so a multi-GB file costs a few KB of memory.
 */
/**
 * Why an upload can't go out, or null when it can (lurker#627 / PR #648).
 *
 * Checked BEFORE a byte leaves the phone: without the server's advertised ceiling
 * a too-large file streamed all the way up over cellular only to come back a 413.
 * The advertised number already has the server's multipart headroom subtracted, so
 * the raw size is compared straight against it. Decimal MB (10^6) to match the
 * server's own 413 text, so the two can't contradict each other. Silent when the
 * server advertises nothing (older build) or the picker gave no size — then we
 * upload as before and let the 413 arbitrate.
 */
internal fun uploadTooLarge(sizeBytes: Long, maxUploadBytes: Long): String? {
    if (maxUploadBytes <= 0L || sizeBytes <= 0L || sizeBytes <= maxUploadBytes) return null
    fun mb(b: Long) = "%.1f".format(b / 1_000_000.0).removeSuffix(".0")
    return "That file is ${mb(sizeBytes)} MB — this server accepts up to ${mb(maxUploadBytes)} MB."
}

private fun readUpload(
    context: android.content.Context,
    uri: android.net.Uri,
    /** The server's maxStaticImageDimension (Lurker 2.4+), or 0 to send as-is.
     *  Only for chat uploads — never a DCC send, which must arrive byte-exact. */
    shrinkToPx: Int = 0,
): Pair<String, RequestBody>? {
    // Content URIs only (pickers, the camera's FileProvider, the share sheet).
    // A file:// path would be read with this app's own permissions.
    if (!uri.scheme.equals("content", ignoreCase = true)) return null
    if (shrinkToPx > 0) {
        val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
        if (mime == "image/jpeg" || mime == "image/png") shrinkingUpload(context, uri, mime, shrinkToPx)?.let { return it }
    }
    return try {
        var name = "file"
        var size = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) name = c.getString(nameIdx) ?: name
                val sizeIdx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
            }
        }
        // Probe readability now so failures surface as a message, not mid-stream.
        context.contentResolver.openInputStream(uri)?.close() ?: return null
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = size
            override fun writeTo(sink: BufferedSink) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    sink.writeAll(input.source())
                } ?: throw java.io.IOException("content no longer readable: $uri")
            }
        }
        name to body
    } catch (_: Exception) {
        null
    }
}

/**
 * A chat upload of a static JPEG/PNG, shrunk on the way out to the longest edge
 * the server keeps (`maxStaticImageDimension`). The server would downscale it
 * anyway, so sending a 48 MP photo whole only burns mobile data. The work runs
 * when OkHttp writes the body — on the upload thread, not the UI. Anything that
 * already fits, an animated PNG, or an image that won't decode goes up as-is.
 * Keeps the source format; the server's re-encode owns quality and metadata.
 * Length unknown up front (-1), so the size pre-check is left to the server.
 */
private fun shrinkingUpload(
    context: android.content.Context,
    uri: android.net.Uri,
    mime: String,
    maxPx: Int,
): Pair<String, RequestBody>? {
    var name = "image"
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i >= 0) name = c.getString(i) ?: name
        }
    }
    runCatching { context.contentResolver.openInputStream(uri)?.close() ?: return null }.getOrElse { return null }
    val body = object : RequestBody() {
        override fun contentType() = "application/octet-stream".toMediaType()
        override fun contentLength() = -1L
        override fun writeTo(sink: BufferedSink) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw java.io.IOException("content no longer readable: $uri")
            sink.write(shrinkImage(bytes, mime, maxPx) ?: bytes)
        }
    }
    return name to body
}

/** [bytes] re-encoded with its longest edge at [maxPx], or null to send the
 *  original (already fits, animated, or undecodable). Pure enough to unit-test. */
internal fun shrinkImage(bytes: ByteArray, mime: String, maxPx: Int): ByteArray? {
    if (maxPx <= 0) return null
    if (mime == "image/png" && isAnimatedPng(bytes)) return null
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0 || longest <= maxPx) return null
    return runCatching {
        // ImageDecoder applies the EXIF orientation, which a plain re-encode would drop.
        val bmp = android.graphics.ImageDecoder.decodeBitmap(
            android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes)),
        ) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = maxPx.toDouble() / maxOf(w, h)
            if (scale < 1.0) {
                decoder.setTargetSize(maxOf(1, (w * scale).roundToInt()), maxOf(1, (h * scale).roundToInt()))
            }
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val out = java.io.ByteArrayOutputStream()
        val format = if (mime == "image/png") android.graphics.Bitmap.CompressFormat.PNG else android.graphics.Bitmap.CompressFormat.JPEG
        bmp.compress(format, 95, out)
        bmp.recycle()
        out.toByteArray()
    }.getOrNull()
}

/** An APNG carries an `acTL` chunk before its first `IDAT`; resizing one would
 *  flatten the animation to a single frame. */
internal fun isAnimatedPng(bytes: ByteArray): Boolean {
    fun at(i: Int, tag: String) = i + 4 <= bytes.size && (0 until 4).all { bytes[i + it] == tag[it].code.toByte() }
    var i = 8 // after the PNG signature
    while (i + 8 <= bytes.size) {
        val len = ((bytes[i].toInt() and 0xFF) shl 24) or ((bytes[i + 1].toInt() and 0xFF) shl 16) or
            ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
        if (at(i + 4, "acTL")) return true
        if (at(i + 4, "IDAT")) return false
        if (len < 0) return false
        i += 12 + len
    }
    return false
}

@Composable
private fun MessageBubble(
    msg: Msg,
    first: Boolean,
    last: Boolean,
    baseSize: Int,
    onLink: ((String) -> Unit)? = null,
    onMediaLoaded: () -> Unit = {},
    onAction: ((Msg) -> Unit)? = null,
    onSwipeReply: ((Msg) -> Unit)? = null,
    onChannel: ((String) -> Unit)? = null,
    flash: Boolean = false,
    /** Buffer key for live translation, or null to always show the original. */
    translateKey: String? = null,
    // iMessage-style timestamp reveal. revealProvider returns the shared offset
    // (0 = hidden, negative = pulled left); onReveal feeds a left-drag delta into
    // it; onRevealEnd springs it back. Read as a lambda so the drag animates in the
    // layout phase without recomposing every bubble each frame.
    revealProvider: () -> Float = { 0f },
    revealMaxPx: Float = 0f,
    onReveal: (Float) -> Unit = {},
    onRevealEnd: () -> Unit = {},
    reactions: List<ReactionGroup> = emptyList(),
    onReact: ((String) -> Unit)? = null,
    onJumpTo: ((Long) -> Unit)? = null,
) {
    val self = msg.self
    // Null unless this buffer has translation on and the row is worth translating
    // (see rememberTranslation for the exclusions). Hoisted here because the badge
    // renders up by the nick, above the bubble body.
    val translation = rememberTranslation(msg, translateKey)
    // Swipe a bubble sideways to reply (reuses the long-press reply seam). The
    // live drag is a plain float (synchronous, no per-delta coroutine that could
    // land after the release); the Animatable only drives the spring back so the
    // bubble always returns to place.
    var dragPx by remember(msg.id) { mutableStateOf(0f) }
    val settleAnim = remember(msg.id) { androidx.compose.animation.core.Animatable(0f) }
    var settling by remember(msg.id) { mutableStateOf(false) }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val maxDragPx = with(density) { 72.dp.toPx() }
    val triggerPx = with(density) { 56.dp.toPx() }
    var firedThisDrag by remember(msg.id) { mutableStateOf(false) }
    val shownX = if (settling) settleAnim.value else dragPx
    // Grouping reads through the corners: the shared edge between messages of
    // one group is tightened, the outside stays fully rounded.
    val big = 18.dp
    val tight = 5.dp
    // Vertical rhythm scales with the chat font so small fonts don't read as
    // paradoxically airy (d3fc0n1's note): at the default baseSize 16 these
    // resolve to today's exact values (7 / 8 / 2), only the extremes shift.
    val vPad = (baseSize * 0.44f * Ui.densityScale).dp     // inner bubble top+bottom
    val groupTop = (baseSize * 0.5f * Ui.densityScale).dp  // gap above a new sender group
    val lineTop = (baseSize * 0.13f * Ui.densityScale).dp  // gap between lines in one group
    val shape = RoundedCornerShape(
        topStart = if (!self && !first) tight else big,
        topEnd = if (self && !first) tight else big,
        bottomStart = if (!self && !last) tight else big,
        bottomEnd = if (self && !last) tight else big,
    )
    // A single horizontal drag drives two behaviours, latched by initial direction:
    // right = swipe-to-reply (this bubble, local), left = reveal timestamps (all
    // bubbles, via the shared reveal offset). 0 undecided, 1 reply, -1 reveal.
    var dragMode by remember(msg.id) { mutableStateOf(0) }
    Box(Modifier.fillMaxWidth()) {
        // Per-message timestamp, parked just off the right edge and slid + faded in
        // as the bubbles pull left (iMessage). Shown for every row on reveal.
        if (revealMaxPx > 0f) {
            Text(
                formatTimeWithSeconds(msg.time) ?: "",
                color = if (msg.e2e) OnlineGreen else TextSecondary,
                fontSize = (baseSize - 2).sp,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .offset { IntOffset((revealMaxPx + revealProvider()).roundToInt(), 0) }
                    .graphicsLayer { alpha = (-revealProvider() / revealMaxPx).coerceIn(0f, 1f) },
            )
        }
        Column(
            horizontalAlignment = if (self) Alignment.End else Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset((shownX + revealProvider()).roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        if (dragMode == 0) dragMode = if (delta < 0f) -1 else 1
                        if (dragMode == -1) {
                            onReveal(delta)
                        } else if (onSwipeReply != null) {
                            val next = (dragPx + delta).coerceIn(0f, maxDragPx)
                            if (next >= triggerPx && !firedThisDrag) {
                                firedThisDrag = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            dragPx = next
                        }
                    },
                    onDragStopped = {
                        if (dragMode == -1) {
                            onRevealEnd()
                        } else if (onSwipeReply != null) {
                            val reached = dragPx >= triggerPx
                            settleAnim.snapTo(dragPx)
                            settling = true
                            if (reached) onSwipeReply(msg)
                            settleAnim.animateTo(0f)
                            dragPx = 0f
                            settling = false
                        }
                        firedThisDrag = false
                        dragMode = 0
                    },
                )
                .padding(
                    start = if (self) 64.dp else 12.dp,
                    end = if (self) 12.dp else 64.dp,
                    top = if (first) groupTop else lineTop,
                    bottom = 1.dp,
                ),
        ) {
        if (first && !self) {
            // Group header: nick (+ E2E lock). No inline time — timestamps are
            // hidden and revealed by swiping the timeline left (iMessage style).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, bottom = 2.dp),
            ) {
                Text(
                    msg.nick,
                    color = nickColor(msg.nick),
                    fontSize = (baseSize - 3).sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (msg.e2e) {
                    Spacer(Modifier.width(6.dp))
                    LockGlyph(color = OnlineGreen, size = (baseSize - 5).dp)
                }
            }
        }
        // An IRCv3 reply: the line it answers, quoted above the bubble.
        msg.replyTo?.let { r ->
            ReplyQuote(r, baseSize, onJumpTo, Modifier.padding(horizontal = 6.dp).padding(bottom = 2.dp))
        }
        // A message fully painted with one mIRC background becomes a bubble of
        // that color instead of colored stripes inside a gray bubble.
        val paintedBg = remember(msg.text) { Mirc.wholeMessageBg(msg.text)?.let { Color(it) } }
        // Highlight (a mention of you) gets a gold bubble; a notification jump
        // flashes the same gold so the eye lands on it. Never recolour your own
        // messages or a fully mIRC-painted one.
        val goldHighlight = !self && paintedBg == null && (msg.matched || flash)
        val highlightBg = if (Ui.highlightColor != 0) Color(Ui.highlightColor) else HighlightGold
        Box(
            Modifier
                .clip(shape)
                .background(
                    paintedBg ?: when {
                        goldHighlight -> highlightBg
                        self -> AccentBlue
                        else -> SurfaceRaised
                    },
                    shape,
                )
                // Long-press opens the message action sheet. onClick is a no-op so
                // link/media taps inside the text still reach their own handlers.
                .then(
                    if (onAction != null) {
                        Modifier.combinedClickable(onClick = {}, onLongClick = { onAction(msg) })
                    } else Modifier,
                )
                .padding(horizontal = 13.dp, vertical = vPad),
        ) {
            // mIRC parse + URL/channel regex is the per-row hot path — cache it so a
            // ChatScreen recomposition (typing, media load, unread ticks) doesn't
            // re-parse every visible bubble. Keyed on everything that changes the
            // output; the link/channel handlers are stable (remembered in ChatScreen),
            // so their nullability is the only relevant input.
            val linkColor = if (self) Color.White else AccentBlue
            // Live translation is a render-time OVERLAY: only the displayed text
            // changes. Everything that scans the message (media embeds, link
            // previews below) keeps reading msg.text, so a translator can't
            // re-spell a URL out from under them.
            val shown = translation?.text ?: msg.text
            val parsed = remember(shown, linkColor, onLink != null, onChannel != null) {
                mircAnnotated(shown, linkColor, onLink, onChannel)
            }
            // Badge INLINE, not in the group header: the header renders only on the
            // first message of a sender's run, so badging there left every
            // following translated message unmarked — a whole block of foreign
            // messages showed a single badge somewhere in the middle of it.
            val body = if (translation == null) parsed else buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = if (self) Color.White.copy(alpha = 0.75f) else AccentBlue,
                        fontSize = (baseSize - 4).sp,
                    ),
                ) { append("🌐" + (translation.lang ?: "") + " ") }
                append(parsed)
            }
            Text(
                if (msg.type == "error") buildAnnotatedString {
                    withStyle(SpanStyle(color = AlertRed)) { append(body) }
                } else body,
                color = when {
                    self -> Color.White
                    // Keep text readable on whatever highlight colour was chosen.
                    goldHighlight -> if (highlightBg.luminance() > 0.55f) Color(0xFF1C1C1E) else Color.White
                    msg.type == "notice" -> NoticeAmber
                    else -> TextPrimary
                },
                fontSize = baseSize.sp,
                fontFamily = Ui.chatFont,
            )
        }
        // Inline media thumbnails (device-local toggle). Tap → the full viewer via
        // the same onLink route that already handles media URLs.
        // NOT when the server resolves previews: those same URLs come back as
        // `image`/`video` descriptors and render in the block below, so running
        // this too drew every picture and clip TWICE (d3fc0n). Page links were
        // unaffected, which is why it only ever looked like a media bug.
        if (!ServerPreviews.enabled && Ui.inlineMedia && onLink != null) {
            val embeds = remember(msg.text) { mediaUrlsIn(msg.text) }
            embeds.forEach { (url, kind) ->
                when (kind) {
                    MediaKind.AUDIO -> InlineAudioPlayer(url)
                    MediaKind.VIDEO -> InlineVideoPlayer(url)
                    MediaKind.IMAGE -> MediaEmbed(url, onOpen = { onLink(url) }, onLoaded = onMediaLoaded)
                }
            }
        }
        // OpenGraph / YouTube preview cards for non-media links (own Settings toggles).
        // Lurker mode resolves previews SERVER-side (the reader never contacts the
        // linked host). Direct-IRC mode has no server, so it keeps the on-device
        // scraper — see ServerPreview.kt for why that distinction matters.
        if (onLink != null) {
            if (ServerPreviews.enabled) ServerPreviewCards(msg, onLink)
            else LinkPreviewCards(msg.text, onLink)
        }
        ReactionChips(reactions, onReact, alignEnd = self, modifier = Modifier.padding(top = 3.dp))
            // Timestamps are hidden here and revealed by swiping left (iMessage
            // style) — see the reveal overlay + drag above. Keeps the timeline
            // dense (d3fc0n1) while per-message times stay one gesture away.
        }
    }
}

/** Dense IRC-style single message row: "time nick  message", no bubble. The
 *  whole timeline reads as continuous text (freakyy85's compact mode). Preserves
 *  mIRC colours/links, highlights (subtle row tint), and inline media. */
@Composable
private fun CompactMessageRow(
    msg: Msg,
    baseSize: Int,
    onLink: ((String) -> Unit)? = null,
    onAction: ((Msg) -> Unit)? = null,
    onMediaLoaded: () -> Unit = {},
    flash: Boolean = false,
    onNickTap: (() -> Unit)? = null,
    onChannel: ((String) -> Unit)? = null,
    /** Buffer key for live translation, or null to always show the original. */
    translateKey: String? = null,
    reactions: List<ReactionGroup> = emptyList(),
    onReact: ((String) -> Unit)? = null,
    onJumpTo: ((Long) -> Unit)? = null,
) {
    val paintedBg = remember(msg.text) { Mirc.wholeMessageBg(msg.text)?.let { Color(it) } }
    val goldHighlight = !msg.self && paintedBg == null && (msg.matched || flash)
    val highlightBg = if (Ui.highlightColor != 0) Color(Ui.highlightColor) else HighlightGold
    val time = remember(msg.time, Ui.clock24h) { formatTime(msg.time) }
    val accent = AccentBlue
    val translation = rememberTranslation(msg, translateKey)
    val shown = translation?.text ?: msg.text
    // Compact mode is one continuous Text, so the badge rides inline as a glyph
    // rather than a separate composable.
    val badge = translation?.let { "🌐" + (it.lang ?: "") + " " } ?: ""
    // Cache the regex-heavy mIRC parse so a ChatScreen recomposition doesn't
    // re-parse every visible compact row (the buildAnnotatedString assembly below
    // is cheap; the parse is not).
    val body = remember(shown, accent, onLink != null, onChannel != null) {
        mircAnnotated(shown, accent, onLink, onChannel)
    }
    val line = buildAnnotatedString {
        if (time != null) {
            withStyle(SpanStyle(color = TextSecondary, fontSize = (baseSize - 4).sp)) { append("$time ") }
        }
        // Compact mode has no swipe-to-reply, so the nick itself is tappable to
        // drop a "nick, " ping into the composer (d3fc0n).
        val nickStyle = SpanStyle(color = nickColor(msg.nick), fontWeight = FontWeight.SemiBold)
        if (onNickTap != null) {
            withLink(LinkAnnotation.Clickable("nick", linkInteractionListener = { onNickTap() })) {
                withStyle(nickStyle) { append(msg.nick) }
            }
        } else {
            withStyle(nickStyle) { append(msg.nick) }
        }
        append("  ")
        if (badge.isNotEmpty()) {
            withStyle(SpanStyle(color = AccentBlue, fontSize = (baseSize - 4).sp)) { append(badge) }
        }
        when (msg.type) {
            "error" -> withStyle(SpanStyle(color = AlertRed)) { append(body) }
            "notice" -> withStyle(SpanStyle(color = NoticeAmber)) { append(body) }
            else -> append(body)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (goldHighlight) highlightBg.copy(alpha = 0.16f) else Color.Transparent)
            .then(if (onAction != null) Modifier.combinedClickable(onClick = {}, onLongClick = { onAction(msg) }) else Modifier)
            .padding(horizontal = 12.dp, vertical = (1.5f * Ui.densityScale).dp),
    ) {
        msg.replyTo?.let { r -> ReplyQuote(r, baseSize, onJumpTo, Modifier.padding(bottom = 1.dp)) }
        Text(line, fontSize = baseSize.sp, lineHeight = (baseSize + 3).sp, color = TextPrimary, fontFamily = Ui.chatFont)
        // NOT when the server resolves previews: those same URLs come back as
        // `image`/`video` descriptors and render in the block below, so running
        // this too drew every picture and clip TWICE (d3fc0n). Page links were
        // unaffected, which is why it only ever looked like a media bug.
        if (!ServerPreviews.enabled && Ui.inlineMedia && onLink != null) {
            val embeds = remember(msg.text) { mediaUrlsIn(msg.text) }
            embeds.forEach { (url, kind) ->
                when (kind) {
                    MediaKind.AUDIO -> InlineAudioPlayer(url)
                    MediaKind.VIDEO -> InlineVideoPlayer(url)
                    MediaKind.IMAGE -> MediaEmbed(url, onOpen = { onLink(url) }, onLoaded = onMediaLoaded)
                }
            }
        }
        // Lurker mode resolves previews SERVER-side (the reader never contacts the
        // linked host). Direct-IRC mode has no server, so it keeps the on-device
        // scraper — see ServerPreview.kt for why that distinction matters.
        if (onLink != null) {
            if (ServerPreviews.enabled) ServerPreviewCards(msg, onLink)
            else LinkPreviewCards(msg.text, onLink)
        }
        ReactionChips(reactions, onReact, alignEnd = false, modifier = Modifier.padding(top = 2.dp, bottom = 1.dp))
    }
}

/** The six one-tap reactions on a message's action sheet. */
private val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🎉")

/**
 * The line an IRCv3 reply answers, quoted small above it (Lurker 2.4+). Tap to
 * jump there. When the server no longer holds it, says so rather than vanishing.
 */
@Composable
private fun ReplyQuote(reply: ReplyRef, baseSize: Int, onJump: ((Long) -> Unit)?, modifier: Modifier = Modifier) {
    val p = reply.parent
    val text = remember(p) {
        if (p == null) "↪ a message that's no longer here"
        else "↪ ${p.nick}: " + Mirc.strip(p.text).replace('\n', ' ')
    }
    Text(
        text,
        color = TextSecondary,
        fontSize = (baseSize - 3).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceRaised.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .then(if (p != null && onJump != null) Modifier.clickable { onJump(p.id) } else Modifier)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * A line's IRCv3 reactions as chips: value + count, filled when one is yours.
 * Tap toggles yours (when the line can take one); long-press shows who reacted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReactionChips(
    groups: List<ReactionGroup>,
    onToggle: ((String) -> Unit)?,
    alignEnd: Boolean,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) return
    val context = LocalContext.current
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp, if (alignEnd) Alignment.End else Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        groups.forEach { g ->
            val shape = RoundedCornerShape(10.dp)
            Text(
                if (g.nicks.size > 1) "${g.value} ${g.nicks.size}" else g.value,
                color = if (g.mine) Color.White else TextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    // A long text reaction mustn't widen the row (upstream #1020).
                    .widthIn(max = 160.dp)
                    .clip(shape)
                    .background(if (g.mine) AccentBlue.copy(alpha = 0.85f) else SurfaceRaised, shape)
                    .border(0.5.dp, GlassBorder, shape)
                    .combinedClickable(
                        onClick = { onToggle?.invoke(g.value) },
                        onLongClick = {
                            android.widget.Toast.makeText(
                                context, "${g.value} — ${g.nicks.joinToString(", ")}", android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/** Above the composer while a reply is being written: who to, and ✕ to cancel. */
@Composable
private fun ReplyComposeBanner(reply: DraftReply, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "↪ Replying to " + (reply.parent?.let { "${it.nick}: " + Mirc.strip(it.text).replace('\n', ' ') } ?: "a message"),
            color = AccentBlue,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onCancel) { Text("✕", color = TextSecondary, fontSize = 14.sp) }
    }
}

/** Compact-mode CTCP ACTION ("* nick does thing"). */
@Composable
private fun CompactActionRow(msg: Msg, baseSize: Int, onLink: ((String) -> Unit)? = null) {
    val time = remember(msg.time, Ui.clock24h) { formatTime(msg.time) }
    val accent = AccentBlue
    val body = remember(msg.text, accent, onLink != null) { mircAnnotated(msg.text, accent, onLink) }
    val line = buildAnnotatedString {
        if (time != null) {
            withStyle(SpanStyle(color = TextSecondary, fontSize = (baseSize - 4).sp)) { append("$time ") }
        }
        withStyle(SpanStyle(color = NoticeAmber, fontStyle = FontStyle.Italic)) {
            append("* ")
            withStyle(SpanStyle(color = nickColor(msg.nick), fontWeight = FontWeight.SemiBold)) { append(msg.nick) }
            append(" ")
            append(body)
        }
    }
    Text(
        line,
        fontSize = baseSize.sp,
        lineHeight = (baseSize + 3).sp,
        fontFamily = Ui.chatFont,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = (1.5f * Ui.densityScale).dp),
    )
}

@Composable
private fun ActionLine(msg: Msg, baseSize: Int = 16, onLink: ((String) -> Unit)? = null) {
    val accent = AccentBlue
    val body = remember(msg.text, accent, onLink != null) { mircAnnotated(msg.text, accent, onLink) }
    val line = buildAnnotatedString {
        withStyle(SpanStyle(color = TextSecondary, fontStyle = FontStyle.Italic)) { append("* ") }
        withStyle(
            SpanStyle(color = nickColor(msg.nick), fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold),
        ) { append(msg.nick) }
        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(" ") ; append(body) }
    }
    Text(
        line,
        fontSize = (baseSize - 2).sp,
        color = TextPrimary,
        // Vertical padding tracks the font (default 16 → 5.dp), matching bubbles.
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = (baseSize * 0.31f).dp),
    )
}

/**
 * An undelivered message. Shows the text you actually typed (not a truncated
 * notice) in a red-edged card with why it failed, and the two things you'd want:
 * send it again, or take it back into the composer to edit. Sits inline where the
 * message would have appeared.
 */
@Composable
private fun FailedSendRow(
    failed: FailedSend,
    baseSize: Int,
    onResend: () -> Unit,
    onDiscard: () -> Unit,
    onCopyToComposer: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AlertRed.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                .border(0.5.dp, AlertRed.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚠", color = AlertRed, fontSize = (baseSize - 3).sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Not delivered — ${failed.reason}",
                    color = AlertRed,
                    fontSize = (baseSize - 4).sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                failed.text,
                color = TextPrimary,
                fontSize = baseSize.sp,
                fontFamily = Ui.chatFont,
            )
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PanelActionChip("Resend", onResend)
                PanelActionChip("Edit", onCopyToComposer)
                PanelActionChip("Discard", onDiscard)
            }
        }
    }
}

/**
 * The Multi-channel firehose: one read-only view that merges the timelines of every
 * channel toggled into it (from each channel's control panel). Each line is tagged
 * with its source — network · channel — and tapping it jumps to that channel and
 * flashes the exact message (reusing the search-result scroll seam). Renders as
 * compact rows or bubbles per [Ui.multichanCompact], flipped from the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultichanScreen(
    client: LurkerClient,
    onBack: () -> Unit,
    onOpenSource: (Buffer, Long) -> Unit,
) {
    BackHandler(onBack = onBack)
    val hazeState = remember { HazeState() }
    val density = LocalDensity.current
    val context = LocalContext.current
    var topBarHeightPx by remember { mutableStateOf(0) }
    val uriHandler = LocalUriHandler.current
    val openLink: (String) -> Unit = { url ->
        val u = if (url.startsWith("http", ignoreCase = true)) url else "https://$url"
        runCatching { uriHandler.openUri(u) }
    }
    val baseSize = (client.settingInt("look.font.size.mobile", 14) + 2 + Ui.chatTextScale).coerceIn(11, 30)

    // Merge every included buffer's messages, tagged with source, oldest→newest.
    // Read the snapshot state directly (no remember) so freshly-arrived messages in
    // any source channel recompose the feed. IDs are per-network and not comparable
    // across networks, so we sort by server-time (ISO-8601 sorts chronologically);
    // lines without a time (rare local notices) sort to the top.
    val feed = buildList {
        Ui.multichan.forEach { key ->
            val buf = client.buffers.find { it.key == key } ?: return@forEach
            val msgs = client.messagesByBuffer[key] ?: return@forEach
            msgs.asReversed().take(250).forEach { m ->
                if (!m.system && m.text.isNotBlank()) add(buf to m)
            }
        }
    }.sortedBy { it.second.time ?: "" }.takeLast(500)

    val listState = rememberLazyListState()
    var pinnedOnce by remember { mutableStateOf(false) }
    // Start pinned to the newest line; thereafter follow the tail while the user is
    // already near the bottom, but don't yank them down if they've scrolled up.
    LaunchedEffect(feed.size) {
        if (feed.isEmpty()) return@LaunchedEffect
        val atBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= feed.size - 3 } ?: true
        if (!pinnedOnce || (atBottom && !listState.isScrollInProgress)) {
            listState.scrollToItem(feed.lastIndex)
            pinnedOnce = true
        }
    }

    Box(Modifier.fillMaxSize().background(CanvasBlack)) {
        AmbientBackground(Modifier.hazeSource(hazeState, zIndex = 0f))
        if (feed.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (Ui.multichan.isEmpty()) "No channels added yet.\nToggle \"Add to Multi-channel\" in a channel's control panel."
                    else "No messages yet in the merged channels.",
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().navigationBarsPadding().hazeSource(hazeState, zIndex = 1f),
                contentPadding = PaddingValues(top = with(density) { topBarHeightPx.toDp() } + 4.dp, bottom = 8.dp),
            ) {
                items(feed.size) { i ->
                    val (buf, m) = feed[i]
                    val netName = buf.networkId?.let { client.networkName(it) } ?: buf.networkName
                    val label = "$netName · ${buf.displayName}"
                    if (Ui.multichanCompact) {
                        MultichanCompactRow(m, label, baseSize, openLink) { onOpenSource(buf, m.id) }
                    } else {
                        MultichanBubbleRow(m, label, baseSize, openLink) { onOpenSource(buf, m.id) }
                    }
                }
            }
        }

        // Frosted top bar: back, title, and the compact/bubble mode flip.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { topBarHeightPx = it.height }
                .hazeEffect(hazeState, style = glassStyle()),
        ) {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹", color = AccentBlue, fontSize = 26.sp) }
                },
                title = { Text("Multi-channel", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                actions = {
                    TextButton(onClick = {
                        Ui.multichanCompact = !Ui.multichanCompact
                        Prefs(context).multichanCompact = Ui.multichanCompact
                    }) {
                        Text(if (Ui.multichanCompact) "Bubbles" else "Compact", color = AccentBlue, fontSize = 14.sp)
                    }
                },
            )
        }
    }
}

/** A merged-view bubble: source header (network · channel · nick, tappable) over the
 *  message body. Always received-style (left, no self colouring) — it's a reader. */
@Composable
private fun MultichanBubbleRow(
    msg: Msg,
    sourceLabel: String,
    baseSize: Int,
    onLink: ((String) -> Unit)?,
    onOpen: () -> Unit,
) {
    val vPad = (baseSize * 0.44f).dp
    Column(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 64.dp, top = (baseSize * 0.5f).dp, bottom = 1.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onOpen)
                .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        ) {
            // "network · channel" is long, and the bubble column is inset 76dp, so
            // a large font scale squeezes the nick to nothing and it wraps a letter
            // per line — on EVERY row. Truncate the source, keep the nick whole:
            // the nick is the part you're scanning for.
            Text(
                sourceLabel,
                color = AccentBlue,
                fontSize = (baseSize - 4).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text("  ·  ", color = TextSecondary, fontSize = (baseSize - 4).sp, maxLines = 1, softWrap = false)
            Text(
                msg.nick,
                color = nickColor(msg.nick),
                fontSize = (baseSize - 3).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
        val shape = RoundedCornerShape(18.dp)
        Box(
            Modifier
                .clip(shape)
                .background(if (msg.matched) HighlightGold else SurfaceRaised, shape)
                .padding(horizontal = 13.dp, vertical = vPad),
        ) {
            val accent = AccentBlue
            val body = remember(msg.text, accent, onLink != null) { mircAnnotated(msg.text, accent, onLink) }
            Text(
                body,
                color = if (msg.type == "notice") NoticeAmber else TextPrimary,
                fontSize = baseSize.sp,
                fontFamily = Ui.chatFont,
            )
        }
        // NOT when the server resolves previews: those same URLs come back as
        // `image`/`video` descriptors and render in the block below, so running
        // this too drew every picture and clip TWICE (d3fc0n). Page links were
        // unaffected, which is why it only ever looked like a media bug.
        if (!ServerPreviews.enabled && Ui.inlineMedia && onLink != null) {
            val embeds = remember(msg.text) { mediaUrlsIn(msg.text) }
            embeds.forEach { (url, kind) ->
                when (kind) {
                    MediaKind.AUDIO -> InlineAudioPlayer(url)
                    MediaKind.VIDEO -> InlineVideoPlayer(url)
                    MediaKind.IMAGE -> MediaEmbed(url, onOpen = { onLink(url) }, onLoaded = {})
                }
            }
        }
        // Lurker mode resolves previews SERVER-side (the reader never contacts the
        // linked host). Direct-IRC mode has no server, so it keeps the on-device
        // scraper — see ServerPreview.kt for why that distinction matters.
        if (onLink != null) {
            if (ServerPreviews.enabled) ServerPreviewCards(msg, onLink)
            else LinkPreviewCards(msg.text, onLink)
        }
    }
}

/** A merged-view compact row: "time  network · channel · nick  message". The whole
 *  row is tappable to jump to that channel + message. */
@Composable
private fun MultichanCompactRow(
    msg: Msg,
    sourceLabel: String,
    baseSize: Int,
    onLink: ((String) -> Unit)?,
    onOpen: () -> Unit,
) {
    val time = remember(msg.time, Ui.clock24h) { formatTime(msg.time) }
    val accent = AccentBlue
    val body = remember(msg.text, accent, onLink != null) { mircAnnotated(msg.text, accent, onLink) }
    val line = buildAnnotatedString {
        if (time != null) {
            withStyle(SpanStyle(color = TextSecondary, fontSize = (baseSize - 4).sp)) { append("$time ") }
        }
        withStyle(SpanStyle(color = AccentBlue, fontWeight = FontWeight.SemiBold)) { append(sourceLabel) }
        withStyle(SpanStyle(color = TextSecondary)) { append("  ·  ") }
        withStyle(SpanStyle(color = nickColor(msg.nick), fontWeight = FontWeight.SemiBold)) { append(msg.nick) }
        append("  ")
        when (msg.type) {
            "error" -> withStyle(SpanStyle(color = AlertRed)) { append(body) }
            "notice" -> withStyle(SpanStyle(color = NoticeAmber)) { append(body) }
            else -> append(body)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (msg.matched) HighlightGold.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 1.5.dp),
    ) {
        Text(line, fontSize = baseSize.sp, lineHeight = (baseSize + 3).sp, color = TextPrimary, fontFamily = Ui.chatFont)
        // NOT when the server resolves previews: those same URLs come back as
        // `image`/`video` descriptors and render in the block below, so running
        // this too drew every picture and clip TWICE (d3fc0n). Page links were
        // unaffected, which is why it only ever looked like a media bug.
        if (!ServerPreviews.enabled && Ui.inlineMedia && onLink != null) {
            val embeds = remember(msg.text) { mediaUrlsIn(msg.text) }
            embeds.forEach { (url, kind) ->
                when (kind) {
                    MediaKind.AUDIO -> InlineAudioPlayer(url)
                    MediaKind.VIDEO -> InlineVideoPlayer(url)
                    MediaKind.IMAGE -> MediaEmbed(url, onOpen = { onLink(url) }, onLoaded = {})
                }
            }
        }
        // Lurker mode resolves previews SERVER-side (the reader never contacts the
        // linked host). Direct-IRC mode has no server, so it keeps the on-device
        // scraper — see ServerPreview.kt for why that distinction matters.
        if (onLink != null) {
            if (ServerPreviews.enabled) ServerPreviewCards(msg, onLink)
            else LinkPreviewCards(msg.text, onLink)
        }
    }
}

private val CHANNEL_RE = Regex("""#[^\s,]+""")

// IMAGE_EXTS/VIDEO_EXTS/AUDIO_EXTS live in Media.kt now (shared with embeds).
private val MEDIA_EXTS = VIDEO_EXTS + AUDIO_EXTS

/** One shared animated image loader for all inline embeds (GIF/WebP capable). */

@Composable
private fun rememberEmbedLoader(): coil.ImageLoader {
    // The app-wide loader (LurkerApp.newImageLoader) — one shared 256 MB disk
    // cache so inline media survives scrolls, buffer switches, and relaunches.
    val context = LocalContext.current
    return remember { context.imageLoader }
}

/**
 * An inline image card in a chat bubble. Takes the picture's own aspect ratio
 * (scaled to fit, never cropped), bounded so a tall portrait can't run away;
 * only extreme aspects letterbox. The card resizes when the image loads, so
 * [onLoaded] lets the chat re-pin to the tail — that keeps the tail-follow /
 * load-older anchoring stable despite the size change.
 */
@Composable
private fun MediaEmbed(url: String, onOpen: () -> Unit, onLoaded: () -> Unit = {}) {
    // Real aspect once known; a landscape default reserves sane space first.
    var ratio by remember(url) { mutableStateOf<Float?>(null) }
    BoxWithConstraints(Modifier.padding(top = 6.dp).fillMaxWidth()) {
        val h = (maxWidth / (ratio ?: 1.6f)).coerceIn(80.dp, 360.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(h)
                .clip(RoundedCornerShape(12.dp))
                .background(CanvasBlack)
                .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).build(),
                imageLoader = rememberEmbedLoader(),
                contentDescription = "Shared image",
                contentScale = ContentScale.Fit,
                onSuccess = { st ->
                    val d = st.result.drawable
                    if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                        ratio = d.intrinsicWidth.toFloat() / d.intrinsicHeight
                    }
                    onLoaded()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Inline video player in a chat bubble. Shows a 16:9 poster with a ▶ until
 * tapped, then plays in place with ExoPlayer's standard transport controls —
 * no full-screen hop. Created lazily and released on dispose, so a video link
 * costs nothing until the user actually plays it.
 */
@Composable
private fun InlineVideoPlayer(url: String) {
    var started by remember(url) { mutableStateOf(false) }
    Box(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .aspectRatio(1.78f)
            .heightIn(max = 300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CanvasBlack)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
            .then(if (!started) Modifier.clickable { started = true } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (!started) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("▶", color = Color.White, fontSize = 40.sp)
                Text(
                    url.substringAfterLast('/').substringBefore('?').take(40),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp),
                )
            }
        } else {
            val context = LocalContext.current
            val player = remember(url) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                    playWhenReady = true
                }
            }
            DisposableEffect(player) { onDispose { player.release() } }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Compact inline audio player for a chat bubble. Fixed height keeps the chat
 * list's tail-follow / load-older anchoring stable (see [MediaEmbed]). The
 * ExoPlayer is created lazily and only prepared on the first ▶, so audio links
 * don't fetch until the user actually plays them. Tap the bar to seek.
 */
@Composable
private fun InlineAudioPlayer(url: String) {
    val context = LocalContext.current
    val player = remember(url) { ExoPlayer.Builder(context).build() }
    var prepared by remember(url) { mutableStateOf(false) }
    var isPlaying by remember(url) { mutableStateOf(false) }
    var positionMs by remember(url) { mutableStateOf(0L) }
    var durationMs by remember(url) { mutableStateOf(0L) }
    // Surface load/decode failures instead of leaving the button silently stuck at
    // ▶ 0:00 (which read as "the play button doesn't work"). Tapping again retries.
    var error by remember(url) { mutableStateOf<String?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && player.duration > 0) durationMs = player.duration
                if (state == Player.STATE_ENDED) { player.seekTo(0); player.playWhenReady = false }
            }
            override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                DebugLog.e("audio", "play failed for $url: ${e.errorCodeName}: ${e.message}")
                error = "couldn't play"
                prepared = false // let a re-tap rebuild the pipeline and retry
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    // Poll position only while playing — no work when paused/idle.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = player.currentPosition
            if (durationMs <= 0 && player.duration > 0) durationMs = player.duration
            delay(400)
        }
    }
    fun mmss(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }
    val frac = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Row(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CanvasBlack)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A generous 44dp round tap target (the bare glyph was a small, easy-to-miss
        // hit box). Clears any prior error and retries on tap.
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable {
                    error = null
                    if (!prepared) {
                        player.setMediaItem(MediaItem.fromUri(url))
                        player.prepare()
                        prepared = true
                    }
                    if (player.isPlaying) player.pause() else player.play()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (error != null) "↻" else if (isPlaying) "⏸" else "▶",
                color = if (error != null) AlertRed else AccentBlue,
                fontSize = 22.sp,
            )
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                url.substringAfterLast('/').substringBefore('?').take(40),
                color = TextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
            )
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .pointerInput(durationMs) {
                        detectTapGestures { off ->
                            if (durationMs > 0) {
                                val f = (off.x / size.width).coerceIn(0f, 1f)
                                val target = (f * durationMs).toLong()
                                player.seekTo(target)
                                positionMs = target
                            }
                        }
                    },
            ) {
                LinearProgressIndicator(
                    progress = { frac },
                    color = AccentBlue,
                    trackColor = SurfaceRaised,
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                )
            }
        }
        Text(
            error ?: if (durationMs > 0) "${mmss(positionMs)} / ${mmss(durationMs)}" else mmss(positionMs),
            color = if (error != null) AlertRed else TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/**
 * Full-screen in-app media viewer. Images: pinch-zoom + pan, GIFs animate.
 * Video/audio: embedded ExoPlayer with the standard controls. Escape hatches:
 * open in browser, or ✕ / back to close.
 */
@Composable
private fun MediaViewerDialog(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clean = url.substringBefore('?').substringBefore('#').lowercase()
    val isImage = IMAGE_EXTS.any { clean.endsWith(it) }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f)),
        ) {
            if (isImage) {
                val loader = context.imageLoader // shared app-wide disk cache
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                AsyncImage(
                    model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                    imageLoader = loader,
                    contentDescription = "Shared image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                offset = if (scale > 1f) offset + pan else Offset.Zero
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            } else {
                // Video and audio share ExoPlayer; audio just shows the controls.
                val player = remember {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(url))
                        prepare()
                        playWhenReady = true
                    }
                }
                DisposableEffect(Unit) {
                    onDispose { player.release() }
                }
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp),
            ) {
                TextButton(onClick = { uriHandler.openUri(url) }) {
                    Text("Browser", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                }
                TextButton(onClick = onClose) {
                    Text("✕", color = Color.White, fontSize = 20.sp)
                }
            }
        }
    }
}

/** iMessage-style "someone is composing" bubble with three breathing dots. */
@Composable
private fun TypingBubble(nicks: List<String>) {
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 64.dp, top = 8.dp, bottom = 2.dp),
    ) {
        // Each nick keeps ITS OWN colour, the same one it has in the message list
        // and the roster. Joining the names into one string painted them all with
        // the FIRST nick's colour, so "d3fc0n, freakyy85" showed a green freakyy85
        // — the one place in the app where a nick's colour lied about who it was.
        val line = remember(nicks, Ui.nickColors) {
            buildAnnotatedString {
                nicks.forEachIndexed { i, nick ->
                    if (i > 0) withStyle(SpanStyle(color = TextSecondary)) { append(", ") }
                    withStyle(SpanStyle(color = nickColor(nick))) { append(nick) }
                }
            }
        }
        Text(
            line,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 14.dp, bottom = 2.dp),
        )
        Box(
            Modifier
                .background(SurfaceRaised, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            val transition = rememberInfiniteTransition(label = "typing")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { i ->
                    val alpha by transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(i * 160),
                        ),
                        label = "dot$i",
                    )
                    Box(Modifier.size(8.dp).background(TextSecondary.copy(alpha = alpha), CircleShape))
                }
            }
        }
    }
}

@Composable
private fun SystemLine(msg: Msg, onJoin: ((String) -> Unit)? = null) {
    if (msg.type == "e2e") {
        // Encryption status / handshake line: an "E2E" tag, green for info,
        // red for warn — mirrors the web client's .p-e2e styling.
        val warn = msg.level == "warn"
        val line = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = if (warn) AlertRed else OnlineGreen,
                    fontWeight = FontWeight.Bold,
                ),
            ) { append("E2E  ") }
            append(msg.text)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 4.dp)
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .padding(12.dp, 7.dp),
        ) {
            LockGlyph(color = if (warn) AlertRed else OnlineGreen, size = 13.dp)
            Spacer(Modifier.width(6.dp))
            Text(line, fontSize = 12.sp, color = if (warn) AlertRed else TextSecondary)
        }
        return
    }
    if (msg.type == "system") {
        // :system: log lines read like a terminal: left-aligned, level-tinted.
        Text(
            msg.text,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = when (msg.level) {
                "error" -> AlertRed
                "warn" -> NoticeAmber
                else -> TextSecondary
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp, 3.dp),
        )
        return
    }
    if (msg.type == "error") {
        // Server-voice failure ("gnat isn't on this network.", a rejected
        // command). Red like a failed send, left-aligned because these read as
        // sentences and can run long; through the mIRC renderer since ircd
        // text occasionally carries format codes.
        Text(
            mircAnnotated(msg.text, AccentBlue),
            fontSize = 13.sp,
            color = AlertRed,
            modifier = Modifier.fillMaxWidth().padding(24.dp, 6.dp),
        )
        return
    }
    if (msg.type == "send-failed") {
        Text(
            msg.text,
            fontSize = 13.sp,
            color = AlertRed,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(24.dp, 6.dp),
        )
        return
    }
    if (msg.type == "whois") {
        // WHOIS blocks read like terminal output: left-aligned, monospace.
        // Channel names are tappable (join prompt) when a handler is provided.
        val body = if (onJoin != null) {
            buildAnnotatedString {
                append(msg.text)
                for (m in CHANNEL_RE.findAll(msg.text)) {
                    addLink(
                        LinkAnnotation.Clickable(
                            m.value,
                            TextLinkStyles(SpanStyle(color = AccentBlue)),
                        ) { onJoin(m.value) },
                        m.range.first,
                        m.range.last + 1,
                    )
                }
            }
        } else {
            AnnotatedString(msg.text)
        }
        Text(
            body,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = TextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .padding(12.dp, 8.dp),
        )
        return
    }
    // Through the mIRC renderer: topics regularly carry color/format codes,
    // which as plain text leak their digits ("09[Game Online]…").
    // A topic can wrap to several lines — keep its OWN line spacing tight and set
    // the block apart with padding above/below instead (freakyy85), rather than
    // an airy line-height spreading its wrapped lines out.
    val isTopic = msg.type == "topic"
    Text(
        mircAnnotated(msg.text, AccentBlue),
        fontSize = 12.sp,
        fontStyle = FontStyle.Italic,
        lineHeight = 15.sp,
        color = TextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = if (isTopic) 10.dp else 3.dp),
    )
}

@Composable
private fun NewMessagesDivider() {
    Text(
        "New messages",
        color = AlertRed,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
    )
}

/**
 * Wrap the selection in mIRC control codes, or insert the opening code at the
 * cursor (mIRC toggle semantics make a lone opener valid — it formats what's
 * typed after it).
 */
private fun applyFormat(v: TextFieldValue, code: String, end: String = code): TextFieldValue {
    val sel = v.selection
    return if (sel.collapsed) {
        val at = sel.start.coerceIn(0, v.text.length)
        TextFieldValue(
            v.text.substring(0, at) + code + v.text.substring(at),
            TextRange(at + code.length),
        )
    } else {
        val lo = minOf(sel.start, sel.end)
        val hi = maxOf(sel.start, sel.end)
        TextFieldValue(
            v.text.substring(0, lo) + code + v.text.substring(lo, hi) + end + v.text.substring(hi),
            TextRange(hi + code.length + end.length),
        )
    }
}

@Composable
private fun Composer(
    draft: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    target: String,
    uploading: Boolean = false,
    onPickFile: (() -> Unit)? = null,
    onTakePhoto: (() -> Unit)? = null,
    suggestions: List<String> = emptyList(),
    onPickSuggestion: (String) -> Unit = {},
    /** Long-press send: post EXACTLY what was typed, skipping translation. */
    onSendVerbatim: (() -> Unit)? = null,
    /** Shown above the input while a translation waits for approval. */
    banner: (@Composable () -> Unit)? = null,
    // Trailing lambda — must stay LAST so the call site keeps its `) { … }` form.
    onSend: () -> Unit,
) {
    var showFormat by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (suggestions.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(suggestions.size) { i ->
                    val s = suggestions[i]
                    Text(
                        s,
                        color = AccentBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .background(SurfaceRaised, RoundedCornerShape(14.dp))
                            .border(0.5.dp, GlassBorder, RoundedCornerShape(14.dp))
                            .clickable { onPickSuggestion(s) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        if (showFormat) FormatBar(draft, onChange)
        banner?.invoke()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Compact glyph buttons (not TextButtons) so the input gets the room.
            // The + opens a small menu: take a photo, or pick an existing file.
            if (onPickFile != null || onTakePhoto != null) {
                var showAttachMenu by remember { mutableStateOf(false) }
                Box {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape)
                            .clickable(enabled = !uploading) { showAttachMenu = true }
                            .semantics { contentDescription = "Attach"; role = Role.Button },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (uploading) "…" else "+",
                            color = if (uploading) TextSecondary else AccentBlue,
                            fontSize = 24.sp,
                        )
                    }
                    AppDropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                        if (onTakePhoto != null) {
                            DropdownMenuItem(
                                text = { Text("Take photo", color = TextPrimary) },
                                leadingIcon = { CameraGlyph(color = TextPrimary, size = 18.dp) },
                                onClick = { showAttachMenu = false; onTakePhoto() },
                            )
                        }
                        if (onPickFile != null) {
                            DropdownMenuItem(
                                text = { Text("Choose file", color = TextPrimary) },
                                leadingIcon = { FolderGlyph(color = TextPrimary, size = 18.dp) },
                                onClick = { showAttachMenu = false; onPickFile() },
                            )
                        }
                    }
                }
            }
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .clickable { showFormat = !showFormat }
                    .semantics { contentDescription = "Text formatting"; role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Aa",
                    color = if (showFormat) AccentBlue else TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }
            TextField(
                value = draft,
                onValueChange = onChange,
                placeholder = { Text("Message $target", color = TextSecondary) },
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = AccentBlue,
                ),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
            )
            val canSend = draft.text.isNotBlank()
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .combinedClickable(
                        enabled = canSend,
                        onClick = onSend,
                        onLongClick = onSendVerbatim,
                    )
                    .semantics { contentDescription = "Send message"; role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                SendGlyph(color = if (canSend) AccentBlue else TextSecondary, size = 19.dp)
            }
        }
    }
}

@Composable
private fun FormatBar(draft: TextFieldValue, onChange: (TextFieldValue) -> Unit) {
    var showColors by remember { mutableStateOf(false) }
    // The last text color picked this session: a fill pick pairs with it, since
    // mIRC syntax can't set a background without a foreground.
    var lastFg by remember { mutableStateOf<Int?>(null) }
    var fillMode by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Which toggles are "open" at the cursor, so the buttons light up while
        // you're typing bold/italic/etc. (you can't see the invisible codes).
        val open = Fmt.openTogglesAt(draft.text, draft.selection.start)
        FormatKey("B", bold = true, active = Fmt.BOLD in open) { onChange(applyFormat(draft, Fmt.BOLD)) }
        FormatKey("I", italic = true, active = Fmt.ITALIC in open) { onChange(applyFormat(draft, Fmt.ITALIC)) }
        FormatKey("U", underline = true, active = Fmt.UNDERLINE in open) { onChange(applyFormat(draft, Fmt.UNDERLINE)) }
        FormatKey("S", strike = true, active = Fmt.STRIKE in open) { onChange(applyFormat(draft, Fmt.STRIKE)) }
        FormatKey("M", mono = true, active = Fmt.MONO in open) { onChange(applyFormat(draft, Fmt.MONO)) }
        Box {
            // Color entry point: a rainbow chip that opens the classic palette.
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .size(22.dp)
                    .background(
                        Brush.sweepGradient(
                            listOf(
                                Color(0xFFFF453A), Color(0xFFFFD60A), Color(0xFF30D158),
                                Color(0xFF0A84FF), Color(0xFFBF5AF2), Color(0xFFFF453A),
                            ),
                        ),
                        CircleShape,
                    )
                    .clickable { showColors = true },
            )
            AppDropdownMenu(expanded = showColors, onDismissRequest = { showColors = false }) {
                Column(Modifier.padding(10.dp)) {
                    // Text vs Fill: fill inserts a fg,bg pair (fg = last-picked
                    // text color, else a contrast-safe black/white).
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { fillMode = false }) {
                            Text(
                                "Text",
                                color = if (!fillMode) AccentBlue else TextSecondary,
                                fontWeight = if (!fillMode) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 13.sp,
                            )
                        }
                        TextButton(onClick = { fillMode = true }) {
                            Text(
                                "Background",
                                color = if (fillMode) AccentBlue else TextSecondary,
                                fontWeight = if (fillMode) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    for (rowStart in 0 until 16 step 8) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (i in rowStart until rowStart + 8) {
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .background(Color(Mirc.color(i) ?: 0), CircleShape)
                                        .border(1.dp, PillGray, CircleShape)
                                        .clickable {
                                            showColors = false
                                            if (fillMode) {
                                                val fg = lastFg ?: Mirc.contrastIndex(i)
                                                onChange(applyFormat(draft, Fmt.colorPair(fg, i), Fmt.COLOR))
                                            } else {
                                                lastFg = i
                                                // Selection gets color..clear; bare opener at cursor.
                                                onChange(applyFormat(draft, Fmt.color(i), Fmt.COLOR))
                                            }
                                        },
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    TextButton(onClick = {
                        showColors = false
                        onChange(applyFormat(draft, Fmt.RESET, ""))
                    }) { Text("Clear formatting", color = TextSecondary, fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
private fun FormatKey(
    label: String,
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false,
    strike: Boolean = false,
    mono: Boolean = false,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(36.dp)
            .background(if (active) AccentBlue else SurfaceDark, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 15.sp,
            color = if (active) CanvasBlack else TextPrimary,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontFamily = if (mono) FontFamily.Monospace else null,
            textDecoration = when {
                underline -> TextDecoration.Underline
                strike -> TextDecoration.LineThrough
                else -> null
            },
        )
    }
}

// ---- Settings ------------------------------------------------------------

// Category order + labels mirror shared/settingsRegistry.ts CATEGORIES (only
// the registry-driven ones — bespoke panes like Networks/Account are web-only).
// Categories not listed still render, alphabetically after these.
private val CATEGORY_META = listOf(
    "appearance" to "Appearance",
    "chat" to "Chat",
    "input" to "Input bar",
    "uploads" to "Uploads",
    "notifications" to "Notifications",
    "away" to "Away",
    "fserve" to "File server",
    "dcc" to "DCC transfers",
)

// Group headers, mirroring the GROUPS map in shared/settingsRegistry.ts.
private val GROUP_LABELS = mapOf(
    "fonts" to "Fonts", "palette" to "Colors", "messages" to "Message rows",
    "members" to "Member prefixes", "buffer-list" to "Buffer list",
    "nicks" to "Nick coloring", "layout" to "Layout", "misc" to "Misc",
    "consolidate" to "Join/part consolidation", "routing" to "Message routing",
    "channel-behavior" to "Channels", "composing" to "Composing",
    "smart-filter" to "Smart filter", "viewing" to "Viewing",
    "connection" to "Connection", "ctcp" to "CTCP replies",
    "system_features" to "System text features", "autocomplete" to "Autocomplete",
    "formatting" to "Formatting", "auto-away" to "Auto-away",
    "alerts" to "Alerts", "push_filters" to "Push filters", "pipeline" to "Image pipeline",
    "fserve" to "File server", "fserve-queue" to "Queue & sends",
    "fserve-ads" to "Advertising", "fserve-find" to "Search (@find)",
    "fserve-files" to "File filters",
    "dcc-incoming" to "Incoming", "dcc-outgoing" to "Outgoing", "dcc-notify" to "Notifications",
)

/**
 * Registry settings that only steer the *web* client and do nothing here are
 * hidden, so the mobile settings screen shows only what actually affects this
 * app or the account server-side. Server behaviors (chat filters, away, ctcp,
 * uploads, dcc, channel, push) still apply to mobile via the server, so they
 * stay. Everything under `look.*` styles the web renderer — mobile has its own
 * themes. The app still READS look.font.size.mobile as its base font size, but
 * the row is hidden: users adjust size via the device-local scale in Settings,
 * so showing the synced web value too was a confusing second font control
 * (freakyy85).
 */
private fun settingHiddenOnMobile(key: String): Boolean = when {
    key.startsWith("look.") -> true              // fonts, palette, desktop layout, bars
    key.startsWith("input.") -> true             // web input-box prefs; Android uses the system IME
    key.contains(".sound.") -> true              // web notification audio; Android uses channels
    key == "chat.image_modal.enabled" -> true    // web lightbox; mobile has its own media viewer
    key == "uploads.paste.enabled" -> true       // web clipboard paste
    else -> false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(client: LurkerClient, prefs: Prefs, onBack: () -> Unit) {
    var category by remember { mutableStateOf<String?>(null) }
    BackHandler { if (category != null) category = null else onBack() }

    // Keyed on the feature flags too: an entry can become available mid-session when
    // /api/config lands (it's fetched async on connect, after the registry).
    val byCategory = remember(client.settingsRegistry.toList(), client.serverFeatureFlags.toMap()) {
        client.settingsRegistry
            .filter { it.category.isNotEmpty() && it.category != "system" && !settingHiddenOnMobile(it.key) }
            // HIDE, don't merely disable, anything gated on a feature this instance
            // never mounted — the server sends those entries regardless, so without
            // this we'd render authentic-looking toggles whose endpoints 404.
            .filter { client.settingAvailable(it) }
            .groupBy { it.category }
    }
    val orderedCategories = remember(byCategory) {
        val known = CATEGORY_META.map { it.first }.filter { it in byCategory }
        known + (byCategory.keys - known.toSet()).sorted()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasBlack,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CanvasBlack),
                navigationIcon = {
                    TextButton(onClick = { if (category != null) category = null else onBack() }) {
                        Text("‹", color = AccentBlue, fontSize = 26.sp)
                    }
                },
                title = {
                    Text(
                        category?.let { c -> CATEGORY_META.firstOrNull { it.first == c }?.second ?: c.replaceFirstChar { ch -> ch.uppercase() } }
                            ?: "Settings",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        },
    ) { padding ->
        when {
            !client.settingsLoaded ->
                Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            category == null -> LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                client.settingsError?.let { err ->
                    item { Text(err, color = AlertRed, modifier = Modifier.padding(16.dp)) }
                }
                item { Spacer(Modifier.height(8.dp)) }
                // Device-local settings always render — they're this device's Prefs,
                // not the server's registry, so they exist in direct mode too.
                item { ThemePickerCard(prefs) }
                // Device-local preview toggles belong to the ON-DEVICE scraper. In
                // Lurker mode the server-side registry settings govern instead, and
                // showing both would be two controls for one behaviour.
                if (!ServerPreviews.enabled) {
                    item { InlineMediaCard(prefs) }
                    item { LinkPreviewsCard(prefs) }
                    item { YoutubeDescriptionsCard(prefs) }
                }
                item { TranslationCard(prefs) }
                item { ClockFormatCard(prefs) }
                item { CompactMessagesCard(prefs) }
                item { AccentColorCard(prefs) }
                item { HighlightColorCard(prefs) }
                item { FontFamilyCard(prefs) }
                item { DensityCard(prefs) }
                item { NickColorsCard(prefs) }
                item { ChatBackgroundCard(prefs) }
                item { ChatTextSizeCard(prefs) }
                item { BiometricLockCard(prefs) }
                item { E2eBiometricLockCard(prefs) }
                item { BackgroundConnectCard(prefs) }
                item { DiagnosticsCard() }
                item { ConnectionModeCard(prefs) }
                // FORK-ONLY: only shown when the connected server supports it.
                if (client.serverExtended) item { AliasesCard(client) }
                // No server-side registry (direct mode, or a server without it).
                if (orderedCategories.isEmpty() && client.settingsError == null) item {
                    Text(
                        "Server-side chat settings aren't available in this mode.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp, 12.dp),
                    )
                }
                items(orderedCategories.size) { i ->
                    val cat = orderedCategories[i]
                    val label = CATEGORY_META.firstOrNull { it.first == cat }?.second
                        ?: cat.replaceFirstChar { it.uppercase() }
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, GlassBorder),
                        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { category = cat }
                                .padding(16.dp, 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(label, fontSize = 17.sp)
                                Text(
                                    "${byCategory.getValue(cat).size} settings",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                )
                            }
                            Text("›", color = TextSecondary, fontSize = 20.sp)
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }

            else -> {
                // groupBy preserves registry order, which is the designed order.
                val groups = remember(category) {
                    byCategory[category].orEmpty().groupBy { it.group }
                }
                LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                    for ((group, options) in groups) {
                        item {
                            Text(
                                GROUP_LABELS[group] ?: group.replaceFirstChar { it.uppercase() },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary,
                                modifier = Modifier.fillMaxWidth().padding(28.dp, 18.dp, 16.dp, 6.dp),
                            )
                        }
                        item {
                            Surface(
                                color = SurfaceDark,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(0.5.dp, GlassBorder),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            ) {
                                Column {
                                    options.forEachIndexed { i, option ->
                                        SettingRow(client, option)
                                        if (i < options.lastIndex) {
                                            HorizontalDivider(
                                                color = SurfaceRaised,
                                                modifier = Modifier.padding(start = 16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

// FORK-ONLY: custom server-synced aliases (stripped from the public build).
@Composable
private fun AliasesCard(client: LurkerClient) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var expansion by remember { mutableStateOf("") }
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
    ) {
        Column(Modifier.padding(16.dp, 12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Aliases", fontSize = 17.sp)
                    Text("Custom /commands ($1..$9, \$me, \$chan)", color = TextSecondary, fontSize = 12.sp)
                }
                TextButton(onClick = { adding = !adding }) { Text(if (adding) "Cancel" else "Add", color = AccentBlue) }
            }
            if (adding) {
                OutlinedTextField(name, { name = it.trimStart('/').trim() }, label = { Text("name (e.g. wave)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(expansion, { expansion = it }, label = { Text("expansion (e.g. /me waves at \$1)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                Button(
                    onClick = {
                        if (name.isNotBlank() && expansion.isNotBlank()) {
                            client.addAlias(name.trim(), expansion.trim())
                            name = ""; expansion = ""; adding = false
                        }
                    },
                    enabled = name.isNotBlank() && expansion.isNotBlank(),
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Save alias") }
            }
            client.aliases.forEach { a ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("/${a.name}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(a.expansion, color = TextSecondary, fontSize = 12.sp)
                    }
                    TextButton(onClick = { client.removeAlias(a.id) }) { Text("Delete", color = AlertRed, fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
private fun InlineMediaCard(prefs: Prefs) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Inline media", fontSize = 17.sp)
                Text(
                    "Show image & video links as thumbnails. Off = tap-to-open only (won't fetch link previews).",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Switch(
                checked = Ui.inlineMedia,
                onCheckedChange = { Ui.inlineMedia = it; prefs.inlineMedia = it },
            )
        }
    }
}

@Composable
private fun LinkPreviewsCard(prefs: Prefs) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Link previews", fontSize = 17.sp)
                Text(
                    "Show a title, description & image card under pasted links. " +
                        "This device fetches the page, so the linked site sees your address.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Switch(
                checked = Ui.linkPreviews,
                onCheckedChange = { Ui.linkPreviews = it; prefs.linkPreviews = it },
            )
        }
    }
}

@Composable
private fun YoutubeDescriptionsCard(prefs: Prefs) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("YouTube descriptions", fontSize = 17.sp)
                Text(
                    "Expand YouTube links into a card with the video's title, thumbnail & description.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Switch(
                checked = Ui.youtubeDescriptions,
                onCheckedChange = { Ui.youtubeDescriptions = it; prefs.youtubeDescriptions = it },
            )
        }
    }
}

@Composable
private fun ClockFormatCard(prefs: Prefs) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("24-hour time", fontSize = 17.sp)
                Text(
                    "Show message times as ${if (Ui.clock24h) "14:05" else "2:05 PM"}.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Switch(
                checked = Ui.clock24h,
                onCheckedChange = { Ui.clock24h = it; prefs.clock24h = it },
            )
        }
    }
}

@Composable
private fun CompactMessagesCard(prefs: Prefs) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Compact messages", fontSize = 17.sp)
                Text(
                    "Default for all channels — dense IRC-style rows (time · nick · message) instead of bubbles. Override per channel in its control panel.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Switch(
                checked = Ui.compact,
                onCheckedChange = { Ui.compact = it; prefs.compactMessages = it },
            )
        }
    }
}

private val ACCENT_SWATCHES: List<Int> = listOf(
    0xFF0A84FF, 0xFF30D158, 0xFFFF375F, 0xFFBF5AF2, 0xFFFF9F0A,
    0xFFFFD60A, 0xFF64D2FF, 0xFFFF6482, 0xFF66D4CF, 0xFF5E5CE6,
).map { it.toInt() }

/** Reusable settings-card shell. */
@Composable
private fun AppearanceCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
    ) {
        Column(Modifier.padding(16.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** A pill-segment selector used by the font/density cards. */
@Composable
private fun OptionCard(
    title: String,
    subtitle: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    AppearanceCard {
        Text(title, fontSize = 17.sp)
        Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (id, label) ->
                val sel = id == selected
                Text(
                    label,
                    color = if (sel) Color.White else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) AccentBlue else PillGray, RoundedCornerShape(10.dp))
                        .clickable { onSelect(id) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun AccentColorCard(prefs: Prefs) {
    var showCustom by remember { mutableStateOf(false) }
    AppearanceCard {
        Text("Accent colour", fontSize = 17.sp)
        Text("Recolours links, buttons, toggles and your own messages.", color = TextSecondary, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Reset-to-theme chip.
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(SurfaceRaised, CircleShape)
                    .border(if (Ui.accentColor == 0) 2.dp else 1.dp, if (Ui.accentColor == 0) AccentBlue else PillGray, CircleShape)
                    .clickable { Ui.accentColor = 0; prefs.accentColor = 0 },
                contentAlignment = Alignment.Center,
            ) { Text("↺", color = TextSecondary, fontSize = 13.sp) }
            ACCENT_SWATCHES.forEach { argb ->
                val sel = Ui.accentColor == argb
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Color(argb), CircleShape)
                        .border(if (sel) 2.dp else 1.dp, if (sel) Color.White else PillGray, CircleShape)
                        .clickable { Ui.accentColor = argb; prefs.accentColor = argb },
                )
            }
            // Custom picker.
            Box(
                Modifier.size(30.dp).clip(CircleShape).border(1.dp, PillGray, CircleShape).clickable { showCustom = true },
                contentAlignment = Alignment.Center,
            ) { Text("+", color = TextSecondary, fontSize = 16.sp) }
        }
    }
    if (showCustom) {
        ColorPickerDialog(
            initial = if (Ui.accentColor != 0) Ui.accentColor else AccentBlue.toArgb(),
            onDismiss = { showCustom = false },
            onPick = { argb -> Ui.accentColor = argb; prefs.accentColor = argb; showCustom = false },
        )
    }
}

/** A simple HSV colour picker (hue / saturation / brightness sliders + preview). */
@Composable
private fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var h by remember { mutableStateOf(start[0]) }
    var s by remember { mutableStateOf(start[1]) }
    var v by remember { mutableStateOf(start[2]) }
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = SurfaceDark, shape = RoundedCornerShape(16.dp), border = BorderStroke(0.5.dp, GlassBorder)) {
            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Custom colour", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(argb)))
                Text("Hue", color = TextSecondary, fontSize = 12.sp)
                Slider(value = h, onValueChange = { h = it }, valueRange = 0f..360f)
                Text("Saturation", color = TextSecondary, fontSize = 12.sp)
                Slider(value = s, onValueChange = { s = it }, valueRange = 0f..1f)
                Text("Brightness", color = TextSecondary, fontSize = 12.sp)
                Slider(value = v, onValueChange = { v = it }, valueRange = 0f..1f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
                    TextButton(onClick = { onPick(argb) }) { Text("Use colour", color = AccentBlue, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun FontFamilyCard(prefs: Prefs) = OptionCard(
    "Chat font", "Typeface for message text.",
    listOf("system" to "System", "serif" to "Serif", "mono" to "Monospace"),
    Ui.fontFamily,
) { Ui.fontFamily = it; prefs.fontFamily = it }

@Composable
private fun DensityCard(prefs: Prefs) = OptionCard(
    "Message density", "Spacing between messages.",
    listOf("compact" to "Compact", "cozy" to "Cozy", "comfortable" to "Comfortable"),
    Ui.density,
) { Ui.density = it; prefs.density = it }

@Composable
private fun NickColorsCard(prefs: Prefs) {
    Surface(
        color = SurfaceDark, shape = RoundedCornerShape(12.dp), border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Colour nicknames", fontSize = 17.sp)
                Text("Give each person a consistent colour (off = plain text).", color = TextSecondary, fontSize = 12.sp)
            }
            Switch(checked = Ui.nickColors, onCheckedChange = { Ui.nickColors = it; prefs.nickColors = it })
        }
    }
}

/**
 * The chat background: a still image, an animated GIF/WebP, or a looping video.
 *
 * Images (including animated ones — the app-wide Coil loader has the animated
 * decoder) go through AsyncImage. Videos play muted, looping, cover-cropped, on a
 * texture surface so the frosted chrome above can blur them. Paused while the app
 * is away so a full-screen loop doesn't drain the battery in your pocket.
 */
@Composable
private fun ChatBackground(path: String, modifier: Modifier = Modifier) {
    val isVideo = VIDEO_EXTS.any { path.endsWith(it, ignoreCase = true) }
    if (!isVideo) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
        return
    }
    val context = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f // a chat background is never audible
            prepare()
            playWhenReady = true
        }
    }
    // Follow the app's foreground state: keep the loop off-screen work to nothing
    // while backgrounded, and its own DisposableEffect releases the player.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> player.playWhenReady = false
                androidx.lifecycle.Lifecycle.Event.ON_START -> player.playWhenReady = true
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs); player.release() }
    }
    AndroidView(
        factory = { ctx ->
            (android.view.LayoutInflater.from(ctx).inflate(R.layout.chat_bg_player, null) as PlayerView)
                .also { it.player = player }
        },
        modifier = modifier,
    )
}

/**
 * Copy a picked image/video into app storage as the (global or per-channel)
 * background, replacing any previous file under [prefix]. Returns the saved path,
 * or null if the pick couldn't be read. The extension is derived from the MIME
 * type so [ChatBackground] can tell a video from an image later.
 */
private fun saveBackgroundPick(
    context: android.content.Context,
    uri: android.net.Uri,
    prefix: String,
): String? = runCatching {
    val mime = context.contentResolver.getType(uri).orEmpty()
    val ext = when {
        mime.startsWith("video/") && mime.contains("webm") -> ".webm"
        mime.startsWith("video/") -> ".mp4" // ExoPlayer sniffs the real container
        mime == "image/gif" -> ".gif"
        mime == "image/webp" -> ".webp"
        mime == "image/png" -> ".png"
        else -> ".jpg"
    }
    val dir = context.filesDir
    dir.listFiles { f -> f.name.startsWith(prefix) }?.forEach { it.delete() }
    val dst = File(dir, "$prefix${System.currentTimeMillis()}$ext")
    context.contentResolver.openInputStream(uri)?.use { i -> dst.outputStream().use { i.copyTo(it) } }
        ?: return null
    dst.absolutePath
}.getOrNull()

@Composable
private fun ChatBackgroundCard(prefs: Prefs) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            saveBackgroundPick(context, uri, "chat_bg_")?.let {
                Ui.backgroundUri = it
                prefs.backgroundUri = it
            }
        }
    }
    AppearanceCard {
        Text("Chat background", fontSize = 17.sp)
        Text("Use a photo, GIF, or short video from your device behind your chats.", color = TextSecondary, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) {
                Text(if (Ui.backgroundUri == null) "Choose image / video" else "Change", color = AccentBlue)
            }
            if (Ui.backgroundUri != null) {
                TextButton(onClick = { Ui.backgroundUri = null; prefs.backgroundUri = null }) {
                    Text("Remove", color = AlertRed)
                }
            }
        }
        if (Ui.backgroundUri != null) {
            Text("Dim for readability", color = TextSecondary, fontSize = 12.sp)
            Slider(
                value = Ui.backgroundDim,
                onValueChange = { Ui.backgroundDim = it; prefs.backgroundDim = it },
                valueRange = 0f..0.9f,
            )
        }
    }
}

@Composable
private fun HighlightColorCard(prefs: Prefs) {
    // 0 = theme-default gold; the rest are muted bubble fills.
    val swatches = listOf(
        0, 0xFF4A3B12.toInt(), 0xFF6A4E12.toInt(), 0xFF1E4620.toInt(), 0xFF13424A.toInt(),
        0xFF1E3A5C.toInt(), 0xFF3A2456.toInt(), 0xFF4E2440.toInt(), 0xFF5A2020.toInt(),
        0xFFFDECB8.toInt(),
    )
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp, 12.dp)) {
            Text("Highlight colour", fontSize = 17.sp)
            Text(
                "Bubble colour for messages that mention you. First swatch = default gold.",
                color = TextSecondary, fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                swatches.forEach { argb ->
                    val selected = Ui.highlightColor == argb
                    val shown = if (argb == 0) HighlightGold else Color(argb)
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(shown, CircleShape)
                            .border(
                                if (selected) 2.5.dp else 0.5.dp,
                                if (selected) AccentBlue else GlassBorder,
                                CircleShape,
                            )
                            .clickable { Ui.highlightColor = argb; prefs.highlightColor = argb },
                    )
                }
            }
        }
    }
}

/** A titled on-device toggle row, matching the InlineMedia/theme cards. */
@Composable
private fun PrefToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onClick: (() -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp)
                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun BiometricLockCard(prefs: Prefs) {
    val context = LocalContext.current
    // Only offer the lock if the device can actually satisfy it (a biometric or a
    // device PIN/pattern/password) — otherwise enabling it would strand the user.
    val canAuth = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }
    if (!canAuth) return
    var on by remember { mutableStateOf(prefs.biometricLock) }
    PrefToggleCard(
        title = "Require unlock",
        subtitle = "Ask for fingerprint, face, or device PIN each time the app opens. Changing this asks first.",
        checked = on,
    ) { want ->
        confirmIdentity(
            context,
            title = if (want) "Turn on app lock" else "Turn off app lock",
        ) { ok ->
            // Only commit on success. `on` is what drives the Switch, so leaving it
            // alone snaps the toggle back on a failed or cancelled prompt.
            if (ok) {
                on = want
                prefs.biometricLock = want
            }
        }
    }
}

/**
 * Confirm the person tapping is the owner, then report whether they were.
 *
 * Gates changes that are one stray tap from undoing the lock entirely — turning
 * it off is exactly what someone who picked up an unlocked phone would try, and
 * it's just as easy to hit by accident in a pocket.
 *
 * FAILS CLOSED, deliberately unlike [MainActivity.promptUnlock] and
 * [MainActivity.promptE2eUnlock], which fall open so a device that can't
 * authenticate never strands its owner. Here the safe answer is the opposite:
 * no confirmation means no change. There's no lockout risk in doing so, because
 * both cards are hidden entirely unless the device can authenticate.
 */
private fun confirmIdentity(
    context: android.content.Context,
    title: String,
    onResult: (Boolean) -> Unit,
) {
    val activity = context.findActivity() as? FragmentActivity ?: return onResult(false)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                onResult(true)
            override fun onAuthenticationError(code: Int, err: CharSequence) = onResult(false)
            // Not terminal — the prompt stays up for another attempt — so don't
            // answer here or the setting would resolve on a bad fingerprint read.
            override fun onAuthenticationFailed() = Unit
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle("Confirm it's you to change this setting")
        .setAllowedAuthenticators(authenticators)
        .build()
    runCatching { prompt.authenticate(info) }.onFailure { onResult(false) }
}

@Composable
private fun E2eBiometricLockCard(prefs: Prefs) {
    val context = LocalContext.current
    val canAuth = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }
    if (!canAuth) return
    var on by remember { mutableStateOf(prefs.e2eBiometricLock) }
    PrefToggleCard(
        title = "Lock encrypted channels",
        subtitle = "Require an unlock to open a secure (E2E) channel; re-locks when you leave the app. " +
            "Changing this asks first.",
        checked = on,
    ) { want ->
        confirmIdentity(
            context,
            title = if (want) "Turn on encrypted-channel lock" else "Turn off encrypted-channel lock",
        ) { ok ->
            if (ok) {
                on = want
                prefs.e2eBiometricLock = want
            }
        }
    }
}

@Composable
private fun ChatTextSizeCard(prefs: Prefs) {
    var scale by remember { mutableStateOf(Ui.chatTextScale) }
    fun set(v: Int) { scale = v.coerceIn(-3, 14); Ui.chatTextScale = scale; prefs.chatTextScale = scale }
    val preview = (16 + scale).coerceIn(11, 30)
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Message text size", fontSize = 17.sp)
                Text(
                    if (scale == 0) "Default · great to bump up on a tablet" else "${if (scale > 0) "+" else ""}$scale from default",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable { set(scale - 1) }
                        .semantics { contentDescription = "Smaller text"; role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) { Text("A", color = AccentBlue, fontSize = 15.sp) }
                Text(
                    "Aa",
                    fontSize = preview.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(48.dp),
                )
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable { set(scale + 1) }
                        .semantics { contentDescription = "Larger text"; role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) { Text("A", color = AccentBlue, fontSize = 22.sp) }
            }
        }
    }
}

@Composable
private fun BackgroundConnectCard(prefs: Prefs) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(prefs.backgroundConnect) }
    // Re-checked on every resume: the user comes back from the system settings.
    val power = remember { context.getSystemService(android.os.PowerManager::class.java) }
    var exempt by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        exempt = power?.isIgnoringBatteryOptimizations(context.packageName) != false
        onPauseOrDispose { }
    }
    var showBatteryHelp by remember { mutableStateOf(false) }
    PrefToggleCard(
        title = "Stay connected in background",
        subtitle = "Keep a persistent notification so highlights & DMs notify you even when the app is closed." +
            if (on && !exempt) "\n⚠ Battery optimisation is on for this app — tap here to fix." else "",
        checked = on,
        onClick = if (on && !exempt) ({ showBatteryHelp = true }) else null,
    ) {
        on = it
        prefs.backgroundConnect = it
        if (it) LurkerConnectionService.start(context) else LurkerConnectionService.stop(context)
        // The service keeps the process alive, but battery optimisation (and OEM
        // battery managers on top of it) still kill or freeze it overnight. Every
        // Android IRC client that stays connected asks for the exemption; send the
        // user to the system list rather than requesting it directly (Play-safe).
        if (it && !exempt) showBatteryHelp = true
    }
    if (showBatteryHelp) {
        AlertDialog(
            onDismissRequest = { showBatteryHelp = false },
            containerColor = SurfaceRaised,
            title = { Text("Stay connected overnight", color = TextPrimary) },
            text = {
                Text(
                    "Android's battery optimisation can still close the connection while your phone sleeps. " +
                        "To stay connected reliably, set this app to \"Unrestricted\" (or \"Not optimised\") in the " +
                        "battery settings. Some phones (Samsung, Xiaomi, OnePlus) also need background activity " +
                        "allowed in their own battery manager.",
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBatteryHelp = false
                    runCatching {
                        context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }.onFailure {
                        runCatching {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.parse("package:${context.packageName}")),
                            )
                        }
                    }
                }) { Text("Open battery settings", color = AccentBlue, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryHelp = false }) { Text("Not now", color = TextSecondary) }
            },
        )
    }
}

/** Opens the in-app diagnostics log — recent connection/error events the user can
 *  copy or share when reporting a bug (drove by "app was repeatedly terminated"
 *  reports with no detail). */
@Composable
private fun DiagnosticsCard() {
    var show by remember { mutableStateOf(false) }
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp).clickable { show = true },
    ) {
        Column(Modifier.padding(16.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Diagnostics", fontSize = 17.sp)
            Text(
                "View the app's recent connection & error log — handy for bug reports.",
                color = TextSecondary, fontSize = 13.sp,
            )
        }
    }
    if (show) DiagnosticsDialog(onDismiss = { show = false })
}

@Composable
private fun DiagnosticsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val entries = DebugLog.entries
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = CanvasBlack,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(0.5.dp, GlassBorder),
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f),
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Diagnostics", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close", color = AccentBlue) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(DebugLog.dump())) }) {
                        Text("Copy", fontSize = 13.sp, color = AccentBlue)
                    }
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, DebugLog.dump())
                        context.startActivity(Intent.createChooser(send, "Share diagnostics"))
                    }) { Text("Share", fontSize = 13.sp, color = AccentBlue) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { DebugLog.clear() }) { Text("Clear", fontSize = 13.sp, color = AlertRed) }
                }
                HorizontalDivider(color = GlassBorder)
                if (entries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No diagnostics yet.", color = TextSecondary, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(top = 6.dp)) {
                        items(entries.size) { i ->
                            val e = entries[entries.lastIndex - i] // newest first
                            Text(
                                DebugLog.render(e),
                                color = when (e.level) {
                                    DebugLog.Level.ERROR -> AlertRed
                                    DebugLog.Level.WARN -> Color(0xFFFFD60A)
                                    else -> TextSecondary
                                },
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Switch between Lurker-server and direct-IRC/bouncer mode. Applying the switch
 *  restarts the app so LurkerApp rebuilds its lazy backend for the new mode. */
@Composable
private fun ConnectionModeCard(prefs: Prefs) {
    val context = LocalContext.current
    val mode = prefs.clientMode ?: "lurker"
    val isDirect = mode == "direct"
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
    ) {
        Column(Modifier.padding(16.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Connection mode", fontSize = 17.sp)
            Text(
                if (isDirect) "Direct IRC / bouncer" else "Lurker server",
                color = TextSecondary, fontSize = 13.sp,
            )
            Text(
                "Switch restarts the app. " +
                    if (isDirect) "Switching to Lurker signs you into a Lurker server."
                    else "Switching to Direct connects straight to IRC / a bouncer.",
                color = TextSecondary, fontSize = 12.sp,
            )
            Surface(
                onClick = { prefs.clientMode = if (isDirect) "lurker" else "direct"; restartAppProcess(context) },
                shape = RoundedCornerShape(10.dp),
                color = SurfaceRaised,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    if (isDirect) "Switch to Lurker server" else "Switch to Direct IRC / bouncer",
                    color = AccentBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(14.dp, 10.dp),
                )
            }
        }
    }
}

/** Relaunch the app in a fresh process (mode switch = restart). */
private fun restartAppProcess(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

@Composable
private fun ThemePickerCard(prefs: Prefs) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
    ) {
        Column(Modifier.padding(16.dp, 12.dp)) {
            Text("App theme", fontSize = 17.sp)
            Text("On this device", color = TextSecondary, fontSize = 13.sp)
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppTheme.entries.forEach { t ->
                    val selected = Ui.theme == t
                    Text(
                        t.label,
                        color = if (selected) AccentBlue else TextSecondary,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .background(
                                if (selected) SurfaceRaised else Color.Transparent,
                                RoundedCornerShape(14.dp),
                            )
                            .clickable { Ui.theme = t; prefs.theme = t.id }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(client: LurkerClient, option: SettingOption) {
    val overridden = client.settingsValues.containsKey(option.key)
    // Bootstrap `values` carries only overrides; everything else shows its default.
    val value = if (overridden) client.settingsValues[option.key] else option.default
    Column(Modifier.fillMaxWidth().padding(16.dp, 10.dp)) {
        when (option.type) {
            "bool" -> Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(option.label, modifier = Modifier.weight(1f))
                Switch(
                    checked = value as? Boolean ?: false,
                    onCheckedChange = { client.patchSetting(option.key, it) },
                )
            }

            "enum" -> EnumSetting(option, value?.toString()) { client.patchSetting(option.key, it) }

            "int" -> EditableSetting(option.label, value?.toString() ?: "", number = true) {
                it.toIntOrNull()?.let { n ->
                    val clamped = n.coerceIn(option.min ?: n, option.max ?: n)
                    client.patchSetting(option.key, clamped)
                }
            }

            "string-list" -> EditableSetting(
                option.label,
                (value as? List<*>)?.joinToString(", ") ?: "",
                secret = false,
            ) { text ->
                client.patchSetting(option.key, text.split(",").map { it.trim() }.filter { it.isNotEmpty() })
            }

            else -> EditableSetting(option.label, value?.toString() ?: "", secret = option.type == "secret") {
                client.patchSetting(option.key, it)
            }
        }
        if (option.description.isNotEmpty()) {
            Text(
                option.description,
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(option.key, color = PillGray, fontSize = 11.sp, modifier = Modifier.weight(1f))
            if (overridden) {
                Text(
                    "Reset",
                    color = AccentBlue,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { client.resetSetting(option.key) }
                        .padding(4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumSetting(option: SettingOption, current: String?, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(option.label, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) { Text(current ?: "—") }
            AppDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                option.choices.forEach { choice ->
                    DropdownMenuItem(text = { Text(choice) }, onClick = { open = false; onPick(choice) })
                }
            }
        }
    }
}

@Composable
private fun EditableSetting(
    label: String,
    initial: String,
    number: Boolean = false,
    secret: Boolean = false,
    onCommit: (String) -> Unit,
) {
    // Keyed by the initial value so a server-side update re-seeds the field.
    var text by remember(initial) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---- Networks --------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworksScreen(
    client: LurkerClient,
    onEdit: (NetworkConfig) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasBlack,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CanvasBlack),
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹", color = AccentBlue, fontSize = 26.sp) }
                },
                title = { Text("Networks", fontWeight = FontWeight.SemiBold) },
                actions = {
                    TextButton(onClick = onAdd) { Text("+", color = AccentBlue, fontSize = 24.sp) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            client.networksError?.let { err ->
                item { Text(err, color = AlertRed, modifier = Modifier.padding(16.dp)) }
            }
            if (client.networkConfigs.isEmpty()) {
                item { Text("No networks yet — add one with +.", Modifier.padding(16.dp), color = TextSecondary) }
            }
            items(client.networkConfigs.size) { i ->
                val cfg = client.networkConfigs[i]
                val live = client.networks[cfg.id]
                val connected = live?.connected == true
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 6.dp),
                ) {
                    Column(Modifier.padding(16.dp, 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ConnectionDot(connected)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                cfg.name,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                // Weighted, so a large font scale squeezes it against
                                // the arrange/Edit buttons; truncate rather than break
                                // "Libera.Chat" across two lines.
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            // Arrange: swap with the neighbor and persist the order
                            // (drives the buffer-list section order too).
                            TextButton(
                                onClick = {
                                    val ids = client.networkConfigs.map { it.id }.toMutableList()
                                    val at = ids.indexOf(cfg.id)
                                    if (at > 0) {
                                        ids[at] = ids[at - 1].also { ids[at - 1] = ids[at] }
                                        client.reorderNetworks(ids)
                                    }
                                },
                                enabled = i > 0,
                            ) { Text("↑", color = if (i > 0) TextSecondary else PillGray) }
                            TextButton(
                                onClick = {
                                    val ids = client.networkConfigs.map { it.id }.toMutableList()
                                    val at = ids.indexOf(cfg.id)
                                    if (at >= 0 && at < ids.lastIndex) {
                                        ids[at] = ids[at + 1].also { ids[at + 1] = ids[at] }
                                        client.reorderNetworks(ids)
                                    }
                                },
                                enabled = i < client.networkConfigs.lastIndex,
                            ) { Text("↓", color = if (i < client.networkConfigs.lastIndex) TextSecondary else PillGray) }
                            TextButton(onClick = { onEdit(cfg) }) { Text("Edit", color = AccentBlue) }
                        }
                        Text(
                            "${cfg.host}:${cfg.port}${if (cfg.tls) " (TLS)" else ""} · ${live?.nick ?: cfg.nick}" +
                                if (cfg.blocked) " · blocked by server policy" else "",
                            color = if (cfg.blocked) AlertRed else TextSecondary,
                            fontSize = 13.sp,
                        )
                        // FlowRow: two action words don't share one line at a large
                        // font scale, and a Row answers that by squeezing the second
                        // until it wraps a letter per line.
                        FlowRow {
                            if (connected) {
                                TextButton(onClick = { client.networkAction(cfg.id, "disconnect") }) {
                                    Text("Disconnect", color = AlertRed, fontSize = 13.sp, maxLines = 1, softWrap = false)
                                }
                                TextButton(onClick = { client.networkAction(cfg.id, "reconnect") }) {
                                    Text("Reconnect", color = TextSecondary, fontSize = 13.sp, maxLines = 1, softWrap = false)
                                }
                            } else {
                                TextButton(
                                    onClick = { client.networkAction(cfg.id, "connect") },
                                    enabled = !cfg.blocked,
                                ) { Text("Connect", color = if (cfg.blocked) TextSecondary else OnlineGreen, fontSize = 13.sp) }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkEditScreen(client: LurkerClient, config: NetworkConfig?, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var name by remember { mutableStateOf(config?.name ?: "") }
    var host by remember { mutableStateOf(config?.host ?: "") }
    var port by remember { mutableStateOf((config?.port ?: 6697).toString()) }
    var tls by remember { mutableStateOf(config?.tls ?: true) }
    var nick by remember { mutableStateOf(config?.nick ?: "") }
    var username by remember { mutableStateOf(config?.username ?: "") }
    var realname by remember { mutableStateOf(config?.realname ?: "") }
    var serverPassword by remember { mutableStateOf("") }
    var saslAccount by remember { mutableStateOf(config?.saslAccount ?: "") }
    var saslPassword by remember { mutableStateOf("") }
    var channels by remember { mutableStateOf("") }
    var autoconnect by remember { mutableStateOf(config?.autoconnect ?: true) }
    // Direct-mode endpoint type: a plain network, or a soju bouncer (which
    // auto-discovers its upstream networks). Only meaningful in direct mode.
    val directMode = !client.serverFeatures
    var endpointType by remember { mutableStateOf(config?.type ?: "direct") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasBlack,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CanvasBlack),
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹", color = AccentBlue, fontSize = 26.sp) }
                },
                title = { Text(config?.name ?: "Add network", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            error?.let { Text(it, color = AlertRed, fontSize = 13.sp) }
            FormField("Name", name) { name = it }
            FormField("Host", host) { host = it }
            FormField("Port", port, number = true) { port = it }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TLS")
                Switch(checked = tls, onCheckedChange = { tls = it })
            }
            FormField("Nick", nick) { nick = it }
            FormField("Username (ident) — optional", username) { username = it }
            FormField("Real name — optional", realname) { realname = it }
            FormField(
                if (config?.hasPassword == true) "Server password (set — leave blank to keep)"
                else "Server password — optional",
                serverPassword, secret = true,
            ) { serverPassword = it }
            FormField("SASL account — optional", saslAccount) { saslAccount = it }
            FormField(
                if (config?.hasSaslPassword == true) "SASL password (set — leave blank to keep)"
                else "SASL password — optional",
                saslPassword, secret = true,
            ) { saslPassword = it }
            if (config == null) {
                FormField("Channels to join — optional (#a, #b)", channels) { channels = it }
            }
            if (directMode) {
                Text("Endpoint type", color = TextSecondary, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("direct" to "Direct network", "soju" to "Soju bouncer").forEach { (v, label) ->
                        val sel = endpointType == v
                        Text(
                            label,
                            color = if (sel) AccentBlue else TextSecondary,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .background(if (sel) SurfaceRaised else Color.Transparent, RoundedCornerShape(14.dp))
                                .clickable { endpointType = v }
                                .padding(12.dp, 8.dp),
                        )
                    }
                }
                if (endpointType == "soju") {
                    Text(
                        "Soju discovers its upstream networks automatically. For other bouncers (ZNC), use Direct and put user/network in the username or SASL account.",
                        color = TextSecondary, fontSize = 12.sp,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Connect automatically")
                Switch(checked = autoconnect, onCheckedChange = { autoconnect = it })
            }
            Button(
                onClick = {
                    saving = true
                    val fields = buildMap<String, Any?> {
                        put("name", name.trim())
                        put("host", host.trim())
                        put("port", port.toIntOrNull() ?: 6697)
                        put("tls", tls)
                        put("nick", nick.trim())
                        if (username.isNotBlank()) put("username", username.trim())
                        if (realname.isNotBlank()) put("realname", realname.trim())
                        if (serverPassword.isNotEmpty()) put("server_password", serverPassword)
                        if (saslAccount.isNotBlank()) put("sasl_account", saslAccount.trim())
                        if (saslPassword.isNotEmpty()) put("sasl_password", saslPassword)
                        if (config == null && channels.isNotBlank()) put("default_channel", channels)
                        put("autoconnect", autoconnect)
                        if (directMode) put("type", endpointType)
                    }
                    client.saveNetwork(config?.id, fields) { err ->
                        saving = false
                        if (err == null) onBack() else error = err
                    }
                },
                enabled = !saving && name.isNotBlank() && host.isNotBlank() && nick.isNotBlank(),
            ) { Text(if (config == null) "Save & connect" else "Save") }

            if (config != null) {
                TextButton(onClick = {
                    if (!confirmDelete) {
                        confirmDelete = true
                    } else {
                        client.deleteNetwork(config.id) { err ->
                            if (err == null) onBack() else error = err
                        }
                    }
                }) {
                    Text(
                        if (confirmDelete) "Tap again to permanently delete" else "Delete network",
                        color = AlertRed,
                        fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    number: Boolean = false,
    secret: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
            // These fields are URLs / nicks / hosts — never auto-capitalise or
            // autocorrect them (that's what turned "https" into "Https").
            capitalization = KeyboardCapitalization.None,
            autoCorrect = false,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---- Search + highlights ---------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(
    client: LurkerClient,
    onOpenResult: (Int, String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var tab by remember { mutableStateOf(0) } // 0 = search, 1 = highlights, 2 = bookmarks
    var query by remember { mutableStateOf("") }

    // Debounced search: re-run 300ms after typing stops.
    LaunchedEffect(query) {
        if (query.isBlank()) return@LaunchedEffect
        kotlinx.coroutines.delay(300)
        client.runSearch(query)
    }
    LaunchedEffect(tab) {
        if (tab == 1 && client.highlightItems.isEmpty()) client.loadHighlights(fresh = true)
        if (tab == 2) client.loadBookmarks(fresh = true)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasBlack,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CanvasBlack),
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹", color = AccentBlue, fontSize = 26.sp) }
                },
                title = { Text("Search", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp, 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SearchTab("Messages", tab == 0) { tab = 0 }
                // Lurker 2.4 renamed it: reactions to your lines show here too.
                SearchTab(if (client.activityFeed) "Activity" else "Highlights", tab == 1) { tab = 1 }
                SearchTab("Bookmarks", tab == 2) { tab = 2 }
            }
            if (tab == 0) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search — from:nick in:#chan on:net text", color = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = AccentBlue,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 6.dp),
                )
                val results = client.searchResults
                if (client.searchLoading && results.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                if (!client.searchLoading && results.isEmpty() && query.isNotBlank()) {
                    Text("No matches.", color = TextSecondary, modifier = Modifier.padding(16.dp))
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(results.size) { i ->
                        val r = results[i]
                        ResultRow(client, r, onOpenResult)
                        if (i == results.size - 1 && client.searchHasMore) {
                            LaunchedEffect(results.size) { client.searchMore() }
                        }
                    }
                }
            } else if (tab == 1) {
                val items = client.highlightItems
                if (client.highlightsLoading && items.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                if (!client.highlightsLoading && items.isEmpty()) {
                    Text("No recent highlights.", color = TextSecondary, modifier = Modifier.padding(16.dp))
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(items.size) { i ->
                        ResultRow(client, items[i], onOpenResult)
                        if (i == items.size - 1 && client.highlightsHasMore) {
                            LaunchedEffect(items.size) { client.loadHighlights(fresh = false) }
                        }
                    }
                }
            } else {
                val items = client.bookmarkItems
                if (client.bookmarksLoading && items.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                if (!client.bookmarksLoading && items.isEmpty()) {
                    Text(
                        "No bookmarks yet. Long-press a message and tap Bookmark to save it here.",
                        color = TextSecondary, modifier = Modifier.padding(16.dp),
                    )
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(items.size) { i ->
                        ResultRow(client, items[i], onOpenResult)
                        if (i == items.size - 1 && client.bookmarksHasMore) {
                            LaunchedEffect(items.size) { client.loadBookmarks(fresh = false) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) AccentBlue else TextSecondary,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = cappedSp(15f, TAB_MAX_FONT_SCALE),
        // One word per tab — never break it. The capped size is what keeps all
        // three on the line; this just guarantees it can't wrap if they ever grow.
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .background(if (selected) SurfaceRaised else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

/** How far the search tabs follow the system font scale before they stop. Three
 *  labels share one row; past this they no longer fit, and a Row answers that by
 *  squeezing the last one until it wraps a letter per line. */
private const val TAB_MAX_FONT_SCALE = 1.0f

/**
 * [base] sp, but scaled by at most [maxScale] of the system font setting.
 *
 * An sp value is already multiplied by the user's font scale, so to render at
 * `base * min(scale, maxScale)` the value handed over has to be divided back down
 * by the live scale. Below the cap this returns exactly [base], leaving normal
 * text sizes untouched.
 */
@Composable
private fun cappedSp(base: Float, maxScale: Float): androidx.compose.ui.unit.TextUnit {
    val scale = LocalDensity.current.fontScale
    return if (scale <= maxScale) base.sp else (base * maxScale / scale).sp
}

@Composable
private fun ResultRow(client: LurkerClient, r: SearchResult, onOpen: (Int, String) -> Unit) {
    val net = client.networks[r.networkId]?.name ?: ""
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { if (r.networkId >= 0) onOpen(r.networkId, r.target) }
            .padding(16.dp, 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.nick, color = nickColor(r.nick), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                r.target + (if (net.isNotEmpty()) " · $net" else ""),
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            formatTime(r.createdAt)?.let { Text(it, color = TextSecondary, fontSize = 11.sp) }
        }
        if (r.reaction != null) {
            // Activity: someone reacted to your line ([body] is that line).
            Text("reacted ${r.reaction}", color = AccentBlue, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
            Text(
                "↪ " + Mirc.strip(r.body).replace('\n', ' '),
                color = TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        } else {
            r.replyTo?.let { p ->
                Text(
                    "↪ ${p.nick}: " + Mirc.strip(p.text).replace('\n', ' '),
                    color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(mircAnnotated(r.body, AccentBlue), fontSize = 15.sp, color = TextPrimary, modifier = Modifier.padding(top = 2.dp))
        }
    }
    HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(start = 16.dp))
}

// ---- Channel list (/LIST) --------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelListScreen(
    client: LurkerClient,
    initialQuery: String? = null,
    onJoin: (Int, String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val connected = remember(client.networks.toMap()) { client.networks.values.filter { it.connected } }
    var networkId by remember(connected.map { it.id }) { mutableStateOf(connected.firstOrNull()?.id) }
    var query by remember { mutableStateOf(initialQuery ?: "") }
    var sortByUsers by remember { mutableStateOf(true) }
    var netMenu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(networkId) { networkId?.let { client.openChannelList(it) } }
    LaunchedEffect(query, sortByUsers, networkId) {
        kotlinx.coroutines.delay(300)
        if (networkId != null) client.searchChannelList(query, if (sortByUsers) "users" else "name", "desc", 0)
    }

    confirm?.let { chan ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = SurfaceRaised,
            title = { Text("Join $chan?", color = TextPrimary) },
            confirmButton = {
                TextButton(onClick = { confirm = null; networkId?.let { onJoin(it, chan) } }) {
                    Text("Join", color = AccentBlue, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", color = TextSecondary) } },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasBlack,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CanvasBlack),
                navigationIcon = { TextButton(onClick = onBack) { Text("‹", color = AccentBlue, fontSize = 26.sp) } },
                title = { Text("Channels", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(onClick = { netMenu = true }) {
                        Text(connected.firstOrNull { it.id == networkId }?.name ?: "Network", color = AccentBlue)
                    }
                    AppDropdownMenu(expanded = netMenu, onDismissRequest = { netMenu = false }) {
                        connected.forEach { n -> DropdownMenuItem(text = { Text(n.name) }, onClick = { networkId = n.id; netMenu = false }) }
                    }
                }
                SearchTab(if (sortByUsers) "By users" else "By name", true) { sortByUsers = !sortByUsers }
                if (client.chanlistInProgress) Text("refreshing… ${client.chanlistTotalCount}", color = TextSecondary, fontSize = 12.sp)
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Filter channels", color = TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = AccentBlue,
                ),
                modifier = Modifier.fillMaxWidth().padding(16.dp, 6.dp),
            )
            val rows = client.chanlistRows
            if (client.chanlistLoading && rows.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows.size) { i ->
                    val r = rows[i]
                    Column(
                        Modifier.fillMaxWidth().clickable { confirm = r.channel }.padding(16.dp, 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.channel, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Text("${r.users}", color = TextSecondary, fontSize = 13.sp)
                        }
                        if (r.topic.isNotBlank()) {
                            Text(mircAnnotated(r.topic, AccentBlue), color = TextSecondary, fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(start = 16.dp))
                    if (i == rows.size - 1 && rows.size < client.chanlistTotalCount) {
                        LaunchedEffect(rows.size) {
                            client.searchChannelList(query, if (sortByUsers) "users" else "name", "desc", rows.size)
                        }
                    }
                }
            }
        }
    }
}

// ---- DCC -----------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IgnoresScreen(client: LurkerClient, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var showAdd by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasBlack,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CanvasBlack),
                navigationIcon = { TextButton(onClick = onBack) { Text("‹", color = AccentBlue, fontSize = 26.sp) } },
                title = { Text("Ignore list", fontWeight = FontWeight.SemiBold) },
                actions = { TextButton(onClick = { showAdd = true }) { Text("Add", color = AccentBlue) } },
            )
        },
    ) { padding ->
        val rules = remember(client.ignores.toList()) {
            client.ignores.sortedWith(compareBy({ it.networkId ?: -1 }, { it.who.lowercase() }))
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            if (rules.isEmpty()) {
                item {
                    Text(
                        "Nobody ignored. Tap Add, or long-press a nick in a channel and choose “Ignore highlights.”",
                        color = TextSecondary, modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(rules.size) { i ->
                val r = rules[i]
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.who, fontSize = 16.sp, color = TextPrimary)
                        val scope = if (r.networkId == null) "global" else client.networks[r.networkId]?.name ?: "net ${r.networkId}"
                        val exc = if (r.isExcept) "except " else ""
                        Text("$exc${r.levelsLabel} · $scope", fontSize = 13.sp, color = TextSecondary)
                    }
                    TextButton(onClick = { client.removeIgnore(r.networkId, r.id) }) {
                        Text("Remove", color = AlertRed, fontSize = 13.sp)
                    }
                }
                HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(start = 20.dp))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (showAdd) {
        AddIgnoreDialog(onDismiss = { showAdd = false }) { mask, levels ->
            client.addIgnore(null, mask, levels = levels)
            showAdd = false
        }
    }
}

/** Independent level checkboxes — pick exactly what to ignore (e.g. just
 *  highlights). Unlike the web UI, "Everything" is optional, not forced-on. */
private val IGNORE_LEVELS = listOf(
    "NOHIGHLIGHT" to "Highlights (mentions of your nick)",
    "NONOTIFY" to "Push notifications",
    "ALL" to "Everything (all messages & events)",
    "PUBLIC" to "Channel messages",
    "MSGS" to "Direct messages",
    "NOTICES" to "Notices",
    "ACTIONS" to "Actions (/me)",
    "JOINS" to "Joins",
    "PARTS" to "Parts",
    "QUITS" to "Quits",
    "NICKS" to "Nick changes",
)

@Composable
private fun AddIgnoreDialog(onDismiss: () -> Unit, onAdd: (String, List<String>) -> Unit) {
    var mask by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaised,
        title = { Text("Ignore rule", color = TextPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    mask, { mask = it },
                    label = { Text("Nick or mask (e.g. botnick or *!*@host)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text("Ignore which:", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    IGNORE_LEVELS.forEach { (token, label) ->
                        val on = token in selected
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { if (on) selected.remove(token) else selected.add(token) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = { if (on) selected.remove(token) else selected.add(token) })
                            Text(label, color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = mask.isNotBlank() && selected.isNotEmpty(),
                onClick = { onAdd(mask.trim(), selected.toList()) },
            ) { Text("Add", color = AccentBlue, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
    )
}

/**
 * The one presence colour guide, shared by every surface that shows it: green
 * online, yellow away, red offline, grey unknown (no identity on that network, or
 * we simply haven't heard). [state] is a raw presence value from
 * [LurkerClient.presenceOf] / the `presence` map.
 */
private fun presenceColor(state: String?): Color = when (state) {
    "online", "back" -> OnlineGreen
    "away" -> NoticeAmber
    "offline" -> AlertRed
    else -> TextSecondary
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendsScreen(client: LurkerClient, onOpenBuffer: (Buffer) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    // Friends are the DM half of the server's favorites list (Lurker 2.0 removed
    // the named-contact model, so a friend is simply a favorited DM buffer).
    val friends = client.favorites.filterNot { it.isChannel }
    // Refresh presence on entry so the dots are accurate rather than stale.
    LaunchedEffect(friends.size) {
        friends.forEach { client.probePresence(it.networkId, it.target) }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasBlack,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CanvasBlack),
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹", color = AccentBlue, fontSize = 26.sp) }
                },
                title = { Text("Friends", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            if (friends.isEmpty()) {
                item {
                    Text(
                        "No friends yet. Long-press a nick in a channel's member list → “Add to Friends”, " +
                            "or open a DM and tap ⭐. Friends sync across every device signed into your account.",
                        color = TextSecondary,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                // Colour key: each word IS its status colour.
                item {
                    // FlowRow, not Row: at a large system font scale four words
                    // don't fit on one line, and a Row would squeeze the last one
                    // until it wrapped letter by letter. This wraps between words.
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("online", color = OnlineGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("away", color = NoticeAmber, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("offline", color = AlertRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("unknown", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(start = 20.dp))
                }
            }
            items(friends.size) { i ->
                val f = friends[i]
                var menuOpen by remember(f.bufferId) { mutableStateOf(false) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenBuffer(client.focusTarget(f.networkId, f.target)) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(presenceColor(client.presenceState(f.networkId, f.target))),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(f.target, fontSize = 16.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text(
                            client.networkName(f.networkId),
                            fontSize = 13.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Box {
                        TextButton(onClick = { menuOpen = true }) { Text("⋯", color = TextSecondary, fontSize = 20.sp) }
                        AppDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Remove from Friends", color = AlertRed) },
                                onClick = { menuOpen = false; client.toggleFavorite(f.networkId, f.target) },
                            )
                        }
                    }
                }
                HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(start = 20.dp))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DccScreen(client: LurkerClient, onOpenBuffer: (Buffer) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasBlack,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CanvasBlack),
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹", color = AccentBlue, fontSize = 26.sp) }
                },
                title = { Text("DCC transfers", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        if (!client.dccEnabled) {
            Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Text("DCC is not enabled for this account.", Modifier.padding(24.dp))
            }
            return@Scaffold
        }
        val transfers = remember(client.transfers.toMap()) {
            client.transfers.values.sortedByDescending { it.id }
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            client.dccError?.let { err ->
                item { Text(err, color = AlertRed, modifier = Modifier.padding(16.dp)) }
            }
            // FORK-ONLY (outgoing DCC): receiving transfers still list below.
            item { DccStartCard(client, onOpenBuffer) }
            if (transfers.isEmpty()) {
                item { Text("No transfers.", Modifier.padding(16.dp), color = TextSecondary) }
            }
            items(transfers.size) { i -> TransferRow(client, transfers[i]) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/**
 * DCC CHAT prompts. Every pending inbound offer gets Accept / Decline — nothing is
 * dialled until the user says so (the server never auto-accepts). In a `=nick`
 * chat ([current]) whose session isn't live and has no offer pending, a strip says
 * so and offers to start it again, the way a DM says its peer is offline.
 */
@Composable
private fun DccChatBanner(client: LurkerClient, current: Buffer?) {
    val offers = client.dccChatOffers.values.sortedBy { it.nick.lowercase() }
    val currentNet = current?.networkId
    val showDown = current != null && current.isDccChat && currentNet != null &&
        !client.isDccChatLive(current) && client.dccOfferFor(current) == null
    if (offers.isEmpty() && !showDown) return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        offers.forEach { offer ->
            DccStrip(
                text = "${offer.nick} wants to start a DCC chat" +
                    (if (client.networks.size > 1) " (${client.networkName(offer.networkId)})" else "") +
                    (if (offer.passive) " — they're firewalled, so the server will listen" else ""),
                primary = "Accept",
                onPrimary = {
                    client.dccChat(offer.networkId, offer.nick, open = true, reportTo = current, focus = true)
                },
                secondary = "Decline",
                onSecondary = { client.dccChat(offer.networkId, offer.nick, open = false, reportTo = current) },
            )
        }
        if (showDown && current != null && currentNet != null) {
            DccStrip(
                text = "DCC chat with ${current.peer} is not connected.",
                primary = "Start again",
                onPrimary = { client.dccChat(currentNet, current.peer, open = true, reportTo = current) },
            )
        }
    }
}

@Composable
private fun DccStrip(
    text: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceRaised, RoundedCornerShape(10.dp))
            .border(0.5.dp, GlassBorder, RoundedCornerShape(10.dp))
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Text(text, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (secondary != null) {
            TextButton(onClick = onSecondary) { Text(secondary, color = TextSecondary, fontSize = 13.sp) }
        }
        TextButton(onClick = onPrimary) {
            Text(primary, color = AccentBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Start an outgoing DCC send or chat: pick a network, type a nick, go. */
@Composable
private fun DccStartCard(client: LurkerClient, onOpenBuffer: (Buffer) -> Unit) {
    val context = LocalContext.current
    var nick by remember { mutableStateOf("") }
    val connected = client.networks.values.filter { it.connected }
    var networkId by remember(connected.map { it.id }) {
        mutableStateOf(connected.firstOrNull()?.id)
    }
    var netMenu by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val id = networkId
        val who = nick.trim()
        if (uri != null && id != null && who.isNotEmpty()) {
            val upload = readUpload(context, uri)
            if (upload == null) {
                client.dccError = "Couldn't read that file."
            } else {
                client.dccSendFile(id, who, upload.first, upload.second)
            }
        }
    }

    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (client.serverExtended) "Start a transfer or chat" else "Start a DCC chat",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    TextButton(onClick = { netMenu = true }) {
                        Text(
                            connected.firstOrNull { it.id == networkId }?.name ?: "No network",
                            color = AccentBlue,
                        )
                    }
                    AppDropdownMenu(expanded = netMenu, onDismissRequest = { netMenu = false }) {
                        connected.forEach { n ->
                            DropdownMenuItem(
                                text = { Text(n.name) },
                                onClick = { networkId = n.id; netMenu = false },
                            )
                        }
                    }
                }
                TextField(
                    value = nick,
                    onValueChange = { nick = it },
                    placeholder = { Text("nick", color = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceRaised,
                        unfocusedContainerColor = SurfaceRaised,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = AccentBlue,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // FORK-ONLY: outgoing DCC SEND.
                if (client.serverExtended) Button(
                    onClick = { picker.launch("*/*") },
                    enabled = networkId != null && nick.isNotBlank(),
                ) { Text("Send a file…") }
                TextButton(
                    onClick = {
                        val id = networkId ?: return@TextButton
                        client.dccChat(id, nick.trim(), open = true, focus = true)
                    },
                    enabled = networkId != null && nick.isNotBlank(),
                ) { Text("Start a chat") }
            }
        }
    }
}

@Composable
private fun TransferRow(client: LurkerClient, t: DccTransfer) {
    Column(Modifier.fillMaxWidth().padding(16.dp, 10.dp)) {
        Text(t.filename, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Text(
            "${if (t.isSend) "to" else "from"} ${t.peerNick} · ${t.state}" + (t.error?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        if (t.isActive && t.total > 0) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { t.progress }, modifier = Modifier.fillMaxWidth())
            Text(
                "${humanBytes(t.received)} / ${humanBytes(t.total)}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (t.isPending) {
                TextButton(onClick = { client.dccAccept(t.id) }) { Text("Accept") }
                TextButton(onClick = { client.dccReject(t.id) }) { Text("Reject") }
            } else if (t.isActive) {
                TextButton(onClick = { client.dccCancel(t.id) }) { Text("Cancel") }
            }
        }
    }
    HorizontalDivider()
}

private fun humanBytes(n: Long): String {
    if (n < 1024) return "$n B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = n.toDouble() / 1024
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024; i++
    }
    return String.format("%.1f %s", value, units[i])
}

/**
 * Per-buffer translation controls. Reading and posting are independent: one
 * translates what arrives, the other what you send.
 *
 * Languages are PICKERS, never text entry — a typo'd language code fails silently
 * at the API and looks like the feature is broken. Endpoint/key/model are genuinely
 * free-form and live in Settings.
 */
@Composable
private fun TranslateSheet(bufferKey: String, bufferName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { Prefs(context) }
    val off = Ui.translateBackend == TranslateBackend.OFF
    val reading = bufferKey in Ui.translateRead
    val outgoing = Ui.translateOutgoing[bufferKey]

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaised,
        title = { Text("Translate $bufferName", color = TextPrimary) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = AccentBlue) } },
        text = {
        Column {
        if (off) {
            Text(
                "No translator configured. Settings → Translation.",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Translate incoming", color = TextPrimary, fontSize = 15.sp)
                Text(
                    "Into ${languageName(Ui.translateTargetLang)}. Sends message text to your translator.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Switch(
                enabled = !off,
                checked = reading,
                onCheckedChange = { on ->
                    if (on) Ui.translateRead.add(bufferKey) else Ui.translateRead.remove(bufferKey)
                    prefs.translateRead = Ui.translateRead.toSet()
                    // Toggling must not leave a stale overlay on any cached row.
                    Translator.clear()
                },
            )
        }
        HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(vertical = 8.dp))
        Text("Post in another language", color = TextPrimary, fontSize = 15.sp)
        Text(
            "Your message is translated and shown for approval before it sends.",
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        LanguagePicker(
            selected = outgoing,
            enabled = !off,
            includeOff = true,
        ) { code ->
            if (code == null) Ui.translateOutgoing.remove(bufferKey) else Ui.translateOutgoing[bufferKey] = code
            prefs.translateOutgoing = Ui.translateOutgoing.toMap()
        }
        }
        },
    )
}

/** A dropdown of the supported languages. [includeOff] adds a leading "Off" entry
 *  (used for the outgoing picker, where not translating is the default). */
@Composable
private fun LanguagePicker(
    selected: String?,
    enabled: Boolean = true,
    includeOff: Boolean = false,
    onPick: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(enabled = enabled, onClick = { open = true }) {
            Text(
                selected?.let { languageName(it) } ?: "Off",
                color = if (enabled) AccentBlue else TextSecondary,
                fontSize = 15.sp,
            )
            Text(" ⌄", color = TextSecondary, fontSize = 13.sp)
        }
        AppDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (includeOff) {
                DropdownMenuItem(
                    text = { Text("Off", color = if (selected == null) AccentBlue else TextPrimary) },
                    onClick = { open = false; onPick(null) },
                )
            }
            TRANSLATE_LANGUAGES.forEach { (code, name) ->
                DropdownMenuItem(
                    text = { Text(name, color = if (code == selected) AccentBlue else TextPrimary) },
                    onClick = { open = false; onPick(code) },
                )
            }
        }
    }
}

/**
 * Device-local translation setup. Deliberately NOT synced: which translator a
 * device can reach is a property of that device, and the endpoint may be on a LAN.
 * Turning it on here doesn't translate anything by itself — each buffer opts in
 * from its own Translate menu, because translating ships that conversation's text
 * to the configured endpoint.
 */
@Composable
private fun TranslationCard(prefs: Prefs) {
    var backendMenu by remember { mutableStateOf(false) }
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp, 12.dp)) {
            Text("Translation", fontSize = 17.sp)
            Text(
                "Translate messages per channel. Text is sent to the translator you choose — " +
                    "nothing goes through Lurker.",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Service", color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Box {
                    TextButton(onClick = { backendMenu = true }) {
                        Text(
                            when (Ui.translateBackend) {
                                TranslateBackend.LIBRETRANSLATE -> "LibreTranslate"
                                TranslateBackend.OPENAI -> "OpenAI-compatible"
                                TranslateBackend.OFF -> "Off"
                            },
                            color = AccentBlue,
                            fontSize = 15.sp,
                        )
                        Text(" ⌄", color = TextSecondary, fontSize = 13.sp)
                    }
                    AppDropdownMenu(expanded = backendMenu, onDismissRequest = { backendMenu = false }) {
                        listOf(
                            TranslateBackend.OFF to "Off",
                            TranslateBackend.LIBRETRANSLATE to "LibreTranslate",
                            TranslateBackend.OPENAI to "OpenAI-compatible",
                        ).forEach { (b, label) ->
                            DropdownMenuItem(
                                text = { Text(label, color = if (b == Ui.translateBackend) AccentBlue else TextPrimary) },
                                onClick = {
                                    backendMenu = false
                                    Ui.translateBackend = b
                                    prefs.translateBackend = when (b) {
                                        TranslateBackend.LIBRETRANSLATE -> "libretranslate"
                                        TranslateBackend.OPENAI -> "openai"
                                        TranslateBackend.OFF -> "off"
                                    }
                                    // Backend change invalidates every cached answer.
                                    Translator.clear()
                                },
                            )
                        }
                    }
                }
            }
            if (Ui.translateBackend != TranslateBackend.OFF) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Read messages in", color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    LanguagePicker(selected = Ui.translateTargetLang) { code ->
                        code?.let {
                            Ui.translateTargetLang = it
                            prefs.translateTargetLang = it
                            Translator.clear()
                        }
                    }
                }
                SettingField("Endpoint", Ui.translateEndpoint) {
                    Ui.translateEndpoint = it; prefs.translateEndpoint = it; Translator.clear()
                }
                SettingField("API key (optional)", Ui.translateApiKey, secret = true) {
                    Ui.translateApiKey = it; prefs.translateApiKey = it; Translator.clear()
                }
                if (Ui.translateBackend == TranslateBackend.OPENAI) {
                    SettingField("Model", Ui.translateModel) {
                        Ui.translateModel = it; prefs.translateModel = it; Translator.clear()
                    }
                }
            }
        }
    }
}

/** A labelled free-text setting row (endpoint / key / model — the genuinely
 *  free-form ones; languages are always pickers). */
@Composable
private fun SettingField(label: String, value: String, secret: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}
