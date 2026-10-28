package com.flipcash.app.tokens.internal.components.info

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import com.flipcash.app.tokens.ui.CommunityChatHoldings
import com.flipcash.features.tokens.R
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme

/** "Top Community Chats", its info button and View All, then the chats given. */
@Composable
internal fun CommunityChatsHeader(
    onInfo: () -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.title_topCommunityChats),
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textMain,
        )
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = stringResource(R.string.title_howChatsAreRanked),
            tint = CodeTheme.colors.textSecondary,
            modifier = Modifier
                .padding(start = CodeTheme.dimens.grid.x1)
                .requiredSize(CodeTheme.dimens.staticGrid.x4)
                .clip(CircleShape)
                .clickable(onClick = onInfo),
        )
        Text(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onViewAll),
            text = stringResource(R.string.action_viewAll),
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
internal fun CommunityChatRow(
    chat: ChatMetadata,
    holdings: CommunityChatHoldings,
    currencyName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The View All card (node 11622:3267, drawn at 0.65 and scaled here): a 5% white fill, 18dp
     * corners, 16/12 padding, a 42dp photo, 16/12sp text and a trailing chevron.
     */
    card: Boolean = false,
) {
    val rowModifier = if (card) {
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(horizontal = CodeTheme.dimens.grid.x3, vertical = CodeTheme.dimens.grid.x2)
    } else {
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = CodeTheme.dimens.grid.x2)
    }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (card) CodeTheme.dimens.grid.x2 else CodeTheme.dimens.grid.x3),
    ) {
        ContactAvatar(
            image = chat.picture,
            displayName = chat.title.orEmpty(),
            access = BlobAccessContext.ChatProfile(chat.chatId),
            modifier = Modifier
                .requiredSize(CodeTheme.dimens.staticGrid.x10)
                .clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.title.orEmpty(),
                style = CodeTheme.typography.textMedium.let { if (card) it.copy(fontSize = 16.sp) else it },
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (card) Spacer(Modifier.height(CodeTheme.dimens.grid.x1))
            Text(
                text = holdings.amount?.let {
                    pluralStringResource(
                        R.plurals.subtitle_communityChatHoldings,
                        holdings.memberCount.toInt(),
                        it,
                        currencyName,
                        holdings.memberCount.toInt(),
                    )
                } ?: pluralStringResource(
                    R.plurals.subtitle_communityChatMembers,
                    holdings.memberCount.toInt(),
                    holdings.memberCount.toInt(),
                ),
                style = CodeTheme.typography.textSmall.let { if (card) it.copy(fontSize = 12.sp) else it },
                color = if (card) Color.White.copy(alpha = 0.55f) else CodeTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (card) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x4),
            )
        }
    }
}
