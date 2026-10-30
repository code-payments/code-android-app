package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.chat.v1.ChatGrpcKt
import com.codeinc.flipcash.gen.chat.v1.ChatService as RpcChatService
import com.codeinc.flipcash.gen.chat.v1.validate
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.asChatId
import com.flipcash.services.internal.network.extensions.asProtoChatType
import com.flipcash.services.internal.network.extensions.asProtoIdempotencyKey
import com.flipcash.services.internal.network.extensions.asProtoMuteState
import com.flipcash.services.internal.network.extensions.asProtoSilenceState
import com.flipcash.services.internal.network.extensions.asProtoRules
import com.flipcash.services.internal.network.extensions.asQueryOptions
import com.flipcash.services.internal.network.extensions.asProtoKeyEnvelope
import com.flipcash.services.internal.network.extensions.asProtoInviteId
import com.flipcash.services.internal.network.extensions.asProtoInviteKeyEnvelope
import com.flipcash.services.internal.network.extensions.asProtoInviteParameters
import com.flipcash.services.internal.network.extensions.asProtoInviteSecretEnvelope
import com.flipcash.services.internal.network.extensions.asProtoInviteToken
import com.flipcash.services.internal.network.extensions.asTimestamp
import com.flipcash.services.internal.network.extensions.asUserId
import com.flipcash.services.internal.network.extensions.asUsername
import com.flipcash.services.internal.network.extensions.asMessageId
import com.flipcash.services.internal.network.extensions.asViewMode
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.DescriptionEdit
import com.flipcash.services.models.chat.EditChatParameters
import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.models.chat.InviteId
import com.flipcash.services.models.chat.InviteKeyEnvelope
import com.flipcash.services.models.chat.InviteParameters
import com.flipcash.services.models.chat.InviteSecretEnvelope
import com.flipcash.services.models.chat.InviteToken
import com.flipcash.services.models.chat.KeyEnvelope
import com.flipcash.services.models.chat.MuteState
import com.flipcash.services.models.chat.SilenceState
import com.flipcash.services.models.chat.StartChatParameters
import com.flipcash.services.models.chat.ViewMode
import com.getcode.utils.toByteString
import kotlin.time.Instant
import com.getcode.ed25519kmp.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.internal.network.core.GrpcApi
import dev.bmcreations.protovalidate.orThrow
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ChatApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = ChatGrpcKt.ChatCoroutineStub(managedChannel)
        .withWaitForReady()

    suspend fun getChat(
        owner: KeyPair,
        chatId: ChatId,
        viewMode: ViewMode = ViewMode.FULL,
    ): RpcChatService.GetChatResponse {
        val request = RpcChatService.GetChatRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setViewMode(viewMode.asViewMode())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getChat(request)
        }
    }

    suspend fun getDmChatFeed(
        owner: KeyPair,
        queryOptions: QueryOptions,
        chatType: ChatType,
    ): RpcChatService.GetDmChatFeedResponse {
        val request = RpcChatService.GetDmChatFeedRequest.newBuilder()
            .setQueryOptions(queryOptions.asQueryOptions())
            .setDmChatType(chatType.asProtoChatType())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getDmChatFeed(request)
        }
    }

    suspend fun getGroupChatFeed(
        owner: KeyPair,
        queryOptions: QueryOptions,
    ): RpcChatService.GetGroupChatFeedResponse {
        val request = RpcChatService.GetGroupChatFeedRequest.newBuilder()
            .setQueryOptions(queryOptions.asQueryOptions())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getGroupChatFeed(request)
        }
    }

    /**
     * Starts a new chat. [idempotencyKey] must be minted by the caller — once per logical
     * attempt — and reused across retries of the same attempt; generating a fresh key per call
     * defeats the retry-safety the server offers.
     */
    suspend fun startChat(
        owner: KeyPair,
        parameters: StartChatParameters,
        idempotencyKey: IdempotencyKey,
    ): RpcChatService.StartChatResponse {
        val requestBuilder = RpcChatService.StartChatRequest.newBuilder()
        when (parameters) {
            is StartChatParameters.Group -> requestBuilder.setPublicGroup(
                RpcChatService.StartChatRequest.PublicGroupChatParameters.newBuilder()
                    .setTitle(parameters.title)
                    .apply { parameters.description?.let { setDescription(it) } }
                    .apply {
                        parameters.picture?.let {
                            setProfilePicture(
                                com.codeinc.flipcash.gen.blob.v1.Model.BlobId.newBuilder()
                                    .setValue(it.bytes.toByteString())
                            )
                        }
                    }
                    .apply {
                        parameters.coverPicture?.let {
                            setCoverPicture(
                                com.codeinc.flipcash.gen.blob.v1.Model.BlobId.newBuilder()
                                    .setValue(it.bytes.toByteString())
                            )
                        }
                    }
                    .apply { parameters.rules?.let { setRules(it.asProtoRules()) } }
            )
            is StartChatParameters.PrivateGroup -> requestBuilder.setPrivateGroup(
                RpcChatService.StartChatRequest.PrivateGroupChatParameters.newBuilder()
                    .setTitle(parameters.title)
                    .apply { parameters.description?.let { setDescription(it) } }
                    .apply {
                        parameters.picture?.let {
                            setProfilePicture(
                                com.codeinc.flipcash.gen.blob.v1.Model.BlobId.newBuilder()
                                    .setValue(it.bytes.toByteString())
                            )
                        }
                    }
                    .apply {
                        parameters.coverPicture?.let {
                            setCoverPicture(
                                com.codeinc.flipcash.gen.blob.v1.Model.BlobId.newBuilder()
                                    .setValue(it.bytes.toByteString())
                            )
                        }
                    }
            )
        }

        val request = requestBuilder
            .setIdempotencyKey(idempotencyKey.asProtoIdempotencyKey())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.startChat(request)
        }
    }

    /**
     * [owner] is optional when [inviteToken] is set: the token stands in for auth, so a viewer
     * with an invite link can read the roster before joining. Without a token, [owner] is required.
     */
    suspend fun getRoster(
        owner: KeyPair?,
        chatId: ChatId,
        queryOptions: QueryOptions = QueryOptions(),
        inviteToken: InviteToken? = null,
    ): RpcChatService.GetRosterResponse {
        require(owner != null || inviteToken != null) { "getRoster needs an owner or an invite token" }
        val request = RpcChatService.GetRosterRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setQueryOptions(queryOptions.asQueryOptions())
            .apply { inviteToken?.let { setInviteToken(it.asProtoInviteToken()) } }
            .apply { owner?.let { setAuth(authenticate(it)) } }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getRoster(request)
        }
    }

    /**
     * Public groups only. [owner] is optional: the sample is the same with or without auth, and
     * a viewer who is not in the chat (preview) can call it unauthenticated.
     */
    suspend fun sampleChatters(
        owner: KeyPair?,
        chatId: ChatId,
    ): RpcChatService.SampleChattersResponse {
        val request = RpcChatService.SampleChattersRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .apply { owner?.let { setAuth(authenticate(it)) } }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.sampleChatters(request)
        }
    }

    suspend fun setFeaturedGroups(
        owner: KeyPair,
        chatIds: List<ChatId>,
    ): RpcChatService.SetFeaturedGroupsResponse {
        val request = RpcChatService.SetFeaturedGroupsRequest.newBuilder()
            .addAllChatIds(chatIds.map { it.asChatId() })
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.setFeaturedGroups(request)
        }
    }

    /** [owner] is optional: the list is the same with or without auth. */
    suspend fun getFeaturedGroups(
        owner: KeyPair?,
        username: String,
    ): RpcChatService.GetFeaturedGroupsResponse {
        val request = RpcChatService.GetFeaturedGroupsRequest.newBuilder()
            .setUsername(username.asUsername())
            .apply { owner?.let { setAuth(authenticate(it)) } }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getFeaturedGroups(request)
        }
    }

    suspend fun getMentionSuggestions(
        owner: KeyPair,
        chatId: ChatId,
    ): RpcChatService.GetMentionSuggestionsResponse {
        val request = RpcChatService.GetMentionSuggestionsRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getMentionSuggestions(request)
        }
    }

    suspend fun editChat(
        owner: KeyPair,
        chatId: ChatId,
        parameters: EditChatParameters,
    ): RpcChatService.EditChatResponse {
        val request = RpcChatService.EditChatRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .apply {
                parameters.title?.let {
                    setTitle(
                        RpcChatService.EditChatRequest.Title.newBuilder()
                            .setValue(it)
                    )
                }
                parameters.description?.let { edit ->
                    setDescription(
                        RpcChatService.EditChatRequest.Description.newBuilder()
                            .setValue(
                                when (edit) {
                                    is DescriptionEdit.Set -> edit.value
                                    DescriptionEdit.Clear -> ""
                                }
                            )
                    )
                }
                parameters.picture?.let {
                    setProfilePicture(
                        RpcChatService.EditChatRequest.ProfilePicture.newBuilder()
                            .setBlobId(
                                com.codeinc.flipcash.gen.blob.v1.Model.BlobId.newBuilder()
                                    .setValue(it.bytes.toByteString())
                            )
                    )
                }
                parameters.rules?.let { setRules(it.asProtoRules()) }
                parameters.coverPicture?.let {
                    setCoverPicture(
                        RpcChatService.EditChatRequest.CoverPicture.newBuilder()
                            .setBlobId(
                                com.codeinc.flipcash.gen.blob.v1.Model.BlobId.newBuilder()
                                    .setValue(it.bytes.toByteString())
                            )
                    )
                }
            }
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.editChat(request)
        }
    }

    suspend fun joinChat(
        owner: KeyPair,
        chatId: ChatId,
        invite: InviteParameters? = null,
    ): RpcChatService.JoinChatResponse {
        val request = RpcChatService.JoinChatRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .apply { invite?.let { setInvite(it.asProtoInviteParameters()) } }
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.joinChat(request)
        }
    }

    suspend fun leaveChat(
        owner: KeyPair,
        chatId: ChatId,
    ): RpcChatService.LeaveChatResponse {
        val request = RpcChatService.LeaveChatRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.leaveChat(request)
        }
    }

    suspend fun muteChat(
        owner: KeyPair,
        chatId: ChatId,
        mute: MuteState,
    ): RpcChatService.MuteChatResponse {
        val request = RpcChatService.MuteChatRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setMute(mute.asProtoMuteState())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.muteChat(request)
        }
    }

    suspend fun unmuteChat(
        owner: KeyPair,
        chatId: ChatId,
    ): RpcChatService.UnmuteChatResponse {
        val request = RpcChatService.UnmuteChatRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.unmuteChat(request)
        }
    }

    suspend fun enterLobby(
        owner: KeyPair,
        chatId: ChatId,
    ): RpcChatService.EnterLobbyResponse {
        val request = RpcChatService.EnterLobbyRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.enterLobby(request)
        }
    }

    suspend fun leaveLobby(
        owner: KeyPair,
        chatId: ChatId,
    ): RpcChatService.LeaveLobbyResponse {
        val request = RpcChatService.LeaveLobbyRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.leaveLobby(request)
        }
    }

    suspend fun getLobbyMembers(
        owner: KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions = QueryOptions(),
    ): RpcChatService.GetLobbyMembersResponse {
        val request = RpcChatService.GetLobbyMembersRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setQueryOptions(queryOptions.asQueryOptions())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getLobbyMembers(request)
        }
    }

    suspend fun admitLobbyMember(
        owner: KeyPair,
        chatId: ChatId,
        userId: ID,
        keyEnvelope: KeyEnvelope,
    ): RpcChatService.AdmitLobbyMemberResponse {
        val request = RpcChatService.AdmitLobbyMemberRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setUserId(userId.asUserId())
            .setKeyEnvelope(keyEnvelope.asProtoKeyEnvelope())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.admitLobbyMember(request)
        }
    }

    suspend fun denyLobbyMember(
        owner: KeyPair,
        chatId: ChatId,
        userId: ID,
    ): RpcChatService.DenyLobbyMemberResponse {
        val request = RpcChatService.DenyLobbyMemberRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setUserId(userId.asUserId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.denyLobbyMember(request)
        }
    }

    suspend fun silenceUser(
        owner: KeyPair,
        chatId: ChatId,
        userId: ID,
        silence: SilenceState,
    ): RpcChatService.SilenceUserResponse {
        val request = RpcChatService.SilenceUserRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setUserId(userId.asUserId())
            .setSilence(silence.asProtoSilenceState())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.silenceUser(request)
        }
    }

    suspend fun unsilenceUser(
        owner: KeyPair,
        chatId: ChatId,
        userId: ID,
    ): RpcChatService.UnsilenceUserResponse {
        val request = RpcChatService.UnsilenceUserRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setUserId(userId.asUserId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.unsilenceUser(request)
        }
    }

    suspend fun banUser(
        owner: KeyPair,
        chatId: ChatId,
        userId: ID,
    ): RpcChatService.BanUserResponse {
        val request = RpcChatService.BanUserRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setUserId(userId.asUserId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.banUser(request)
        }
    }

    suspend fun unbanUser(
        owner: KeyPair,
        chatId: ChatId,
        userId: ID,
    ): RpcChatService.UnbanUserResponse {
        val request = RpcChatService.UnbanUserRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setUserId(userId.asUserId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.unbanUser(request)
        }
    }

    suspend fun getRestrictedUsers(
        owner: KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions = QueryOptions(),
    ): RpcChatService.GetRestrictedUsersResponse {
        val request = RpcChatService.GetRestrictedUsersRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setQueryOptions(queryOptions.asQueryOptions())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getRestrictedUsers(request)
        }
    }

    suspend fun pinMessage(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
    ): RpcChatService.PinMessageResponse {
        val request = RpcChatService.PinMessageRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setMessageId(messageId.asMessageId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.pinMessage(request)
        }
    }

    suspend fun unpinMessage(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
    ): RpcChatService.UnpinMessageResponse {
        val request = RpcChatService.UnpinMessageRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setMessageId(messageId.asMessageId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.unpinMessage(request)
        }
    }

    suspend fun getPinnedMessages(
        owner: KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions = QueryOptions(),
        viewMode: ViewMode = ViewMode.FULL,
    ): RpcChatService.GetPinnedMessagesResponse {
        val request = RpcChatService.GetPinnedMessagesRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setQueryOptions(queryOptions.asQueryOptions())
            .setViewMode(viewMode.asViewMode())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getPinnedMessages(request)
        }
    }

    suspend fun setKeyEnvelope(
        owner: KeyPair,
        chatId: ChatId,
        keyEnvelope: KeyEnvelope,
    ): RpcChatService.SetKeyEnvelopeResponse {
        val request = RpcChatService.SetKeyEnvelopeRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setKeyEnvelope(keyEnvelope.asProtoKeyEnvelope())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.setKeyEnvelope(request)
        }
    }

    suspend fun getKeyEnvelope(
        owner: KeyPair,
        chatId: ChatId,
    ): RpcChatService.GetKeyEnvelopeResponse {
        val request = RpcChatService.GetKeyEnvelopeRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getKeyEnvelope(request)
        }
    }

    /**
     * Idempotent on [token]. [maxUses] 0 means unlimited; [expiresAt] null means it never expires.
     * The envelopes are produced by invite crypto this layer does not do.
     */
    suspend fun createInvite(
        owner: KeyPair,
        chatId: ChatId,
        token: InviteToken,
        keyEnvelope: InviteKeyEnvelope,
        secretEnvelope: InviteSecretEnvelope,
        maxUses: Int = 0,
        expiresAt: Instant? = null,
    ): RpcChatService.CreateInviteResponse {
        val request = RpcChatService.CreateInviteRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setInviteToken(token.asProtoInviteToken())
            .setKeyEnvelope(keyEnvelope.asProtoInviteKeyEnvelope())
            .setSecretEnvelope(secretEnvelope.asProtoInviteSecretEnvelope())
            .setMaxUses(maxUses)
            .apply { expiresAt?.let { setExpiresAt(it.asTimestamp()) } }
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.createInvite(request)
        }
    }

    /** [owner] is optional: an anonymous viewer can look an invite up. */
    suspend fun getInvite(
        owner: KeyPair?,
        token: InviteToken,
    ): RpcChatService.GetInviteResponse {
        val request = RpcChatService.GetInviteRequest.newBuilder()
            .setInviteToken(token.asProtoInviteToken())
            .apply { owner?.let { setAuth(authenticate(it)) } }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getInvite(request)
        }
    }

    suspend fun getChatInvites(
        owner: KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions = QueryOptions(),
    ): RpcChatService.GetChatInvitesResponse {
        val request = RpcChatService.GetChatInvitesRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setQueryOptions(queryOptions.asQueryOptions())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getChatInvites(request)
        }
    }

    suspend fun revokeInvite(
        owner: KeyPair,
        chatId: ChatId,
        inviteId: InviteId,
    ): RpcChatService.RevokeInviteResponse {
        val request = RpcChatService.RevokeInviteRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setInviteId(inviteId.asProtoInviteId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.revokeInvite(request)
        }
    }
}
