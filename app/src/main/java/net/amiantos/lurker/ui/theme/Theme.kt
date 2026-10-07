// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import net.amiantos.lurker.TranslateBackend
import androidx.compose.ui.text.font.FontFamily

/** The app's three looks. OLED is the original pure-black design. */
enum class AppTheme(val id: String, val label: String) {
    Light("light", "Light"),
    Dark("dark", "Dark"),
    Oled("oled", "OLED black"),
    ;

    companion object {
        fun from(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: Oled
    }
}

/** Global device-local UI prefs; the Activity seeds them from Prefs at launch. */
object Ui {
    var theme by mutableStateOf(AppTheme.Oled)
    var inlineMedia by mutableStateOf(true)
    /** Keyboard GIFs: upload the file (true) or send the link (false). */
    var gifUpload by mutableStateOf(false)

    /** Fetch OpenGraph preview cards for pasted (non-media) links. */
    var linkPreviews by mutableStateOf(true)

    /** Show a rich card with a YouTube link's title + description. */
    var youtubeDescriptions by mutableStateOf(true)

    // ---- Live translation (device-local mirror of Prefs) ---------------------
    var translateBackend by mutableStateOf(TranslateBackend.OFF)
    var translateEndpoint by mutableStateOf("https://translate.irc.so")
    var translateApiKey by mutableStateOf("")
    var translateModel by mutableStateOf("")

    /** The language incoming messages are translated INTO. */
    var translateTargetLang by mutableStateOf("en")

    /** Buffer keys with incoming translation on. */
    val translateRead = mutableStateListOf<String>()

    /** buffer.key -> outgoing language for composed messages. */
    val translateOutgoing = mutableStateMapOf<String, String>()

    /** Device-local sp added to the synced chat font size (0 = server default).
     *  Handy on tablets, where the mobile default reads tiny on a big screen. */
    var chatTextScale by mutableStateOf(0)

    /** Show message timestamps in 24-hour time (14:05) instead of 12-hour (2:05 PM). */
    var clock24h by mutableStateOf(false)

    /** Highlight-bubble colour override (ARGB); 0 = theme default gold. */
    var highlightColor by mutableStateOf(0)

    /** Compact message layout: dense IRC-style "time nick message" rows instead of
     *  the default iMessage bubbles (freakyy85). This is the DEFAULT; individual
     *  channels can override it below. */
    var compact by mutableStateOf(false)

    /** Per-buffer compact overrides (buffer.key -> compact?), toggled from each
     *  channel's control panel. Absent = fall back to the [compact] default. */
    val compactOverrides = mutableStateMapOf<String, Boolean>()

    /** Effective compact setting for a buffer: its override, else the default. */
    fun compactFor(key: String): Boolean = compactOverrides[key] ?: compact

    // ---- Appearance overhaul -------------------------------------------------

    /** User accent/UI colour override (ARGB); 0 = theme default. Recolours links,
     *  buttons, active toggles, own message bubbles, the send button, etc. */
    var accentColor by mutableStateOf(0)

    /** Chat font family: "system" | "rounded" | "mono" | "serif". */
    var fontFamily by mutableStateOf("system")

    /** Message density preset: "compact" | "cozy" | "comfortable" — scales the
     *  vertical rhythm between messages. */
    var density by mutableStateOf("cozy")

    /** Colour nicks by a stable hash (true) or render them plain (false). */
    var nickColors by mutableStateOf(true)

    /** Custom chat background image (content URI string), or null for none. */
    var backgroundUri by mutableStateOf<String?>(null)

    /** How much to dim a background image for text legibility (0f = none,
     *  1f = fully dimmed to the canvas colour). */
    var backgroundDim by mutableStateOf(0.55f)

    /** Per-buffer background override (buffer.key -> image path, or "" for "no
     *  background here" even if a global one is set). Absent = inherit [backgroundUri]. */
    val backgroundOverrides = mutableStateMapOf<String, String>()

    /** Effective background image path for a buffer: its override (null when the
     *  override is the "" = none sentinel), else the global default. */
    fun backgroundFor(key: String): String? =
        if (backgroundOverrides.containsKey(key)) backgroundOverrides[key]?.ifEmpty { null }
        else backgroundUri

    /** Vertical-rhythm multiplier for [density], applied to message spacing AND
     *  each bubble's own vertical padding, so the three settings read clearly. */
    val densityScale: Float get() = when (density) {
        "compact" -> 0.4f
        "comfortable" -> 1.7f
        else -> 1f
    }

    // ---- Multi-channel firehose ----------------------------------------------

    /** Buffer keys whose messages are mirrored into the Multi-channel view — a
     *  single pinned buffer that merges the timelines of every toggled channel,
     *  each line tagged with its source (network · channel) and tappable to jump
     *  to that channel + message. Device-local, seeded from Prefs at launch. */
    val multichan = mutableStateListOf<String>()

    /**
     * Replace the mirrored-channel set from persisted prefs.
     *
     * Seeding MUST be idempotent. This object lives for the whole process, but
     * MainActivity.onCreate runs again on every Activity recreation (rotation,
     * theme change, any config change) — so a plain `addAll` stacked another copy
     * of every key each time. The sibling maps above use `putAll`, which quietly
     * overwrites, which is why only this list drifted: the buffer-list count
     * climbed and each message rendered once per duplicate (d3fc0n).
     */
    fun seedMultichan(keys: Collection<String>) {
        multichan.clear()
        multichan.addAll(keys.distinct())
    }

