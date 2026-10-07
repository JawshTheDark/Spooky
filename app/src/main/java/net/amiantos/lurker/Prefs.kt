// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

/**
 * Small persistent store for the session. The prototype held the token in
 * process memory, so every launch meant signing in again; keeping it here (in
 * app-private SharedPreferences) is what lets the app resume silently.
 *
 * The bearer *is* the session token — treat it like a password. It lives in
 * app-private storage, unreadable by other apps on a non-rooted device; moving
 * it into EncryptedSharedPreferences / the Keystore is the obvious next
 * hardening step and is noted in the README.
 */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("lurker", Context.MODE_PRIVATE)

    var serverUrl: String?
        get() = sp.getString(KEY_SERVER, null)
        set(v) = sp.edit { putString(KEY_SERVER, v) }

    var username: String?
        get() = sp.getString(KEY_USER, null)
        set(v) = sp.edit { putString(KEY_USER, v) }

    var token: String?
        get() = sp.getString(KEY_TOKEN, null)
        set(v) = sp.edit { putString(KEY_TOKEN, v) }

    val hasSession: Boolean get() = !token.isNullOrEmpty() && !serverUrl.isNullOrEmpty()

    /** This install's identity for OAuth: the application id is the redirect
     *  scheme (per flavor, so the two installs don't catch each other's
     *  redirects) and the launcher label is what the approval page shows. */
    val appId: String = context.applicationContext.packageName
    val appName: String = context.applicationContext.getString(R.string.app_name)

    /** The `client_id` Lurker issued us for one server. Kept per server and
     *  reused (OAUTH.md: an approved app's registration is permanent; one
     *  nobody approved is deleted after an hour). Null = register again. */
    fun oauthClientId(base: String): String? = sp.getString("oauthClient:$base", null)
    fun setOauthClientId(base: String, id: String?) = sp.edit {
        if (id == null) remove("oauthClient:$base") else putString("oauthClient:$base", id)
    }

    /** The authorization in flight ([OAuthPending] as JSON) while the member is
     *  off in the browser. Persisted because the browser is another app and
     *  ours may be killed before the redirect brings them back. */
    var oauthPending: String?
        get() = sp.getString("oauthPending", null)
        set(v) = sp.edit(commit = true) { if (v == null) remove("oauthPending") else putString("oauthPending", v) }

    /** Which backend to run: "lurker" (WebSocket to a Lurker server) or "direct"
     *  (raw IRC / bouncer via KICL). null = first run → mode picker. */
    var clientMode: String?
        get() = sp.getString("clientMode", null)
        // commit synchronously — the mode picker kills the process right after
        // setting this, and an async apply() would be lost before it flushes.
        set(value) = sp.edit(commit = true) { putString("clientMode", value) }

    /** Selected app theme id ("light" | "dark" | "oled"); null = default. */
    var theme: String?
        get() = sp.getString("theme", null)
        set(value) = sp.edit { putString("theme", value) }

    /** Auto-embed image/video links as inline thumbnails in chat (default on). */
    var inlineMedia: Boolean
        get() = sp.getBoolean("inlineMedia", true)
        set(value) = sp.edit { putBoolean("inlineMedia", value) }

    /** A GIF from the keyboard (GIPHY/Tenor) or a pasted image: upload the file
     *  through the server (true) or send its link (false, the default). */
    var gifUpload: Boolean
        get() = sp.getBoolean("gifUpload", false)
        set(value) = sp.edit { putBoolean("gifUpload", value) }

    /** Fetch OpenGraph cards (title/description/image) for pasted links that
     *  aren't already inline media (default on). */
    var linkPreviews: Boolean
        get() = sp.getBoolean("linkPreviews", true)
        set(value) = sp.edit { putBoolean("linkPreviews", value) }

    /** Show a YouTube link's title + full description as a rich card (default on). */
    var youtubeDescriptions: Boolean
        get() = sp.getBoolean("youtubeDescriptions", true)
        set(value) = sp.edit { putBoolean("youtubeDescriptions", value) }

    // ---- Live translation (device-local; never synced) ------------------------
    // Which translator a device can reach is a property of that device, and
    // translating ships message text to a third party — so all of this is local
    // and per-buffer, never server state.

    /** "off" | "libretranslate" | "openai". */
    var translateBackend: String
        get() = sp.getString("translateBackend", "off") ?: "off"
        set(value) = sp.edit { putString("translateBackend", value) }

    var translateEndpoint: String
        get() = sp.getString("translateEndpoint", "https://translate.irc.so") ?: "https://translate.irc.so"
        set(value) = sp.edit { putString("translateEndpoint", value) }

    var translateApiKey: String
        get() = sp.getString("translateApiKey", "") ?: ""
        set(value) = sp.edit { putString("translateApiKey", value) }

    var translateModel: String
        get() = sp.getString("translateModel", "") ?: ""
        set(value) = sp.edit { putString("translateModel", value) }

    /** The language incoming messages are translated INTO. */
    var translateTargetLang: String
        get() = sp.getString("translateTargetLang", "en") ?: "en"
        set(value) = sp.edit { putString("translateTargetLang", value) }

    /** Buffer keys with incoming translation switched on. */
    var translateRead: Set<String>
        get() = sp.getStringSet("translateRead", emptySet()) ?: emptySet()
        set(value) = sp.edit { putStringSet("translateRead", value) }

    /** buffer.key -> outgoing language code for composed messages. */
    var translateOutgoing: Map<String, String>
        get() = runCatching {
            val s = sp.getString("translateOutgoing", null) ?: return emptyMap()
            val o = JSONObject(s)
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
        set(value) = sp.edit { putString("translateOutgoing", JSONObject(value as Map<*, *>).toString()) }

    /** Device-local sp added to the synced chat font size (0 = server default). */
    var chatTextScale: Int
        get() = sp.getInt("chatTextScale", 0)
        set(value) = sp.edit { putInt("chatTextScale", value) }

    /** Show message timestamps in 24-hour time (default off = 12-hour). */
    var clock24h: Boolean
        get() = sp.getBoolean("clock24h", false)
        set(value) = sp.edit { putBoolean("clock24h", value) }

    /** Dense IRC-style message rows instead of iMessage bubbles — the DEFAULT for
     *  channels without a per-channel override (default off). */
    var compactMessages: Boolean
        get() = sp.getBoolean("compactMessages", false)
        set(value) = sp.edit { putBoolean("compactMessages", value) }

    /** Per-buffer compact overrides (buffer.key -> Boolean), JSON-encoded. */
    var compactOverrides: Map<String, Boolean>
        get() = runCatching {
            val s = sp.getString("compactOverrides", null) ?: return emptyMap()
            val o = JSONObject(s)
            o.keys().asSequence().associateWith { o.getBoolean(it) }
        }.getOrDefault(emptyMap())
        set(value) = sp.edit { putString("compactOverrides", JSONObject(value as Map<*, *>).toString()) }

    /** Highlight-message bubble colour (ARGB int). 0 = theme default (gold). */
    var highlightColor: Int
        get() = sp.getInt("highlightColor", 0)
        set(value) = sp.edit { putInt("highlightColor", value) }

    // ---- Appearance overhaul -------------------------------------------------

    /** Accent/UI colour override (ARGB). 0 = theme default. */
    var accentColor: Int
        get() = sp.getInt("accentColor", 0)
        set(value) = sp.edit { putInt("accentColor", value) }

    /** Chat font family: "system" | "rounded" | "mono" | "serif". */
    var fontFamily: String
        get() = sp.getString("fontFamily", "system") ?: "system"
        set(value) = sp.edit { putString("fontFamily", value) }

    /** Message density: "compact" | "cozy" | "comfortable". */
    var density: String
        get() = sp.getString("density", "cozy") ?: "cozy"
        set(value) = sp.edit { putString("density", value) }

    /** Colour nicks by hash (true) or render them plain (false). */
    var nickColors: Boolean
        get() = sp.getBoolean("nickColors", true)
        set(value) = sp.edit { putBoolean("nickColors", value) }

    /** Custom chat background image URI (persisted content:// string), or null. */
    var backgroundUri: String?
        get() = sp.getString("backgroundUri", null)
        set(value) = sp.edit { if (value == null) remove("backgroundUri") else putString("backgroundUri", value) }

    /** Background-image dim, 0f..1f. */
    var backgroundDim: Float
        get() = sp.getFloat("backgroundDim", 0.55f)
        set(value) = sp.edit { putFloat("backgroundDim", value) }

    /** Per-buffer background overrides (buffer.key -> path; "" = none), JSON. */
    var backgroundOverrides: Map<String, String>
        get() = runCatching {
            val s = sp.getString("backgroundOverrides", null) ?: return emptyMap()
            val o = JSONObject(s)
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
        set(value) = sp.edit { putString("backgroundOverrides", JSONObject(value as Map<*, *>).toString()) }

    /** Buffer keys mirrored into the Multi-channel firehose view. */
    var multichanKeys: Set<String>
        get() = sp.getStringSet("multichanKeys", emptySet())?.toSet() ?: emptySet()
        set(value) = sp.edit { putStringSet("multichanKeys", value) }

    /** Multi-channel view uses compact rows (true) or bubbles (false = default). */
    var multichanCompact: Boolean
        get() = sp.getBoolean("multichanCompact", false)
        set(value) = sp.edit { putBoolean("multichanCompact", value) }

    /** Buffer keys known to carry E2E — drives the sidebar lock before a buffer's
     *  history is loaded. (SharedPreferences returns an unmodifiable set; copy.) */
    var e2eBuffers: Set<String>
        get() = sp.getStringSet("e2eBuffers", emptySet())?.toSet() ?: emptySet()
        set(value) = sp.edit { putStringSet("e2eBuffers", value) }

    /** Require a biometric / device-credential unlock to open the app (default off). */
    var biometricLock: Boolean
        get() = sp.getBoolean("biometricLock", false)
        set(value) = sp.edit { putBoolean("biometricLock", value) }

    /** Require a biometric unlock to open an encrypted (E2E) channel; re-locks when
     *  the app is backgrounded (amiantos's idea). Independent of the app-open lock. */
    var e2eBiometricLock: Boolean
        get() = sp.getBoolean("e2eBiometricLock", false)
        set(value) = sp.edit { putBoolean("e2eBiometricLock", value) }

    /** Hold the connection open in the background via a foreground service so
     *  highlight/DM notifications keep arriving. Opt-in — off = no persistent
     *  notification, rely on ?since= resume on return (default off). */
    var backgroundConnect: Boolean
        get() = sp.getBoolean("backgroundConnect", false)
        set(value) = sp.edit { putBoolean("backgroundConnect", value) }

    /** Persist a fresh session after a successful token mint. */
    fun saveSession(server: String, username: String, token: String) {
        sp.edit {
            putString(KEY_SERVER, server)
            putString(KEY_USER, username)
            putString(KEY_TOKEN, token)
        }
    }

    /** Drop only the token on sign-out; server URL and username prefill re-login. */
    fun clearSession() {
        sp.edit { remove(KEY_TOKEN) }
    }

    private companion object {
        const val KEY_SERVER = "serverUrl"
        const val KEY_USER = "username"
        const val KEY_TOKEN = "token"
    }
}
