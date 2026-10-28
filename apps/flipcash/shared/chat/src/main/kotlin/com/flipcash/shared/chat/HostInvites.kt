package com.flipcash.shared.chat

import com.flipcash.services.chat.ChatKeySource
import com.flipcash.services.chat.GroupKeySource
import com.flipcash.services.chat.toEnvelope
import com.flipcash.services.chat.toInviteSecretEnvelope
import com.flipcash.services.chat.toPayload
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.CreateInviteError
import com.flipcash.services.models.GetChatInvitesError
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.RevokeInviteError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.Invite
import com.flipcash.services.models.chat.InviteId
import com.flipcash.services.models.chat.InviteToken
import com.flipcash.services.models.chat.ViewMode
import com.getcode.chatcipher.InviteCipher
import com.getcode.chatcipher.InviteLink
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** New Invite Link's Expires presets (node 12844:334). [Never] sends no `expires_at`. */
enum class InviteExpiry(val duration: Duration?) {
    OneDay(1.days),
    SevenDays(7.days),
    ThirtyDays(30.days),
    Never(null),
    ;

    /** The next shorter preset, for a server that refuses this one's `expires_at`. */
    val shorter: InviteExpiry?
        get() = when (this) {
            Never -> ThirtyDays
            ThirtyDays -> SevenDays
            SevenDays -> OneDay
            OneDay -> null
        }
}

/** New Invite Link's Who can join presets (node 12844:334). 0 is no limit. */
enum class InviteUses(val maxUses: Int) {
    NoLimit(0),
    One(1),
    Ten(10),
    Fifty(50),
}

data class InviteSettings(val expiry: InviteExpiry, val uses: InviteUses) {
    companion object {
        /** The current link's settings when the sheet makes one: 7 days, no use limit. */
        val Default = InviteSettings(InviteExpiry.SevenDays, InviteUses.NoLimit)
    }
}

/**
 * One of the host's invites, with its link when this device can rebuild it. [url] is null when
 * the invite's `secret_envelope` is missing or won't open here: the host can still revoke it, but
 * can't share it. The link is as good as the chat key, so [toString] never prints it.
 */
class HostInvite(val invite: Invite, val url: String?) {
    val canShare: Boolean get() = url != null
    val isActive: Boolean get() = invite.status == Invite.Status.ACTIVE

    override fun toString(): String = "HostInvite(status=${invite.status}, canShare=$canShare)"
}

/** How a create attempt ended. */
sealed interface InviteCreation {
    class Created(val invite: HostInvite) : InviteCreation

    /** `TOO_MANY_INVITES`: the group holds the most ACTIVE links the server allows. */
    data object TooMany : InviteCreation

    /**
     * `DENIED`. While `CreateInvite` is staff-only, a host with `can_invite` gets this too
     * (spec Decision 10), so it is shown as invite links not being available yet.
     */
    data object NotAvailable : InviteCreation

    /** Anything else. A transport failure keeps the attempt's secret for the retry. */
    class Failed(val cause: Throwable) : InviteCreation
}

/**
 * The invite links of the private groups this account hosts: listing them with their rebuilt
 * links, making a new one, and revoking. One copy for the invite sheet and Manage Invites, so the
 * link the sheet shares is the one Manage Invites marks Current.
 *
 * Nothing here logs a link, its secret or its token.
 */
