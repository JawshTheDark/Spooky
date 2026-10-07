// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import net.amiantos.lurker.ui.theme.AccentBlue
import net.amiantos.lurker.ui.theme.CanvasBlack
import net.amiantos.lurker.ui.theme.GlassBorder
import net.amiantos.lurker.ui.theme.SurfaceDark
import net.amiantos.lurker.ui.theme.TextPrimary
import net.amiantos.lurker.ui.theme.TextSecondary

/**
 * Rendering for server-resolved previews. The store ([PreviewStore]) owns all the
 * batching/pacing/retry logic; this file only draws, and pumps the store.
 */

/** Process-wide so a buffer switch doesn't drop in-flight work or re-ask for
 *  everything already answered. Reset on sign-out by [LurkerApp]. */
object ServerPreviews {
    val store = PreviewStore()

    /**
     * The live client, parked here so the row composables don't each have to
     * thread it down (they render a Msg, not a session). Same pattern as [Ui].
     */
    var client: LurkerClient? = null

    /** Server-side previews are usable: a Lurker server that mounted the feature. */
    val enabled: Boolean
        get() = client?.let { it.serverFeatures && it.hasFeature("linkPreviews") } == true

    /** Bumped whenever an answer lands, purely to wake composables that are
     *  reading [PreviewStore.get] — Compose can't observe a plain HashMap. */
    var revision by mutableStateOf(0)
        private set

    fun bump() { revision++ }
}

/**
 * Drives the store: drains its queue, sends batches, folds responses.
 *
 * Hosted once, high in the tree, rather than per row — pacing is a global
 * property and twenty rows each running their own pump would be twenty pumps.
 */
@Composable
fun ServerPreviewPump(client: LurkerClient) {
    ServerPreviews.client = client
    LaunchedEffect(client.hasFeature("linkPreviews")) {
        if (!client.hasFeature("linkPreviews")) return@LaunchedEffect
        while (true) {
            val batch = ServerPreviews.store.nextBatch()
            if (batch == null) {
                delay(120) // nothing due; idle cheaply
                continue
            }
            val gen = ServerPreviews.store.currentGeneration()
            client.resolvePreviews(batch) { previews ->
                // null == transport failure, which says NOTHING about these URLs.
                val changed = if (previews == null) {
                    ServerPreviews.store.onFailure(gen, batch)
                } else {
                    ServerPreviews.store.onResponse(gen, batch, previews)
                }
                if (changed.isNotEmpty()) ServerPreviews.bump()
            }
        }
    }
}

/**
 * The attachment block for one message, resolved server-side.
 *
 * Withheld entirely until every URL has settled, then drawn complete — a preview
 * changes a row's height, and revealing three of them 200ms apart makes the
 * reader watch the layout rearrange three times.
 */
/**
 * Whether [msg] is only links and the server resolved every one of them to an
 * image this client draws — then the picture stands in for the link and the text
 * bubble is left out. False until the answers settle, so the link shows first
 * and is replaced, never the other way round; anything not an image (a page
 * card, a video, audio) keeps its link.
 */
@Composable
fun serverImagesCoverText(msg: Msg): Boolean {
    val client = ServerPreviews.client ?: return false
    if (!ServerPreviews.enabled || !previewableEvent(msg.type)) return false
    val links = remember(msg.text) { linkOnlyUrls(msg.text)?.toSet() } ?: return false
    val inlineMedia = client.settingBool("chat.inline_media.enabled", false)
    if (!inlineMedia) return false
    val cards = client.settingBool("chat.link_previews.enabled", false)
    val urls = remember(msg.text, inlineMedia, cards) { serverPreviewUrls(msg.text, inlineMedia, cards) }
    // Every link must be one we preview (not cut by the per-message budget).
    if (urls.toSet() != links) return false
    @Suppress("UNUSED_EXPRESSION") ServerPreviews.revision
    if (!ServerPreviews.store.settled(urls)) return false
    return urls.all { u ->
        ServerPreviews.store.get(u)?.let { it.kind == "image" && it.isAllowed(inlineMedia, cards) } == true
    }
}

