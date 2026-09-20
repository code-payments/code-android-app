package com.flipcash.shared.chat.internal

import com.flipcash.app.persistence.sources.ChatDraftDataSource
import com.flipcash.app.persistence.sources.ChatDraftRecord
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ChatDraft
import com.flipcash.shared.chat.ChatDraftReply
import com.flipcash.shared.chat.ChatDraftSnapshot
import com.flipcash.shared.chat.ChatDraftStore
import com.getcode.utils.TraceType
import com.getcode.utils.hexEncodedString
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ChatDraftStore] over the `chat_draft` table.
 *
 * The reply target is serialized here rather than in the data source because its shape is this
 * module's: the row stores a blob, and what the blob means is the composer's business.
 */
@Singleton
class RealChatDraftStore internal constructor(
    private val dataSource: ChatDraftDataSource,
    dispatchers: DispatcherProvider,
    private val now: () -> Long,
) : ChatDraftStore {

    @Inject
    constructor(
        dataSource: ChatDraftDataSource,
        dispatchers: DispatcherProvider,
    ) : this(dataSource, dispatchers, System::currentTimeMillis)

    // Deliberately not a caller's scope: [saveInBackground]'s two callers are a ViewModel scope
    // that has just been cancelled and a process about to be backgrounded, so a write parented to
    // either would be cancelled at exactly the moment it matters.
    private val scope = CoroutineScope(dispatchers.IO + SupervisorJob())

    override suspend fun load(chatId: ChatId): ChatDraft? {
        val record = dataSource.get(chatId.hex) ?: return null
        return ChatDraft(
            text = record.text,
            replyTarget = record.replyTargetJson?.let { decodeReply(it) },
            savedAt = record.savedAt,
        )
    }

    override suspend fun save(chatId: ChatId, snapshot: ChatDraftSnapshot) {
        if (snapshot.isEmpty) {
            dataSource.delete(chatId.hex)
            return
        }
        dataSource.upsert(
            ChatDraftRecord(
                chatIdHex = chatId.hex,
                text = snapshot.text,
                replyTargetJson = snapshot.replyTarget?.let { json.encodeToString(it) },
                savedAt = now(),
            )
        )
    }

    override fun saveInBackground(chatId: ChatId, snapshot: ChatDraftSnapshot) {
        scope.launch { save(chatId, snapshot) }
    }

    override suspend fun clear(chatId: ChatId) {
        dataSource.delete(chatId.hex)
    }

    override suspend fun clearAll() {
        dataSource.clear()
    }

    /**
     * A reply target that no longer parses costs the strip and not the words: the text is the part
     * that exists nowhere else, so a blob written by an older shape of [ChatDraftReply] degrades to
     * a plain draft rather than taking the draft down with it.
     */
    private fun decodeReply(blob: String): ChatDraftReply? =
        runCatching { json.decodeFromString<ChatDraftReply>(blob) }
            .onFailure {
                trace(
                    tag = TAG,
                    message = "dropping unreadable reply target",
                    error = it,
                    type = TraceType.Error,
                )
            }
            .getOrNull()

    // Matches ChatEntityMapper.chatIdHex, so a draft is keyed the same way as the chat's messages.
    private val ChatId.hex: String
        get() = bytes.toList().hexEncodedString()

    companion object {
        private const val TAG = "ChatDraftStore"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
