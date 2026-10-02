// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0
package net.amiantos.lurker

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/** One direct-mode IRC/bouncer connection descriptor, secrets included. */
data class StoredNet(
    val id: Int,
    val name: String,
    val host: String,
    val port: Int,
    val tls: Boolean,
    val nick: String,
    val username: String?,
    val realname: String?,
    val serverPassword: String?,
    val saslAccount: String?,
    val saslPassword: String?,
    val channels: String?,
    val autoconnect: Boolean,
    /** "direct" | "soju" | "znc" — a UI hint; the engine treats them identically
     *  (bouncers bind a network via `user/network` in the username/SASL field). */
    val type: String = "direct",
) {
    /** UI-facing view — secrets collapse to has* booleans, matching NetworkConfig. */
    fun toConfig() = NetworkConfig(
        id = id, name = name, host = host, port = port, tls = tls, nick = nick,
        username = username, realname = realname, autoconnect = autoconnect,
        hasPassword = !serverPassword.isNullOrEmpty(),
        hasSaslPassword = !saslPassword.isNullOrEmpty(),
        saslAccount = saslAccount, blocked = false, type = type,
    )
}

/**
 * Local persistence for direct-mode networks. NOTE: secrets are stored in
 * app-private SharedPreferences in plaintext — the same posture as the existing
 * Lurker bearer token (see Prefs). Moving these to EncryptedSharedPreferences /
 * Keystore is a noted hardening residual.
 */
class DirectNetworkStore(context: Context) {
    private val sp = encryptedPrefs(context) ?: context.applicationContext
        .getSharedPreferences("lurker_direct", Context.MODE_PRIVATE)

