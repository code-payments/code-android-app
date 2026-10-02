package com.flipcash.app.messenger.internal.mention

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getcode.ui.utils.generateComplementaryColorPalette
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.shared.chat.MemberMatch
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.shared.chat.ui.ChatAnimations
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.ChatInputDefaults
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials

/**
 * Group members offered for an `@`-mention, in the reply strip's card: the same glass, hairline and
 * shape, so the two stacked cards read as one set. Rows are [MentionSuggestionListDefaults.rowHeight]
 * tall; past [maxRows] the list scrolls and the card stays that height.
 *
 * Metrics follow the design (mention row set, node 10633:68; spec, node 10662:64911).
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
    // Sized to the rows on show, up to [maxRows], and moved there on the reply spring. The count
    // changes as typing narrows the matches, and the cap changes when the reply strip opens or closes
    // under the list (4 rows to 3 and back); snapping either cuts or adds a row in one frame, and the
    // transcript, inset by this bar, would jump with it.
    val height by animateDpAsState(
        mentionListHeight(minOf(matches.size, maxRows), border),
        ChatAnimations.replySurfaceDp,
    )

    // A LazyColumn keeps the item it was showing in view across a data change, by key. When typing
    // widens the matches, the one row that was up lands lower in the new order, and the list
    // scrolled down to follow it while the card grew. The order is a ranking, so the top is what
    // belongs in view: pin it before the new matches are measured.
    val listState = rememberLazyListState()
    val keys = matches.map { it.userId }
    var lastKeys by remember { mutableStateOf(keys) }
    if (keys != lastKeys) {
        lastKeys = keys
        listState.requestScrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .hazeBlur(HazeInput.Sources(hazeState), material)
            // The input field's own fill over the same glass, so card and field read as one surface.
            .background(ChatInputDefaults.ContainerColor)
            .border(border, CodeTheme.colors.divider, shape)
            .height(height),
    ) {
        itemsIndexed(matches, key = { _, match -> match.userId.toString() }) { index, match ->
            // Rows a narrower query drops fade out, and the survivors slide up into the gap, on the
            // same spring as the card closing around them.
            Column(
                Modifier.animateItem(
                    fadeInSpec = ChatAnimations.replySurface,
                    placementSpec = ChatAnimations.replySurfaceIntOffset,
                    fadeOutSpec = ChatAnimations.replySurface,
                ),
            ) {
                if (index > 0) {
                    // Starts under the name, clear of the avatar column; none after the last row.
                    Box(
                        Modifier
                            .padding(start = MentionSuggestionListDefaults.dividerInset)
                            .fillMaxWidth()
                            .height(border)
                            .background(CodeTheme.colors.divider),
                    )
                }
                MentionRow(match = match, onClick = { onSelect(match) })
            }
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
            style = CodeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, lineHeight = 16.sp),
            // The same per-person colour the reply strip gives a quoted author.
            color = nameColor(match.userId) ?: CodeTheme.colors.textMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            modifier = Modifier.weight(1f),
            text = "@${match.username}",
            style = CodeTheme.typography.textSmall.copy(fontWeight = FontWeight.Medium),
            color = MentionSuggestionListDefaults.usernameColor,
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
    val horizontalPadding = 14.dp
    val gap = 10.dp

    /** Lines a divider up with the name: the row's leading padding, the avatar and the gap after it. */
    val dividerInset = horizontalPadding + avatar + gap

    /** iOS's secondary label, #EBEBF5 at 60%. */
    val usernameColor = Color(0xFFEBEBF5).copy(alpha = 0.6f)
}

@Composable
private fun nameColor(userId: List<Byte>): Color? =
    remember(userId) { generateComplementaryColorPalette(userId)?.second }
