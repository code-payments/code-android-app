package com.flipcash.shared.chat.internal

import com.flipcash.app.persistence.sources.ChatArchiveDataSource
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ChatArchiveStore
import com.getcode.utils.hexEncodedString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** [ChatArchiveStore] over the `chat_archive` table. */
@Singleton
class RealChatArchiveStore internal constructor(
    private val dataSource: ChatArchiveDataSource,
    private val now: () -> Long,
) : ChatArchiveStore {

    @Inject
    constructor(dataSource: ChatArchiveDataSource) : this(dataSource, System::currentTimeMillis)

    override fun observeArchived(): Flow<Set<ChatId>> =
        dataSource.observeIds().map { hexes -> hexes.mapTo(HashSet()) { ChatId(it) } }

    override suspend fun isArchived(chatId: ChatId): Boolean = dataSource.isArchived(chatId.hex)

    override suspend fun archive(chatId: ChatId) = dataSource.archive(chatId.hex, now())

    override suspend fun unarchive(chatId: ChatId) = dataSource.unarchive(chatId.hex)

    override suspend fun clearAll() = dataSource.clear()

    // Matches ChatEntityMapper.chatIdHex, so the record is keyed the same way as the chat's rows.
    private val ChatId.hex: String
        get() = bytes.toList().hexEncodedString()
}
