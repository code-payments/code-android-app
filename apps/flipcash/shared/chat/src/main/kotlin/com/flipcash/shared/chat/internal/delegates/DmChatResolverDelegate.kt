package com.flipcash.shared.chat.internal.delegates

import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ContactDataSource
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.DmChatResolver
import com.flipcash.shared.chat.FailedToGenerateChatIdException
import com.flipcash.shared.chat.NoDmChatInitializedException
import com.flipcash.shared.chat.internal.ChatIdGenerator
import com.getcode.opencode.model.core.ID
import com.getcode.utils.decodeBase58
import com.getcode.utils.hexEncodedString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the [ChatId] of a DM from its participants — either by deterministic
 * derivation ([generateChatId]) or by looking up an already-initialized chat in
 * local persistence ([getChatId]).
 *
 * This is pure identity resolution: it does not send, receive, or mutate any
 * conversation. Operating on a resolved [ChatId] is
 * [MessagingDelegate][com.flipcash.shared.chat.internal.delegates.MessagingDelegate]'s job.
 *
 * @see com.flipcash.shared.chat.internal.RealChatCoordinator
 */
@Singleton
class DmChatResolverDelegate @Inject constructor(
    private val chatIdGenerator: ChatIdGenerator,
    private val userManager: UserManager,
    private val contactDataSource: ContactDataSource,
    private val memberDataSource: ChatMemberDataSource,
) : DmChatResolver {

    override suspend fun generateChatId(userId: ID): Result<ChatId> {
        val self = userManager.accountId ?: return Result.failure(FailedToGenerateChatIdException(null))
        return Result.success(chatIdGenerator.generate(ChatType.TIP_DM, self, userId))
    }

    override suspend fun getChatId(contact: DeviceContact): Result<ChatId> {
        val raw = contactDataSource.getDmChatId(contact.e164)
        if (raw.isNullOrEmpty()) {
            return Result.failure(NoDmChatInitializedException(contact.e164))
        }
        return runCatching { ChatId(raw.decodeBase58()) }
    }

    override suspend fun getChatId(userId: ID): Result<ChatId> {
        val chatId = memberDataSource.getChatIdForUser(userId, ChatType.TIP_DM)
            ?: return Result.failure(NoDmChatInitializedException(userId.hexEncodedString()))
        return Result.success(chatId)
    }
}
