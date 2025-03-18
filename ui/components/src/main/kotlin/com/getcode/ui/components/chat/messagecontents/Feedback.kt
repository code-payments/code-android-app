package com.getcode.ui.components.chat.messagecontents

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import com.getcode.extensions.formattedRaw
import com.getcode.model.sum
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R
import com.getcode.ui.components.chat.UserAvatar
import com.getcode.ui.components.chat.utils.MessageReaction
import com.getcode.ui.components.chat.utils.MessageTip
import com.getcode.ui.emojis.processEmoji

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Feedback(
    tips: List<MessageTip>,
    reactions: List<MessageReaction>,
    isMessageFromSelf: Boolean,
    modifier: Modifier = Modifier,
    actionHandler: MessageContentActionHandler,
) {
    if (tips.isNotEmpty() || reactions.isNotEmpty()) {
        FlowRow(
            modifier = modifier.padding(top = 5.dp).animateContentSize(),
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
        ) {
            if (tips.isNotEmpty()) {
                TipCounter(
                    tips = tips,
                    isMessageFromSelf = isMessageFromSelf,
                    actionHandler = actionHandler,
                )
            }

            val rxns = reactions.groupBy { it.emoji }

            rxns.onEach { (emoji, occurrences) ->
                EmojiCounter(
                    emoji = emoji,
                    occurrences = occurrences,
                    isMessageFromSelf = isMessageFromSelf,
                    actionHandler = actionHandler,
                )
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun TipCounter(
    tips: List<MessageTip>,
    isMessageFromSelf: Boolean,
    modifier: Modifier = Modifier,
    actionHandler: MessageContentActionHandler,
) {
    val didUserTip = tips.any { it.tipper.isSelf }
    val backgroundColor by animateColorAsState(
        when {
            isMessageFromSelf -> CodeTheme.colors.surface
            didUserTip -> CodeTheme.colors.tertiary.copy(alpha = 0.30f)
            else -> Color.Transparent
        }
    )

    val borderColor by animateColorAsState(
        when {
            !didUserTip && !isMessageFromSelf -> CodeTheme.colors.dividerVariant
            else -> CodeTheme.colors.tertiary
        }
    )

    val contentColor by animateColorAsState(
        when {
            isMessageFromSelf -> CodeTheme.colors.onBackground
            didUserTip -> CodeTheme.colors.onBackground
            else -> CodeTheme.colors.onBackground
        }
    )

    val totalTips = tips.map { it.amount }.sum().formattedRaw()

    Row(
        modifier = modifier
            .clip(CircleShape)
            .handleTapGestures(
                onTap = {
                    if (!isMessageFromSelf) { actionHandler.giveTip() }
                },
                onDesiredLongPress = {
                    actionHandler.viewReactions(SelectedReaction.Tips)
                },
                onMissedLongPress = {
                    actionHandler.openMessageControls()
                }
            )
            .background(backgroundColor, CircleShape)
            .border(CodeTheme.dimens.border, borderColor, CircleShape)
            .padding(
                horizontal = CodeTheme.dimens.grid.x2,
                vertical = CodeTheme.dimens.grid.x1,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.wrapContentWidth(),
            text = stringResource(R.string.title_kinAmountWithLogo, totalTips),
            color = contentColor,
            maxLines = 1,
            style = CodeTheme.typography.caption.copy(fontWeight = FontWeight.W700),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiCounter(
    emoji: String,
    occurrences: List<MessageReaction>,
    isMessageFromSelf: Boolean,
    modifier: Modifier = Modifier,
    actionHandler: MessageContentActionHandler,
) {
    val selfOccurrence = occurrences.find { it.sender.isSelf }
    val backgroundColor by animateColorAsState(
        when {
            isMessageFromSelf -> {
                if (selfOccurrence != null) {
                    CodeTheme.colors.tertiary.copy(alpha = 0.54f).compositeOver(CodeTheme.colors.surface)
                } else {
                    Color.Transparent
                }
            }
            selfOccurrence != null -> CodeTheme.colors.tertiary.copy(alpha = 0.30f)
            else -> Color.Transparent
        }
    )

    val borderColor by animateColorAsState(
        when {
            selfOccurrence == null -> CodeTheme.colors.dividerVariant
            else -> CodeTheme.colors.tertiary
        }
    )

    val contentColor by animateColorAsState(
        when {
            isMessageFromSelf -> CodeTheme.colors.onBackground
            selfOccurrence != null -> CodeTheme.colors.onBackground
            else -> CodeTheme.colors.onBackground
        }
    )

    val countForEmoji = occurrences.count()

    Row(
        modifier = modifier
            .clip(CircleShape)
            .handleTapGestures(
                onTap = {
                    if (selfOccurrence != null) actionHandler.removeReaction(selfOccurrence.messageId)
                    else actionHandler.addReaction(emoji)
                },
                onDesiredLongPress = {
                    actionHandler.viewReactions(SelectedReaction.Emoji(emoji))
                },
                onMissedLongPress = {
                    actionHandler.openMessageControls()
                }
            )
            .background(backgroundColor, CircleShape)
            .border(CodeTheme.dimens.border, borderColor, CircleShape)
            .padding(
                horizontal = CodeTheme.dimens.grid.x2,
                vertical = CodeTheme.dimens.grid.x1,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val emojiText = if (LocalInspectionMode.current) {
            emoji
        } else {
            processEmoji(emoji).toString()
        }

        Text(
            text = "$emojiText $countForEmoji",
            color = contentColor,
            style = CodeTheme.typography.caption.copy(fontWeight = FontWeight.W700),
        )
    }
}

private fun Modifier.handleTapGestures(
    key: Any = Unit,
    onTap: () -> Unit,
    onDesiredLongPress: () -> Unit,
    onMissedLongPress: () -> Unit,
): Modifier {
    return this.pointerInput(key) {
        detectTapGestures(
            onTap = { onTap() },
            onLongPress = { offset ->
                val width = size.width.toFloat()
                val height = size.height.toFloat()

                if (width == 0f || height == 0f) return@detectTapGestures

                val isInLast75Percent = offset.x >= (width * 0.25f) && offset.y >= (height * 0.25f)

                if (isInLast75Percent) {
                    onDesiredLongPress()
                } else {
                    onMissedLongPress()
                }
            },
        )
    }
}

@Composable
internal fun TipperDisplay(
    tips: List<MessageTip>,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((-8).dp)
    ) {
        val imageModifier = Modifier
            .size(CodeTheme.dimens.staticGrid.x4)
            .clip(CircleShape)
            .border(CodeTheme.dimens.border, contentColor, CircleShape)

        val tippers = remember(tips) { tips.map { it.tipper }.distinct() }

        tippers.take(3).fastForEachIndexed { index, tipper ->
            UserAvatar(
                modifier = imageModifier
                    .zIndex((tips.size - index).toFloat()),
                data = tipper.profileImage.nullIfEmpty() ?: tipper.id
            )
        }
    }
}

private fun String?.nullIfEmpty() = if (this?.isEmpty() == true) null else this

