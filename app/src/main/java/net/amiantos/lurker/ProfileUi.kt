// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import net.amiantos.lurker.ui.theme.AccentBlue
import net.amiantos.lurker.ui.theme.TextPrimary
import net.amiantos.lurker.ui.theme.TextSecondary
import net.amiantos.lurker.ui.theme.nickColor

/**
 * Someone's IRCv3 metadata (Lurker 2.6, #607), as the web's profile shows it
 * under the nick: avatar, display name, then pronouns, status and homepage.
 * The avatar URL is the server's own (proxied), never one the person typed, so
 * loading it reveals nothing to them; an empty slot when the server can't serve it.
 */
@Composable
internal fun ProfileMetaBlock(nick: String, meta: UserMeta, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        meta.avatarUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = "Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(nickColor(nick).copy(alpha = 0.35f)),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column {
            meta.shownName?.let {
                Text(it, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            meta.pronouns?.let { Text(Mirc.strip(it), color = TextSecondary, fontSize = 13.sp) }
            meta.status?.let { Text(Mirc.strip(it), color = TextSecondary, fontSize = 13.sp) }
            meta.homepage?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }?.let { link ->
                Text(
                    link,
                    color = AccentBlue,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { runCatching { uri.openUri(link) } }.padding(vertical = 2.dp),
                )
            }
        }
    }
}
