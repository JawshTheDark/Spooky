// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.amiantos.lurker.ui.theme.AccentBlue
import net.amiantos.lurker.ui.theme.AlertRed
import net.amiantos.lurker.ui.theme.CanvasBlack
import net.amiantos.lurker.ui.theme.GlassBorder
import net.amiantos.lurker.ui.theme.NoticeAmber
import net.amiantos.lurker.ui.theme.OnlineGreen
import net.amiantos.lurker.ui.theme.SurfaceDark
import net.amiantos.lurker.ui.theme.SurfaceRaised
import net.amiantos.lurker.ui.theme.TextPrimary
import net.amiantos.lurker.ui.theme.TextSecondary

/** Who a DCC send goes to, held while a file is picked. */
data class DccSendTarget(
    val networkId: Int,
    val nick: String,
    val secure: Boolean = false,
    val passive: Boolean = false,
)

/**
 * The DCC screen, laid out as Lurker 2.5's DCC window is: what's **waiting for
 * you**, what's **in progress** (grouped by who it's with — a bot's group is its
 * XDCC queue), what **finished**, and the files you **keep** on the server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DccScreen(client: LurkerClient, onOpenBuffer: (Buffer) -> Unit, onBack: () -> Unit) {
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
                title = { Text("DCC", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        val cfg = client.dccConfig
        val all = remember(client.transfers.toMap()) { client.transfers.values.toList() }
        val files = remember(client.dccFiles.toMap()) { client.dccFiles.values.sortedByDescending { it.id } }
        // Off for this account: nothing to start, and nothing already here.
        val nothingGranted = if (cfg != null) !cfg.chat && !cfg.receive && !cfg.send else !client.dccEnabled
        if (nothingGranted && all.isEmpty() && files.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Text(
                    "DCC isn't turned on for this account. Your server's admin can grant it.",
                    Modifier.padding(24.dp),
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }
        val context = LocalContext.current
        var askPartial by remember { mutableStateOf<Int?>(null) }
        var sendingKept by remember { mutableStateOf<DccFile?>(null) }
        var sendTo by remember { mutableStateOf<DccSendTarget?>(null) }
        // Newer Lurker: files stay on the server and can be saved, cleared, sent.
        val keeps = cfg != null
        val waiting = all.filter { it.isPending && !it.isSend }.sortedByDescending { it.id }
        val moving = all.filter { !it.isPending && (it.isActive || it.canRetry || it.hasPartial) }
        val busyIds = (waiting + moving).map { it.id }.toSet()
        val finished = all.filter { it.id !in busyIds }.sortedByDescending { it.id }
        val groups = moving.groupBy { (it.networkId ?: 0) to it.peerNick.lowercase() }
            .values.sortedByDescending { g -> g.maxOf { it.id } }
            .map { g -> g.sortedWith(compareBy({ progressRank(it) }, { it.queueSeq ?: Int.MAX_VALUE }, { -it.id })) }
        val manyNetworks = client.networks.size > 1

        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            client.dccError?.let { err ->
                item {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(err, color = AlertRed, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { client.dccError = null }) { Text("OK", color = TextSecondary) }
                    }
                }
            }
            if (client.dccCanChat || client.dccCanSend || cfg?.receive == true) {
                item { DccStartCard(client, onOpenBuffer, onSend = { sendTo = it }) }
            }

            if (waiting.isNotEmpty()) {
                item { SectionHeader("Waiting for you") }
                waiting.forEach { t -> item(key = "w${t.id}") { TransferRow(client, t, manyNetworks) } }
            }

            item {
                SectionHeader(
                    "In progress",
                    trailing = if (moving.isEmpty()) null else "${moving.size}",
                )
            }
            if (groups.isEmpty()) {
                item { Text("Nothing moving.", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = TextSecondary, fontSize = 14.sp) }
            }
            groups.forEach { g ->
                val first = g.first()
                val queued = g.filter { it.isQueuedPack }
                item(key = "g${first.networkId}:${first.peerNick.lowercase()}") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            first.peerNick + (if (manyNetworks && first.networkId != null) " · ${client.networkName(first.networkId)}" else "") +
                                (if (queued.isNotEmpty()) " · ${queued.size} queued" else ""),
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        val nid = first.networkId
                        if (queued.size > 1 && nid != null) {
                            TextButton(onClick = { client.xdccRemove(nid, first.peerNick, null) }) {
                                Text("Remove all", color = AlertRed, fontSize = 13.sp)
                            }
                        }
                    }
                }
                g.forEach { t ->
                    item(key = "m${t.id}") {
                        TransferRow(
                            client, t, manyNetworks = false,
                            onClear = { client.dccClear(t.id, onPartial = { askPartial = t.id }) },
                        )
                    }
                }
            }

            if (finished.isNotEmpty()) {
                item {
                    SectionHeader(
                        "Finished",
                        action = if (keeps) "Clear finished" else null,
                        onAction = { client.dccClearFinished() },
                    )
                }
                finished.forEach { t ->
                    item(key = "f${t.id}") {
                        TransferRow(
                            client, t, manyNetworks,
                            onSave = { ServerDownloads.save(context, client, client.dccTransferFilePath(t.id), t.filename) },
                            onClear = { client.dccClear(t.id, onPartial = { askPartial = t.id }) },
                        )
                    }
                }
            }

            if (keeps && (files.isNotEmpty() || cfg?.receive == true || cfg?.send == true)) {
                item {
                    SectionHeader(
                        "Kept",
                        trailing = cfg?.let { c ->
                            humanBytes(c.usedBytes) + (c.limitBytes?.let { " of ${humanBytes(it)}" } ?: " used")
                        },
                    )
                }
                if (files.isEmpty()) {
                    item { Text("No files on the server.", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = TextSecondary, fontSize = 14.sp) }
                }
                files.forEach { f ->
                    item(key = "k${f.id}") {
                        DccFileRow(
                            f,
                            networkName = f.networkId?.let { client.networkName(it) },
                            onSave = { ServerDownloads.save(context, client, client.dccFilePath(f.id), f.name) },
                            onSend = if (cfg?.send == true) ({ sendingKept = f }) else null,
                            onDelete = { client.dccDeleteFile(f.id) },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        askPartial?.let { id ->
            AlertDialog(
                onDismissRequest = { askPartial = null },
                containerColor = SurfaceRaised,
                title = { Text("Delete the partial download?", color = TextPrimary) },
                text = { Text("This transfer still holds the part of the file that arrived. Clearing it deletes that too.", color = TextSecondary) },
                confirmButton = {
                    TextButton(onClick = { askPartial = null; client.dccClear(id, deletePartial = true) }) {
                        Text("Delete", color = AlertRed, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = { TextButton(onClick = { askPartial = null }) { Text("Keep", color = TextSecondary) } },
            )
        }
        sendingKept?.let { f -> DccSendKeptDialog(client, f) { sendingKept = null } }
        sendTo?.let { t -> DccSendPicker(client, t) { sendTo = null } }
    }
}

/** Moving first, then what waits its turn, then what ended. */
private fun progressRank(t: DccTransfer): Int = when {
    t.state in setOf("receiving", "sending", "verifying", "connecting", "offered") -> 0
    t.state == "requested" -> 1
    t.state == "queued" -> 2
    t.state == "stalled" -> 3
    else -> 4
}

