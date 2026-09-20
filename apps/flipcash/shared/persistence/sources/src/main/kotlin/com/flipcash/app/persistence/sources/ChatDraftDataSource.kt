package com.flipcash.app.persistence.sources

import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.ChatDraftEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One chat's stored draft, as it sits in the row.
 *
 * The reply target stays a JSON string on this side of the boundary: its shape belongs to the
 * chat layer that writes and reads it, and nothing here needs to look inside.
 */
data class ChatDraftRecord(
    val chatIdHex: String,
    val text: String,
    val replyTargetJson: String?,
    val savedAt: Long,
)

/**
 * The `chat_draft` table.
 *
 * No database means no drafts rather than an error, matching the other data sources here: the
 * instance is null only before a user has logged in and after one has logged out, and neither of
 * those has a composer on screen.
 */
@Singleton
class ChatDraftDataSource @Inject constructor() {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    suspend fun get(chatIdHex: String): ChatDraftRecord? =
        db?.chatDraftDao()?.getByChatId(chatIdHex)?.let {
            ChatDraftRecord(
                chatIdHex = it.chatIdHex,
                text = it.text,
                replyTargetJson = it.replyTargetJson,
                savedAt = it.savedAt,
            )
        }

    suspend fun upsert(record: ChatDraftRecord) {
        db?.chatDraftDao()?.upsert(
            ChatDraftEntity(
                chatIdHex = record.chatIdHex,
                text = record.text,
                replyTargetJson = record.replyTargetJson,
                savedAt = record.savedAt,
            )
        )
    }

    suspend fun delete(chatIdHex: String) {
        db?.chatDraftDao()?.deleteByChatId(chatIdHex)
    }

    suspend fun clear() {
        db?.chatDraftDao()?.deleteAll()
    }
}
