package com.flipcash.shared.chat.internal

import com.flipcash.app.persistence.sources.ChatMarkedUnreadDataSource
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ChatMarkedUnreadStore
import com.getcode.utils.hexEncodedString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** [ChatMarkedUnreadStore] over the `chat_marked_unread` table. */
@Singleton
class RealChatMarkedUnreadStore internal constructor(
    private val dataSource: ChatMarkedUnreadDataSource,
    private val now: () -> Long,
) : ChatMarkedUnreadStore {

    @Inject
    constructor(dataSource: ChatMarkedUnreadDataSource) : this(dataSource, System::currentTimeMillis)

    override fun observeMarked(): Flow<Set<ChatId>> =
        dataSource.observeIds().map { hexes -> hexes.mapTo(HashSet()) { ChatId(it) } }

    override suspend fun mark(chatId: ChatId) = dataSource.mark(chatId.hex, now())

    override suspend fun clear(chatId: ChatId) = dataSource.clear(chatId.hex)

    override suspend fun clear(chatIds: Collection<ChatId>) = dataSource.clear(chatIds.map { it.hex })

    override suspend fun clearAll() = dataSource.clearAll()

    // Matches ChatEntityMapper.chatIdHex, as RealChatArchiveStore does.
    private val ChatId.hex: String
        get() = bytes.toList().hexEncodedString()
}
