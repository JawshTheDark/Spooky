// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Saves a file the Lurker server holds for this account — a DCC transfer's or a
 * kept DCC file — into the phone's Downloads/Spooky folder. MediaStore needs no
 * storage permission from Android 10 on. Streams, so a large file costs a few KB
 * of memory, and its own thread, so it never queues behind (or blocks) the
 * client's REST calls.
 */
object ServerDownloads {
    private val pool by lazy { Executors.newSingleThreadExecutor() }
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** A name safe to save under: no path, no control characters, never empty. */
    internal fun safeName(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\')
            .filter { it.code >= 0x20 && it !in "<>:\"|?*" }
            .trim().trimStart('.').ifEmpty { "file" }.take(180)

    fun save(context: Context, client: LurkerClient, path: String, name: String) {
        val app = context.applicationContext
        val token = client.tokenForMedia
        val base = client.serverBaseUrl
        if (token == null || base.isEmpty()) return toast(app, "Not signed in.")
        val fileName = safeName(name)
        toast(app, "Saving $fileName…")
        pool.execute {
            val resolver = app.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Spooky")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            var target: android.net.Uri? = null
            try {
                val req = Request.Builder().url(base + path).header("Authorization", "Bearer $token").build()
                http.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) {
                        val why = when (res.code) {
                            409 -> "the transfer isn't finished"
                            410 -> "it's no longer on the server"
                            404 -> "it wasn't found"
                            else -> "HTTP ${res.code}"
                        }
                        return@execute toast(app, "Couldn't save $fileName: $why.")
                    }
                    res.header("Content-Type")?.substringBefore(';')?.takeIf { it.isNotBlank() }
                        ?.let { values.put(MediaStore.Downloads.MIME_TYPE, it) }
                    target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: return@execute toast(app, "Couldn't save $fileName: no room in Downloads.")
                    resolver.openOutputStream(target!!)?.use { out -> res.body!!.byteStream().copyTo(out) }
                        ?: throw java.io.IOException("can't write to Downloads")
                }
                resolver.update(target!!, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                toast(app, "Saved $fileName to Downloads/Spooky.")
            } catch (e: Exception) {
                target?.let { runCatching { resolver.delete(it, null, null) } }
                toast(app, "Couldn't save $fileName: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun toast(context: Context, text: String) {
        main.post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }
}