@Composable
private fun SectionHeader(title: String, trailing: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, color = TextSecondary, fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action, color = AccentBlue, fontSize = 13.sp) }
    }
}

/** A transfer state as people read it. */
internal fun dccStateLabel(state: String): String = when (state) {
    "pending_approval" -> "waiting for you"
    "offered" -> "waiting for them"
    "completed" -> "done"
    "requested" -> "asked the bot"
    "verifying" -> "checking the checksum"
    "stalled" -> "interrupted"
    else -> state.replace('_', ' ')
}

/** "3 min left" for [remaining] bytes at [rate] a second; null when it can't say. */
internal fun etaText(remaining: Long, rate: Long): String? {
    if (rate <= 0 || remaining <= 0) return null
    val s = remaining / rate
    return when {
        s < 60 -> "${s.coerceAtLeast(1)} s left"
        s < 3600 -> "${(s + 30) / 60} min left"
        else -> "${s / 3600} h ${(s % 3600) / 60} min left"
    }
}

internal fun humanBytes(n: Long): String {
    if (n < 1024) return "$n B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = n.toDouble() / 1024
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024; i++
    }
    return String.format("%.1f %s", value, units[i])
}

@Composable
private fun TransferRow(
    client: LurkerClient,
    t: DccTransfer,
    manyNetworks: Boolean,
    onSave: () -> Unit = {},
    onClear: () -> Unit = {},
) {
    val cfg = client.dccConfig
    val keeps = cfg != null
    val nid = t.networkId
    Column(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
        val name = if (DccCommands.isPlaceholderName(t.filename, t.pack)) "Pack #${t.pack}" else t.filename
        Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val state = if (t.state == "requested" && t.botAcked) "in the bot's queue" else dccStateLabel(t.state)
        val bits = buildList {
            add("#${t.id}")
            add((if (t.isSend) "to " else "from ") + t.peerNick + (if (manyNetworks && nid != null) " on ${client.networkName(nid)}" else ""))
            if (t.pack != null && !DccCommands.isPlaceholderName(t.filename, t.pack)) add("pack #${t.pack}")
            add(state)
            if (t.secure) add("secure")
            if (t.passive) add("passive")
            if (t.attempt > 1 && !t.isFinished) add("second try")
            if (t.total > 0 && !t.isActive) add(humanBytes(t.total))
        }
        Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        if (t.isCompleted && !t.isSend) CrcNote(t.crcStatus)
        t.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AlertRed) }
        if (t.isPending && t.passive && cfg?.canListen == false) {
            Text("They're firewalled, and your server can't take a passive offer.", style = MaterialTheme.typography.bodySmall, color = NoticeAmber)
        }
        val moving = t.state == "receiving" || t.state == "sending" || t.state == "verifying" || (t.state == "stalled" && t.received > 0)
        if (moving && t.total > 0) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { t.progress }, modifier = Modifier.fillMaxWidth())
            val rate = client.transferRates[t.id] ?: 0L
            val line = buildList {
                add("${humanBytes(t.received)} / ${humanBytes(t.total)}")
                if (rate > 0) add("${humanBytes(rate)}/s")
                etaText(t.total - t.received, rate)?.let { add(it) }
            }
            Text(line.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            when {
                t.isPending -> {
                    // Accepting needs the receive grant; turning it down never does.
                    val canAccept = client.dccCanReceive && !(t.passive && cfg?.canListen == false)
                    if (canAccept) TextButton(onClick = { client.dccAccept(t.id) }) { Text("Accept") }
                    TextButton(onClick = { client.dccReject(t.id) }) { Text("Decline") }
                }
                t.isQueuedPack && nid != null ->
                    TextButton(onClick = { client.xdccRemove(nid, t.peerNick, listOf(t.pack!!)) }) { Text("Remove", color = AlertRed) }
                t.isActive -> TextButton(onClick = { client.dccCancel(t.id) }) { Text("Cancel") }
            }
            if (t.canRetry && client.dccCanReceive) TextButton(onClick = { client.dccRetry(t.id) }) { Text("Retry") }
            if (t.hasPartial) TextButton(onClick = { client.dccDeleteTransferFile(t.id) }) { Text("Delete partial", color = AlertRed) }
            // Its file lives under Kept once kept; before (or on a server that lists
            // none), save it from the row.
            if (keeps && t.isCompleted && !t.isSend && t.hasFile && (t.fileId == null || t.fileId !in client.dccFiles)) {
                TextButton(onClick = onSave) { Text("Save to phone") }
            }
            if (keeps && t.isFinished) TextButton(onClick = onClear) { Text("Clear", color = TextSecondary) }
        }
    }
    HorizontalDivider(color = SurfaceRaised)
}

