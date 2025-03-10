package xyz.flipchat.app.features.chat.conversation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.paging.compose.LazyPagingItems
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.getcode.manager.TopBarManager
import com.getcode.model.chat.AnnouncementAction
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.ContextSheet
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.MessageList
import com.getcode.ui.components.chat.MessageListEvent
import com.getcode.ui.components.chat.MessageListPointerResult
import com.getcode.ui.components.chat.TypingIndicator
import com.getcode.ui.components.chat.messagecontents.LocalAnnouncementActionResolver
import com.getcode.ui.components.chat.messagecontents.MessageContextAction
import com.getcode.ui.components.chat.messagecontents.ResolvedAction
import com.getcode.ui.components.chat.utils.ChatItem
import com.getcode.ui.components.chat.utils.HandleMessageChanges
import com.getcode.ui.components.text.markup.Markup
import com.getcode.ui.emojis.EmojiModal
import com.getcode.ui.emojis.FrequentlyUsedEmojis
import com.getcode.ui.utils.animateScrollToItemWithFullVisibility
import com.getcode.ui.utils.keyboardAsState
import com.getcode.ui.utils.scrollToItemWithFullVisibility
import com.getcode.ui.utils.verticalScrollStateGradient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.flipchat.app.R
import xyz.flipchat.app.util.dialNumber
import kotlin.math.abs

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun ConversationMessages(
    modifier: Modifier = Modifier,
    state: ConversationViewModel.State,
    messages: LazyPagingItems<ChatItem>,
    focusRequester: FocusRequester,
    dispatchEvent: (ConversationViewModel.Event) -> Unit,
) {
    val navigator = LocalCodeNavigator.current
    val lazyListState = rememberLazyListState()
    val keyboardVisible by keyboardAsState()
    val ime = LocalSoftwareKeyboardController.current
    val composeScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    fun resolveAnnouncementAction(action: AnnouncementAction): ResolvedAction? {
        return when (action) {
            AnnouncementAction.Unknown -> null
            AnnouncementAction.Share -> ResolvedAction(
                text = if (state.isHost) {
                    context.getString(R.string.action_shareRoomLinkAsHost)
                } else {
                    context.getString(R.string.action_shareRoomLinkAsMember)
                },
                onClick = { dispatchEvent(ConversationViewModel.Event.OnShareRoomLink) }
            )
        }
    }

    Box(
        modifier = modifier,
    ) {
        CompositionLocalProvider(LocalAnnouncementActionResolver provides {
            resolveAnnouncementAction(
                it
            )
        }) {
            MessageList(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScrollStateGradient(
                        scrollState = lazyListState,
                        color = CodeTheme.colors.background,
                        showAtStartAlways = true,
                        showAtEnd = false
                    ),
                messages = messages,
                listState = lazyListState,
                handleMessagePointers = { (current, previous, next) ->
                    MessageListPointerResult(
                        current.sender.id == previous?.sender?.id,
                        current.sender.id == next?.sender?.id
                    )
                },
                footer = {
                    AnimatedContent(
                        targetState = state.otherUsersTyping.isNotEmpty(),
                        transitionSpec = {
                            slideInVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) { it } + scaleIn() + fadeIn() togetherWith
                                    fadeOut() + slideOutVertically { it }
                        }
                    ) { show ->
                        if (show) {
                            TypingIndicator(
                                modifier = Modifier
                                    .padding(horizontal = CodeTheme.dimens.inset),
                                userImages = state.otherUsersTyping
                            )
                        }
                    }
                },
                dispatch = { event ->
                    when (event) {
                        is MessageListEvent.AdvancePointer -> {
                            dispatchEvent(ConversationViewModel.Event.MarkRead(event.messageId))
                        }

                        is MessageListEvent.OpenMessageActions -> {
                            composeScope.launch {
                                if (keyboardVisible) {
                                    ime?.hide()
                                }

                                val actions = event.actions.map {
                                    if (it is MessageContextAction.Emojis) {
                                        it.copy(
                                            Content = {
                                                FrequentlyUsedEmojis(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    onSelect = { emoji ->
                                                        navigator.hide()
                                                        dispatchEvent(
                                                            ConversationViewModel.Event.SendReaction(
                                                                event.messageId,
                                                                emoji
                                                            )
                                                        )
                                                    },
                                                    onViewAll = {
                                                        composeScope.launch {
                                                            navigator.hide()
                                                            delay(250)
                                                            navigator.show(
                                                                EmojiModal { emoji ->
                                                                    navigator.hide()
                                                                    dispatchEvent(
                                                                        ConversationViewModel.Event.SendReaction(
                                                                            event.messageId,
                                                                            emoji
                                                                        )
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    }
                                                )
                                            }
                                        )
                                    } else {
                                        it
                                    }
                                }
                                navigator.show(ContextSheet(actions))
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

                        is MessageListEvent.ReplyToMessage -> {
                            dispatchEvent(ConversationViewModel.Event.ReplyTo(event.message))
                        }

                        is MessageListEvent.ViewOriginalMessage -> {
                            composeScope.launch {
                                val itemIndex = messages.itemSnapshotList
                                    .filterIsInstance<ChatItem.Message>()
                                    .indexOfFirst { it.chatMessageId == event.originalMessageId }

                                val currentItemIndex = messages.itemSnapshotList
                                    .filterIsInstance<ChatItem.Message>()
                                    .indexOfFirst { it.chatMessageId == event.messageId }

                                if (itemIndex >= 0) {
                                    val distance = abs(itemIndex - currentItemIndex)

                                    println("distance from current ($currentItemIndex) is $distance")
                                    if (distance <= 100) {
                                        // Animate smoothly if within 100 items
                                        lazyListState.animateScrollToItemWithFullVisibility(
                                            to = itemIndex,
                                        )
                                    } else {
                                        // Jump directly if too far
                                        lazyListState.scrollToItemWithFullVisibility(
                                            to = itemIndex,
                                        )
                                    }
                                }
                            }
                        }

                        is MessageListEvent.TipMessage -> {
                            composeScope.launch {
                                if (keyboardVisible) {
                                    ime?.hide()
                                    delay(500)
                                }
                                dispatchEvent(
                                    ConversationViewModel.Event.TipUser(
                                        event.message.chatMessageId,
                                        event.message.sender.id.orEmpty()
                                    )
                                )
                            }
                        }

                        is MessageListEvent.ShowTipsForMessage -> {
                            composeScope.launch {
                                if (keyboardVisible) {
                                    ime?.hide()
                                    delay(500)
                                }
                                navigator.show(MessageTipsSheet(event.tips))
                            }
                        }

                        is MessageListEvent.UnreadStateHandled -> {
                            dispatchEvent(ConversationViewModel.Event.OnUnreadStateHandled)
                        }

                        is MessageListEvent.ViewUserProfile -> {
                            composeScope.launch {
                                if (keyboardVisible) {
                                    ime?.hide()
                                    delay(500)
                                }
                                navigator.push(
                                    ScreenRegistry.get(
                                        NavScreenProvider.UserProfile(
                                            event.userId
                                        )
                                    )
                                )
                            }
                        }

                        is MessageListEvent.AddReaction -> {
                            dispatchEvent(
                                ConversationViewModel.Event.SendReaction(
                                    event.messageId,
                                    event.emoji
                                )
                            )
                        }
                        is MessageListEvent.RemoveReaction -> {
                            dispatchEvent(
                                ConversationViewModel.Event.RemoveReaction(
                                    event.originalMessageId,
                                )
                            )
                        }
                    }
                }
            )
        }

        val animatedAlpha by animateFloatAsState(
            targetValue = if (lazyListState.canScrollBackward) 1f else 0f,
            animationSpec = tween(durationMillis = 300),
            label = "alpha of jump-to-bottom"
        )

        Surface(
            modifier = Modifier
                .graphicsLayer {
                    alpha = animatedAlpha
                }
                .padding(
                    end = CodeTheme.dimens.inset,
                    bottom = CodeTheme.dimens.inset
                )
                .align(Alignment.BottomEnd),
            shape = CircleShape,
            color = CodeTheme.colors.tertiary,
            onClick = {
                composeScope.launch {
                    if (lazyListState.firstVisibleItemIndex > 100) {
                        lazyListState.scrollToItem(0)
                    } else {
                        lazyListState.animateScrollToItem(0)
                    }
                }
            }
        ) {
            Image(
                modifier = Modifier.padding(CodeTheme.dimens.grid.x2),
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                colorFilter = ColorFilter.tint(CodeTheme.colors.onSurface)
            )
        }
    }

    HandleMessageChanges(listState = lazyListState, items = messages) { message ->
        dispatchEvent(ConversationViewModel.Event.MarkDelivered(message.chatMessageId))
    }
}