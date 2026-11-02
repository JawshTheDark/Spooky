// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.amiantos.lurker.ui.theme.AccentBlue
import net.amiantos.lurker.ui.theme.AlertRed
import net.amiantos.lurker.ui.theme.CanvasBlack
import net.amiantos.lurker.ui.theme.GlassBorder
import net.amiantos.lurker.ui.theme.SurfaceDark
import net.amiantos.lurker.ui.theme.SurfaceRaised
import net.amiantos.lurker.ui.theme.TextPrimary
import net.amiantos.lurker.ui.theme.TextSecondary
import net.amiantos.lurker.ui.theme.nickColor

/** What the editor dialog is working on: a new mark, or an existing one. */
private data class RelayEdit(val networkId: Int?, val nick: String, val pattern: String, val isNew: Boolean)

/**
 * Relay bots, every network's at once (Lurker #277). The web marks a bot from
 * its profile and takes a custom format through `/relay add <nick> <pattern>`;
 * here both live on one screen, with a tester so a pattern can be tried on a
 * real line before it's saved. On Lurker the marks are server-held, so a change
 * shows on every device; in direct mode they're kept on the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RelayBotsScreen(client: LurkerClient, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var editing by remember { mutableStateOf<RelayEdit?>(null) }
    val networks = client.networks.values.sortedWith(
        compareBy({ client.networkOrder(it.name) }, { it.name.lowercase() }),
    )
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasBlack,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CanvasBlack),
                navigationIcon = { TextButton(onClick = onBack) { Text("‹", color = AccentBlue, fontSize = 26.sp) } },
                title = { Text("Relay bots", fontWeight = FontWeight.SemiBold) },
                actions = {
                    if (networks.isNotEmpty()) {
                        TextButton(onClick = { editing = RelayEdit(networks.first().id, "", "", isNew = true) }) {
                            Text("Add", color = AccentBlue)
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Text(
                    "A relay bot posts other people's messages — bridged in from Discord, Matrix or " +
                        "another network — inside an envelope like [Discord] <alice> hi. Mark it, and " +
                        "its lines read as the person who wrote them, with the source beside their name.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
                )
            }
            if (client.relayBots.isEmpty()) {
                item {
                    Text(
                        "No relay bots yet. Tap Add, or long-press one of a bot's messages and choose “Mark … as a relay bot.”",
                        color = TextSecondary,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
            networks.forEach { net ->
                val marks = client.relayBotsFor(net.id)
                if (marks.isEmpty()) return@forEach
                item(key = "h${net.id}") {
                    Text(
                        net.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                        modifier = Modifier.fillMaxWidth().padding(28.dp, 18.dp, 16.dp, 6.dp),
                    )
                }
                item(key = "n${net.id}") {
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, GlassBorder),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Column {
                            marks.forEachIndexed { i, m ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { editing = RelayEdit(net.id, m.nick, m.pattern, isNew = false) }
                                        .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(m.nick, color = nickColor(m.nick), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            m.pattern.ifBlank { "Built-in formats" },
                                            color = TextSecondary,
                                            fontSize = 13.sp,
                                            fontFamily = if (m.pattern.isBlank()) null else FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    TextButton(onClick = { client.setRelayBot(net.id, m.nick, false) }) {
                                        Text("Remove", color = AlertRed, fontSize = 13.sp)
                                    }
                                }
                                if (i < marks.lastIndex) {
                                    HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(start = 16.dp))
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    editing?.let { e ->
        RelayEditDialog(
            client = client,
            edit = e,
            networks = networks,
            onDismiss = { editing = null },
            onSave = { netId, nick, pattern ->
                client.setRelayBot(netId, nick, true, pattern)
                editing = null
            },
        )
    }
}

/** Add a mark (pick the network, name the bot) or change one's pattern. */
@Composable
private fun RelayEditDialog(
    client: LurkerClient,
    edit: RelayEdit,
    networks: List<Network>,
    onDismiss: () -> Unit,
    onSave: (networkId: Int, nick: String, pattern: String) -> Unit,
) {
    var networkId by remember { mutableStateOf(edit.networkId) }
    var nick by remember { mutableStateOf(edit.nick) }
    var pattern by remember { mutableStateOf(edit.pattern) }
    var sample by remember { mutableStateOf("") }
    var netMenu by remember { mutableStateOf(false) }
    val patternOk = pattern.isBlank() || Relay.isValidTemplate(pattern.trim())
    val canSave = networkId != null && nick.isNotBlank() && !nick.trim().contains(' ') && patternOk
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaised,
        title = { Text(if (edit.isNew) "Mark a relay bot" else edit.nick, color = TextPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (edit.isNew) {
                    Box {
                        Text(
                            "Network: " + (networks.firstOrNull { it.id == networkId }?.name ?: "pick one") + "  ⌄",
                            color = AccentBlue,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable { netMenu = true }
                                .padding(vertical = 6.dp),
                        )
                        AppDropdownMenu(expanded = netMenu, onDismissRequest = { netMenu = false }) {
                            networks.forEach { n ->
                                DropdownMenuItem(text = { Text(n.name) }, onClick = { networkId = n.id; netMenu = false })
                            }
                        }
                    }
                    OutlinedTextField(
                        nick, { nick = it.replace("\n", "") },
                        label = { Text("Bot's nick") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    pattern, { pattern = it.replace("\n", "").take(Relay.MAX_PATTERN) },
                    label = { Text("Format (blank = built-in)") },
                    placeholder = { Text("<{nick}> {message}") },
                    singleLine = true,
                    isError = !patternOk,
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (patternOk) {
                        "{nick} and {message} are required, {source} is optional. Blank tries " +
                            Relay.DEFAULT_TEMPLATES.joinToString(" then ") + "."
                    } else {
                        "A format needs {nick} and {message}."
                    },
                    color = if (patternOk) TextSecondary else AlertRed,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(12.dp))
                // Try it on a real line before saving.
                OutlinedTextField(
                    sample, { sample = it.replace("\n", "") },
                    label = { Text("Test with a line from the bot") },
                    placeholder = { Text("[Discord] <alice> hi all") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (sample.isNotBlank() && patternOk) {
                    // Other marks on the network still unwrap a chained envelope.
                    val net = networkId
                    val parsed = Relay.parseChain(sample, pattern.trim()) { inner ->
                        if (net == null || inner.equals(nick.trim(), true)) null else client.relayBot(net, inner)?.pattern
                    }
                    Row(
                        Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .background(SurfaceDark, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                    ) {
                        if (parsed == null) {
                            Text("Doesn't match — this line would stay the bot's.", color = TextSecondary, fontSize = 13.sp)
                        } else {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(parsed.nick, color = nickColor(parsed.nick), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    parsed.source?.let {
                                        Text("  [$it]", color = TextSecondary, fontSize = 12.sp)
                                    }
                                }
                                Text(Mirc.strip(parsed.text), color = TextPrimary, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { networkId?.let { onSave(it, nick.trim(), pattern.trim()) } },
            ) { Text("Save", color = AccentBlue, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
    )
}

/**
 * Account-level strips for the buffer list and every chat (Lurker 2.6): the
 * account is paused (read-only until restored), and people our caller ID (+g)
 * turned away, each with Accept — `ACCEPT nick`, so their next message gets in —
 * as the web's toast offers. In a chat ([current]), only that network's callers.
 */
@Composable
internal fun AccountBanners(client: LurkerClient, current: Buffer?) {
    val callers = client.callerIdBlocked.filter { current?.networkId == null || it.networkId == current.networkId }
    if (!client.accountPaused && callers.isEmpty()) return
    val many = client.networks.size > 1
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        if (client.accountPaused) {
            Text(
                "Account paused. You can read your history, but sending is disabled. " +
                    "Contact the administrator to restore access.",
                color = TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceRaised, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            )
            Spacer(Modifier.height(6.dp))
        }
        callers.takeLast(3).forEach { c ->
            DccStrip(
                text = "${c.nick} tried to message you" +
                    (if (many) " (${client.networkName(c.networkId)})" else "") +
                    ". Caller ID blocked it — accept to let their next message through.",
                primary = "Accept",
                onPrimary = { client.acceptCaller(c) },
                secondary = "Dismiss",
                onSecondary = { client.dismissCaller(c) },
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}
