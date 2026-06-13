package com.flipcash.app.messenger.internal.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.flipcash.app.contacts.device.DeviceContact
import com.flipcash.app.contacts.ui.ContactAvatar
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.screens.components.MessageList
import com.flipcash.app.messenger.internal.screens.components.SeparatorConfig
import com.flipcash.features.messenger.R
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.results.key
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.chat.ChatInput
import com.getcode.ui.core.debugBounds
import com.getcode.ui.core.drawWithGradient
import com.getcode.ui.core.measured
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.utils.rememberKeyboardController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
internal fun MessengerScreen(viewModel: ChatViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val messages = viewModel.messages.collectAsLazyPagingItems()
    val otherReadPointer by viewModel.otherReadPointer.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current

    val hazeState = rememberHazeState()

    ChatInputScaffold(
        topBar = { ChatTopBar(navigator, state.chattingWith) },
        bottomBar = {
            UserControlBottomBar(
                state = state,
                hazeState = hazeState,
                dispatch = viewModel::dispatchEvent,
            )
        },
    ) { overlapPadding ->
        MessageList(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            contentPadding = overlapPadding,
            messages = messages,
            separatorConfig = SeparatorConfig.TimeGap(),
            otherReadPointer = otherReadPointer,
            onAdvanceReadPointer = { messageId ->
                viewModel.dispatchEvent(ChatViewModel.Event.AdvanceReadPointer(messageId))
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
    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(titleHeight)
                .drawWithGradient(
                    color = CodeTheme.colors.background,
                    startY = { size.height * 1.5f },
                    endY = { size.height * 0.25f }
                )
        )
    AppBarWithTitle(
        modifier = Modifier.measured { titleHeight = it.height },
        leftIcon = {
            AppBarDefaults.UpNavigation { navigator.pop() }
        },
        rightContents = {
            AppBarDefaults.Overflow {
                // TODO:
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            ) {
                ContactAvatar(
                    modifier = Modifier
                        .border(
                            CodeTheme.dimens.border,
                            CodeTheme.colors.divider,
                            CircleShape
                        )
                        .size(CodeTheme.dimens.staticGrid.x8)
                        .clip(CircleShape),
                    photoUri = chattingWith?.photoUri,
                    displayName = chattingWith?.displayName.orEmpty(),
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

    LaunchedEffect(keyboard.visible) {
        if (!keyboard.visible) {
            dispatch(ChatViewModel.Event.OnStopMessageInput)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
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
                when (targetState) {
                    ChatViewModel.UserState.Typing ->
                        slideInVertically { it } + fadeIn() togetherWith fadeOut()

                    ChatViewModel.UserState.Reading ->
                        fadeIn() togetherWith slideOutVertically { it } + fadeOut()
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
                        if (state.hasPayment) {
                            val material = HazeMaterials.ultraThin(
                                containerColor = CodeTheme.colors.background
                            )
                            CodeButton(
                                modifier = Modifier.weight(1f)
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
                            .fillMaxWidth()
                            .border(
                                CodeTheme.dimens.border,
                                CodeTheme.colors.divider,
                                CodeTheme.shapes.medium,
                            ),
                        focusRequester = focusRequester,
                        hint = "Message",
                        state = state.chatInputState,
                        onSendMessage = { dispatch(ChatViewModel.Event.SendMessage) },
                    )

                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }
                }
            }
        }
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

    Box {
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
                .imePadding()
        ) {
            bottomBar()
        }
    }
}
