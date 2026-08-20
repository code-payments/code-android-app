package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.services.models.chat.ChatType
import com.flipcash.features.messenger.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.addIf
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

@Composable
internal fun RowScope.SendCashButton(
    state: ChatViewModel.State,
    hazeState: HazeState,
    hazeMaterial: HazeBlurStyle,
    onClick: () -> Unit,
) {
    // Tip chats always use the minimized (dark, symbol-only) button. The normal send flow keeps the
    // expanded "Send $" presentation and only collapses to the symbol once the user starts typing.
    // chatType resolves from the fast local contact lookup, so a tip DM condenses immediately rather
    // than waiting on the server profile.
    val isTipChat = state.chatType == ChatType.TIP_DM
    val isTyping = isTipChat || state.chatInputState.text.isNotEmpty()
    val canType = state.typingConstraints.enabled

    // Colors ease slowly and independently of the width/label so the fill change reads as one calm
    // transition instead of snapping with the resize — but NOT on the first settle. A tip chat opens
    // before its kind is known, briefly reading as a non-tip chat (white); easing that initial commit
    // would fade white→transparent in view. Snap until the kind resolves and the button commits its
    // first appearance, then ease subsequent typing toggles.
    val kindResolved = state.chatType != ChatType.UNKNOWN
    var hasSettled by remember { mutableStateOf(false) }
    LaunchedEffect(kindResolved) { if (kindResolved) hasSettled = true }
    val colorSpec: AnimationSpec<Color> =
        if (hasSettled) tween(durationMillis = 350) else snap()
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

    // The symbol grows from textMedium to textLarge as the button condenses. Animating the
    // fontUnit (rather than swapping styles) lets it ease alongside the width spring so the
    // resize and the type scale read as one motion. Sizes come from the theme so they track
    // any typography change.
    val symbolFontSize by animateFloatAsState(
        targetValue = if (isTyping) {
            22.sp.value
        } else {
            CodeTheme.typography.textMedium.fontSize.value
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "send button symbol size",
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
            .hazeBlur(HazeInput.Sources(hazeState), hazeMaterial)
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
            // Base style stays textMedium; the animated fontSize carries it up to textLarge in
            // the condensed state so the symbol matches the "Send " prefix when expanded.
            style = CodeTheme.typography.textMedium.copy(fontSize = symbolFontSize.sp),
            maxLines = 1,
            softWrap = false,
        )
    }
}