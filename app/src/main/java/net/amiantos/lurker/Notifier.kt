// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput

/**
 * Posts local notifications for notify-worthy messages that arrive while the app
 * is backgrounded. Rendering is decoupled from delivery so a future FCM path can
 * feed the same [post] with a plain [NotifiableEvent] — no server push exists yet
 * (native FCM is roadmapped), so today this only covers the window the socket
 * stays alive after backgrounding.
 */
object Notifier {
    private const val CHANNEL_MENTIONS = "mentions"
    private const val CHANNEL_DMS = "dms"
    // Bumped from "connection" (was IMPORTANCE_LOW) so existing installs pick up
    // the quieter IMPORTANCE_MIN — Android locks a channel's importance after
    // creation, so lowering it requires a new channel id. The old one is deleted
    // in ensureChannels.
    const val CHANNEL_SERVICE = "connection_quiet"
    private const val CHANNEL_SERVICE_OLD = "connection"
    const val EXTRA_NETWORK_ID = "lurker.networkId"
    const val EXTRA_TARGET = "lurker.target"
    const val EXTRA_MESSAGE_ID = "lurker.messageId"

    // Notification-action intents (Reply carries RemoteInput text under KEY_REPLY).
    // These drive both the phone's notification actions and — for free — the
    // Reply/Mark-read/Mute affordances on a paired Wear OS watch.
    const val ACTION_REPLY = "net.amiantos.lurker.action.REPLY"
    const val ACTION_MARK_READ = "net.amiantos.lurker.action.MARK_READ"
    const val ACTION_MUTE = "net.amiantos.lurker.action.MUTE"
    const val KEY_REPLY = "lurker.replyText"

    fun ensureChannels(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_MENTIONS, "Mentions & highlights", NotificationManager.IMPORTANCE_HIGH),
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_DMS, "Direct messages", NotificationManager.IMPORTANCE_HIGH),
        )
        // Silent, minimal ongoing notification for the opt-in background service:
        // IMPORTANCE_MIN keeps it out of the status bar and collapsed at the bottom
        // of the shade — a quiet "permanent" indicator rather than a recurring alert
        // (freaky: it kept reappearing and demanding a swipe).
        mgr.deleteNotificationChannel(CHANNEL_SERVICE_OLD)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Background connection", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            },
        )
    }

    /** Replace a conversation's notification with "your reply didn't send", keeping
     *  the text so it isn't lost. Tapping opens the conversation. */
    fun postReplyFailed(context: Context, networkId: Int, target: String, text: String) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        ensureChannels(context)
        val tap = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NETWORK_ID, networkId)
            putExtra(EXTRA_TARGET, target)
        }
        val pi = PendingIntent.getActivity(
            context, "$networkId::$target".hashCode(), tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_DMS)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle("Reply not sent — not connected")
            .setContentText("“${text.take(120)}” — open the app to send it.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("“$text”\n\nOpen the app to send it."))
            .setAutoCancel(true)
            .setSilent(true)
            .setContentIntent(pi)
            .build()
        try {
            nm.notify("$networkId::$target".hashCode(), notif)
        } catch (_: SecurityException) {
        }
    }

    fun post(context: Context, e: NotifiableEvent) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        ensureChannels(context)
        val tap = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            e.networkId?.let { putExtra(EXTRA_NETWORK_ID, it) }
            putExtra(EXTRA_TARGET, e.target)
            if (e.msgId > 0) putExtra(EXTRA_MESSAGE_ID, e.msgId)
        }
        val pi = PendingIntent.getActivity(
            context,
            "${e.networkId}::${e.target}".hashCode(),
            tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (e.isDm) e.nick else "${e.nick} · ${e.target}"
        // Strip mIRC bold/color/etc. control codes — the notification is plain text,
        // so raw codes would show as literal "3,01" garbage.
        val body = Mirc.strip(e.text)

        // Reply / Mark-read / Mute actions — shown on the phone and, for free, as
        // wrist actions on a paired Wear OS watch (Reply becomes voice/canned).
        val idBase = "${e.networkId}::${e.target}".hashCode()
        fun action(name: String, req: Int, mutable: Boolean): PendingIntent {
            val i = Intent(context, NotificationActionReceiver::class.java)
                .setAction(name)
                .putExtra(EXTRA_NETWORK_ID, e.networkId ?: -1)
                .putExtra(EXTRA_TARGET, e.target)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE)
            return PendingIntent.getBroadcast(context, req, i, flags)
        }
        val replyInput = RemoteInput.Builder(KEY_REPLY)
            .setLabel("Reply")
            .setChoices(arrayOf("👍", "omw", "brb", "lol", "on it", "👋"))
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_notification, "Reply", action(ACTION_REPLY, idBase * 31 + 1, mutable = true),
        ).addRemoteInput(replyInput).setAllowGeneratedReplies(true).build()

        // MessagingStyle + CATEGORY_MESSAGE: Wear OS treats these as conversations,
        // renders them far better on the wrist, and bridges them more reliably than
        // a plain notification. (setLocalOnly is deliberately NOT set — that would
        // stop it reaching the watch at all.) The channel keeps its title as the
        // conversation name; the sender is the Person.
        val self = androidx.core.app.Person.Builder().setName("You").build()
        val sender = androidx.core.app.Person.Builder().setName(e.nick).build()
        val style = NotificationCompat.MessagingStyle(self)
            .setConversationTitle(if (e.isDm) null else e.target)
            .addMessage(body.take(400), System.currentTimeMillis(), sender)
        val notif = NotificationCompat.Builder(context, if (e.isDm) CHANNEL_DMS else CHANNEL_MENTIONS)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(body.take(200))
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setLocalOnly(false)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .addAction(replyAction)
            .addAction(0, "Mark read", action(ACTION_MARK_READ, idBase * 31 + 2, mutable = false))
            .addAction(0, "Mute", action(ACTION_MUTE, idBase * 31 + 3, mutable = false))
            .build()
        try {
            // One notification per buffer — a burst collapses onto the same id.
            nm.notify("${e.networkId}::${e.target}".hashCode(), notif)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted; nothing to show.
        }
    }
}
