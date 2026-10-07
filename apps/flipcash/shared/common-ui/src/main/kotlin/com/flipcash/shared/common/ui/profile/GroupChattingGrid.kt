package com.flipcash.shared.common.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.shared.common.ui.R
import com.getcode.opencode.model.core.ID
import com.getcode.theme.CodeTheme

private const val Columns = 4
private val SectionSpacing = 16.dp
private val ColumnSpacing = 8.dp
private val RowSpacing = 16.dp
private val PortraitSize = 60.dp
private val HostBadgeSize = 22.dp
private val HostBadgeRing = 2.dp
private val HostBadgeIconSize = 12.dp
private val HostBadgeOffset = 2.dp

/** One chatter as the grid draws it, already resolved to what is shown. */
data class ChattingGridItem(
    val userId: ID,
    val name: String,
    val picture: MediaItem?,
    val isHost: Boolean,
)

/**
 * What a chatter is called under their portrait: their display name, else their handle, else
 * [fallback]. Blank counts as missing, since the server sends an empty display name for a profile
 * that has none.
 */
fun chatterName(displayName: String?, username: String?, fallback: String): String =
    displayName?.takeIf { it.isNotBlank() }
        ?: username?.takeIf { it.isNotBlank() }?.let { "@$it" }
        ?: fallback

/**
 * Who has been talking in a public group: a "Chatting" heading over portraits four to a row, the
 * host badged. Draws nothing for an empty list, heading included.
 *
 * Rows are built by hand rather than as a lazy grid so it can sit inside the profile's own scroll.
 * A short last row leaves its empty cells blank to keep the columns aligned. The order is the
 * caller's; the grid does not sort.
 */
@Composable
fun GroupChattingGrid(
    chatters: List<ChattingGridItem>,
    onOpen: (ChattingGridItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (chatters.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        Text(
            text = stringResource(R.string.title_chatting),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
        )
        Column(verticalArrangement = Arrangement.spacedBy(RowSpacing)) {
            chatters.chunked(Columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(ColumnSpacing)) {
                    row.forEach { chatter ->
                        ChatterCell(
                            modifier = Modifier.weight(1f),
                            chatter = chatter,
                            onClick = { onOpen(chatter) },
                        )
                    }
                    repeat(Columns - row.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ChatterCell(
    chatter: ChattingGridItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hostLabel = stringResource(R.string.label_chatterHost, chatter.name)
    Column(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (chatter.isHost) Modifier.semantics { contentDescription = hostLabel } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            ContactAvatar(
                image = chatter.picture,
                displayName = chatter.name,
                access = BlobAccessContext.profile(chatter.userId),
                modifier = Modifier
                    .size(PortraitSize)
                    .clip(CircleShape),
            )
            if (chatter.isHost) {
                HostBadge(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = HostBadgeOffset, y = HostBadgeOffset),
                )
            }
        }
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = chatter.name,
            style = CodeTheme.typography.caption,
            color = CodeTheme.colors.textMain,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A white disc with a wave, ringed in the page's background so it reads as cut out of the portrait. */
@Composable
private fun HostBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(HostBadgeSize)
            .background(CodeTheme.colors.textMain, CircleShape)
            .border(HostBadgeRing, CodeTheme.colors.background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(HostBadgeIconSize),
            imageVector = Icons.Outlined.WavingHand,
            contentDescription = null,
            tint = CodeTheme.colors.background,
        )
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_GroupChattingGrid() {
    GroupChattingGrid(
        chatters = List(6) { index ->
            ChattingGridItem(
                userId = listOf(index.toByte()),
                name = if (index == 0) "Ada Lovelace" else "@user$index",
                picture = null,
                isHost = index == 0,
            )
        },
        onOpen = {},
    )
}