/** [onLongPress]: the message's action sheet, for when the picture IS the
 *  message (see [serverImagesCoverText]) and there's no bubble to press. */
@Composable
fun ServerPreviewCards(msg: Msg, onOpen: (String) -> Unit, onLongPress: (() -> Unit)? = null) {
    val client = ServerPreviews.client ?: return
    if (!ServerPreviews.enabled) return
    if (!previewableEvent(msg.type)) return
    val inlineMedia = client.settingBool("chat.inline_media.enabled", false)
    val cards = client.settingBool("chat.link_previews.enabled", false)
    if (!inlineMedia && !cards) return

    val urls = remember(msg.text, inlineMedia, cards) {
        serverPreviewUrls(msg.text, inlineMedia, cards)
    }
    if (urls.isEmpty()) return

    LaunchedEffect(urls) { ServerPreviews.store.prime(urls) }

    // Read the revision so this recomposes when answers land.
    @Suppress("UNUSED_EXPRESSION") ServerPreviews.revision
    if (!ServerPreviews.store.settled(urls)) return

    val resolved = urls.mapNotNull { ServerPreviews.store.get(it) }
        .filter { it.isAllowed(inlineMedia, cards) }
    if (resolved.isEmpty()) return

    val images = resolved.filter { it.kind == "image" }
    val others = resolved.filter { it.kind != "image" }

    Column(Modifier.fillMaxWidth()) {
        if (images.isNotEmpty()) ImageGrid(client, images, onOpen, onLongPress)
        others.forEach { p ->
            when (p.kind) {
                "video-embed" -> EmbedFacade(client, p, onOpen)
                "video", "audio" -> FileCard(client, p, onOpen)
                else -> PageCard(client, p, onOpen)
            }
        }
    }
}

// ---- byte loading -------------------------------------------------------------

/**
 * Build an image request for a server-minted byte URL.
 *
 * The token is attached ONLY for the instance's own host. An absolute URL is a
 * third-party CDN by construction, and sending Authorization there would put the
 * user's session token in that operator's access log once per image.
 */
@Composable
private fun rememberByteModel(client: LurkerClient, path: String?): Any? {
    val context = LocalContext.current
    if (path == null) return null
    val target = remember(path, client.serverBaseUrl) { previewMediaTarget(path, client.serverBaseUrl) } ?: return null
    return remember(target, client.tokenForMedia) {
        when (target) {
            is MediaTarget.ThirdParty ->
                ImageRequest.Builder(context).data(target.url).build()
            is MediaTarget.OwnServer -> {
                val b = ImageRequest.Builder(context).data(target.url)
                client.tokenForMedia?.let { b.addHeader("Authorization", "Bearer $it") }
                b.build()
            }
        }
    }
}

@Composable
private fun ByteImage(
    client: LurkerClient,
    path: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val model = rememberByteModel(client, path) ?: return
    AsyncImage(
        model = model,
        imageLoader = LocalContext.current.imageLoader,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
    )
}

// ---- cards --------------------------------------------------------------------

/** Two per row at a FIXED height, so a message's attachment block has a height
 *  known from the COUNT alone — nothing reads an image's real dimensions, so
 *  nothing reflows when the bytes land. */
@Composable
private fun ImageGrid(
    client: LurkerClient,
    images: List<ServerLinkPreview>,
    onOpen: (String) -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    val rows = images.chunked(2)
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { p ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(if (images.size == 1) 220.dp else 130.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CanvasBlack)
                            .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
                            .combinedClickable(onClick = { onOpen(p.url) }, onLongClick = onLongPress),
                    ) {
                        ByteImage(client, p.src, Modifier.fillMaxSize())
                    }
                }
                // Keep a lone trailing image half-width rather than stretching it.
                if (row.size == 1 && images.size > 1) Box(Modifier.weight(1f))
            }
            if (row !== rows.last()) Box(Modifier.height(6.dp))
        }
    }
}

