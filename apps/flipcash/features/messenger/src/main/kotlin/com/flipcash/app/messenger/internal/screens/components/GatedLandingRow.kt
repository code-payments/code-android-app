package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.ui.BubblePosition
import com.flipcash.shared.chat.ui.ChatAnimations
import com.flipcash.shared.chat.ui.ContentBubble
import com.flipcash.shared.chat.ui.ReactionPillRow
import com.flipcash.shared.chat.ui.bubbleMaxWidth
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme
import kotlinx.coroutines.delay

/**
 * The linked message a link lands on in a group that withholds its transcript (flow 6), drawn on
 * its own above the blur. Sender, picture and reactions are sharp; the body is the server's
 * placeholder, blurred by the bubble ([ChatListItem.ContentBubble.redacted]).
 *
 * Display only: the bubble is not interactive, and the avatar and pills answer nothing. It flashes
 * once when it appears, as a jump in a readable transcript flashes the message it lands on.
 */
@Composable
internal fun GatedLandingRow(
    item: ChatListItem.ContentBubble,
    modifier: Modifier = Modifier,
) {
    val attention = remember(item.messageId) { Animatable(0f) }
    LaunchedEffect(item.messageId) {
        attention.snapTo(1f)
        delay(ChatAnimations.attentionHoldMs)
        attention.animateTo(0f, ChatAnimations.attentionFade)
    }
    val sender = item.sender
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CodeTheme.dimens.inset)
            .testTag("gated_landing_row"),
    ) {
        if (sender != null && sender.displayName.isNotBlank()) {
            Text(
                modifier = Modifier.padding(
                    start = senderNameInset(!item.isFromSelf),
                    bottom = CodeTheme.dimens.grid.x1,
                ),
                text = sender.displayName,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
        ) {
            if (!item.isFromSelf) {
                Box(
                    modifier = Modifier.requiredSize(CodeTheme.dimens.staticGrid.x6),
                ) {
                    if (sender != null) {
                        ContactAvatar(
                            image = sender.picture,
                            displayName = sender.initialsName.orEmpty(),
                            access = BlobAccessContext.profile(sender.userId),
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = if (item.isFromSelf) Alignment.End else Alignment.Start,
            ) {
                ContentBubble(
                    item = item,
                    position = BubblePosition.Solo,
                    interactive = false,
                    attention = { attention.value },
                )
                if (item.reactionPills.isNotEmpty()) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        ReactionPillRow(
                            pills = item.reactionPills,
                            canReact = false,
                            onToggle = {},
                            onPillLongClick = {},
                            onOpenPicker = {},
                            modifier = Modifier
                                .align(if (item.isFromSelf) Alignment.TopEnd else Alignment.TopStart)
                                .width(
                                    bubbleMaxWidth(
                                        content = item.content,
                                        rowWidth = maxWidth,
                                        rowInset = CodeTheme.dimens.inset,
                                    ),
                                ),
                            alignEnd = item.isFromSelf,
                        )
                    }
                }
            }
        }
    }
}