/** How a received file checked out against the CRC32 in its name. Silent when
 *  there was nothing to check, or it couldn't be read back. */
@Composable
private fun CrcNote(status: String?) {
    when (status) {
        "ok" -> Text("✓ Verified", style = MaterialTheme.typography.bodySmall, color = OnlineGreen)
        "mismatch" -> Text("✗ Checksum mismatch — the file is damaged. Get it again.", style = MaterialTheme.typography.bodySmall, color = AlertRed)
    }
}

/** One of the account's kept DCC files: save it to the phone, send it on, or delete it. */
@Composable
private fun DccFileRow(f: DccFile, networkName: String?, onSave: () -> Unit, onSend: (() -> Unit)?, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
        Text(f.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            (if (f.origin == "uploaded") "uploaded" else "from ${f.peerNick ?: "someone"}" + (networkName?.let { " on $it" } ?: "")) +
                " · ${humanBytes(f.size)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        CrcNote(f.crcStatus)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TextButton(onClick = onSave) { Text("Save to phone") }
            if (onSend != null) TextButton(onClick = onSend) { Text("Send…") }
            TextButton(onClick = { confirm = true }) { Text("Delete", color = AlertRed) }
        }
    }
    HorizontalDivider(color = SurfaceRaised)
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = SurfaceRaised,
            title = { Text("Delete ${f.name}?", color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = { Text("It's removed from the server for good. A copy you saved to the phone stays.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onDelete() }) { Text("Delete", color = AlertRed, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep", color = TextSecondary) } },
        )
    }
}

