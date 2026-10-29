package com.flipcash.app.tokens.internal.components.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.features.tokens.R
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.asHandle
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme

/**
 * "Community Leader" (node 12992:180): the heading in the Top Community Chats style, without its
 * info button or View All, over one card for the user who launched the currency.
 */
@Composable
internal fun CommunityLeaderSection(
    leader: UserProfile,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CodeTheme.dimens.grid.x6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CommunitySectionTitle(text = stringResource(R.string.title_communityLeader))
        }
        CommunityLeaderRow(
            // 14dp under the header, as the first chat card sits.
            modifier = Modifier.padding(top = CodeTheme.dimens.grid.x3),
            leader = leader,
            onClick = onClick,
        )
    }
}

/**
 * The leader's card: the Top Community Chats card with their photo, display name and "@username".
 * With no username the name sits alone, centred. [onClick] is null when there is no profile to
 * open, which drops the chevron.
 */
@Composable
internal fun CommunityLeaderRow(
    leader: UserProfile,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val handle = leader.username?.takeIf { it.isNotBlank() }?.asHandle()
    val label = if (handle != null) {
        stringResource(R.string.cd_communityLeaderWithUsername, leader.displayName, handle)
    } else {
        stringResource(R.string.cd_communityLeader, leader.displayName)
    }
    val description = if (onClick != null) {
        stringResource(R.string.cd_communityLeaderOpensProfile, label)
    } else {
        label
    }
    Row(
        modifier = modifier
            .communityCard(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                if (onClick != null) {
                    role = Role.Button
                    onClick { onClick(); true }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        ContactAvatar(
            userProfile = leader,
            modifier = Modifier
                .requiredSize(CodeTheme.dimens.staticGrid.x8)
                .clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = leader.displayName,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (handle != null) {
                Spacer(Modifier.height(CodeTheme.dimens.grid.x1))
                Text(
                    text = handle,
                    style = CodeTheme.typography.textSmall.copy(fontSize = 12.sp),
                    color = SnippetText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onClick != null) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = SnippetText,
                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x3),
            )
        }
    }
}

private val PreviewLeader = UserProfile.Empty.copy(
    displayName = "Jeff Yanta",
    username = "jeff",
    userId = List(16) { 1 },
)

@Preview
@Composable
private fun Preview_CommunityLeaderSection() {
    FlipcashPreview(showBackground = true) {
        CommunityLeaderSection(leader = PreviewLeader, onClick = {})
    }
}

/** Spec case 5: no username, so the name sits alone. */
@Preview
@Composable
private fun Preview_CommunityLeaderRow_NoUsername() {
    FlipcashPreview(showBackground = true) {
        CommunityLeaderRow(leader = PreviewLeader.copy(username = null), onClick = {})
    }
}

/** Spec case 6: no user id, so no chevron and nothing to tap. */
@Preview
@Composable
private fun Preview_CommunityLeaderRow_NoUserId() {
    FlipcashPreview(showBackground = true) {
        CommunityLeaderRow(leader = PreviewLeader.copy(userId = null), onClick = null)
    }
}
