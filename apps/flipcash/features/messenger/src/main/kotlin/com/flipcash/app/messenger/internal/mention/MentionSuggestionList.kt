package com.flipcash.app.messenger.internal.mention

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.shared.chat.MemberMatch
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials

/**
 * Group members offered for an `@`-mention, in the reply strip's card: the same glass, hairline and
 * shape. Rows are [MentionSuggestionListDefaults.rowHeight] tall; past [maxRows] the list scrolls.
 *
 * Values come from the prototype (layout only); the visual pass against the design is still to do.
 */
@Composable
internal fun MentionSuggestionList(
    matches: List<MemberMatch>,
    maxRows: Int,
    hazeState: HazeState,
    onSelect: (MemberMatch) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CodeTheme.shapes.medium
    val material = HazeMaterials.ultraThin(containerColor = CodeTheme.colors.background)
    val border = CodeTheme.dimens.border

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .hazeBlur(HazeInput.Sources(hazeState), material)
            .border(border, CodeTheme.colors.divider, shape)
            .heightIn(max = mentionListHeight(maxRows, border)),
    ) {
        itemsIndexed(matches, key = { _, match -> match.userId.toString() }) { index, match ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(border)
                        .background(CodeTheme.colors.divider),
                )
            }
            MentionRow(match = match, onClick = { onSelect(match) })
        }
    }
}

@Composable
private fun MentionRow(match: MemberMatch, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MentionSuggestionListDefaults.rowHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = MentionSuggestionListDefaults.horizontalPadding)
            .testTag("composer_mention_row"),
        horizontalArrangement = Arrangement.spacedBy(MentionSuggestionListDefaults.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(
            image = match.profilePicture,
            displayName = match.displayName,
            // The picture belongs to this member's profile, which is what authorizes re-minting it.
            access = BlobAccessContext.profile(match.userId),
            modifier = Modifier
                .size(MentionSuggestionListDefaults.avatar)
                .clip(CircleShape),
        )
        Text(
            text = match.displayName,
            style = CodeTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = CodeTheme.colors.textMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            modifier = Modifier.weight(1f),
            text = "@${match.username}",
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The list's height at [rows] rows: the rows plus a hairline between each pair. */
internal fun mentionListHeight(rows: Int, divider: Dp): Dp {
    val n = rows.coerceAtLeast(1)
    return MentionSuggestionListDefaults.rowHeight * n + divider * (n - 1)
}

internal object MentionSuggestionListDefaults {
    val rowHeight = 50.dp
    val avatar = 32.dp
    val horizontalPadding = 10.dp
    val gap = 9.dp
}
