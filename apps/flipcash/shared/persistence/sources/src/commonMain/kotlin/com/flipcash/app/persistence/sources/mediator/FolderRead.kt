package com.flipcash.app.persistence.sources.mediator

import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatFolder
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType

/**
 * Every chat of [chatType] in [folder], paged to the end. Nothing is written.
 *
 * Null when a page fails, or when the server says there is more without a token to fetch it: a
 * read that stopped part way can't stand in for the folder, since a chat it never reached would
 * read as absent.
 */
suspend fun ChatController.readFolder(
    chatType: ChatType,
    folder: ChatFolder,
    pageSize: Int = QueryOptions().limit,
): List<ChatMetadata>? {
    val chats = mutableListOf<ChatMetadata>()
    var token: PagingToken? = null
    do {
        val options = QueryOptions(limit = pageSize, token = token, descending = true)
        val page = when (chatType) {
            ChatType.GROUP -> getGroupChatFeed(options, folder)
            else -> getDmChatFeed(chatType, options, folder)
        }.getOrElse { return null }
        chats += page.chats
        token = page.pagingToken
        if (page.hasMore && token == null) return null
    } while (page.hasMore)
    return chats
}
