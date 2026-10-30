package com.flipcash.services.repository

import com.flipcash.services.models.GetKeyEnvelopeError
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.SetKeyEnvelopeError
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatterSample
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.EditChatParameters
import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.models.chat.Invite
import com.flipcash.services.models.chat.InviteId
import com.flipcash.services.models.chat.InviteKeyEnvelope
import com.flipcash.services.models.chat.InviteParameters
import com.flipcash.services.models.chat.InvitePreview
import com.flipcash.services.models.chat.InviteSecretEnvelope
import com.flipcash.services.models.chat.InviteToken
import com.flipcash.services.models.chat.InvitesPage
import com.flipcash.services.models.chat.KeyEnvelope
import com.flipcash.services.models.chat.Lobby
import com.flipcash.services.models.chat.LobbyPage
import com.flipcash.services.models.chat.MuteState
import com.flipcash.services.models.chat.RestrictedUsersPage
import com.flipcash.services.models.chat.SilenceState
import com.flipcash.services.models.chat.MentionSuggestion
import com.flipcash.services.models.chat.RosterPage
import com.flipcash.services.models.chat.StartChatParameters
import com.flipcash.services.models.chat.StoredKeyEnvelope
import com.flipcash.services.models.chat.ViewerState
import com.flipcash.services.models.chat.ViewMode
import com.getcode.ed25519kmp.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

interface ChatRepository {
    /**
     * A sample of a public group's chatters. [owner] is optional: the sample is the same with or
     * without it, so a viewer who is not a member (preview) can call it. Fails with
     * `SampleChattersError.Denied` for a private group or DM.
     */
    suspend fun sampleChatters(
        owner: KeyPair?,
        chatId: ChatId,
    ): Result<ChatterSample>

    /**
     * Replaces the caller's featured groups with [chatIds], in the order to show them (at most
     * 10, all public groups, no repeats; empty clears). Returns the list as stored.
     */
    suspend fun setFeaturedGroups(
        owner: KeyPair,
        chatIds: List<ChatId>,
    ): Result<List<ChatMetadata>>

    /**
     * [username]'s featured groups, in their order. [owner] is optional. The returned metadata is
     * list-view shaped: no members, viewer state, last message or cover picture.
     */
    suspend fun getFeaturedGroups(
        owner: KeyPair?,
        username: String,
    ): Result<List<ChatMetadata>>

    suspend fun getChat(
        owner: KeyPair,
        chatId: ChatId,
        viewMode: ViewMode = ViewMode.FULL,
    ): Result<ChatMetadata>

    suspend fun getDmChatFeed(
        owner: KeyPair,
        queryOptions: QueryOptions,
        chatType: ChatType,
    ): Result<ChatFeedPage>

    /**
     * One page of the group feed. Unlike the DM feed there is no type filter: the server
     * decides what a group is, and `GetGroupChatFeedRequest` carries only query options.
     */
    suspend fun getGroupChatFeed(
        owner: KeyPair,
        queryOptions: QueryOptions,
    ): Result<ChatFeedPage>

    /**
     * Starts a new chat from [parameters]. [idempotencyKey] must be minted by the caller once
     * per logical attempt and reused across retries of that attempt — a retry with the same key
     * returns the originally created chat (result OK) even if [parameters] differ.
     */
    suspend fun startChat(
        owner: KeyPair,
        parameters: StartChatParameters,
        idempotencyKey: IdempotencyKey,
    ): Result<ChatMetadata>

    /**
     * One page of [chatId]'s roster, most recently joined first. Pass the previous page's
     * [RosterPage.pagingToken] in [queryOptions] to continue; omit it for the first page.
     *
     * See [RosterPage] for how to merge a page into a locally held roster — a page can lag
     * the roster's true state for a large group, unlike the exact list [getChat] returns.
     *
     * [owner] may be null when [inviteToken] is set: the token stands in for auth so a viewer
     * holding an invite link can read the roster before joining. Send the token on every page.
     * With neither, the call fails.
     */
    suspend fun getRoster(
        owner: KeyPair?,
        chatId: ChatId,
        queryOptions: QueryOptions = QueryOptions(),
        inviteToken: InviteToken? = null,
    ): Result<RosterPage>

