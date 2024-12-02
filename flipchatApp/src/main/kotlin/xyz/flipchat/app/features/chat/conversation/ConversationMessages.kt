package xyz.flipchat.app.features.chat.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.paging.compose.LazyPagingItems
import com.getcode.manager.TopBarManager
import com.getcode.navigation.core.LocalCodeNavigator
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
                }
            }
        )
    }

    HandleMessageChanges(listState = lazyListState, items = messages) { message ->
        dispatchEvent(ConversationViewModel.Event.MarkDelivered(message.chatMessageId))
    }
}