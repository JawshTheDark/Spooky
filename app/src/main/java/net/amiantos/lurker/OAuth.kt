// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * The pure half of browser sign-in (Lurker 2.3+, `docs/OAUTH.md`): PKCE, the
 * authorize URL, and reading the redirect back. No Android or network here so
 * every rule is a unit test — [LurkerClient.beginOAuth] / [LurkerClient.completeOAuth]
 * do the I/O.
 *
 * Lurker is a Mastodon-style OAuth 2 server: the app registers itself once per
 * server (no secret, PKCE `S256` mandatory), the member approves it in the
 * browser, and a one-time code is exchanged for a bearer token that never
 * expires. The token is sent exactly like a password-minted one, so nothing
 * downstream of sign-in knows the difference.
 */
object OAuth {
    /** Shown as the app's website host on the approval page. */
    const val CLIENT_URI = "https://github.com/JawshTheDark/lurker-android-upstream"

    /** Where the browser sends the member back. Lurker requires a reverse-DNS
     *  custom scheme (one with a dot), which both flavors' application ids are —
     *  `chat.irc.lurker:/oauth`, `net.amiantos.lurker:/oauth` — so the two
     *  installs never intercept each other's redirects. */
    fun redirectUri(appId: String): String = "$appId:/oauth"

    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    private val random = SecureRandom()

    /** RFC 7636 `code_verifier`: 64 characters of the unreserved set. */
    fun newVerifier(): String = buildString(64) {
        repeat(64) { append(UNRESERVED[random.nextInt(UNRESERVED.length)]) }
    }

    /** `BASE64URL(SHA-256(verifier))`, no padding. */
    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /** Opaque per-attempt nonce; the redirect must echo it or it isn't ours. */
    fun newState(): String = buildString(32) {
        repeat(32) { append(UNRESERVED[random.nextInt(62)]) } // alphanumerics only
    }

    fun authorizeUrl(
        authorizationEndpoint: String,
        clientId: String,
        redirectUri: String,
        codeChallenge: String,
        state: String,
    ): String {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val sep = if ('?' in authorizationEndpoint) "&" else "?"
        return authorizationEndpoint + sep +
            "response_type=code" +
            "&client_id=" + enc(clientId) +
            "&redirect_uri=" + enc(redirectUri) +
            "&code_challenge=" + enc(codeChallenge) +
            "&code_challenge_method=S256" +
            "&state=" + enc(state)
    }

    /** What the browser handed back on the custom-scheme redirect. Exactly one of
     *  [code] / [error] is set on a well-formed reply; [state] echoes ours. */
    data class Redirect(val code: String?, val error: String?, val state: String?)

    /** Null when the URI isn't an OAuth redirect at all (wrong scheme or path). */
    fun parseRedirect(uri: String, appId: String): Redirect? {
        val u = runCatching { URI(uri) }.getOrNull() ?: return null
        if (!u.scheme.equals(appId, ignoreCase = true)) return null
        if (u.path != "/oauth") return null
        val q = HashMap<String, String>()
        for (pair in (u.rawQuery ?: "").split('&')) {
            if (pair.isEmpty()) continue
            val k = pair.substringBefore('=')
            val v = pair.substringAfter('=', "")
            // Belt and braces: URI() above already rejects a malformed `%` escape,
            // but this runs on a bare thread, where a throw would take the app down.
            val dk = runCatching { URLDecoder.decode(k, "UTF-8") }.getOrNull() ?: continue
            val dv = runCatching { URLDecoder.decode(v, "UTF-8") }.getOrNull() ?: continue
            q[dk] = dv
        }
        return Redirect(code = q["code"], error = q["error"], state = q["state"])
    }
}

/**
 * Everything an authorization in flight needs to finish — persisted, not held
 * in memory, because the browser is a different app and ours may well be
 * killed before the member comes back.
 */
data class OAuthPending(
    val base: String,
    val clientId: String,
    val redirectUri: String,
    val verifier: String,
    val state: String,
    val tokenEndpoint: String,
) {
    fun toJson(): String = JSONObject()
        .put("base", base)
        .put("clientId", clientId)
        .put("redirectUri", redirectUri)
        .put("verifier", verifier)
        .put("state", state)
        .put("tokenEndpoint", tokenEndpoint)
        .toString()

    companion object {
        fun fromJson(s: String?): OAuthPending? {
            val o = runCatching { JSONObject(s ?: return null) }.getOrNull() ?: return null
            val p = OAuthPending(
                base = o.optString("base"),
                clientId = o.optString("clientId"),
                redirectUri = o.optString("redirectUri"),
                verifier = o.optString("verifier"),
                state = o.optString("state"),
                tokenEndpoint = o.optString("tokenEndpoint"),
            )
            return if (p.base.isEmpty() || p.clientId.isEmpty() || p.verifier.isEmpty() || p.tokenEndpoint.isEmpty()) null else p
        }
    }
}
