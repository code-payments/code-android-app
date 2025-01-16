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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.Lifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.manager.BottomBarManager
import com.getcode.model.ID
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.AppScreen
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.OnLifecycleEvent
import com.getcode.ui.components.chat.TypingIndicator
import com.getcode.ui.components.chat.messagecontents.MessageReplyPreview
import com.getcode.ui.components.chat.utils.ChatItem
import com.getcode.ui.components.chat.utils.ReplyMessageAnchor
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.addIf
import com.getcode.ui.utils.keyboardAsState
import com.getcode.ui.utils.noRippleClickable
import com.getcode.ui.utils.unboundedClickable
import com.getcode.ui.utils.withTopBorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.R
import xyz.flipchat.app.features.home.TabbedHomeScreen

@Parcelize
data class ConversationScreen(
    val chatId: ID? = null,
    val roomNumber: Long? = null
) : AppScreen(), Parcelable {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val vm = getViewModel<ConversationViewModel>()

        val keyboardVisible by keyboardAsState()
        val keyboard = LocalSoftwareKeyboardController.current
        val composeScope = rememberCoroutineScope()

        LaunchedEffect(chatId) {
            if (chatId != null) {
                vm.dispatchEvent(
                    ConversationViewModel.Event.OnChatIdChanged(chatId)
                )
            }
        }

        LaunchedEffect(roomNumber) {
            if (roomNumber != null) {
                vm.dispatchEvent(
                    ConversationViewModel.Event.OnRoomNumberChanged(roomNumber)
                )
            }
        }

        LaunchedEffect(vm) {
            vm.eventFlow
                .filterIsInstance<ConversationViewModel.Event.NeedsAccountCreated>()
                .onEach {
                    navigator.show(ScreenRegistry.get(NavScreenProvider.CreateAccount.Start))
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
                    navigator.push(ScreenRegistry.get(NavScreenProvider.Room.Messages(it)))
                }.launchIn(this)
        }

        LaunchedEffect(vm) {
            vm.eventFlow
                .filterIsInstance<ConversationViewModel.Event.OpenJoinConfirmation>()
                .map { it.roomInfoArgs }
                .onEach {
                    navigator.push(
                        ScreenRegistry.get(
                            NavScreenProvider.Room.Lookup.Confirm(args = it, returnToSender = true)
                        )
                    )
                }.launchIn(this)
        }

        LaunchedEffect(result) {
            result
                .filter { it == true }
                .onEach { vm.dispatchEvent(ConversationViewModel.Event.OnAccountCreated) }
                .launchIn(this)
        }

        val state by vm.stateFlow.collectAsState()

        val goBack = {
            composeScope.launch {
                if (keyboardVisible) {
                    keyboard?.hide()
                    delay(500)
                }
                navigator.popUntil { it is TabbedHomeScreen }
            }
        }

        BackHandler { goBack() }

        OnLifecycleEvent { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    vm.dispatchEvent(ConversationViewModel.Event.Resumed)
                }

                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> {
                    vm.dispatchEvent(ConversationViewModel.Event.Stopped)
                }

                else -> Unit
            }
        }

        val openRoomDetails = {
            composeScope.launch {
                if (keyboardVisible) {
                    keyboard?.hide()
                    delay(500)
                }
                navigator.push(
                    ScreenRegistry.get(
                        NavScreenProvider.Room.Info(
                            state.roomInfoArgs
                        )
                    )
                )
            }
        }

        Column {
            AppBarWithTitle(
                title = {
                    ConversationTitle(
                        modifier = Modifier
                            .noRippleClickable { openRoomDetails() },
                        state = state
                    )
                },
                leftIcon = { AppBarDefaults.UpNavigation { goBack() } },
                rightContents = {
                    AppBarDefaults.Overflow { openRoomDetails() }
                }
            )

            val messages = vm.messages.collectAsLazyPagingItems()

            ConversationScreenContent(
                state = state,
                messages = messages,
                dispatchEvent = vm::dispatchEvent
            )
        }
    }
}

@Composable
private fun ConversationScreenContent(
    state: ConversationViewModel.State,
    messages: LazyPagingItems<ChatItem>,
    dispatchEvent: (ConversationViewModel.Event) -> Unit,
) {
    val navigator = LocalCodeNavigator.current
    val focusRequester = remember { FocusRequester() }

    CodeScaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .addIf(navigator.lastItem is ConversationScreen) {
                        Modifier.imePadding()
                    },
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

                Column(
                    modifier = Modifier
                        .addIf(state.chattableState?.isActiveMember() == true) {
                            Modifier.withTopBorder(color = CodeTheme.colors.dividerVariant)
                        }
                ) {
                    AnimatedContent(
                        targetState = state.replyMessage,
                        transitionSpec = {
                            (slideInVertically { it }).togetherWith(slideOutVertically { it })
                        },
                        label = "replying to message visibility",
                    ) { replyingTo ->
                        if (replyingTo != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CodeTheme.colors.background)
                                    .height(IntrinsicSize.Min)
                            ) {
                                MessageReplyPreview(
                                    modifier = Modifier.weight(1f),
                                    originalMessage = ReplyMessageAnchor(
                                        id = replyingTo.id,
                                        sender = replyingTo.sender,
                                        message = replyingTo.message,
                                        isDeleted = false,
                                        deletedBy = null,
                                    )
                                )
                                Image(
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .padding(vertical = CodeTheme.dimens.grid.x1)
                                        .padding(end = CodeTheme.dimens.grid.x1)
                                        .unboundedClickable {
                                            dispatchEvent(ConversationViewModel.Event.CancelReply)
                                        },
                                    imageVector = Icons.Outlined.Clear,
                                    colorFilter = ColorFilter.tint(Color.White),
                                    contentDescription = null
                                )
                            }
                        }
                    }

                    ConversationChatInput(
                        state = state,
                        focusRequester = focusRequester,
                        dispatchEvent = dispatchEvent
                    )
                }
            }
        }
    ) { padding ->
        ConversationMessages(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            messages = messages,
            focusRequester = focusRequester,
            dispatchEvent = dispatchEvent
        )
    }
}