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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.model.ID
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.OnLifecycleEvent
import com.getcode.ui.components.chat.TypingIndicator
import com.getcode.ui.components.chat.utils.ChatItem
import com.getcode.ui.components.chat.utils.localizedText
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.addIf
import com.getcode.ui.utils.generateComplementaryColorPalette
import com.getcode.ui.utils.unboundedClickable
import com.getcode.ui.utils.withTopBorder
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.features.home.TabbedHomeScreen

@Parcelize
data class ConversationScreen(
    val chatId: ID? = null,
    val intentId: ID? = null
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

        val goBack = {
            vm.dispatchEvent(ConversationViewModel.Event.OnClose)
            navigator.popUntil { it is TabbedHomeScreen }
        }

        BackHandler { goBack() }

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
                    if (state.chattableState.isMember()) {
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
                }
            )
            ConversationScreenContent(
                state = state,
                messages = messages,
                dispatchEvent = vm::dispatchEvent
            )
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
private fun ConversationScreenContent(
    state: ConversationViewModel.State,
    messages: LazyPagingItems<ChatItem>,
    dispatchEvent: (ConversationViewModel.Event) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

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

                Column(
                    modifier = Modifier
                        .addIf(state.chattableState.isActiveMember()) {
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
                            val colors = generateComplementaryColorPalette(replyingTo.sender.id!!)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CodeTheme.colors.background)
                                    .height(IntrinsicSize.Min)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(CodeTheme.dimens.thickBorder)
                                        .background(colors?.first ?: CodeTheme.colors.tertiary)
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(start = CodeTheme.dimens.grid.x1)
                                        .padding(vertical = CodeTheme.dimens.grid.x1)
                                        .weight(1f)
                                ) {
                                    Text(
                                        text = replyingTo.sender.displayName.orEmpty()
                                            .ifEmpty { "Member" },
                                        color = colors?.second ?: CodeTheme.colors.tertiary,
                                        style = CodeTheme.typography.textSmall
                                    )
                                    Text(
                                        text = replyingTo.message.localizedText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = CodeTheme.colors.textMain,
                                        style = CodeTheme.typography.caption
                                    )
                                }
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