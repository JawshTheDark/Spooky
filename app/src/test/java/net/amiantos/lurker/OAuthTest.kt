// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthTest {
    @Test
    fun `PKCE challenge matches the RFC 7636 test vector`() {
        // docs/OAUTH.md quotes this pair for exactly this purpose.
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            OAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `verifier is 43-128 unreserved characters and fresh each time`() {
        val a = OAuth.newVerifier()
        val b = OAuth.newVerifier()
        assertTrue(a.length in 43..128)
        assertTrue(a.all { it.isLetterOrDigit() || it in "-._~" })
        assertNotEquals(a, b)
    }

    @Test
    fun `redirect uri is the reverse-DNS app id with the oauth path`() {
        assertEquals("chat.irc.lurker:/oauth", OAuth.redirectUri("chat.irc.lurker"))
        assertEquals("net.amiantos.lurker:/oauth", OAuth.redirectUri("net.amiantos.lurker"))
    }

    @Test
    fun `authorize url carries every PKCE parameter, encoded`() {
        val url = OAuth.authorizeUrl(
            "https://irc.example.com/oauth/authorize",
            "cid 1", "chat.irc.lurker:/oauth", "chal", "st",
        )
        assertTrue(url.startsWith("https://irc.example.com/oauth/authorize?response_type=code"))
        assertTrue("&client_id=cid+1" in url)
        assertTrue("&redirect_uri=chat.irc.lurker%3A%2Foauth" in url)
        assertTrue("&code_challenge=chal&code_challenge_method=S256" in url)
        assertTrue(url.endsWith("&state=st"))
    }

    @Test
    fun `approval redirect yields code and state`() {
        // Hosted codes start with the cell name and a tilde — must survive intact.
        val r = OAuth.parseRedirect("chat.irc.lurker:/oauth?code=cell1~abc%2Fdef&state=xyz", "chat.irc.lurker")!!
        assertEquals("cell1~abc/def", r.code)
        assertEquals("xyz", r.state)
        assertNull(r.error)
    }

    @Test
    fun `denial redirect yields the error, no code`() {
        val r = OAuth.parseRedirect("chat.irc.lurker:/oauth?error=access_denied&state=xyz", "chat.irc.lurker")!!
        assertNull(r.code)
        assertEquals("access_denied", r.error)
        assertEquals("xyz", r.state)
    }

    @Test
    fun `another app's scheme or a different path is not ours`() {
        assertNull(OAuth.parseRedirect("net.amiantos.lurker:/oauth?code=x", "chat.irc.lurker"))
        assertNull(OAuth.parseRedirect("chat.irc.lurker:/share?code=x", "chat.irc.lurker"))
        assertNull(OAuth.parseRedirect("not a uri", "chat.irc.lurker"))
    }

    @Test
    fun `pending state round-trips through json and rejects a gutted record`() {
        val p = OAuthPending(
            base = "https://irc.example.com", clientId = "cid", redirectUri = "chat.irc.lurker:/oauth",
            verifier = "v".repeat(43), state = "s", tokenEndpoint = "https://irc.example.com/api/oauth/token",
        )
        assertEquals(p, OAuthPending.fromJson(p.toJson()))
        assertNull(OAuthPending.fromJson(null))
        assertNull(OAuthPending.fromJson("{}"))
        assertNull(OAuthPending.fromJson("garbage"))
    }

    @Test
    fun aMalformedEscapeIsRejectedNotThrown() {
        // The redirect activity is exported, so any app can send us this; it runs
        // on a bare thread where a throw would kill the app.
        assertNull(OAuth.parseRedirect("chat.irc.lurker:/oauth?code=%zz&state=ok", "chat.irc.lurker"))
    }
}