/** Offer a kept file to someone: pick the network, type the nick. */
@Composable
private fun DccSendKeptDialog(client: LurkerClient, f: DccFile, onDone: () -> Unit) {
    val connected = client.networks.values.filter { it.connected }
    var networkId by remember { mutableStateOf(f.networkId?.takeIf { id -> connected.any { it.id == id } } ?: connected.firstOrNull()?.id) }
    var nick by remember { mutableStateOf("") }
    var secure by remember { mutableStateOf(false) }
    val ok = networkId != null && isSinglePeerTarget(nick.trim())
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = SurfaceRaised,
        title = { Text("Send ${f.name}", color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NetworkChips(connected, networkId) { networkId = it }
                if (connected.isEmpty()) Text("No network is connected.", color = AlertRed)
                NickField(nick, Modifier.fillMaxWidth()) { nick = it }
                SecureToggle(secure) { secure = it }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                networkId?.let { client.dccSendKeptFile(it, nick.trim(), f.id, secure = secure) }
                onDone()
            }) { Text("Send", color = if (ok) AccentBlue else TextSecondary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel", color = TextSecondary) } },
    )
}

/**
 * Pick what to send [target]: one of the files the account keeps, or a new one
 * from the phone. With nothing kept (or on a server that keeps none) the phone's
 * picker opens straight away.
 */
@Composable
internal fun DccSendPicker(client: LurkerClient, target: DccSendTarget, onDone: () -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val upload = readUpload(context, uri)
            if (upload == null) {
                Toast.makeText(context, "Couldn't read that file.", Toast.LENGTH_SHORT).show()
            } else {
                client.dccSendFile(
                    target.networkId, target.nick, upload.first, upload.second, uploadSourceFor(context, uri),
                    passive = target.passive, secure = target.secure,
                )
                Toast.makeText(context, "Sending ${upload.first} to ${target.nick}…", Toast.LENGTH_SHORT).show()
            }
        }
        onDone()
    }
    val files = remember(client.dccFiles.toMap()) { client.dccFiles.values.sortedByDescending { it.id } }
    if (client.dccConfig?.send != true || files.isEmpty()) {
        LaunchedEffect(target) { picker.launch("*/*") }
        return
    }
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = SurfaceRaised,
        title = {
            Text(
                (if (target.secure) "Secure send to " else "Send to ") + target.nick,
                color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column {
                Text("A file you keep:", color = TextSecondary, fontSize = 13.sp)
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    files.forEach { f ->
                        item(key = f.id) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        client.dccSendKeptFile(
                                            target.networkId, target.nick, f.id,
                                            passive = target.passive, secure = target.secure,
                                        )
                                        onDone()
                                    }
                                    .padding(vertical = 8.dp),
                            ) {
                                Text(f.name, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(humanBytes(f.size), color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { picker.launch("*/*") }) { Text("Upload a new file…", color = AccentBlue, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel", color = TextSecondary) } },
    )
}

@Composable
private fun NetworkChips(connected: List<Network>, selected: Int?, onPick: (Int) -> Unit) {
    if (connected.size <= 1) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        connected.forEach { n ->
            val on = n.id == selected
            Text(
                n.name,
                color = if (on) Color.White else TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .background(if (on) AccentBlue else SurfaceDark, RoundedCornerShape(8.dp))
                    .clickable { onPick(n.id) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun NickField(value: String, modifier: Modifier = Modifier, placeholder: String = "nick", onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, color = TextSecondary) },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SurfaceDark,
            unfocusedContainerColor = SurfaceDark,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = AccentBlue,
        ),
        modifier = modifier,
    )
}

/** Secure DCC (KVIrc's SCHAT / SSEND): the same chat or transfer inside TLS. */
@Composable
private fun SecureToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChange(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onChange, colors = CheckboxDefaults.colors(checkedColor = AccentBlue))
        Column {
            Text("Secure (TLS)", color = TextPrimary, fontSize = 14.sp)
            Text("KVIrc and other Lurkers. Stops eavesdropping, not impersonation.", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

/** Start an outgoing DCC send or chat, or queue XDCC packs: pick a network, type a nick, go. */
@Composable
private fun DccStartCard(client: LurkerClient, onOpenBuffer: (Buffer) -> Unit, onSend: (DccSendTarget) -> Unit) {
    val context = LocalContext.current
    var nick by remember { mutableStateOf("") }
    var packs by remember { mutableStateOf("") }
    var secure by remember { mutableStateOf(false) }
    val connected = client.networks.values.filter { it.connected }
    var networkId by remember(connected.map { it.id }) { mutableStateOf(connected.firstOrNull()?.id) }
    var netMenu by remember { mutableStateOf(false) }
    val cfg = client.dccConfig
    // Secure DCC and the XDCC queue came with per-account grants (Lurker 2.5).
    val newer = cfg != null
    val who = nick.trim()
    val ready = networkId != null && isSinglePeerTarget(who) && !who.startsWith("=")

    Surface(
        color = SurfaceDark.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when {
                    client.dccCanSend && client.dccCanChat -> "Send a file or start a chat"
                    client.dccCanSend -> "Send a file"
                    client.dccCanChat -> "Start a DCC chat"
                    else -> "Get packs from a bot"
                },
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    TextButton(onClick = { netMenu = true }) {
                        Text(connected.firstOrNull { it.id == networkId }?.name ?: "No network", color = AccentBlue)
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = netMenu,
                        onDismissRequest = { netMenu = false },
                        containerColor = SurfaceRaised,
                    ) {
                        connected.forEach { n ->
                            DropdownMenuItem(text = { Text(n.name) }, onClick = { networkId = n.id; netMenu = false })
                        }
                    }
                }
                NickField(nick, Modifier.weight(1f), placeholder = "nick or bot") { nick = it }
            }
            if (newer && (client.dccCanSend || client.dccCanChat)) SecureToggle(secure) { secure = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (client.dccCanSend) Button(
                    onClick = { networkId?.let { onSend(DccSendTarget(it, who, secure = secure)) } },
                    enabled = ready,
                ) { Text("Send a file…") }
                if (client.dccCanChat) TextButton(
                    onClick = { networkId?.let { client.dccChat(it, who, open = true, focus = true, secure = secure) } },
                    enabled = ready,
                ) { Text(if (secure) "Start a secure chat" else "Start a chat") }
            }
            // The server-side XDCC queue: one pack at a time per bot, every bot's
            // queue at once, carrying on while you're away.
            if (newer && cfg?.receive == true) {
                HorizontalDivider(color = GlassBorder.copy(alpha = 0.4f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NickField(packs, Modifier.weight(1f), placeholder = "packs: 29  31-34") { packs = it }
                    TextButton(
                        enabled = ready && packs.isNotBlank(),
                        onClick = {
                            val id = networkId ?: return@TextButton
                            when (val c = DccCommands.parseXdcc("addm $who ${packs.replace(',', ' ')}")) {
                                is XdccCommand.Add -> { client.xdccQueue(id, c.bot, c.packs); packs = "" }
                                is XdccCommand.Error -> Toast.makeText(context, c.message.removePrefix("addm "), Toast.LENGTH_SHORT).show()
                                else -> {}
                            }
                        },
                    ) { Text("Queue from bot", color = if (ready && packs.isNotBlank()) AccentBlue else TextSecondary) }
                }
            }
        }
    }
}

/**
 * Offers waiting on an answer, on top of the buffer list and every chat: a
 * file or a chat someone wants to start, with Accept / Decline — and Later for a
 * file, which waits on the DCC screen instead. Nothing is dialled until the
 * user says so (the server never auto-accepts). In a `=nick` chat ([current])
 * that isn't live, a strip says so and offers to start it again, the same kind
 * it was (a secure chat isn't re-offered in the clear).
 */
@Composable
internal fun DccBanner(client: LurkerClient, current: Buffer?, onOpenDcc: () -> Unit = {}) {
    val offers = client.dccChatOffers.values.sortedBy { it.nick.lowercase() }
    val fileOffers = client.transfers.values
        .filter { it.isPending && !it.isSend && it.id !in client.dccOffersLater }
        .sortedBy { it.id }
    val currentNet = current?.networkId
    val showDown = current != null && current.isDccChat && currentNet != null &&
        !client.isDccChatLive(current) && client.dccOfferFor(current) == null
    if (offers.isEmpty() && fileOffers.isEmpty() && !showDown) return
    val many = client.networks.size > 1
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        fileOffers.take(3).forEach { t ->
            val cantTake = t.passive && client.dccConfig?.canListen == false
            DccStrip(
                text = "${t.peerNick} offers you ${t.filename}" +
                    (if (t.total > 0) " (${humanBytes(t.total)})" else "") +
                    (if (t.secure) " · secure" else "") +
                    (if (many && t.networkId != null) " · ${client.networkName(t.networkId)}" else "") +
                    (if (cantTake) " — they're firewalled, and your server can't take a passive offer" else ""),
                primary = if (client.dccCanReceive && !cantTake) "Accept" else null,
                onPrimary = { client.dccAccept(t.id, current) },
                secondary = "Decline",
                onSecondary = { client.dccReject(t.id, current) },
                tertiary = "Later",
                onTertiary = { client.dccOffersLater.add(t.id) },
            )
        }
        if (fileOffers.size > 3) {
            DccStrip(text = "${fileOffers.size - 3} more file offers waiting.", primary = "Open DCC", onPrimary = onOpenDcc)
        }
        offers.forEach { offer ->
            DccStrip(
                text = "${offer.nick} wants to start a " + (if (offer.secure) "secure " else "") + "DCC chat" +
                    (if (many) " (${client.networkName(offer.networkId)})" else "") +
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
            val secure = client.isDccChatSecure(current)
            DccStrip(
                text = (if (secure) "Secure DCC chat" else "DCC chat") + " with ${current.peer} is not connected.",
                primary = "Start again",
                onPrimary = { client.dccChat(currentNet, current.peer, open = true, reportTo = current, secure = secure) },
            )
        }
    }
}

@Composable
internal fun DccStrip(
    text: String,
    primary: String?,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    tertiary: String? = null,
    onTertiary: () -> Unit = {},
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
        if (tertiary != null) {
            TextButton(onClick = onTertiary) { Text(tertiary, color = TextSecondary, fontSize = 13.sp) }
        }
        if (secondary != null) {
            TextButton(onClick = onSecondary) { Text(secondary, color = TextSecondary, fontSize = 13.sp) }
        }
        if (primary != null) {
            TextButton(onClick = onPrimary) {
                Text(primary, color = AccentBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
