package com.getcode.inject

import com.getcode.network.ConversationController
import com.getcode.network.ConversationMockController
import com.getcode.network.ConversationStreamController
import com.getcode.network.HistoryController
import com.getcode.network.client.Client
import com.getcode.network.exchange.Exchange
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
        historyController: HistoryController,
        client: Client,
        exchange: Exchange
    ): ConversationController = ConversationStreamController(historyController, exchange, client)
}