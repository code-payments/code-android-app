@file:OptIn(ExperimentalFoundationApi::class)

package com.flipchat.features.chat.conversation

import android.os.Parcelable
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.oct24.R
import com.flipchat.features.home.TabbedHomeScreen
import com.getcode.extensions.formatted
import com.getcode.model.TwitterUser
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.NamedScreen
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.components.chat.ChatInput
import com.getcode.ui.components.chat.MessageList
import com.getcode.ui.components.chat.MessageListEvent
import com.getcode.ui.components.chat.TypingIndicator
import com.getcode.ui.components.chat.UserAvatar
import com.getcode.ui.components.chat.utils.ChatItem
import com.getcode.ui.components.chat.utils.HandleMessageChanges
import com.getcode.util.formatDateRelatively
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class ConversationScreen(
    val user: @RawValue TwitterUser? = null,
    val chatId: com.getcode.model.ID? = null,
    val intentId: com.getcode.model.ID? = null
) : Screen, NamedScreen, Parcelable {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val vm = getViewModel<ConversationViewModel>()
        val state by vm.stateFlow.collectAsState()

        val messages = vm.messages.collectAsLazyPagingItems()

        val goBack = { navigator.popUntil { it is TabbedHomeScreen } }

        Column {
            AppBarWithTitle(
                title = {
                    ConversationTitle(
                        modifier = Modifier,
                        state = state,
                    )
                },
                titleAlignment = Alignment.Start,
                leftIcon = {
                    AppBarDefaults.UpNavigation { goBack() }
                }
            )
            ConversationScreenContent(state, messages, vm::dispatchEvent)
        }

        BackHandler {
            goBack()
        }

        LaunchedEffect(vm) {
            vm.eventFlow
                .filterIsInstance<ConversationViewModel.Event.SendCash>()
                .onEach {
//                    navigator.push(EnterTipModal(isInChat = true))
                }.launchIn(this)
        }

        val context = LocalContext.current
        LaunchedEffect(vm) {
            vm.eventFlow
                .filterIsInstance<ConversationViewModel.Event.Error>()
                .onEach {
                    if (it.show) {
                        Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                    }
                    if (it.fatal) {
                        navigator.popAll()
                    }
                }.launchIn(this)
        }

        LaunchedEffect(user) {
            if (user != null) {
                vm.dispatchEvent(
                    ConversationViewModel.Event.OnTwitterUserChanged(user)
                )
            }
        }

        LaunchedEffect(chatId) {
            if (chatId != null) {
                vm.dispatchEvent(
                    ConversationViewModel.Event.OnChatIdChanged(chatId)
                )
            }
        }
    }
}

@Composable
private fun ConversationTitle(
    modifier: Modifier = Modifier,
    state: ConversationViewModel.State,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((-8).dp)
        ) {
            val imageModifier = Modifier
                .padding(start = CodeTheme.dimens.grid.x7)
                .size(CodeTheme.dimens.staticGrid.x6)
                .clip(CircleShape)

            state.users.fastForEachIndexed { index, user ->
                UserAvatar(
                    modifier = imageModifier
                        .zIndex((state.users.size - index).toFloat()),
                    data = if (user.isRevealed) {
                        user.imageUrl
                    } else {
                        user.memberId
                    }
                )
            }

            if (state.users.isEmpty()) {
                Spacer(modifier = Modifier.requiredWidth(CodeTheme.dimens.grid.x3))
            }
        }

        Column {
            val title = state.users.mapNotNull { it.displayName }
                .joinToString()
                .takeIf { it.isNotEmpty() } ?: "Anonymous Tipper"
            Text(
                text = title,
                style = CodeTheme.typography.screenTitle
            )
            state.lastSeen?.let {
                Text(
                    text = "Last seen ${it.formatDateRelatively()}",
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ConversationScreenContent(
    state: ConversationViewModel.State,
    messages: LazyPagingItems<ChatItem>,
    dispatchEvent: (ConversationViewModel.Event) -> Unit,
) {
    CodeScaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .imePadding()
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
            ) {
                val canChat = remember(state.twitterUser) {
                    state.twitterUser == null || state.twitterUser.isFriend
                }
                if (canChat) {
                    AnimatedVisibility(
                        visible = state.showTypingIndicator,
                        enter = slideInVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ) { it } + scaleIn() + fadeIn(),
                        exit = fadeOut() + scaleOut() + slideOutVertically { it }
                    ) {
                        TypingIndicator(
                            modifier = Modifier
                                .padding(horizontal = CodeTheme.dimens.grid.x2)
                        )
                    }
                    ChatInput(
                        state = state.textFieldState,
                        sendCashEnabled = state.tipChatCash.enabled,
                        onSendMessage = { dispatchEvent(ConversationViewModel.Event.SendMessage) },
                        onSendCash = { dispatchEvent(ConversationViewModel.Event.SendCash) }
                    )
                } else {
                    CodeButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = CodeTheme.dimens.grid.x2)
                            .padding(horizontal = CodeTheme.dimens.inset),
                        buttonState = ButtonState.Filled,
                        text = stringResource(
                            R.string.action_payToChat,
                            state.costToChat.formatted(suffix = "")
                        )
                    ) {
                        dispatchEvent(ConversationViewModel.Event.PresentPaymentConfirmation)
                    }
                }
            }
        }
    ) { padding ->
        val lazyListState = rememberLazyListState()
        MessageList(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            messages = messages,
            listState = lazyListState,
            dispatch = { event ->
                if (event is MessageListEvent.AdvancePointer) {
                    dispatchEvent(ConversationViewModel.Event.MarkRead(event.messageId))
                }
            }
        )

        HandleMessageChanges(listState = lazyListState, items = messages) { message ->
            dispatchEvent(ConversationViewModel.Event.MarkDelivered(message.chatMessageId))
        }
    }
}