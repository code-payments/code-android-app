@file:OptIn(ExperimentalFoundationApi::class)

package xyz.flipchat.app.features.chat.conversation

import android.os.Parcelable
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.lifecycle.Lifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.manager.TopBarManager
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.OnLifecycleEvent
import com.getcode.ui.components.chat.ChatInput
import com.getcode.ui.components.chat.MessageList
import com.getcode.ui.components.chat.MessageListEvent
import com.getcode.ui.components.chat.MessageListPointerResult
import com.getcode.ui.components.chat.TypingIndicator
import com.getcode.ui.components.chat.UserAvatar
import com.getcode.ui.components.chat.messagecontents.MessageControlAction
import com.getcode.ui.components.chat.utils.ChatItem
import com.getcode.ui.components.chat.utils.HandleMessageChanges
import com.getcode.ui.components.text.markup.Markup
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.keyboardAsState
import com.getcode.ui.utils.withTopBorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.R
import xyz.flipchat.app.features.home.TabbedHomeScreen
import xyz.flipchat.app.util.dialNumber

@Parcelize
data class ConversationScreen(
    val chatId: com.getcode.model.ID? = null,
    val intentId: com.getcode.model.ID? = null
) : Screen, Parcelable {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val vm = getViewModel<ConversationViewModel>()

        LaunchedEffect(chatId) {
            if (chatId != null) {
                vm.dispatchEvent(
                    ConversationViewModel.Event.OnChatIdChanged(chatId)
                )
            }
        }

        val state by vm.stateFlow.collectAsState()

        val messages = vm.messages.collectAsLazyPagingItems()

        val goBack = { navigator.popUntil { it is TabbedHomeScreen } }

        OnLifecycleEvent { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    vm.dispatchEvent(ConversationViewModel.Event.ReopenStream)
                }

                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> {
                    vm.dispatchEvent(ConversationViewModel.Event.CloseStream)
                }

                else -> Unit
            }
        }

        Column {
            AppBarWithTitle(
                title = {
                    ConversationTitle(
                        modifier = Modifier,
                        state = state,
                    )
                },
                leftIcon = {
                    AppBarDefaults.UpNavigation { goBack() }
                },
                rightContents = {
                    AppBarDefaults.Overflow {
                        navigator.push(
                            ScreenRegistry.get(
                                NavScreenProvider.Chat.Info(
                                    state.roomInfoArgs
                                )
                            )
                        )
                    }
                }
            )
            ConversationScreenContent(
                navigator = navigator,
                state = state,
                messages = messages,
                dispatchEvent = vm::dispatchEvent
            )
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

        LaunchedEffect(vm) {
            vm.eventFlow
                .filterIsInstance<ConversationViewModel.Event.OpenRoom>()
                .map { it.roomId }
                .onEach {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.Chat.Conversation(it)))
                }.launchIn(this)
        }

        LaunchedEffect(vm) {
            vm.eventFlow
                .filterIsInstance<ConversationViewModel.Event.OpenJoinConfirmation>()
                .map { it.roomInfoArgs }
                .onEach {
                    navigator.push(
                        ScreenRegistry.get(
                            NavScreenProvider.Chat.Lookup.Confirm(args = it, returnToSender = true)
                        )
                    )
                }.launchIn(this)
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
        UserAvatar(
            modifier = Modifier
                .padding(start = CodeTheme.dimens.grid.x2)
                .size(CodeTheme.dimens.staticGrid.x6)
                .clip(CircleShape),
            data = state.imageUri ?: state.conversationId,
            overlay = {
                Image(
                    modifier = Modifier.padding(5.dp),
                    painter = painterResource(R.drawable.ic_fc_chats),
                    colorFilter = ColorFilter.tint(Color.White),
                    contentDescription = null,
                )
            }
        )
        Column {
            Text(
                text = state.title,
                style = CodeTheme.typography.screenTitle.copy(fontSize = 18.sp)
            )

            val memberCount = remember(state.members) {
                state.members.count()
            }

            Text(
                text = pluralStringResource(R.plurals.title_conversationMemberCount, memberCount, memberCount),
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ConversationScreenContent(
    navigator: CodeNavigator,
    state: ConversationViewModel.State,
    messages: LazyPagingItems<ChatItem>,
    dispatchEvent: (ConversationViewModel.Event) -> Unit,
) {
    CodeScaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
            ) {
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

                AnimatedContent(
                    targetState = state.chattableState,
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
                                    .withTopBorder(color = CodeTheme.colors.dividerVariant)
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
                                onSendMessage = { dispatchEvent(ConversationViewModel.Event.SendMessage) },
                                onSendCash = { dispatchEvent(ConversationViewModel.Event.SendCash) }
                            )
                        }
                    }

                }
            }
        }
    ) { padding ->
        val lazyListState = rememberLazyListState()
        val keyboardVisible by keyboardAsState()
        val ime = LocalSoftwareKeyboardController.current
        val composeScope = rememberCoroutineScope()
        val uriHandler = LocalUriHandler.current
        val context = LocalContext.current

        MessageList(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            messages = messages,
            listState = lazyListState,
            handleMessagePointers = { (current, previous, next) ->
                MessageListPointerResult(
                    current.sender.id == previous?.sender?.id,
                    current.sender.id == next?.sender?.id
                )
            },
            dispatch = { event ->
                when (event) {
                    is MessageListEvent.AdvancePointer -> {
                        // TODO: reenable when not spammy
//                        dispatchEvent(ConversationViewModel.Event.MarkRead(event.messageId))
                    }

                    is MessageListEvent.OpenMessageActions -> {
                        composeScope.launch {
                            if (keyboardVisible) {
                                ime?.hide()
                                delay(500)
                            }
                            navigator.show(MessageActionContextSheet(event.actions))
                        }
                    }

                    is MessageListEvent.OnMarkupEvent -> {
                        when (val markup = event.markup) {
                            is Markup.RoomNumber -> {
                                dispatchEvent(ConversationViewModel.Event.LookupRoom(markup.number))
                            }
                            is Markup.Url -> {
                                runCatching {
                                    uriHandler.openUri(markup.link)
                                }.onFailure {
                                    TopBarManager.showMessage(
                                        TopBarManager.TopBarMessage(
                                            title = context.getString(R.string.error_title_failedToOpenLink),
                                            message = context.getString(R.string.error_description_failedToOpenLink)
                                        )
                                    )
                                }
                            }

                            is Markup.Phone -> {
                                context.dialNumber(markup.phoneNumber)
                            }
                        }
                    }
                }
            }
        )

        HandleMessageChanges(listState = lazyListState, items = messages) { message ->
            dispatchEvent(ConversationViewModel.Event.MarkDelivered(message.chatMessageId))
        }
    }
}

private data class MessageActionContextSheet(val actions: List<MessageControlAction>) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        Column(
            modifier = Modifier
                .background(Color(0xFF171921))
                .padding(top = CodeTheme.dimens.inset)
                .navigationBarsPadding()
        ) {
            actions.fastForEachIndexed { index, action ->
                Text(
                    text = when (action) {
                        is MessageControlAction.Copy -> stringResource(R.string.action_copyMessage)
                        is MessageControlAction.Delete -> stringResource(R.string.action_deleteMessage)
                        is MessageControlAction.RemoveUser -> stringResource(
                            R.string.action_removeUser,
                            action.name
                        )

                        is MessageControlAction.ReportUserForMessage -> stringResource(R.string.action_report)
                        is MessageControlAction.MuteUser -> stringResource(
                            R.string.action_muteUser,
                            action.name
                        )
                    },
                    style = CodeTheme.typography.textMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            navigator.hide()
                            action.onSelect()
                        }
                        .padding(
                            horizontal = CodeTheme.dimens.inset,
                            vertical = CodeTheme.dimens.grid.x3
                        )
                )
                if (index < actions.lastIndex) {
                    Divider(color = White05)
                }
            }
        }
    }
}