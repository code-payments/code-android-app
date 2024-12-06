package xyz.flipchat.app.features.chat.conversation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
    when (val chattableState = state.chattableState) {
        ChattableState.Lurker, is ChattableState.Spectator -> {
            CodeButton(
                modifier = Modifier.fillMaxWidth()
                    .padding(
                        start = CodeTheme.dimens.inset,
                        end = CodeTheme.dimens.inset
                    ).navigationBarsPadding(),
                buttonState = ButtonState.Filled,
                isLoading = state.attemptingToFollow,
                text = when (chattableState) {
                    ChattableState.Lurker -> "Follow Room"
                    is ChattableState.Spectator -> stringResource(
                        R.string.joinRoomFromSpectating,
                        formatAmountString(
                            resources = LocalResources.current!!,
                            currency = Currency.Kin,
                            amount = chattableState.cover.kin.quarks.toDouble(),
                            suffix = stringResource(R.string.core_kin)
                        )
                    )
                    else -> ""
                },
            ) {
                when (chattableState) {
                    ChattableState.Lurker -> dispatchEvent(ConversationViewModel.Event.OnFollowRoom)
//                    is ChattableState.Spectator -> dispatchEvent(ConversationViewModel.Event.OnJoinRequestedFromSpectating)
                    else -> Unit
                }
            }
        }

        else -> {
            AnimatedContent(
                targetState = chattableState,
                transitionSpec = {
                    (slideInVertically { it }).togetherWith(slideOutVertically { it })
                },
                label = "chat input area"
            ) {
                when (it) {
                    ChattableState.DisabledByMute -> {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CodeTheme.colors.secondary)
                                .padding(
                                    top = CodeTheme.dimens.grid.x1,
                                    bottom = CodeTheme.dimens.grid.x3
                                ).navigationBarsPadding(),
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

                    else -> Unit
                }
            }
        }
    }
}