    private companion object {
        /** AES-256 encrypted prefs for the secrets. security-crypto is alpha and
         *  can throw if the Keystore master key is unavailable/corrupt — fall back
         *  to plaintext prefs so direct mode still works (a noted tradeoff). */
        fun encryptedPrefs(context: Context): SharedPreferences? = try {
            val ctx = context.applicationContext
            val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                ctx, "lurker_direct_enc", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun list(): List<StoredNet> {
        val raw = sp.getString("networks", null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::fromJson) }
    }

    fun get(id: Int): StoredNet? = list().firstOrNull { it.id == id }

    /** Upsert from NetworkEditScreen's snake_case field map. Blank secret = keep
     *  the existing one (the form's "leave blank to keep" contract). Returns id. */
    fun upsert(id: Int?, fields: Map<String, Any?>): Int {
        val current = list().toMutableList()
        val existing = id?.let { eid -> current.firstOrNull { it.id == eid } }
        val newId = existing?.id ?: ((current.maxOfOrNull { it.id } ?: 0) + 1)
        fun s(k: String) = (fields[k] as? String)?.takeIf { it.isNotEmpty() }
        val net = StoredNet(
            id = newId,
            name = s("name") ?: existing?.name ?: s("host") ?: "network",
            host = s("host") ?: existing?.host ?: "",
            port = (fields["port"] as? Int) ?: existing?.port ?: 6697,
            tls = (fields["tls"] as? Boolean) ?: existing?.tls ?: true,
            nick = s("nick") ?: existing?.nick ?: "lurker",
            username = s("username") ?: existing?.username,
            realname = s("realname") ?: existing?.realname,
            serverPassword = s("server_password") ?: existing?.serverPassword,
            saslAccount = s("sasl_account") ?: existing?.saslAccount,
            saslPassword = s("sasl_password") ?: existing?.saslPassword,
            channels = s("default_channel") ?: existing?.channels,
            autoconnect = (fields["autoconnect"] as? Boolean) ?: existing?.autoconnect ?: true,
            type = s("type") ?: existing?.type ?: "direct",
        )
        if (existing != null) current[current.indexOf(existing)] = net else current.add(net)
        save(current)
        return newId
    }

    fun delete(id: Int) = save(list().filterNot { it.id == id })

    // ---- Ignore rules (direct mode has no server to sync them) -------------
    fun loadIgnores(): List<IgnoreRule> {
        val raw = sp.getString("ignores", null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            IgnoreRule(
                id = o.optLong("id"),
                networkId = if (o.has("networkId")) o.optInt("networkId") else null,
                mask = o.optString("mask").ifEmpty { null },
                channels = o.optJSONArray("channels")?.let { c -> (0 until c.length()).map { c.optString(it) } },
                pattern = o.optString("pattern").ifEmpty { null },
                patternKind = o.optString("patternKind").ifEmpty { "substr" },
                levels = o.optJSONArray("levels")?.let { l -> (0 until l.length()).map { l.optString(it) } } ?: emptyList(),
                isExcept = o.optBoolean("isExcept"),
            )
        }
    }

    // ---- Message tail persistence (offline history for direct mode) --------
    /** One buffer's recent tail, enough to recreate the sidebar + a page of chat. */
    data class PersistedBuffer(val networkId: Int?, val target: String, val networkName: String, val msgs: List<Msg>)

    fun saveMessages(buffers: List<PersistedBuffer>, tailPerBuffer: Int = 80) {
        val arr = JSONArray()
        buffers.forEach { b ->
            val m = JSONArray()
            b.msgs.takeLast(tailPerBuffer).forEach { msg ->
                m.put(JSONObject().apply {
                    put("id", msg.id); put("type", msg.type); put("nick", msg.nick)
                    put("text", msg.text); put("self", msg.self); put("system", msg.system)
                    msg.time?.let { put("time", it) }
                    msg.msgid?.let { put("msgid", it) }
                    msg.level?.let { put("level", it) }
                    put("e2e", msg.e2e)
                })
            }
            arr.put(JSONObject().apply {
                b.networkId?.let { put("networkId", it) }
                put("target", b.target); put("networkName", b.networkName); put("msgs", m)
            })
        }
        sp.edit { putString("messages", arr.toString()) }
    }

    fun loadMessages(): List<PersistedBuffer> {
        val raw = sp.getString("messages", null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val ms = o.optJSONArray("msgs") ?: JSONArray()
            val msgs = (0 until ms.length()).mapNotNull { j ->
                val m = ms.optJSONObject(j) ?: return@mapNotNull null
                Msg(
                    id = m.optLong("id"), type = m.optString("type"), nick = m.optString("nick"),
                    text = m.optString("text"), self = m.optBoolean("self"),
                    time = m.optString("time").ifEmpty { null }, system = m.optBoolean("system"),
                    level = m.optString("level").ifEmpty { null }, e2e = m.optBoolean("e2e"),
                    msgid = m.optString("msgid").ifEmpty { null },
                )
            }
            PersistedBuffer(
                networkId = if (o.has("networkId")) o.optInt("networkId") else null,
                target = o.optString("target"),
                networkName = o.optString("networkName"),
                msgs = msgs,
            )
        }
    }

    fun saveIgnores(rules: List<IgnoreRule>) {
        val arr = JSONArray()
        rules.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                r.networkId?.let { put("networkId", it) }
                r.mask?.let { put("mask", it) }
                r.channels?.let { put("channels", JSONArray(it)) }
                r.pattern?.let { put("pattern", it) }
                put("patternKind", r.patternKind)
                put("levels", JSONArray(r.levels))
                put("isExcept", r.isExcept)
            })
        }
        sp.edit { putString("ignores", arr.toString()) }
    }

    fun reorder(ids: List<Int>) {
        val byId = list().associateBy { it.id }
        save(ids.mapNotNull { byId[it] } + list().filter { it.id !in ids })
    }

    private fun save(nets: List<StoredNet>) {
        val arr = JSONArray()
        nets.forEach { arr.put(toJson(it)) }
        sp.edit { putString("networks", arr.toString()) }
    }

    private fun toJson(n: StoredNet) = JSONObject().apply {
        put("id", n.id); put("name", n.name); put("host", n.host); put("port", n.port)
        put("tls", n.tls); put("nick", n.nick)
        n.username?.let { put("username", it) }
        n.realname?.let { put("realname", it) }
        n.serverPassword?.let { put("serverPassword", it) }
        n.saslAccount?.let { put("saslAccount", it) }
        n.saslPassword?.let { put("saslPassword", it) }
        n.channels?.let { put("channels", it) }
        put("autoconnect", n.autoconnect); put("type", n.type)
    }

    private fun fromJson(o: JSONObject) = StoredNet(
        id = o.optInt("id"),
        name = o.optString("name"),
        host = o.optString("host"),
        port = o.optInt("port", 6697),
        tls = o.optBoolean("tls", true),
        nick = o.optString("nick"),
        username = o.optString("username").ifEmpty { null },
        realname = o.optString("realname").ifEmpty { null },
        serverPassword = o.optString("serverPassword").ifEmpty { null },
        saslAccount = o.optString("saslAccount").ifEmpty { null },
        saslPassword = o.optString("saslPassword").ifEmpty { null },
        channels = o.optString("channels").ifEmpty { null },
        autoconnect = o.optBoolean("autoconnect", true),
        type = o.optString("type").ifEmpty { "direct" },
    )
}
