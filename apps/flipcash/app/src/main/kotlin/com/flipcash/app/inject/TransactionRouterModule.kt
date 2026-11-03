package com.flipcash.app.inject

import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatHydration
import com.flipcash.shared.transactionhistory.TransactionRouter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object TransactionRouterModule {
    /**
     * A send-back's activity row opens its DM only when the chat is stored or the server still has
     * it. An absent or unreachable chat opens the details instead.
     */
    @Provides
    fun providesTransactionRouter(chats: ChatCoordinator): TransactionRouter =
        TransactionRouter.forChats { chatId ->
            when (chats.hydrateChat(chatId)) {
                ChatHydration.Stored, is ChatHydration.Fetched -> true
                else -> false
            }
        }
}
