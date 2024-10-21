package com.getcode.inject

import com.getcode.mapper.ConversationMapper
import com.getcode.mapper.ConversationMessageWithContentMapper
import com.getcode.oct24.network.controllers.ConversationController
import com.getcode.oct24.network.controllers.ConversationStreamController
import com.getcode.oct24.network.controllers.ChatHistoryController
import com.getcode.network.TipController
import com.getcode.network.exchange.Exchange
import com.getcode.network.service.ChatServiceV2
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun providesConversationController(
        historyController: com.getcode.oct24.network.controllers.ChatHistoryController,
        chatServiceV2: ChatServiceV2,
        exchange: Exchange,
        conversationMapper: ConversationMapper,
        messageWithContentMapper: ConversationMessageWithContentMapper,
        tipController: TipController,
    ): com.getcode.oct24.network.controllers.ConversationController =
        com.getcode.oct24.network.controllers.ConversationStreamController(
            historyController = historyController,
            exchange = exchange,
            chatService = chatServiceV2,
            conversationMapper = conversationMapper,
            messageWithContentMapper = messageWithContentMapper,
            tipController = tipController
        )
}