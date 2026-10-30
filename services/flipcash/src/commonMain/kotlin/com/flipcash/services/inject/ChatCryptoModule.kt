package com.flipcash.services.inject

import com.flipcash.services.chat.ChatKeySource
import com.flipcash.services.chat.DefaultChatKeySource
import com.flipcash.services.chat.GroupKeyEnvelopes
import com.flipcash.services.chat.GroupKeySource
import com.getcode.chatcipher.ChatCipher
import com.getcode.chatcipher.DefaultChatCipher
import com.getcode.chatcipher.DefaultInviteCipher
import com.getcode.chatcipher.InviteCipher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatCryptoModule {

    @Binds
    internal abstract fun bindChatKeySource(source: DefaultChatKeySource): ChatKeySource

    @Binds
    internal abstract fun bindGroupKeySource(source: GroupKeyEnvelopes): GroupKeySource

    companion object {
        @Provides
        fun provideChatCipher(): ChatCipher = DefaultChatCipher

        @Provides
        fun provideInviteCipher(): InviteCipher = DefaultInviteCipher
    }
}