    /**
     * The pool of members [chatId], a group, offers for `@` mentions: ranked by the server, most
     * relevant first, and neither paged nor complete. Never the caller, users the caller blocked,
     * or users without a username. Fails with [com.flipcash.services.models.GetMentionSuggestionsError.Denied]
     * for a DM or when the caller may not speak.
     */
    suspend fun getMentionSuggestions(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<List<MentionSuggestion>>

    /**
     * Edits [chatId], a group chat, applying only the fields set in [parameters]. Unset fields
     * are left unchanged; an all-null [parameters] is a valid no-op. Requires
     * `ViewerState.Permissions.canEdit` on the caller.
     */
    suspend fun editChat(
        owner: KeyPair,
        chatId: ChatId,
        parameters: EditChatParameters,
    ): Result<ChatMetadata>

    /**
     * Adds the caller to [chatId]'s roster, returning the chat as the caller now sees it. Pass
     * [invite] to join a private group with a link; failures then include the `JoinChatError.Invite*` cases.
     */
    suspend fun joinChat(
        owner: KeyPair,
        chatId: ChatId,
        invite: InviteParameters? = null,
    ): Result<ChatMetadata>

    /**
     * Removes the caller from [chatId]'s roster. Idempotent: a server `NOT_FOUND` is
     * reported as success, because it describes the state the call was asked to produce.
     */
    suspend fun leaveChat(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<Unit>

    /** Sets [chatId] muted for the caller until [mute] lapses (or forever), returning the new viewer state. */
    suspend fun muteChat(
        owner: KeyPair,
        chatId: ChatId,
        mute: MuteState,
    ): Result<ViewerState>

    /** Clears any mute on [chatId] for the caller, returning the new viewer state. */
    suspend fun unmuteChat(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<ViewerState>

    /**
     * Places the caller in private group [chatId]'s lobby, where the creator can admit or deny
     * them. Returns the chat as a non-member sees it, with `inLobby` set.
     */
    suspend fun enterLobby(owner: KeyPair, chatId: ChatId): Result<Lobby>

    /** Withdraws the caller from [chatId]'s lobby. */
    suspend fun leaveLobby(owner: KeyPair, chatId: ChatId): Result<Unit>

    /** One page of the users waiting in [chatId]'s lobby. Creator only. */
    suspend fun getLobbyMembers(
        owner: KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions = QueryOptions(),
    ): Result<LobbyPage>

    /**
     * Admits [userId] from [chatId]'s lobby, storing [keyEnvelope] (the chat key wrapped for that
     * user's public key) as part of the same call. The envelope is carried as opaque bytes.
     */
    suspend fun admitLobbyMember(
        owner: KeyPair,
        chatId: ChatId,
        userId: ID,
        keyEnvelope: KeyEnvelope,
    ): Result<Unit>

    /** Removes [userId] from [chatId]'s lobby without admitting them. Creator only. */
    suspend fun denyLobbyMember(owner: KeyPair, chatId: ChatId, userId: ID): Result<Unit>

    /**
     * Silences [userId] in [chatId] for the duration in [silence]. A silenced user stays a member
     * and can read but not send. Fails with `SilenceUserError.CannotRestrictCreator` for the
     * chat's creator.
     */
    suspend fun silenceUser(owner: KeyPair, chatId: ChatId, userId: ID, silence: SilenceState): Result<Unit>

    /** Lifts [userId]'s silence in [chatId]. */
    suspend fun unsilenceUser(owner: KeyPair, chatId: ChatId, userId: ID): Result<Unit>

    /**
     * Bans [userId] from [chatId]. Fails with `BanUserError.CannotRestrictCreator` for the chat's
     * creator.
     */
    suspend fun banUser(owner: KeyPair, chatId: ChatId, userId: ID): Result<Unit>

    /** Lifts [userId]'s ban from [chatId]. */
    suspend fun unbanUser(owner: KeyPair, chatId: ChatId, userId: ID): Result<Unit>

    /** One page of the users silenced or banned in [chatId]. Callers who can silence or ban only. */
    suspend fun getRestrictedUsers(
        owner: KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions = QueryOptions(),
    ): Result<RestrictedUsersPage>

    /**
     * Stores the caller's own key envelope for [chatId]. [SetKeyEnvelopeError.AlreadySet] means a
     * different envelope already stands; it is surfaced rather than recovered because whether to
     * adopt the stored one is the caller's decision.
     */
    suspend fun setKeyEnvelope(owner: KeyPair, chatId: ChatId, keyEnvelope: KeyEnvelope): Result<Unit>

    /**
     * The caller's own key envelope for [chatId], or [GetKeyEnvelopeError.NoEnvelope]. A member who
     * joined with an invite and has not stored an envelope yet fails with
     * [GetKeyEnvelopeError.InviteKeyBundleOnly], which carries the invite's copy; that copy is never
     * returned as an own envelope.
     */
    suspend fun getKeyEnvelope(owner: KeyPair, chatId: ChatId): Result<StoredKeyEnvelope>

    /**
     * Creates an invite link for private group [chatId]. Requires `ViewerState.Permissions.canInvite`.
     * Idempotent on [token]: a retry returns the invite the first attempt created. [maxUses] 0 means
     * unlimited; null [expiresAt] never expires. The envelopes come from invite crypto, which is not
     * implemented in this layer.
     */
    suspend fun createInvite(
        owner: KeyPair,
        chatId: ChatId,
        token: InviteToken,
        keyEnvelope: InviteKeyEnvelope,
        secretEnvelope: InviteSecretEnvelope,
        maxUses: Int = 0,
        expiresAt: Instant? = null,
    ): Result<Invite>

    /**
     * What [token]'s link leads to: the group's public metadata and the invite's status. [owner] is
     * optional; an anonymous viewer can look an invite up.
     */
    suspend fun getInvite(owner: KeyPair?, token: InviteToken): Result<InvitePreview>

    /** One page of [chatId]'s invites. Creator only (`canInvite`). */
    suspend fun getChatInvites(
        owner: KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions = QueryOptions(),
    ): Result<InvitesPage>

    /**
     * Revokes [inviteId] on [chatId]. Banning a user does not stop a holder of the link, so revoke
     * the invite as well.
     */
    suspend fun revokeInvite(owner: KeyPair, chatId: ChatId, inviteId: InviteId): Result<Unit>
}
