// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.amiantos.lurker.ui.theme.AccentBlue
import net.amiantos.lurker.ui.theme.AlertRed
import net.amiantos.lurker.ui.theme.GlassBorder
import net.amiantos.lurker.ui.theme.SurfaceDark
import net.amiantos.lurker.ui.theme.SurfaceRaised
import net.amiantos.lurker.ui.theme.TextPrimary
import net.amiantos.lurker.ui.theme.TextSecondary
import net.amiantos.lurker.ui.theme.formatTime
import net.amiantos.lurker.ui.theme.nickColor

// Reply threads (Lurker 2.6, #1225). With the user's `chat.threads` setting on,
// a channel keeps replies out of its timeline: a line that started a thread
// carries a chip, and the thread opens in a sheet with its own composer.

/** "4 minutes ago" for an ISO time, or null when it doesn't parse. */
private fun agoText(iso: String?): String? = runCatching {
    val ms = java.time.Instant.parse(iso).toEpochMilli()
    DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
}.getOrNull()

/** `╰─ 3 replies 4 minutes ago` under a line that started a thread; accented while it has unread. */
@Composable
internal fun ThreadChip(replies: Int, lastTime: String?, unread: Int, alignEnd: Boolean, onOpen: () -> Unit) {
    val label = buildString {
        append("╰─ ").append(replies).append(if (replies == 1) " reply" else " replies")
        agoText(lastTime)?.let { append(" ").append(it.replaceFirstChar { c -> c.lowercase() }) }
        if (unread > 0) append(" · ").append(unread).append(" new")
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp), horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start) {
        Text(
            label,
            color = if (unread > 0) AccentBlue else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.clickable(onClick = onOpen).padding(vertical = 3.dp, horizontal = 4.dp),
        )
    }
}

/** One line in the thread sheet: name, time, text — compact, without reply quotes. */
@Composable
private fun ThreadLine(client: LurkerClient, buffer: Buffer, m: Msg, stripAddress: Boolean, onLink: (String) -> Unit) {
    // A reply's leading "nick: " is how the thread composer addresses it; inside the
    // thread it's noise, so it goes (as on the web).
    val text = if (stripAddress) m.text.replaceFirst(Regex("^\\S+: "), "") else m.text
    val shown = client.relayDisplay(buffer.networkId, m.copy(text = text))
    val name = if (shown.relayBot == null) client.displayNameFor(buffer.networkId, shown.nick) ?: shown.nick else shown.nick
    val body = remember(shown.text) { mircAnnotated(shown.text, AccentBlue, onLink) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = nickColor(shown.nick), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            shown.relaySource?.let { Text("  [$it]", color = TextSecondary, fontSize = 11.sp) }
            formatTime(m.time)?.let {
                Spacer(Modifier.width(8.dp))
                Text(it, color = TextSecondary, fontSize = 11.sp)
            }
        }
        Text(body, color = if (m.type == "notice") TextSecondary else TextPrimary, fontSize = 15.sp)
    }
}

/** The open thread: the line that started it, its replies, and a composer that answers in it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadSheet(client: LurkerClient, buffer: Buffer, onLink: (String) -> Unit) {
    val tv = client.threadView?.takeIf { it.bufferKey == buffer.key } ?: return
    var text by remember(tv.rootMsgid) { mutableStateOf("") }
    var error by remember(tv.rootMsgid) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(tv.replies.size) { if (tv.replies.isNotEmpty()) listState.animateScrollToItem(tv.replies.size + 1) }
    val canReply = tv.root?.let { client.canReplyTo(buffer, it) } ?: tv.replies.lastOrNull()?.let { client.canReplyTo(buffer, it) } ?: false
    ModalBottomSheet(
        onDismissRequest = { client.closeThread() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceDark.copy(alpha = SurfaceDark.alpha.coerceAtLeast(0.94f)),
    ) {
        Column(Modifier.imePadding()) {
            Text(
                "Thread",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.heightIn(max = 460.dp), state = listState) {
                item {
                    val root = tv.root
                    if (root != null) {
                        ThreadLine(client, buffer, root, stripAddress = false, onLink = onLink)
                    } else {
                        Text(
                            if (tv.loading) "Loading…" else "original message unavailable",
                            color = TextSecondary, fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                        )
                    }
                }
                item {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (tv.replies.size) {
                                0 -> if (tv.loading) "" else "no replies yet"
                                1 -> "1 reply"
                                else -> "${tv.replies.size} replies"
                            },
                            color = TextSecondary, fontSize = 12.sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        HorizontalDivider(color = GlassBorder, modifier = Modifier.weight(1f))
                    }
                    if (tv.truncated) {
                        Text(
                            "this thread is longer than can be shown at once — its oldest replies aren't shown",
                            color = TextSecondary, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
                        )
                    }
                }
                items(tv.replies.size, key = { i -> tv.replies[i].id.let { if (it > 0) "r$it" else "p$i" } }) { i ->
                    ThreadLine(client, buffer, tv.replies[i], stripAddress = buffer.isChannel, onLink = onLink)
                }
            }
            error?.let { Text(it, color = AlertRed, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp)) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    enabled = canReply && !client.accountPaused,
                    placeholder = { Text(if (canReply) "Reply in thread…" else "Can't reply here right now", fontSize = 14.sp) },
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = canReply && text.isNotBlank(),
                    onClick = {
                        val why = client.sendThreadReply(buffer, text)
                        if (why == null) text = "" else error = why
                    },
                ) { Text("Send", color = AccentBlue, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** The open buffer's threads, newest activity first ("All threads here" on the web). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadListSheet(client: LurkerClient, buffer: Buffer, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceDark.copy(alpha = SurfaceDark.alpha.coerceAtLeast(0.94f)),
    ) {
        Text(
            "Threads",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
        )
        val list = client.threadList
        when {
            list == null -> Text("Loading…", color = TextSecondary, modifier = Modifier.padding(18.dp))
            list.isEmpty() -> Text("No threads here yet. Reply to a line to start one.", color = TextSecondary, modifier = Modifier.padding(18.dp))
            else -> LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(list.size, key = { list[it].rootMsgid }) { i ->
                    val t = list[i]
                    val unread = client.followedThread(buffer, t.rootMsgid)?.unread ?: 0
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onDismiss(); client.openThread(buffer, t.rootMsgid) }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        val root = t.root
                        if (root != null) {
                            Text(root.nick, color = nickColor(root.nick), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(Mirc.strip(root.text), color = TextPrimary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        } else {
                            Text("original message unavailable", color = TextSecondary, fontSize = 13.sp)
                        }
                        Text(
                            buildString {
                                append(t.replies).append(if (t.replies == 1) " reply" else " replies")
                                agoText(t.lastReplyTime)?.let { append(" · ").append(it) }
                                if (unread > 0) append(" · ").append(unread).append(" new")
                            },
                            color = if (unread > 0) AccentBlue else TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(start = 18.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
