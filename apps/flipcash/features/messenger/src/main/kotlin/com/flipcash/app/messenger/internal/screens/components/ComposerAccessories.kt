package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.mention.MentionSuggestionList
import com.flipcash.shared.chat.MemberMatch
import com.flipcash.shared.chat.models.ChatQuote
import com.flipcash.shared.chat.ui.ChatAnimations
import com.flipcash.shared.chat.ui.ComposerReplyStrip
import com.getcode.theme.CodeTheme
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

/**
 * A card stacked above the composer's input row.
 *
 * The stack's order is fixed, top to bottom: mention suggestions, then the reply strip, then the
 * input row. Each kind appears at most once; [composerAccessories] decides which are up.
 */
internal sealed interface ComposerAccessory {
    data class MentionSuggestions(val matches: List<MemberMatch>) : ComposerAccessory
    data class Reply(val quote: ChatQuote) : ComposerAccessory
}

/**
 * The accessories [state] puts above the composer, in stacking order.
 *
 * Nothing when [canType] is false, so no card sits over a bar with nothing to send from. The two
 * are independent: a reply opening or closing leaves an open mention list where it is.
 */
internal fun composerAccessories(state: ChatViewModel.State, canType: Boolean): List<ComposerAccessory> {
    if (!canType) return emptyList()
    return buildList {
        if (state.mentionSuggestions.isNotEmpty()) add(ComposerAccessory.MentionSuggestions(state.mentionSuggestions))
        state.replyingTo?.let { add(ComposerAccessory.Reply(it)) }
    }
}

/**
 * Draws [accessories] above the input row, each growing in and out with the reply surface's
 * spring.
 *
 * The bar grows into each card rather than the card appearing over the bar, so a reveal is a
 * height animation with the content clipped by the moving edge. Each kind keeps its last value
 * while it leaves: the state drops it on the frame the collapse starts, and reading it directly
 * would shrink an empty card. A live change still swaps it, so replying to a second message while
 * the strip is up changes the quote rather than keeping the first.
 */
@Composable
internal fun ComposerAccessoryStack(
    accessories: List<ComposerAccessory>,
    sideInset: Dp,
    mentionRows: Int,
    hazeState: HazeState,
    dispatch: (ChatViewModel.Event) -> Unit,
    mentionListModifier: Modifier = Modifier,
) {
    val mentions = accessories.firstNotNullOfOrNull { it as? ComposerAccessory.MentionSuggestions }
    val reply = accessories.firstNotNullOfOrNull { it as? ComposerAccessory.Reply }

    Column {
        var lastMatches by remember { mutableStateOf(mentions?.matches.orEmpty()) }
        mentions?.let { lastMatches = it.matches }
        ComposerAccessorySlot(visible = mentions != null) {
            MentionSuggestionList(
                matches = lastMatches,
                maxRows = mentionRows,
                hazeState = hazeState,
                onSelect = { dispatch(ChatViewModel.Event.PickMention(it)) },
                modifier = mentionListModifier
                    .padding(horizontal = sideInset)
                    .padding(bottom = CodeTheme.dimens.grid.x2)
                    .testTag("composer_mention_list"),
            )
        }

        var lastQuote by remember { mutableStateOf(reply?.quote) }
        reply?.let { lastQuote = it.quote }
        ComposerAccessorySlot(visible = reply != null) {
            lastQuote?.let { quote ->
                ComposerReplyStrip(
                    quote = quote,
                    onDismiss = { dispatch(ChatViewModel.Event.CancelReply) },
                    hazeState = hazeState,
                    modifier = Modifier
                        // Inset to the composer row's own margins, so the card's edges line up
                        // with the field it sits above, and narrows with it.
                        .padding(horizontal = sideInset)
                        .padding(bottom = CodeTheme.dimens.grid.x2)
                        .testTag("composer_reply_strip"),
                )
            }
        }
    }
}

/**
 * Reveals [content] by growing its slot from nothing to its natural height, and takes it away by
 * the reverse: [ChatAnimations.replySurface] running 0 to 1 and back, content anchored to the top
 * and clipped by the moving edge, fading on the way out only.
 *
 * The slot's height is the content's natural height times the animated fraction, read in layout in
 * every pass. That is the point of doing this by hand: `AnimatedVisibility`'s expand reports the
 * finished height to a lookahead pass, and the scaffold insets the transcript from that pass, so
 * the transcript jumped up the instant a card was added, ahead of the card, while the exit (whose
 * lookahead size is the current one) followed it. Here both directions give the same height in
 * both passes, so the transcript, the card and the input row move together.
 *
 * [content] stays composed until the slot has closed, and is not composed at all when it starts
 * closed.
 */
@Composable
internal fun ComposerAccessorySlot(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Starts at its resting value, so a card that is already up when the screen opens is simply there.
    val reveal = remember { Animatable(if (visible) 1f else 0f) }
    var composed by remember { mutableStateOf(visible) }
    if (visible) composed = true

    LaunchedEffect(visible) {
        reveal.animateTo(if (visible) 1f else 0f, ChatAnimations.replySurface)
        if (!visible) composed = false
    }

    if (!composed) return
    Layout(
        content = content,
        modifier = modifier
            .graphicsLayer { alpha = if (visible) 1f else reveal.value }
            .clipToBounds(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val width = placeables.maxOfOrNull { it.width } ?: 0
        val natural = placeables.maxOfOrNull { it.height } ?: 0
        val height = (natural * reveal.value).roundToInt()
        layout(width, height) { placeables.forEach { it.place(0, 0) } }
    }
}
