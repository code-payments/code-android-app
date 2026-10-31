package com.flipcash.shared.chat.inject

import com.flipcash.shared.chat.ChatArchiveStore
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatDraftStore
import com.flipcash.shared.chat.HostLobbies
import com.flipcash.shared.chat.LobbyRequestStore
import com.flipcash.shared.chat.LobbyUpdateSink
import com.flipcash.shared.chat.GroupRosters
import com.flipcash.shared.chat.RosterUpdateSink
import com.flipcash.shared.chat.RosterSearchSource
import com.flipcash.shared.chat.ApproveNewMembersStore
import com.flipcash.shared.chat.internal.DataStoreApproveNewMembersStore
import com.flipcash.shared.chat.internal.DataStoreLobbyRequestStore
import com.flipcash.shared.chat.internal.DmOutgoingEncryption
import com.flipcash.shared.chat.alerts.ReactionAlertController
import com.flipcash.shared.chat.PaymentRequestUpdateSink
import com.flipcash.shared.chat.PaymentRequests
import com.flipcash.shared.chat.alerts.ReactionAlertSink
import com.flipcash.shared.chat.alerts.ReactionActorProfiles
import com.flipcash.shared.chat.alerts.StoredReactionActorProfiles
import com.flipcash.shared.chat.internal.MentionPoolUpdates
import com.flipcash.shared.chat.internal.MentionSuggestionPool
import com.flipcash.shared.chat.internal.ServerRosterSearchSource
import com.flipcash.shared.chat.internal.OutgoingEncryption
import com.flipcash.shared.chat.internal.RealChatArchiveStore
import com.flipcash.shared.chat.internal.RealChatCoordinator
import com.flipcash.shared.chat.internal.RealChatDraftStore
import com.flipcash.app.persistence.sources.GroupKeyEnvelopeDataSource
import com.flipcash.services.chat.GroupKeyEnvelopeStore
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
    internal abstract fun bindLobbyRequestStore(
        impl: DataStoreLobbyRequestStore
    ): LobbyRequestStore

    @Binds
    internal abstract fun bindApproveNewMembersStore(
        impl: DataStoreApproveNewMembersStore
    ): ApproveNewMembersStore

    @Binds
    abstract fun bindLobbyUpdateSink(
        impl: HostLobbies
    ): LobbyUpdateSink

    @Binds
    abstract fun bindRosterUpdateSink(
        impl: GroupRosters
    ): RosterUpdateSink

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
    internal abstract fun bindReactionActorProfiles(
        impl: StoredReactionActorProfiles
    ): ReactionActorProfiles

    @Binds
    internal abstract fun bindPaymentRequestUpdateSink(
        impl: PaymentRequests
    ): PaymentRequestUpdateSink

    @Binds
    internal abstract fun bindGroupKeyEnvelopeStore(
        impl: GroupKeyEnvelopeDataSource
    ): GroupKeyEnvelopeStore

    @Binds
    internal abstract fun bindReactionAlertSink(
        impl: ReactionAlertController
    ): ReactionAlertSink

    @Binds
    @IntoSet
    abstract fun bindSessionListener(
        impl: RealChatCoordinator
    ): SessionListener
}
