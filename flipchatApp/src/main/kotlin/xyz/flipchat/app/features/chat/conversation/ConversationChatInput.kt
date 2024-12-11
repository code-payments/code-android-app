package xyz.flipchat.app.features.chat.conversation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.getcode.model.Currency
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.ChatInput
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.util.resources.LocalResources
import com.getcode.utils.Kin
import com.getcode.utils.formatAmountString
import xyz.flipchat.app.R

@Composable
fun ConversationChatInput(
    state: ConversationViewModel.State,
    focusRequester: FocusRequester,
    dispatchEvent: (ConversationViewModel.Event) -> Unit,
) {
    var previousState by remember { mutableStateOf<ChattableState?>(null) }

    AnimatedContent(
        targetState = state.chattableState,
        transitionSpec = {
            if (previousState == null) {
                // Skip animation when coming from null
                EnterTransition.None.togetherWith(ExitTransition.None)
            } else {
                (slideInVertically { it }).togetherWith(ExitTransition.None)
            }
        },
        label = "chat input area"
    ) { chattableState ->
        when (chattableState) {
            null -> {
                Spacer(modifier = Modifier.fillMaxWidth().height(100.dp))
            }

            ChattableState.DisabledByMute -> {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CodeTheme.colors.secondary)
                        .padding(
                            top = CodeTheme.dimens.grid.x1,
                            bottom = CodeTheme.dimens.grid.x3
                        )
                        .navigationBarsPadding(),
                    textAlign = TextAlign.Center,
                    text = stringResource(R.string.title_youHaveBeenMuted),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary
                )
            }

            ChattableState.Enabled -> {
                ChatInput(
                    modifier = Modifier.navigationBarsPadding(),
                    state = state.textFieldState,
                    sendCashEnabled = false,
                    focusRequester = focusRequester,
                    onSendMessage = { dispatchEvent(ConversationViewModel.Event.SendMessage) },
                    onSendCash = { dispatchEvent(ConversationViewModel.Event.SendCash) }
                )
            }

            is ChattableState.Spectator -> {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = CodeTheme.dimens.inset,
                            end = CodeTheme.dimens.inset
                        )
                        .navigationBarsPadding(),
                    buttonState = ButtonState.Filled,
                    text = stringResource(
                        R.string.action_joinRoomWithCost,
                        formatAmountString(
                            resources = LocalResources.current!!,
                            currency = Currency.Kin,
                            amount = chattableState.cover.quarks.toDouble(),
                            suffix = stringResource(R.string.core_kin)
                        )
                    ),
                ) {
                    dispatchEvent(ConversationViewModel.Event.OnJoinRequestedFromSpectating)
                }
            }
        }
    }

    // Update the previous state
    LaunchedEffect(state.chattableState) {
        previousState = state.chattableState
    }
}
