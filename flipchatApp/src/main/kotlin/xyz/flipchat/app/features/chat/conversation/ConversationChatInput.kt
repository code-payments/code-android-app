package xyz.flipchat.app.features.chat.conversation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.getcode.model.Currency
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.ChatInput
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.utils.addIf
import com.getcode.ui.utils.keyboardAsState
import com.getcode.ui.utils.withTopBorder
import com.getcode.util.resources.LocalResources
import com.getcode.utils.Kin
import com.getcode.utils.formatAmountString
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import xyz.flipchat.app.R

@Composable
fun ConversationChatInput(
    state: ConversationViewModel.State,
    focusRequester: FocusRequester,
    dispatchEvent: (ConversationViewModel.Event) -> Unit,
) {
    var previousState by remember { mutableStateOf<ChattableState?>(null) }
    val ime = LocalSoftwareKeyboardController.current
    val keyboardVisible by keyboardAsState()

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
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )
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

            is ChattableState.TemporarilyEnabled,
            is ChattableState.Enabled -> {
                Column {
                    if (state.isHost && !keyboardVisible && state.isOpenCloseEnabled && !state.isRoomOpen) {
                        RoomOpenControlBar(
                            modifier = Modifier.fillMaxWidth(),
                        ) { dispatchEvent(ConversationViewModel.Event.OnOpenStateChangedRequested) }
                    }

                    ChatInput(
                        modifier = Modifier.navigationBarsPadding(),
                        state = state.textFieldState,
                        sendCashEnabled = false,
                        focusRequester = focusRequester,
                        onSendMessage = {
                            if (chattableState is ChattableState.TemporarilyEnabled) {
                                ime?.hide()
                            }
                            dispatchEvent(ConversationViewModel.Event.OnSendMessage)
                        },
                        onSendCash = { dispatchEvent(ConversationViewModel.Event.OnSendCash) }
                    )

                    LaunchedEffect(chattableState) {
                        if (chattableState is ChattableState.TemporarilyEnabled) {
                            focusRequester.requestFocus()
                        }
                    }

                    var wasKeyboardVisible by remember { mutableStateOf(false) }

                    LaunchedEffect(keyboardVisible) {
                        if (wasKeyboardVisible && !keyboardVisible) {
                            delay(150) // Short delay to prevent accidental blips
                            if (!keyboardVisible && chattableState is ChattableState.TemporarilyEnabled) {
                                dispatchEvent(ConversationViewModel.Event.ResetToSpectator)
                            }
                        }
                        wasKeyboardVisible = keyboardVisible
                    }

                    LaunchedEffect(state.replyMessage) {
                        if (state.replyMessage != null) {
                            focusRequester.requestFocus()
                        }
                    }

                }
            }

            is ChattableState.Spectator -> {
                Column(
                    modifier = Modifier
                        .addIf(
                            !state.isRoomOpen && state.isOpenCloseEnabled
                        ) {
                            Modifier.background(CodeTheme.colors.secondary)
                        }
                        .navigationBarsPadding(),
                ) {
                    if (!state.isRoomOpen && state.isOpenCloseEnabled) {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = CodeTheme.dimens.grid.x1,
                                    bottom = CodeTheme.dimens.grid.x2
                                ),
                            textAlign = TextAlign.Center,
                            text = stringResource(R.string.title_roomIsClosed),
                            style = CodeTheme.typography.textSmall,
                            color = CodeTheme.colors.textSecondary
                        )
                    }

                    if (state.isRoomOpen) {
                        CodeButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = CodeTheme.dimens.inset,
                                    end = CodeTheme.dimens.inset
                                ),
                            buttonState = ButtonState.Filled,
                            text = stringResource(
                                R.string.action_sendMessageInRoom,
                                formatAmountString(
                                    resources = LocalResources.current!!,
                                    currency = Currency.Kin,
                                    amount = chattableState.messageFee.quarks.toDouble(),
                                    suffix = stringResource(R.string.core_kin)
                                )
                            ),
                        ) {
                            dispatchEvent(ConversationViewModel.Event.OnSendMessageForFee)
                        }
                    }
                }
            }

            ChattableState.DisabledByClosedRoom -> {
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
                    text = stringResource(R.string.title_roomIsClosed),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary
                )
            }
        }
    }

    // Update the previous state
    LaunchedEffect(state.chattableState) {
        previousState = state.chattableState
    }
}

@Composable
private fun RoomOpenControlBar(
    modifier: Modifier = Modifier,
    onChangeRequest: () -> Unit,
) {
    Row(
        modifier = modifier
            .withTopBorder(color = CodeTheme.colors.dividerVariant)
            .padding(CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.subtitle_roomIsClosed),
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textMain
        )

        CodeButton(
            text = stringResource(R.string.action_reopen),
            shape = CircleShape,
            buttonState = ButtonState.Filled,
            overrideContentPadding = true,
            contentPadding = PaddingValues(horizontal = CodeTheme.dimens.grid.x2),
            style = CodeTheme.typography.textSmall
        ) {
            onChangeRequest()
        }
    }
}
