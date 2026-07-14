package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.features.messenger.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.addIf
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect

@Composable
internal fun RowScope.SendCashButton(
    state: ChatViewModel.State,
    hazeState: HazeState,
    hazeMaterial: HazeBlurStyle,
    onClick: () -> Unit,
) {
    val isTyping = state.chatInputState.text.isNotEmpty()
    val canType = state.typingConstraints.enabled

    // Colors ease slowly and independently of the width/label so the fill change reads as one
    // calm transition instead of snapping with the resize.
    val colorSpec = tween<Color>(durationMillis = 350)
    val backgroundColor by animateColorAsState(
        targetValue = if (isTyping) Color.Transparent else Color.White,
        animationSpec = colorSpec,
        label = "send button background",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isTyping) CodeTheme.colors.textMain else Color.Black,
        animationSpec = colorSpec,
        label = "send button content",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isTyping) CodeTheme.colors.divider else Color.Transparent,
        animationSpec = colorSpec,
        label = "send button border",
    )

    // One spring drives the pill's width; the input's weight(1f) fills whatever the pill gives
    // up, so the whole row resizes as a single coordinated motion.
    val widthSpec = spring<IntSize>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    val shape = if (canType) CodeTheme.shapes.medium else CodeTheme.shapes.small

    Row(
        modifier = Modifier
            .addIf(!canType) { Modifier.weight(1f) }
            // Match the chat input's single-line height (~54dp; driven by its trailing send-icon
            // stack). Equal min width keeps the collapsed "$" state a square with even padding
            // instead of a short rectangle; the expanded "Send $" content grows past it.
            .defaultMinSize(minWidth = 54.dp, minHeight = 54.dp)
            .clip(shape)
            // Haze sits under the fill — hidden while the background is opaque white, revealed
            // naturally as the background eases to transparent when typing begins.
            .hazeEffect(hazeState) { blurEffect { style = hazeMaterial } }
            .background(backgroundColor, shape)
            .border(CodeTheme.dimens.border, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(
                horizontal = CodeTheme.dimens.grid.x3,
                vertical = CodeTheme.dimens.grid.x2,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // "action_sendCashViaSymbol" is "Send %1$s" — literally "Send " + the currency symbol.
        // Keep the symbol mounted at all times and only collapse the "Send " prefix, so the
        // symbol never crossfades against a wider label (which garbled into "$nd $").
        val fullText = stringResource(R.string.action_sendCashViaSymbol, state.cashSymbol)
        val prefix = remember(fullText, state.cashSymbol) {
            fullText.removeSuffix(state.cashSymbol)
        }
        AnimatedVisibility(
            visible = !isTyping,
            enter = fadeIn() + expandHorizontally(widthSpec, expandFrom = Alignment.Start),
            exit = fadeOut() + shrinkHorizontally(widthSpec, shrinkTowards = Alignment.Start),
        ) {
            Text(
                text = prefix,
                color = contentColor,
                style = CodeTheme.typography.textMedium,
                maxLines = 1,
                softWrap = false,
            )
        }
        Text(
            text = state.cashSymbol,
            color = contentColor,
            style = CodeTheme.typography.textMedium,
            maxLines = 1,
            softWrap = false,
        )
    }
}