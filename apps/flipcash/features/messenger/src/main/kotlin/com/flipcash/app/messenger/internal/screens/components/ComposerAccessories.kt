package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
        AnimatedVisibility(
            visible = mentions != null,
            enter = ChatAnimations.replySurfaceEnter,
            exit = ChatAnimations.replySurfaceExit,
        ) {
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
        AnimatedVisibility(
            visible = reply != null,
            enter = ChatAnimations.replySurfaceEnter,
            exit = ChatAnimations.replySurfaceExit,
        ) {
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
