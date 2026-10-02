// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput

/**
 * Handles the Reply / Mark-read / Mute actions on highlight & DM notifications.
 * These surface automatically on a paired Wear OS watch (RemoteInput becomes a
 * voice/canned-reply affordance there), so this is our "watch support" without a
 * dedicated Wear module — it also enriches the phone's own notifications.
 *
 * The reply is sent through the process-scoped [LurkerApp.client]; it only lands
 * while the socket is alive (i.e. the app is running or the background-connect
 * service is on). Offline, execute() is a no-op — the reply is dropped.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val networkId = intent.getIntExtra(Notifier.EXTRA_NETWORK_ID, -1)
        val target = intent.getStringExtra(Notifier.EXTRA_TARGET)
        if (networkId < 0 || target.isNullOrEmpty()) return
        val client = (context.applicationContext as? LurkerApp)?.client ?: return
        // On a cold start the client was never started (no Activity ran), so it
        // has no socket: a reply would vanish, and mark-read/mute would no-op.
        val nm = NotificationManagerCompat.from(context)
        val notifId = "$networkId::$target".hashCode()

        when (intent.action) {
            Notifier.ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(Notifier.KEY_REPLY)?.toString()?.trim()
                if (text.isNullOrEmpty()) {
                    nm.cancel(notifId)
                } else if (!client.connected) {
                    // Not connected (the app was closed, or the link is down): the
                    // reply has nowhere to go. It used to vanish silently — keep it
                    // in front of the user instead.
                    DebugLog.w("notif", "reply to $target not sent: not connected")
                    Notifier.postReplyFailed(context, networkId, target, text)
                } else {
                    client.replyFromNotification(networkId, target, text)
                    DebugLog.i("notif", "reply sent to $target")
                    nm.cancel(notifId)
                }
            }
            Notifier.ACTION_MARK_READ -> {
                client.markReadFromNotification(networkId, target)
                nm.cancel(notifId)
            }
            Notifier.ACTION_MUTE -> {
                client.muteFromNotification(networkId, target)
                nm.cancel(notifId)
            }
        }
    }
}
