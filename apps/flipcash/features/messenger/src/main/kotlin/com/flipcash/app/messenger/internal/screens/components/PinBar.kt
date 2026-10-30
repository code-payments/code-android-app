package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.flipcash.app.messenger.internal.pins.PinBar
import com.flipcash.app.messenger.internal.pins.PinSegments
import com.flipcash.app.messenger.internal.pins.segments
import com.flipcash.features.messenger.R
import com.flipcash.shared.chat.models.ChatQuote
import com.flipcash.shared.chat.ui.ChatQuoteContent
import com.flipcash.shared.chat.ui.ChatQuoteRule
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.CircularIconButton
import com.getcode.ui.components.chat.ChatInputDefaults
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * The bar under the top bar that quotes a pinned message (C1), drawn like the composer's reply
 * strip: the author's rule, their name and one line of the message. With several pins the rule
 * splits into one segment per pin, the shown one lit. A tap on the body jumps to the pin it shows
 * and steps the bar to the next older one; the list button opens every pin.
 *
 * Keeps its own glass, like [JoinRequestsAnchor] beside it; only the quote comes from the strip.
 *
 * @param quote the shown pin as a quote, or null while the list is on its way.
 * @param spoken the shown pin's one line for screen readers, which no longer get a title.
 */
@Composable
internal fun PinnedMessageBar(
    bar: PinBar,
    quote: ChatQuote?,
    spoken: String?,
    onTap: () -> Unit,
    onOpenList: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val shape = CodeTheme.shapes.medium
    val ground = if (hazeState != null) {
        Modifier
            .hazeBlur(HazeInput.Sources(hazeState), chatGlassMaterial())
            .background(ChatInputDefaults.ContainerColor)
    } else {
        Modifier.background(Color.White.copy(alpha = 0.1f))
    }

    val showing = bar as? PinBar.Showing
    val description = if (quote != null) {
        stringResource(R.string.cd_pinnedMessageFrom, quote.authorName, spoken.orEmpty())
    } else {
        stringResource(R.string.title_pinnedMessage)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(ground)
            .border(CodeTheme.dimens.border, CodeTheme.colors.divider, shape)
            .heightIn(min = PinBarMinHeight)
            .height(IntrinsicSize.Min)
            .testTag("pin_bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(enabled = showing != null, onClick = onTap)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    if (showing != null) onClick { onTap(); true }
                }
                // Inset like iOS's bar, so the rule sits off the glass edge; the tap target stays full.
                .padding(
                    start = CodeTheme.dimens.staticGrid.x3,
                    top = CodeTheme.dimens.staticGrid.x2,
                    bottom = CodeTheme.dimens.staticGrid.x2,
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            val rule: @Composable (Color) -> Unit = { color ->
                val segments = showing?.segments()
                if (segments != null) SegmentedRule(color = color, segments = segments) else ChatQuoteRule(color)
            }
            if (quote != null) {
                // iOS's bar: 10pt from the rule to the text, and no padding of the quote's own.
                ChatQuoteContent(
                    quote = quote,
                    rule = rule,
                    gap = 10.dp,
                    textVerticalPadding = 0.dp,
                )
            } else {
                // Nothing to quote yet: the rule and a skeleton line hold the bar's place.
                Row(
                    modifier = Modifier.fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChatQuoteRule(CodeTheme.colors.tertiary)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(CodeTheme.dimens.staticGrid.x2)
                            .clip(CircleShape)
                            .background(CodeTheme.colors.textSecondary.copy(alpha = 0.3f)),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(CodeTheme.dimens.staticGrid.x10)
                .clickable(onClick = onOpenList)
                .testTag("action_open_pinned_list"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_list_bullet_pin),
                contentDescription = stringResource(R.string.cd_openPinnedMessages),
                tint = CodeTheme.colors.textMain,
                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x4),
            )
        }
    }
}

/**
 * The quote's rule split into one segment per visible pin, stacked bottom up from the latest, the
 * one the bar shows lit in the author's colour and the rest dimmed. A window two segments tall
 * clips the track, which slides to keep the lit segment in view as taps step to older pins.
 */
@Composable
private fun SegmentedRule(color: Color, segments: PinSegments) {
    val step = SegmentHeight + SegmentGap
    // The track hangs from the window's bottom edge; scrolling up the track moves it down.
    val scroll by animateDpAsState(targetValue = step * (segments.lowest - 1), label = "pin segments scroll")
    Box(
        modifier = Modifier
            .size(width = SegmentWidth, height = SegmentWindowHeight)
            .clipToBounds(),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(
            modifier = Modifier
                .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                .offset { IntOffset(0, scroll.roundToPx()) },
            verticalArrangement = Arrangement.spacedBy(SegmentGap),
        ) {
            for (segment in segments.count downTo 1) {
                val lit by animateColorAsState(
                    targetValue = if (segment == segments.lit) color else color.copy(alpha = 0.3f),
                    label = "pin segment colour",
                )
                Box(
                    modifier = Modifier
                        .size(width = SegmentWidth, height = SegmentHeight)
                        .background(lit),
                )
            }
        }
    }
}

/**
 * The top bar's trailing list button (C6): stands in for the bar while the request strip holds the
 * space under the top bar, and opens the list.
 */
@Composable
internal fun PinListButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CircularIconButton(
        modifier = modifier,
        onClick = onClick,
        testTag = "action_open_pinned_list_top_bar",
    ) { size ->
        Icon(
            painter = painterResource(R.drawable.ic_list_bullet_pin),
            contentDescription = stringResource(R.string.cd_openPinnedMessages),
            tint = Color.White,
            modifier = Modifier.requiredSize(size),
        )
    }
}

// The join-requests anchor's height, which the bar matches so the two read as one family.
private val PinBarMinHeight = 50.dp
// Figma Cycling variant (node 13016:87): two segments fit the window, the rest scroll.
private val SegmentWidth = 3.dp
private val SegmentHeight = 16.dp
private val SegmentGap = 2.dp
private val SegmentWindowHeight = 34.dp