    /** Render the Multi-channel view as dense compact rows (true) or bubbles
     *  (false, default). Independent of the per-channel [compact] setting. */
    var multichanCompact by mutableStateOf(false)

    fun inMultichan(key: String): Boolean = key in multichan

    /** The chat [FontFamily] for [fontFamily] (all built-in, no bundled fonts). */
    val chatFont: FontFamily get() = when (fontFamily) {
        "mono" -> FontFamily.Monospace
        "serif" -> FontFamily.Serif
        else -> FontFamily.Default
    }
}

private data class Palette(
    val canvas: Color,
    val surface: Color, // glass card fill (translucent — canvas ghosts through)
    val raised: Color, // bubbles and raised chrome
    val pill: Color,
    val border: Color, // hairline on glass edges
    val accent: Color,
    val red: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val amber: Color,
    val green: Color,
    val highlight: Color, // gold bubble fill for mentions/highlights
    val dark: Boolean,
)

// OLED: the original look — pure black, near-solid surfaces (translucency has
// nothing to show through on #000 anyway).
private val OledPalette = Palette(
    canvas = Color(0xFF000000),
    surface = Color(0xF01C1C1E),
    raised = Color(0xFF2C2C2E),
    pill = Color(0xFF3A3A3C),
    border = Color(0x16FFFFFF),
    accent = Color(0xFF0A84FF),
    red = Color(0xFFFF453A),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF98989F),
    amber = Color(0xFFFFD60A),
    green = Color(0xFF30D158),
    highlight = Color(0xFF4A3B12),
    dark = true,
)

// Dark: softened near-black with blue undertones and truly translucent glass.
private val DarkPalette = Palette(
    canvas = Color(0xFF12121C),
    surface = Color(0xB32A2A3A),
    raised = Color(0xD93A3A4C),
    pill = Color(0xFF4A4A5E),
    border = Color(0x22FFFFFF),
    accent = Color(0xFF0A84FF),
    red = Color(0xFFFF453A),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFFA0A0AE),
    amber = Color(0xFFFFD60A),
    green = Color(0xFF30D158),
    highlight = Color(0xFF4C4526),
    dark = true,
)

// Light: iOS-light flavored. Near-white canvas with clearly-grey received
// bubbles (iMessage-style) so peers don't wash into the background.
private val LightPalette = Palette(
    canvas = Color(0xFFF7F7FB),
    surface = Color(0xF2FFFFFF), // cards/composer: crisp near-opaque white
    raised = Color(0xFFE4E4EC), // received bubbles + pills — reads as a distinct grey
    pill = Color(0xFFD5D5DE),
    border = Color(0x1A000000),
    accent = Color(0xFF007AFF),
    red = Color(0xFFFF3B30),
    textPrimary = Color(0xFF1C1C1E),
    textSecondary = Color(0xFF6C6C72),
    amber = Color(0xFF9A6B00), // amber text needs contrast on white glass
    green = Color(0xFF34C759),
    highlight = Color(0xFFFDECB8), // light gold, distinct from the grey received bubble
    dark = false,
)

private val palette: Palette
    get() = when (Ui.theme) {
        AppTheme.Light -> LightPalette
        AppTheme.Dark -> DarkPalette
        AppTheme.Oled -> OledPalette
    }

// The pre-theming constant names, now theme-backed so every existing usage
// follows the switch. (The names date from the OLED-only era — "CanvasBlack"
// is white in the light theme; renaming them all is churn for another day.)
val CanvasBlack: Color get() = palette.canvas
val SurfaceDark: Color get() = palette.surface
val SurfaceRaised: Color get() = palette.raised
val PillGray: Color get() = palette.pill
val GlassBorder: Color get() = palette.border
/** The live accent: the user's override when set, else the theme default. */
val effectiveAccent: Color get() = if (Ui.accentColor != 0) Color(Ui.accentColor) else palette.accent
val AccentBlue: Color get() = effectiveAccent
val AlertRed: Color get() = palette.red
val TextPrimary: Color get() = palette.textPrimary
val TextSecondary: Color get() = palette.textSecondary
val NoticeAmber: Color get() = palette.amber
val OnlineGreen: Color get() = palette.green
val HighlightGold: Color get() = palette.highlight

@Composable
fun LurkerTheme(content: @Composable () -> Unit) {
    val p = palette
    val scheme = if (p.dark) {
        darkColorScheme(
            primary = effectiveAccent,
            onPrimary = Color.White,
            secondary = p.textSecondary,
            tertiary = p.amber,
            background = p.canvas,
            onBackground = p.textPrimary,
            surface = p.canvas,
            onSurface = p.textPrimary,
            surfaceVariant = p.raised,
            onSurfaceVariant = p.textPrimary,
            surfaceContainer = p.raised,
            surfaceContainerHigh = p.raised,
            outline = p.textSecondary,
            error = p.red,
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = effectiveAccent,
            onPrimary = Color.White,
            secondary = p.textSecondary,
            tertiary = p.amber,
            background = p.canvas,
            onBackground = p.textPrimary,
            surface = p.canvas,
            onSurface = p.textPrimary,
            surfaceVariant = p.raised,
            onSurfaceVariant = p.textPrimary,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White,
            outline = p.textSecondary,
            error = p.red,
            onError = Color.White,
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content,
    )
}
