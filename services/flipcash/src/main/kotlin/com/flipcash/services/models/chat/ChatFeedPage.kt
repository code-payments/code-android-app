package com.flipcash.services.models.chat

import com.flipcash.services.models.PagingToken

data class ChatFeedPage(
    val chats: List<ChatMetadata>,
    val pagingToken: PagingToken?,
    val hasMore: Boolean,
)
