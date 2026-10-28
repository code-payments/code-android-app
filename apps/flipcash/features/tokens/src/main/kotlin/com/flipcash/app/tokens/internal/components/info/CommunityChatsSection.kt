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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
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
import com.getcode.theme.White05

/** The Figma "snippet" text colour, #ffffff8c, for secondary lines on a dark card. */
internal val SnippetText = Color(0x8CFFFFFF)

/**
 * "Top Community Chats", its info button and View All (nodes 11622:1616-1620): a 19 SemiBold title,
 * a 14 glyph, and View All in white at 13 Medium, in a row 28 tall.
 */
@Composable
internal fun CommunityChatsHeader(
    onInfo: () -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CodeTheme.dimens.grid.x6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.title_topCommunityChats),
            style = CodeTheme.typography.textLarge.copy(fontSize = 19.sp),
            color = CodeTheme.colors.textMain,
        )
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = stringResource(R.string.title_howChatsAreRanked),
            tint = SnippetText,
            modifier = Modifier
                .padding(start = CodeTheme.dimens.grid.x1)
                .requiredSize(CodeTheme.dimens.staticGrid.x3)
                .clip(CircleShape)
                .clickable(onClick = onInfo),
        )
        Text(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onViewAll),
            text = stringResource(R.string.action_viewAll),
            // Medium 13: the caption style is the true Medium face; no theme style is 13.
            style = CodeTheme.typography.caption.copy(fontSize = 13.sp),
            color = CodeTheme.colors.textMain,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * A chat card (node 11622:1622), on Currency Info and View All: 12 radius, a 5% white fill, a 42
 * photo, 16 SemiBold title over a 12 Regular subtitle, and a trailing 16 chevron, 67 tall.
 */
@Composable
internal fun CommunityChatRow(
    chat: ChatMetadata,
    holdings: CommunityChatHoldings,
    currencyName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    card: Boolean = false,
) {
    val rowModifier = if (card) {
        modifier
            .fillMaxWidth()
            .height(CodeTheme.dimens.grid.x13)
            .clip(CodeTheme.shapes.medium)
            .background(White05)
            .clickable(onClick = onClick)
            .padding(horizontal = CodeTheme.dimens.grid.x3)
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
                .requiredSize(if (card) CodeTheme.dimens.staticGrid.x8 else CodeTheme.dimens.staticGrid.x10)
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
                style = CodeTheme.typography.textSmall.let {
                    if (card) it.copy(fontSize = 12.sp) else it
                },
                color = if (card) SnippetText else CodeTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (card) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = SnippetText,
                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x3),
            )
        }
    }
}
