package com.getcode.inject

import com.getcode.mapper.ConversationMapper
import com.getcode.mapper.ConversationMessageWithContentMapper
import com.getcode.network.ConversationController
import com.getcode.network.ConversationStreamController
import com.getcode.network.ChatHistoryController
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
        historyController: ChatHistoryController,
        chatServiceV2: ChatServiceV2,
        exchange: Exchange,
        conversationMapper: ConversationMapper,
        messageWithContentMapper: ConversationMessageWithContentMapper,
        tipController: TipController,
    ): ConversationController = ConversationStreamController(
        historyController = historyController,
        exchange = exchange,
        chatService = chatServiceV2,
        conversationMapper = conversationMapper,
        messageWithContentMapper = messageWithContentMapper,
        tipController = tipController
    )
}