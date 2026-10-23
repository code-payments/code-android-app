package com.flipcash.services.inject

import com.flipcash.services.chat.ChatKeySource
import com.flipcash.services.chat.DefaultChatKeySource
import com.flipcash.services.chat.GroupKeyEnvelopes
import com.flipcash.services.chat.GroupKeySource
import com.getcode.chatcipher.ChatCipher
import com.getcode.chatcipher.DefaultChatCipher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ChatCryptoModule {

    @Binds
    abstract fun bindChatKeySource(source: DefaultChatKeySource): ChatKeySource

    @Binds
    abstract fun bindGroupKeySource(source: GroupKeyEnvelopes): GroupKeySource

    companion object {
        @Provides
        fun provideChatCipher(): ChatCipher = DefaultChatCipher
    }
}
