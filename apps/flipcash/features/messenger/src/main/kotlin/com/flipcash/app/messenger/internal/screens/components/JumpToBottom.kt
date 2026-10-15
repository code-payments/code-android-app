package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.features.messenger.R
import com.getcode.theme.CodeTheme
import com.flipcash.shared.chat.ui.ChatAnimations
import com.getcode.ui.components.unreadCountLabel
import com.getcode.ui.components.chat.ChatInputDefaults
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import kotlinx.coroutines.delay
import androidx.compose.material3.Text
import kotlin.math.sqrt

/** Past this many rows from the newest, a tap jumps rather than animates (from the old Flipchat). */
internal const val JumpToBottomAnimateMaxRows = 100

internal val JumpToBottomButtonSize = 48.dp
private val BadgeOutline = 2.dp

/** Past this the label reads "99+", so counts at or above it are the same state to animate. */
private const val BadgeCountCap = 100
private val BadgeMinSize = 20.dp

/**
 * How far in from the button's bounding-box corner, on both axes, the badge's centre sits so it is on
 * the circle's edge at 45 degrees: the edge point is r/sqrt(2) from the circle's centre, and the
 * corner is r from it on each axis.
 */
internal fun jumpToBottomBadgeInset(buttonSize: Dp): Dp = buttonSize / 2 * (1f - (1f / sqrt(2f)))

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
        enter = fadeIn(ChatAnimations.jumpEnter) +
            scaleIn(ChatAnimations.jumpEnter, initialScale = ChatAnimations.jumpScale),
        exit = fadeOut(ChatAnimations.jumpExit) +
            scaleOut(ChatAnimations.jumpExit, targetScale = ChatAnimations.jumpScale),
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
            // While the button is up the badge follows the count; once the button starts leaving it
            // keeps what it showed, so a count cleared by the jump doesn't shrink it on the way out.
            var heldBadge by remember { mutableStateOf(unreadCount > 0) }
            LaunchedEffect(visible, unreadCount) { if (visible) heldBadge = unreadCount > 0 }
            val badgeWanted = if (visible) unreadCount > 0 else heldBadge
            // Springs carry no delay, so the badge is held back after the button instead.
            var badgeShown by remember { mutableStateOf(false) }
            LaunchedEffect(badgeWanted) {
                if (badgeWanted) delay(ChatAnimations.badgeEnterDelayMs)
                badgeShown = badgeWanted
            }
            val capped = unreadCount.coerceAtMost(BadgeCountCap)
            var previousCapped by remember { mutableIntStateOf(capped) }
            val bump = remember { Animatable(1f) }
            LaunchedEffect(capped) {
                val rose = previousCapped > 0 && capped > previousCapped
                previousCapped = capped
                if (rose) {
                    bump.animateTo(ChatAnimations.badgeBumpScale, ChatAnimations.badgeBump)
                    bump.animateTo(1f, ChatAnimations.badgeBump)
                }
            }
            val inset = with(LocalDensity.current) { jumpToBottomBadgeInset(JumpToBottomButtonSize).roundToPx() }
            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // The pill, outline included, is centred on the circle's edge at 45 degrees: its
                    // centre starts on the bounding box's corner, then moves [inset] in on both axes.
                    // Placed without changing the size it reports.
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val rtl = layoutDirection == LayoutDirection.Rtl
                        val dx = (placeable.width / 2 - inset) * if (rtl) -1 else 1
                        layout(placeable.width, placeable.height) {
                            placeable.place(IntOffset(dx, -placeable.height / 2 + inset))
                        }
                    },
                visible = badgeShown,
                enter = scaleIn(ChatAnimations.badgeEnter, initialScale = 0f),
                // It leaves with the button, which is already scaling and fading away.
                exit = ExitTransition.None,
            ) {
                // The outline is the chat background, so the pill reads as cut out of the button.
                Box(
                    Modifier
                        .graphicsLayer {
                            scaleX = bump.value
                            scaleY = bump.value
                        }
                        .background(background, CircleShape)
                        .padding(BadgeOutline),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(CodeTheme.colors.badgeFocus)
                            // Never narrower than tall, so a single digit is a circle.
                            .defaultMinSize(minWidth = BadgeMinSize, minHeight = BadgeMinSize)
                            .padding(horizontal = CodeTheme.dimens.grid.x1, vertical = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = shownCount.coerceAtMost(BadgeCountCap),
                            transitionSpec = {
                                if (targetState > initialState) {
                                    (slideInVertically { it } + fadeIn()) togetherWith
                                        (slideOutVertically { -it } + fadeOut())
                                } else {
                                    fadeIn() togetherWith fadeOut()
                                }
                            },
                            label = "unreadCount",
                        ) { count ->
                            Text(
                                text = unreadCountLabel(count),
                                style = CodeTheme.typography.caption.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}
