package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatType
import kotlinx.coroutines.flow.Flow

/**
 * The chat types the Chats list shows: tip DMs and groups. Contact DMs have their own inbox.
 *
 * The list and the Chats tab badge both read through the functions below, so the badge cannot
 * count a different set of chats than the rows it sits above.
 */
private val chatListTypes = arrayOf(ChatType.TIP_DM, ChatType.GROUP)

/** [FeedOperations.feed] for the chats the Chats list shows. */
fun FeedOperations.chatListFeed(): Flow<List<ChatSummary>> = feed(*chatListTypes)

/** [FeedOperations.currentFeed] for the chats the Chats list shows. */
fun FeedOperations.currentChatListFeed(): List<ChatSummary>? = currentFeed(*chatListTypes)

/** How many of the chats the Chats list shows have unread messages; the Chats tab badge. */
fun FeedOperations.observeUnreadChatListCount(): Flow<Int> = observeUnreadConversations(*chatListTypes)
