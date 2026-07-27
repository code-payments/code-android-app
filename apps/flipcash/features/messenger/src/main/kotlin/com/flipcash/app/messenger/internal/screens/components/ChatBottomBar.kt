package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.screens.ChatAnimations
import com.flipcash.features.messenger.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.ChatInput
import com.getcode.ui.components.chat.TypingIndicator
import com.getcode.ui.core.drawWithGradient
import com.getcode.ui.core.measured
import com.getcode.ui.utils.rememberKeyboardController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect

@Composable
internal fun UserControlBottomBar(
    state: ChatViewModel.State,
    hazeState: HazeState,
    dispatch: (ChatViewModel.Event) -> Unit,
) {
    if (state.isAnonymous) {
        DeactivatedChatBottomBar()
        return
    }

    val keyboard = rememberKeyboardController()
    val focusRequester = remember { FocusRequester() }
    var buttonHeight by remember { mutableStateOf(0.dp) }
    val material = HazeMaterials.ultraThin(containerColor = CodeTheme.colors.background)

    LaunchedEffect(keyboard.visible) {
        if (!keyboard.visible) {
            dispatch(ChatViewModel.Event.OnStopMessageInput)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        // Typing indicator entry/exit — scale from 0.95 anchored leading + opacity
        // (same as message bubble insertion, no vertical slide)
        AnimatedContent(
            modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
            targetState = state.typists.isNotEmpty(),
            transitionSpec = {
                (scaleIn(
                    ChatAnimations.typingIndicator,
                    initialScale = 0.95f,
                    transformOrigin = TransformOrigin(0f, 0.5f)
                ) + fadeIn(ChatAnimations.typingIndicator)) togetherWith
                        (scaleOut(
                            ChatAnimations.typingIndicator,
                            targetScale = 0.95f,
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        ) + fadeOut(ChatAnimations.typingIndicator))
            }
        ) { show ->
            if (show) {
                TypingIndicator(
                    modifier = Modifier
                        .hazeEffect(hazeState) {
                            blurEffect { style = material }
                        },
                )
            }
        }
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(buttonHeight)
                    .align(Alignment.BottomCenter)
                    .drawWithGradient(
                        color = CodeTheme.colors.background,
                        startY = { 0f },
                    ),
            )
            AnimatedContent(
                modifier = Modifier
                    .measured { buttonHeight = it.height }
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(vertical = CodeTheme.dimens.grid.x3)
                    .navigationBarsPadding()
                    // typingConstraints.enabled starts false and only resolves a frame or two after
                    // open, once Room confirms whether the chat has a cash message. Rendering the
                    // default false layout first showed a full-width "Send $" button that then
                    // scaled down to the pill + input box. Hold the bar invisible (but measured, so
                    // the message list keeps correct padding) until resolved, then reveal the final
                    // layout directly — no visible full-width state, no resize.
                    .alpha(if (state.typingConstraints.resolved) 1f else 0f),
                targetState = state.typingConstraints.enabled,
                // The layout only ever changes on the initial async resolution, which is hidden by
                // the alpha gate above, so snap rather than crossfade. The SendCashButton's own
                // color/label springs still animate the typing interaction.
                transitionSpec = {
                    ContentTransform(
                        targetContentEnter = EnterTransition.None,
                        initialContentExit = ExitTransition.None,
                        sizeTransform = null,
                    )
                },
            ) { canType ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    SendCashButton(
                        state = state,
                        hazeState = hazeState,
                        hazeMaterial = material,
                        onClick = { dispatch(ChatViewModel.Event.OnSendCash) }
                    )

                    if (canType) {
                        ChatInput(
                            modifier = Modifier
                                .testTag("chat_message_input")
                                .weight(1f)
                                .border(
                                    CodeTheme.dimens.border,
                                    CodeTheme.colors.divider,
                                    CodeTheme.shapes.medium,
                                )
                                .hazeEffect(hazeState) {
                                    blurEffect {
                                        style = material
                                    }
                                },
                            focusRequester = focusRequester,
                            hint = "Message",
                            state = state.chatInputState,
                            onSendMessage = {
                                dispatch(ChatViewModel.Event.SendMessage)
                                keyboard.restartInput()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeactivatedChatBottomBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(vertical = CodeTheme.dimens.grid.x3),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.subtitle_chatNoLongerAvailable),
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}