@Singleton
class HostInvites internal constructor(
    private val chatController: ChatController,
    private val groupKeys: GroupKeySource,
    private val keys: ChatKeySource,
    private val cipher: InviteCipher,
    private val clock: Clock,
) {
    @Inject
    constructor(
        chatController: ChatController,
        groupKeys: GroupKeySource,
        keys: ChatKeySource,
        cipher: InviteCipher,
    ) : this(chatController, groupKeys, keys, cipher, Clock.System)

    private val invites = MutableStateFlow<Map<ChatId, List<HostInvite>>>(emptyMap())
    private val access = MutableStateFlow<Map<ChatId, Boolean>>(emptyMap())

    /**
     * Each checked chat's `can_invite`, as `GetChat` last returned it. Kept here because the local
     * store does not carry the permission yet, so a chat read from disk always says false.
     */
    val canInvite: StateFlow<Map<ChatId, Boolean>> = access.asStateFlow()

    /** Each loaded chat's invites, newest first. A chat never loaded is absent. */
    val state: StateFlow<Map<ChatId, List<HostInvite>>> = invites.asStateFlow()

    // One secret per create attempt, kept until the server answers for certain, so a retry after a
    // dropped response sends the same token and the server hands back the first invite rather
    // than making a second.
    private val pending = mutableMapOf<Pair<ChatId, InviteSettings>, ByteArray>()
    private val createLock = Mutex()

    /** Reads [chatId]'s `can_invite` from the server into [canInvite]. A failure leaves it as it was. */
    suspend fun refreshAccess(chatId: ChatId): Result<Boolean> =
        chatController.getChat(chatId, ViewMode.FULL_OR_REDACTED)
            .map { it.viewerState?.permissions?.canInvite == true }
            .onSuccess { allowed -> access.update { it + (chatId to allowed) } }

    /** Records that the viewer can no longer invite to [chatId], after a `DENIED`. */
    fun revokeAccess(chatId: ChatId) {
        access.update { it + (chatId to false) }
    }

    /** Fetches every page of [chatId]'s invites and replaces what was held for it. */
    suspend fun load(chatId: ChatId): Result<List<HostInvite>> = runCatching {
        val all = mutableListOf<Invite>()
        var options = QueryOptions()
        do {
            val page = chatController.getChatInvites(chatId, options).getOrThrow()
            all += page.invites
            options = options.copy(token = page.pagingToken)
        } while (page.hasMore && page.pagingToken != null)
        all.distinctBy { it.id }
            .sortedByDescending { it.createdAt }
            .map { HostInvite(it, linkFor(it)) }
    }.onSuccess { list ->
        invites.update { it + (chatId to list) }
    }

    /**
     * The link the sheet shares: the newest ACTIVE invite the viewer created whose link this
     * device can rebuild. Null when there is none, and the sheet makes one on first Share or Copy.
     */
    fun current(chatId: ChatId): HostInvite? = currentOf(invites.value[chatId].orEmpty(), keys.ownUserId())

    /**
     * Makes an invite with [settings]. If the server answers OK with an invite that is no longer
     * ACTIVE, which only a reused token can cause, the secret is dropped and one new attempt made.
     * A refused `expires_at` is retried once at the next shorter preset.
     */
    suspend fun create(chatId: ChatId, settings: InviteSettings): InviteCreation =
        createLock.withLock { createLocked(chatId, settings) }

    private suspend fun createLocked(chatId: ChatId, settings: InviteSettings): InviteCreation {
        var attempt = settings
        var clamped = false
        var renewed = false
        while (true) {
            when (val result = attemptCreate(chatId, attempt)) {
                is AttemptResult.Created -> {
                    val created = HostInvite(result.invite, result.url)
                    invites.update { all ->
                        val others = all[chatId].orEmpty().filterNot { it.invite.id == created.invite.id }
                        all + (chatId to listOf(created) + others)
                    }
                    return InviteCreation.Created(created)
                }
                AttemptResult.NotActive -> {
                    if (renewed) return InviteCreation.Failed(IllegalStateException("Created invite is not active"))
                    renewed = true
                }
                AttemptResult.ExpiryRefused -> {
                    val shorter = attempt.expiry.shorter
                    if (clamped || shorter == null) {
                        return InviteCreation.Failed(CreateInviteError.InvalidExpiresAt())
                    }
                    clamped = true
                    attempt = attempt.copy(expiry = shorter)
                }
                is AttemptResult.Done -> return result.creation
            }
        }
    }

    /** Revokes [inviteId]. Revoking an invite already revoked is OK, so a retry is safe. */
    suspend fun revoke(chatId: ChatId, inviteId: InviteId): Result<Unit> =
        chatController.revokeInvite(chatId, inviteId).onSuccess {
            invites.update { all ->
                val list = all[chatId] ?: return@update all
                all + (chatId to list.map { item ->
                    if (item.invite.id == inviteId) {
                        HostInvite(item.invite.copy(status = Invite.Status.REVOKED), item.url)
                    } else {
                        item
                    }
                })
            }
        }

    /** Drops everything held. For logout: the invites belong to the account. */
    fun clear() {
        invites.value = emptyMap()
        access.value = emptyMap()
        pending.clear()
    }

    private sealed interface AttemptResult {
        class Created(val invite: Invite, val url: String) : AttemptResult
        data object NotActive : AttemptResult
        data object ExpiryRefused : AttemptResult
        class Done(val creation: InviteCreation) : AttemptResult
    }

    private suspend fun attemptCreate(chatId: ChatId, settings: InviteSettings): AttemptResult {
        val own = keys.ownKeyPair()
            ?: return AttemptResult.Done(InviteCreation.Failed(IllegalStateException("No account key pair")))
        val chatKey = groupKeys.key(chatId).getOrElse { return AttemptResult.Done(InviteCreation.Failed(it)) }

        val slot = chatId to settings
        val secret = pending.getOrPut(slot) { cipher.newSecret() }
        val sealed = runCatching {
            val inviteId = cipher.inviteId(secret)
            Triple(
                InviteToken(cipher.inviteToken(secret)),
                cipher.sealInviteKeyEnvelope(own, secret, chatId.bytes, chatKey).toEnvelope(),
                cipher.sealInviteSecretEnvelope(own, secret, chatId.bytes, inviteId).toInviteSecretEnvelope(),
            )
        }.getOrElse {
            pending.remove(slot)
            return AttemptResult.Done(InviteCreation.Failed(it))
        }
        val (token, keyEnvelope, secretEnvelope) = sealed

        val result = chatController.createInvite(
            chatId = chatId,
            token = token,
            keyEnvelope = keyEnvelope,
            secretEnvelope = secretEnvelope,
            maxUses = settings.uses.maxUses,
            expiresAt = settings.expiry.duration?.let { clock.now() + it },
        )
        val invite = result.getOrElse { error ->
            if (error.isAnswered()) pending.remove(slot)
            return when (error) {
                is CreateInviteError.TooManyInvites -> AttemptResult.Done(InviteCreation.TooMany)
                is CreateInviteError.Denied -> AttemptResult.Done(InviteCreation.NotAvailable)
                is CreateInviteError.InvalidExpiresAt -> AttemptResult.ExpiryRefused
                else -> AttemptResult.Done(InviteCreation.Failed(error))
            }
        }
        pending.remove(slot)
        if (invite.status != Invite.Status.ACTIVE) return AttemptResult.NotActive
        return AttemptResult.Created(invite, InviteLink.url(secret))
    }

    private fun linkFor(invite: Invite): String? {
        val own = keys.ownKeyPair() ?: return null
        val payload = invite.secretEnvelope?.toPayload() ?: return null
        return runCatching {
            val secret = cipher.openInviteSecretEnvelope(own, invite.chatId.bytes, invite.id.bytes, payload)
            // A secret for another invite would share a link that leads somewhere else.
            check(cipher.inviteId(secret).contentEquals(invite.id.bytes))
            InviteLink.url(secret)
        }.getOrNull()
    }

    companion object {
        /** How long Manage Invites keeps showing a link after it stops letting people in (spec Open 9). */
        val InactiveShownFor: Duration = 30.days

        internal fun currentOf(list: List<HostInvite>, viewer: ID?): HostInvite? =
            list.firstOrNull { it.isActive && it.canShare && it.invite.createdBy == viewer }

        /**
         * Manage Invites' sections: ACTIVE links, then the rest, each newest first. An inactive link
         * is hidden once [InactiveShownFor] has passed since it ended, which for an expired link is
         * its expiry and otherwise, since the contract records no revoke or exhaust time, its
         * creation.
         */
        fun sections(list: List<HostInvite>, now: Instant): Pair<List<HostInvite>, List<HostInvite>> {
            val (active, inactive) = list.partition { it.isActive }
            val shown = inactive.filter { item ->
                val ended = when (item.invite.status) {
                    Invite.Status.EXPIRED -> item.invite.expiresAt ?: item.invite.createdAt
                    else -> item.invite.createdAt
                }
                now - ended <= InactiveShownFor
            }
            return active to shown
        }

        /** Whether `GetChatInvites` or `RevokeInvite` failed because the viewer can no longer invite. */
        fun Throwable.isLostInviteAccess(): Boolean =
            this is GetChatInvitesError.Denied || this is RevokeInviteError.Denied

        // The server answered, so the token is spent either way and a retry needs a new secret.
        private fun Throwable.isAnswered(): Boolean = when (this) {
            is CreateInviteError.Denied,
            is CreateInviteError.NotFound,
            is CreateInviteError.InvalidExpiresAt,
            is CreateInviteError.TooManyInvites,
            is CreateInviteError.InvalidSignature -> true
            else -> false
        }
    }
}
