package com.flipcash.shared.chat.inject

import com.flipcash.shared.chat.ChatArchiveStore
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatDraftStore
import com.flipcash.shared.chat.RosterSearchSource
import com.flipcash.shared.chat.internal.DmOutgoingEncryption
import com.flipcash.shared.chat.internal.MentionPoolUpdates
import com.flipcash.shared.chat.internal.MentionSuggestionPool
import com.flipcash.shared.chat.internal.ServerRosterSearchSource
import com.flipcash.shared.chat.internal.OutgoingEncryption
import com.flipcash.shared.chat.internal.RealChatArchiveStore
import com.flipcash.shared.chat.internal.RealChatCoordinator
import com.flipcash.shared.chat.internal.RealChatDraftStore
import com.flipcash.shared.chat.internal.delegates.MediaSendDelegate
import com.flipcash.shared.chat.media.ChatMediaSending
import com.getcode.opencode.providers.SessionListener
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatModule {

    @Binds
    @Singleton
    abstract fun bindChatCoordinator(
        impl: RealChatCoordinator
    ): ChatCoordinator

    @Binds
    @Singleton
    abstract fun bindChatDraftStore(
        impl: RealChatDraftStore
    ): ChatDraftStore

    @Binds
    @Singleton
    abstract fun bindChatArchiveStore(
        impl: RealChatArchiveStore
    ): ChatArchiveStore

    @Binds
    @Singleton
    abstract fun bindChatMediaSending(
        impl: MediaSendDelegate
    ): ChatMediaSending

    @Binds
    internal abstract fun bindOutgoingEncryption(
        impl: DmOutgoingEncryption
    ): OutgoingEncryption

    @Binds
    internal abstract fun bindRosterSearchSource(
        impl: ServerRosterSearchSource
    ): RosterSearchSource

    @Binds
    internal abstract fun bindMentionPoolUpdates(
        impl: MentionSuggestionPool
    ): MentionPoolUpdates

    @Binds
    @IntoSet
    abstract fun bindSessionListener(
        impl: RealChatCoordinator
    ): SessionListener
}