private enum class ThumbShape { NONE, CHIP, HERO }

/**
 * Chip (small square beside the text) vs hero (full-width band under it), chosen
 * from the DECLARED dimensions.
 *
 * Measured against live markup: reddit (256×256) and Ars Technica (512×512) are
 * logos and want the chip; Wikipedia (869×1200) is portrait and a hero crops its
 * subject out; GitHub (1200×600) wants the band. 1.3 sits just under 4:3, so a
 * conventional photo is a hero and anything near square is not.
 */
private fun thumbShape(p: ServerLinkPreview): ThumbShape {
    if (p.thumb == null) return ThumbShape.NONE
    val w = p.thumbWidth
    val h = p.thumbHeight
    if (w == null || h == null) return ThumbShape.HERO
    return if (w.toFloat() / h < 1.3f) ThumbShape.CHIP else ThumbShape.HERO
}

@Composable
private fun PageCard(client: LurkerClient, p: ServerLinkPreview, onOpen: (String) -> Unit) {
    val shape = thumbShape(p)
    Column(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
            .clickable { onOpen(p.url) },
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 64.dp)) {
            if (shape == ThumbShape.CHIP) {
                ByteImage(client, p.thumb, Modifier.fillMaxHeight().width(80.dp))
            }
            PageText(p, Modifier.weight(1f).padding(10.dp))
        }
        if (shape == ThumbShape.HERO) {
            // 1200/630 as a FIXED aspect — the declared pair only picks between
            // layouts. Sizing the box from numbers a stranger asserted about a file
            // we haven't measured would put row height at the mercy of their markup.
            ByteImage(
                client,
                p.thumb,
                Modifier.fillMaxWidth().aspectRatio(1200f / 630f),
            )
        }
    }
}

@Composable
private fun PageText(p: ServerLinkPreview, modifier: Modifier) {
    Column(modifier) {
        p.siteName?.let {
            Text(it, color = AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        p.title?.let {
            Text(
                it,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        p.description?.let {
            Text(
                it,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/** A facade: the poster comes through our own server, so not even the thumbnail
 *  request reaches the provider. Nothing contacts them until the reader presses
 *  play. */
@Composable
private fun EmbedFacade(client: LurkerClient, p: ServerLinkPreview, onOpen: (String) -> Unit) {
    Column(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CanvasBlack)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
            // The server downgrades kind to `page` when it has no player URL, so a
            // video-embed always has one — but fall back to the page regardless.
            .clickable { onOpen(p.embedUrl ?: p.url) },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
            ByteImage(client, p.thumb, Modifier.fillMaxSize())
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Color(0xE6FF0000)),
                contentAlignment = Alignment.Center,
            ) { Text("▶", color = Color.White, fontSize = 20.sp) }
        }
        PageText(p, Modifier.padding(10.dp))
    }
}

/**
 * A clip. There is deliberately NO byte URL for video/audio — the server stopped
 * relaying them after two clips exhausted kernel socket memory and took the hosted
 * service down. The policy: proxy what the reader didn't ask for, don't proxy what
 * they deliberately activate.
 *
 * So the poster (if the instance has a byte cache; it's off by default, and no
 * poster is a complete and correct state) renders unasked, and a tap opens the
 * ORIGIN url — never `src`, which for a descriptor minted before that change is a
 * token that now 404s.
 */
@Composable
private fun FileCard(client: LurkerClient, p: ServerLinkPreview, onOpen: (String) -> Unit) {
    Column(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
            .clickable { onOpen(p.url) },
    ) {
        if (p.thumb != null) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
                ByteImage(client, p.thumb, Modifier.fillMaxSize())
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color(0xB3000000)),
                    contentAlignment = Alignment.Center,
                ) { Text("▶", color = Color.White, fontSize = 19.sp) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (p.kind == "audio") "♪" else "▶", color = AccentBlue, fontSize = 18.sp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    p.title ?: p.url.substringAfterLast('/').substringBefore('?'),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    p.siteName ?: p.mime.orEmpty(),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
