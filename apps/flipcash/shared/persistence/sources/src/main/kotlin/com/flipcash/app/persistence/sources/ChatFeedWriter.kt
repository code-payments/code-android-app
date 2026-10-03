package com.flipcash.app.persistence.sources

import androidx.room.withTransaction
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.services.models.chat.ChatMetadata
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lands a batch of feed chats — rows, members and preview messages, DM and group alike — in one
 * Room transaction.
 *
 * Room tells observers about a transaction once, when it commits. Written as separate calls, the
 * chat list saw the rows reorder first, then each chat's members, then the previews, and rebuilt
 * for every one of them.
 */
@Singleton
class ChatFeedWriter @Inject constructor(
    private val metadataDataSource: ChatMetadataDataSource,
    private val memberDataSource: ChatMemberDataSource,
    private val messageDataSource: ChatMessageDataSource,
) {
    suspend fun write(chats: List<ChatMetadata>) {
        if (chats.isEmpty()) return
        // Opened before the transaction: opening can fetch a key over the network, and a
        // transaction holds the database's one writer.
        val prepared = messageDataSource.prepare(chats.lastMessagesByChat())
        val body: suspend () -> Unit = {
            metadataDataSource.upsert(chats)
            for (chat in chats) memberDataSource.upsert(chat.chatId, chat.members)
            messageDataSource.writePrepared(prepared)
        }
        val database = FlipcashDatabase.getInstance()
        if (database != null) database.withTransaction { body() } else body()
        messageDataSource.afterWrite(prepared)
    }
}
