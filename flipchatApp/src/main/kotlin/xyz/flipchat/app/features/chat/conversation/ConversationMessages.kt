package xyz.flipchat.app.features.chat.conversation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.runtime.Composable
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
import com.getcode.manager.TopBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.MessageList
import com.getcode.ui.components.chat.MessageListEvent
import com.getcode.ui.components.chat.MessageListPointerResult
import com.getcode.ui.components.chat.utils.ChatItem
import com.getcode.ui.components.chat.utils.HandleMessageChanges
import com.getcode.ui.components.text.markup.Markup
import com.getcode.ui.utils.keyboardAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.flipchat.app.R
import xyz.flipchat.app.util.dialNumber
import kotlin.math.abs

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun ConversationMessages(
    modifier: Modifier = Modifier,
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

    Box(
        modifier = modifier,
    ) {
        MessageList(
            modifier = Modifier.fillMaxSize(),
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
                        dispatchEvent(ConversationViewModel.Event.MarkRead(event.messageId))
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

                    is MessageListEvent.ReplyToMessage -> {
                        dispatchEvent(ConversationViewModel.Event.ReplyTo(event.message))
                        focusRequester.requestFocus()
                    }

                    is MessageListEvent.ViewOriginalMessage -> {
                        composeScope.launch {
                            val itemIndex = messages.itemSnapshotList
                                .filterIsInstance<ChatItem.Message>()
                                .indexOfFirst { it.chatMessageId == event.messageId }
                            if (itemIndex >= 0) {
                                val currentIndex = lazyListState.firstVisibleItemIndex
                                val distance = abs(itemIndex - currentIndex)

                                if (distance <= 100) {
                                    lazyListState.animateScrollToItem(itemIndex)
                                } else {
                                    lazyListState.scrollToItem(itemIndex)
                                }

                            }
                        }
                    }
                }
            }
        )

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
                ).align(Alignment.BottomEnd),
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