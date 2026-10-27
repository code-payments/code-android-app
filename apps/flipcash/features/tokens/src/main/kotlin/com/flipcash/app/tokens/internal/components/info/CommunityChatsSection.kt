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
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
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
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
