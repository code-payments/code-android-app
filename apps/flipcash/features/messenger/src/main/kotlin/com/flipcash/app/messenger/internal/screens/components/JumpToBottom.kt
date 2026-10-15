package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.features.messenger.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.Badge
import com.getcode.ui.components.chat.ChatInputDefaults
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur

/** Past this many rows from the newest, a tap jumps rather than animates (from the old Flipchat). */
internal const val JumpToBottomAnimateMaxRows = 100

internal val JumpToBottomButtonSize = 48.dp
private val BadgeOutline = 2.dp

/**
 * How far above the screen's bottom the jump button sits so its bottom edge is twice [composerGap]
 * above the `$` circle's top edge. [barHeight] is the bar's height (the list's bottom padding); the
 * circle starts [barVerticalPadding] under the bar's top, so that padding is taken back off.
 */
internal fun jumpToBottomBottomOffset(barHeight: Dp, barVerticalPadding: Dp, composerGap: Dp): Dp =
    barHeight - barVerticalPadding + composerGap * 2

/** Whether a tap at [firstVisibleIndex] rows from the newest jumps instead of animating. */
internal fun jumpsToBottom(firstVisibleIndex: Int): Boolean = firstVisibleIndex > JumpToBottomAnimateMaxRows

/** Scrolls [this] to the newest row: a jump from far away, an animation from near. */
internal suspend fun LazyListState.scrollToNewest() {
    if (jumpsToBottom(firstVisibleItemIndex)) scrollToItem(0) else animateScrollToItem(0)
}

/**
 * A 48dp glass circle with a down chevron, and the unread count at its top-trailing corner.
 *
 * Hidden by [AnimatedVisibility] rather than alpha, so it is not tappable while it is away.
 * [hazeState] must belong to a `hazeSource` that does not contain this button.
 */
@Composable
internal fun JumpToBottomButton(
    visible: Boolean,
    unreadCount: Int,
    hazeState: HazeState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = CodeTheme.colors.background
    val glass = chatGlassMaterial()
    // Held so the pill doesn't read "0" while it scales out.
    var lastCount by remember { mutableIntStateOf(unreadCount) }
    LaunchedEffect(unreadCount) { if (unreadCount > 0) lastCount = unreadCount }
    // The effect runs a frame late; show the new count straight away rather than the old one.
    val shownCount = if (unreadCount > 0) unreadCount else lastCount

    AnimatedVisibility(
        modifier = modifier,
        visible = visible,
        enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.8f),
        exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.8f),
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(JumpToBottomButtonSize)
                    .clip(CircleShape)
                    .then(
                        if (hazeState != null) {
                            Modifier
                                .hazeBlur(HazeInput.Sources(hazeState), glass)
                                .background(ChatInputDefaults.ContainerColor)
                        } else {
                            Modifier.background(Color.White.copy(alpha = 0.1f))
                        }
                    )
                    .border(CodeTheme.dimens.border, ChatInputDefaults.RimBrush, CircleShape)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (unreadCount > 0) {
                        pluralStringResource(R.plurals.action_jumpToBottomUnread, unreadCount, unreadCount)
                    } else {
                        stringResource(R.string.action_jumpToBottom)
                    },
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // The pill, outline included, is centred on the top-trailing corner of the button's
                    // bounds: placed half its own size out, without changing the size it reports.
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val dx = if (layoutDirection == LayoutDirection.Rtl) -placeable.width / 2 else placeable.width / 2
                        layout(placeable.width, placeable.height) { placeable.place(dx, -placeable.height / 2) }
                    },
                visible = unreadCount > 0,
                enter = fadeIn(tween(200)) + scaleIn(tween(200)),
                exit = fadeOut(tween(150)) + scaleOut(tween(150)),
            ) {
                // The outline is the chat background, so the pill reads as cut out of the button.
                Box(
                    Modifier
                        .background(background, CircleShape)
                        .padding(BadgeOutline),
                ) {
                    Badge(
                        count = shownCount,
                        color = CodeTheme.colors.badgeFocus,
                        textStyle = CodeTheme.typography.caption.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }
        }
    }
}
