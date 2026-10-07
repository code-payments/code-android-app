package com.flipcash.shared.common.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.shared.common.ui.R
import com.getcode.theme.CodeTheme

private val PictureSize = 48.dp

/** One group as the profile and the picker draw it, already resolved to what is shown. */
data class FeaturedGroupItem(
    val chatId: ChatId,
    val title: String,
    /** The group's description, or its member count when it has none. */
    val subtitle: String,
    val picture: MediaItem?,
)

/**
 * The groups a person has chosen to feature, under the stats on their profile. Draws nothing for
 * an empty list, heading and divider included, so a profile without featured groups looks the
 * same as before the section existed.
 *
 * Rows arrive as [FeaturedGroupItem]s and report the tap by id; what opening a group does is the
 * caller's.
 */
@Composable
fun FeaturedGroupsSection(
    groups: List<FeaturedGroupItem>,
    onOpen: (ChatId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = CodeTheme.colors.divider)
        Text(
            modifier = Modifier.padding(vertical = CodeTheme.dimens.staticGrid.x5),
            text = stringResource(R.string.title_featuredGroups),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
        )
        Column(verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x3)) {
            groups.forEach { group ->
                FeaturedGroupRow(
                    group = group,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onOpen(group.chatId) },
                )
            }
        }
    }
}

/**
 * A group's picture, title and subtitle. [trailing] is the slot after the text: empty on a
 * profile, a check in the picker. Tap handling is on [modifier], so each caller decides what a
 * tap on the row means.
 */
@Composable
fun FeaturedGroupRow(
    group: FeaturedGroupItem,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x3),
    ) {
        ContactAvatar(
            image = group.picture,
            displayName = group.title,
            access = BlobAccessContext.ChatProfile(group.chatId),
            modifier = Modifier
                .requiredSize(PictureSize)
                .clip(RoundedCornerShape(CodeTheme.dimens.staticGrid.x3)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x1 / 2),
        ) {
            Text(
                text = group.title,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = group.subtitle,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

/**
 * [groups] as the rows draw them. An untitled group reads "Group Chat", and a group with no
 * description shows how many people are in it instead.
 */
@Composable
fun rememberFeaturedGroupItems(groups: List<ChatMetadata>): List<FeaturedGroupItem> {
    val untitled = stringResource(R.string.label_linkCard_untitledGroup)
    val counts = groups.map {
        pluralStringResource(
            R.plurals.subtitle_chatMemberCount,
            it.rosterSummary.memberCount.toInt(),
            it.rosterSummary.memberCount.toString(),
        )
    }
    return remember(groups, counts, untitled) {
        groups.mapIndexed { index, group ->
            FeaturedGroupItem(
                chatId = group.chatId,
                title = group.title?.trim().takeUnless { it.isNullOrEmpty() } ?: untitled,
                subtitle = group.description?.trim().takeUnless { it.isNullOrEmpty() } ?: counts[index],
                picture = group.picture,
            )
        }
    }
}
