package com.flipcash.app.messenger.internal.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.flipcash.app.contacts.ui.ContactAvatar
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.screens.components.MessageList
import com.flipcash.shared.chat.ui.ChatAction
import com.flipcash.features.messenger.R
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.chat.ChatInput
import com.getcode.ui.components.chat.TypingIndicator
import com.getcode.ui.core.drawWithGradient
import com.getcode.ui.core.measured
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.utils.rememberKeyboardController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
internal fun MessengerScreen(viewModel: ChatViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val messages = viewModel.messages.collectAsLazyPagingItems()
    val otherReadPointer by viewModel.otherReadPointer.collectAsStateWithLifecycle(null)
    val navigator = LocalCodeNavigator.current

    val hazeState = rememberHazeState()
    val keyboard = rememberKeyboardController()

    ChatInputScaffold(
        topBar = { ChatTopBar(navigator, state.chattingWith) },
        bottomBar = {
            if (state.isAnonymous) {
                DeactivatedChatBottomBar()
            } else {
                UserControlBottomBar(
                    state = state,
                    hazeState = hazeState,
                    dispatch = viewModel::dispatchEvent,
                )
            }
        },
    ) { overlapPadding ->
        MessageList(
            modifier = Modifier
                .fillMaxSize()
                .testTag("chat_message_list")
                .hazeSource(hazeState),
            state = state,
            contentPadding = overlapPadding,
            messages = messages,
            separatorConfig = state.separatorConfig,
            otherReadPointer = otherReadPointer,
            onAction = { action ->
                when (action) {
                    is ChatAction.AdvanceReadPointer -> {
                        viewModel.dispatchEvent(ChatViewModel.Event.AdvanceReadPointer(action.messageId))
                    }
                    ChatAction.RefreshContact -> {
                        viewModel.dispatchEvent(ChatViewModel.Event.RefreshContact)
                    }
                    is ChatAction.RetryMessage -> {
                        keyboard.hideIfVisible {
                            viewModel.dispatchEvent(
                                ChatViewModel.Event.RetryMessage(action.bubble.pendingClientIdHex, action.bubble.content)
                            )
                        }
                    }
                    is ChatAction.ViewToken -> {
                        keyboard.hideIfVisible {
                            viewModel.dispatchEvent(
                                ChatViewModel.Event.OpenScreen(AppRoute.Token.Info(action.mint))
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun ChatTopBar(
    navigator: CodeNavigator,
    chattingWith: DeviceContact?,
) {
    var titleHeight by remember { mutableStateOf(0.dp) }
    val bgColor = CodeTheme.colors.background
    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(titleHeight + 24.dp)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to bgColor,
                            1f to Color.Transparent,
                        )
                    )
                )
        )
        AppBarWithTitle(
            modifier = Modifier.measured { titleHeight = it.height },
            leftIcon = {
                AppBarDefaults.UpNavigation { navigator.pop() }
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                ) {
                    ContactAvatar(
                        contact = chattingWith,
                        modifier = Modifier
                            .requiredSize(CodeTheme.dimens.staticGrid.x8)
                            .clip(CircleShape),
                    )

                    Text(
                        modifier = Modifier.weight(1f),
                        text = chattingWith?.displayName.orEmpty(),
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.textMain,
                    )
                }
            }
        )
    }
}

@Composable
private fun UserControlBottomBar(
    state: ChatViewModel.State,
    hazeState: HazeState,
    dispatch: (ChatViewModel.Event) -> Unit,
) {
    val keyboard = rememberKeyboardController()
    val focusRequester = remember { FocusRequester() }
    var buttonHeight by remember { mutableStateOf(0.dp) }
    val material = HazeMaterials.ultraThin(
        containerColor = CodeTheme.colors.background
    )

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
                (scaleIn(ChatAnimations.typingIndicator, initialScale = 0.95f, transformOrigin = TransformOrigin(0f, 0.5f))
                    + fadeIn(ChatAnimations.typingIndicator)) togetherWith
                    (scaleOut(ChatAnimations.typingIndicator, targetScale = 0.95f, transformOrigin = TransformOrigin(0f, 0.5f))
                        + fadeOut(ChatAnimations.typingIndicator))
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
                    .navigationBarsPadding(),
                targetState = state.userState,
                transitionSpec = {
                    // Action bar <-> composer swap
                    // Buttons: scale from 0.95 + opacity; Composer: opacity only
                    val swapSpec = ChatAnimations.composerSwap
                    when (targetState) {
                        ChatViewModel.UserState.Typing ->
                            // Composer fades in; buttons scale+fade out
                            fadeIn(swapSpec) togetherWith
                                (scaleOut(swapSpec, targetScale = 0.95f) + fadeOut(swapSpec))

                        ChatViewModel.UserState.Reading ->
                            // Buttons scale+fade in; composer fades out
                            (scaleIn(swapSpec, initialScale = 0.95f) + fadeIn(swapSpec)) togetherWith
                                fadeOut(swapSpec)
                    }
                },
            ) { s ->
                when (s) {
                    ChatViewModel.UserState.Reading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                        ) {
                            CodeButton(
                                modifier = Modifier.weight(1f),
                                buttonState = ButtonState.Filled,
                                text = stringResource(R.string.action_sendCash),
                            ) { dispatch(ChatViewModel.Event.OnSendCash) }
                            if (state.typingConstraints.enabled) {
                                CodeButton(
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("chat_send_message_button")
                                        .hazeEffect(hazeState) {
                                            blurEffect {
                                                style = material
                                            }
                                        },
                                    buttonState = ButtonState.Filled10,
                                    text = stringResource(R.string.action_sendMessage),
                                ) { dispatch(ChatViewModel.Event.OnStartMessageInput) }
                            }
                        }
                    }

                    ChatViewModel.UserState.Typing -> {
                        ChatInput(
                            modifier = Modifier
                                .testTag("chat_message_input")
                                .fillMaxWidth()
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

                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }
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

@Composable
private fun ChatInputScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    var topBarHeight by remember { mutableStateOf(0.dp) }
    var bottomBarHeight by remember { mutableStateOf(0.dp) }

    Box(modifier = Modifier.imePadding().testTag("chat_screen")) {
        content(
            PaddingValues(
                top = topBarHeight,
                bottom = bottomBarHeight,
            )
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { topBarHeight = with(density) { it.height.toDp() } }
        ) {
            topBar()
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomBarHeight = with(density) { it.height.toDp() } }
        ) {
            bottomBar()
        }
    }
}
