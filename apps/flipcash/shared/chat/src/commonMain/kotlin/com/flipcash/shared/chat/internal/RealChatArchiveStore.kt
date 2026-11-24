package com.flipcash.shared.chat.internal

import com.flipcash.app.persistence.entities.ChatArchiveEntity
import com.flipcash.app.persistence.sources.ChatArchiveDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource.ViewerStateWrite
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.ArchiveChatError
import com.flipcash.services.models.UnarchiveChatError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.UnarchivedChat
import com.flipcash.services.models.chat.ViewerState
import com.flipcash.shared.chat.ChatArchiveStore
import com.getcode.utils.TraceType
import com.getcode.utils.hexEncodedString
import com.getcode.utils.trace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ChatArchiveStore] over the server's archive (`chat_metadata`) and the queue of writes it has
 * not confirmed (`chat_archive`).
 *
 * Every write lands in the queue first, so it shows at once and survives a failed send. The
 * queue drains on [flush], which the feed reconcile runs on every launch, reconnect and
 * foreground. Each write's answer decides what happens to it:
 * - OK applies the returned viewer state through the version gate, then retires the write.
 * - DENIED or NOT_FOUND retires it: the server will never take it.
 * - UNIMPLEMENTED, or no answer at all, keeps it and ends the pass: the rest would fail the same
 *   way. The write keeps showing until a later pass gets an answer.
 */
@Singleton
class RealChatArchiveStore internal constructor(
    private val dataSource: ChatArchiveDataSource,
    private val metadata: ChatMetadataDataSource,
    private val chatController: ChatController,
    private val marker: ArchiveUploadMarker,
    private val now: () -> Long,
) : ChatArchiveStore {

    @Inject
    internal constructor(
        dataSource: ChatArchiveDataSource,
        metadata: ChatMetadataDataSource,
        chatController: ChatController,
        marker: DataStoreArchiveUploadMarker,
    ) : this(dataSource, metadata, chatController, marker, System::currentTimeMillis)

    // One send at a time, so a tap and a flush never send the same write twice.
    private val sending = Mutex()

    private val _unarchivedGroups = MutableSharedFlow<ChatId>(extraBufferCapacity = 16)

    override fun observeArchived(): Flow<Set<ChatId>> =
        dataSource.observeIds().map { hexes -> hexes.mapTo(HashSet()) { ChatId(it) } }

    override suspend fun isArchived(chatId: ChatId): Boolean = dataSource.isArchived(chatId.hex)

    override suspend fun archive(chatId: ChatId) = write(chatId, archived = true)

    override suspend fun unarchive(chatId: ChatId) = write(chatId, archived = false)

    override suspend fun forget(chatId: ChatId) = dataSource.dropPending(chatId.hex)

    override fun unarchivedGroups(): Flow<ChatId> = _unarchivedGroups.asSharedFlow()

    override suspend fun clearAll() = dataSource.clear()

    private suspend fun write(chatId: ChatId, archived: Boolean) {
        val hex = chatId.hex
        if (dataSource.isArchived(hex) == archived) return
        dataSource.putPending(hex, archived, now())
        sending.withLock {
            // Read back rather than rebuilt, so the write sent is the one stored: resolving it
            // checks the stored row still matches.
            val pending = dataSource.pending().firstOrNull { it.chatIdHex == hex } ?: return
            send(pending)
        }
    }

    override suspend fun flush() = sending.withLock {
        // Until the first pass that empties the queue, it holds the archive this device kept
        // before the server stored one. A record the server already shows as archived needs no
        // call; two devices uploading at once give the union.
        val uploading = !marker.isSet()
        for (pending in dataSource.pending()) {
            if (uploading && pending.archived && metadata.archivedAt(ChatId(pending.chatIdHex)) != null) {
                dataSource.resolve(pending)
                continue
            }
            if (send(pending) == Sent.Stopped) return@withLock
        }
        if (dataSource.pending().isEmpty()) marker.set()
    }

    private enum class Sent { Resolved, Kept, Stopped }

    private suspend fun send(pending: ChatArchiveEntity): Sent {
        val chatId = ChatId(pending.chatIdHex)
        val result = if (pending.archived) {
            chatController.archiveChat(chatId).map { applyArchived(chatId, it) }
        } else {
            chatController.unarchiveChat(chatId).map { applyUnarchived(chatId, it) }
        }
        val error = result.exceptionOrNull()
        val sent = when (error) {
            null -> Sent.Resolved
            is ArchiveChatError.Denied, is ArchiveChatError.NotFound,
            is UnarchiveChatError.Denied, is UnarchiveChatError.NotFound -> Sent.Resolved
            // A result code this build does not know names this write, not the connection.
            is ArchiveChatError.Unrecognized, is UnarchiveChatError.Unrecognized -> Sent.Kept
            // UNIMPLEMENTED, a transport failure, or no signed-in account: every other write
            // would fail the same way.
            else -> Sent.Stopped
        }
        if (sent == Sent.Resolved) dataSource.resolve(pending)
        if (error != null) {
            trace(
                tag = TAG,
                message = "Archive write on ${pending.chatIdHex} (archived=${pending.archived}): $sent, ${error::class.simpleName}",
                type = TraceType.Process,
            )
        }
        return sent
    }

    private suspend fun applyArchived(chatId: ChatId, viewerState: ViewerState) {
        metadata.updateViewerState(chatId, viewerState)
    }

    private suspend fun applyUnarchived(chatId: ChatId, unarchived: UnarchivedChat) {
        // The viewer state first, so the transition is read off the write that lands. Upserting
        // the chat first would apply the same version and leave this nothing to change.
        val write = metadata.updateViewerState(chatId, unarchived.viewerState)
        unarchived.chat?.let { metadata.upsert(it) }
        if (write == ViewerStateWrite.Unarchived && metadata.getChatType(chatId) == ChatType.GROUP) {
            _unarchivedGroups.tryEmit(chatId)
        }
    }

    // Matches ChatEntityMapper.chatIdHex, so the record is keyed the same way as the chat's rows.
    private val ChatId.hex: String
        get() = bytes.toList().hexEncodedString()

    private companion object {
        const val TAG = "ChatArchiveStore"
    }
}
