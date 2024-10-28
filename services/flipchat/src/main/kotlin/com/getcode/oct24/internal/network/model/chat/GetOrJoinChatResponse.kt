package com.getcode.oct24.internal.network.model.chat

import com.codeinc.flipchat.gen.chat.v1.FlipchatService

data class GetOrJoinChatResponse(
    val metadata: FlipchatService.Metadata,
    val members: List<FlipchatService.Member>
)