package com.flipcash.shared.chat.internal.delegates

import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.PendingMediaDataSource
import com.flipcash.app.persistence.sources.PendingMediaRecord
import com.flipcash.app.persistence.sources.mapper.chat.ChatEntityMapper
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.models.SendMessageError
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ClientMessageId
import com.flipcash.services.models.chat.ImageMetadata
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.OutgoingEncryption
import com.flipcash.shared.chat.media.ChatMediaSending
import com.flipcash.shared.chat.media.ChatMediaSendPlan
import com.flipcash.shared.chat.media.ChatMediaUploadState
import com.flipcash.shared.chat.media.ChatMediaUploads
import com.flipcash.shared.chat.media.MediaSendProgress
import com.flipcash.shared.chat.media.UploadedPhoto
import com.getcode.opencode.model.core.RandomId
import com.getcode.utils.TraceType
import com.getcode.utils.hexEncodedString
import com.getcode.utils.trace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.flipcash.shared.chat.media.ChatPhoto
import com.flipcash.shared.chat.media.SentPhotoPreviews
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * Sends photo messages: a pending row per photo up front, then a post per row, in chip order, as
 * each upload settles. See [ChatMediaSending].
 *
 * What is queued is in `pending_media`, so a photo survives the process. The photo's own file and
 * state are [ChatMediaUploads]'; this decides what happens to the message around them.
 */
@Singleton
class MediaSendDelegate internal constructor(
    private val messaging: MessagingDelegate,
    private val messagingController: ChatMessagingController,
    private val messageDataSource: ChatMessageDataSource,
    private val metadataDataSource: ChatMetadataDataSource,
    private val pendingMedia: PendingMediaDataSource,
    private val uploads: ChatMediaUploads,
    private val outgoing: OutgoingEncryption,
    private val userManager: UserManager,
    private val scope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ChatMediaSending {

    @Inject
    internal constructor(
        messaging: MessagingDelegate,
        messagingController: ChatMessagingController,
        messageDataSource: ChatMessageDataSource,
        metadataDataSource: ChatMetadataDataSource,
        pendingMedia: PendingMediaDataSource,
        uploads: ChatMediaUploads,
        outgoing: OutgoingEncryption,
        userManager: UserManager,
        dispatchers: DispatcherProvider,
    ) : this(
        messaging, messagingController, messageDataSource, metadataDataSource, pendingMedia, uploads,
        outgoing, userManager, CoroutineScope(SupervisorJob() + dispatchers.IO),
    )

    private val mapper = ChatEntityMapper()

    /** What the post of a row did past the upload, which the upload's own state doesn't say. */
    private sealed interface Stage {
        data object Sending : Stage
        data object Sent : Stage
        data class Failed(val retryable: Boolean) : Stage
    }

    private val stages = MutableStateFlow<Map<String, Stage>>(emptyMap())

    /** Client id hex to chip id, for every row this process is tracking. */
    private val chips = MutableStateFlow<Map<String, String>>(emptyMap())

    private val inFlight = HashSet<String>()

    override suspend fun sendMedia(
        chatId: ChatId,
        chipIds: List<String>,
        text: String,
        replyToMessageId: Long?,
    ): Result<List<String>> {
        val plan = ChatMediaSendPlan.build(chipIds, text, replyToMessageId)
        if (plan.isEmpty()) return Result.success(emptyList())
        if (chipIds.isEmpty()) {
            val message = plan.single() as ChatMediaSendPlan.Message.Text
            return messaging.sendMessage(chatId, message.text, message.replyTo).map { emptyList() }
        }
        if (chipIds.distinct().size != chipIds.size || chipIds.any { !uploads.has(it) }) {
            return Result.failure(IllegalArgumentException("Unknown or repeated photo"))
        }
        val senderId = userManager.accountId
            ?: return Result.failure(IllegalStateException("Cannot send a photo without an account"))

        val createdAt = now()
        val queued = plan.filterIsInstance<ChatMediaSendPlan.Message.Media>().map { message ->
            val (width, height) = uploads.pixelSize(message.chip) ?: (1 to 1)
            Queued(
                clientMessageId = ClientMessageId(RandomId.toByteArray()),
                message = message,
                width = width,
                height = height,
            )
        }

        // The entries first: a refresh that lands between the two writes would otherwise delete a
        // row nothing yet says is a photo in flight.
        pendingMedia.insert(
            queued.map { q ->
                PendingMediaRecord(
                    clientIdHex = hex(q.clientMessageId),
                    chatIdHex = mapper.chatIdHex(chatId),
                    fileName = fileName(q.message.chip),
                    caption = q.message.caption,
                    replyToMessageId = q.message.replyTo,
                    storedBlobIdHex = null,
                    sealedForHex = null,
                    width = q.width,
                    height = q.height,
                    blurhash = null,
                    sizeBytes = null,
                    createdAt = createdAt,
                )
            },
        )
        queued.forEachIndexed { index, q ->
            val local = localContent(q.message.caption, q.message.replyTo, q.width, q.height, fileUri(q.message.chip))
            SentPhotoPreviews.handOff(q.message.chip, hex(q.clientMessageId))
            messageDataSource.insertPending(
                chatId = chatId,
                content = local,
                senderId = senderId,
                clientMessageId = q.clientMessageId,
                ordinal = index,
            )
        }
        chips.update { it + queued.associate { q -> hex(q.clientMessageId) to q.message.chip } }
        val hexes = queued.map { hex(it.clientMessageId) }
        synchronized(inFlight) { inFlight.addAll(hexes) }

        scope.launch {
            for (q in queued) {
                val record = pendingMedia.get(hex(q.clientMessageId)) ?: continue
                post(chatId, record)
            }
        }
        return Result.success(hexes)
    }

    override suspend fun retryMedia(chatId: ChatId, pendingClientIdHex: String): Result<Unit> {
        val record = pendingMedia.get(pendingClientIdHex)
            ?: return Result.failure(IllegalStateException("Nothing left to retry for $pendingClientIdHex"))
        val chip = chipId(record)
        if (!uploads.has(chip)) track(record)
        if (!synchronized(inFlight) { inFlight.add(pendingClientIdHex) }) return Result.success(Unit)

        messageDataSource.retryPending(chatId, pendingClientIdHex)
        stages.update { it - pendingClientIdHex }
        chips.update { it + (pendingClientIdHex to chip) }
        if (uploads.current(chip) is ChatMediaUploadState.Failed) uploads.retry(chip)
        scope.launch { post(chatId, record) }
        return Result.success(Unit)
    }

    override fun observeMediaSendProgress(): Flow<Map<String, MediaSendProgress>> =
        combine(chips, stages, uploads.allStates) { chips, stages, states ->
            chips.mapNotNull { (hex, chip) ->
                val progress = when (val stage = stages[hex]) {
                    Stage.Sending -> MediaSendProgress.Sending
                    Stage.Sent -> MediaSendProgress.Sent
                    is Stage.Failed -> MediaSendProgress.Failed(stage.retryable)
                    null -> when (val state = states[chip]) {
                        is ChatMediaUploadState.Preparing -> MediaSendProgress.Preparing
                        is ChatMediaUploadState.Uploading -> MediaSendProgress.Uploading(state.fraction)
                        is ChatMediaUploadState.Processing -> MediaSendProgress.Processing
                        is ChatMediaUploadState.Uploaded -> MediaSendProgress.Sending
                        is ChatMediaUploadState.Failed -> MediaSendProgress.Failed(state.retryable)
                        null -> return@mapNotNull null
                    }
                }
                hex to progress
            }.toMap()
        }.distinctUntilChanged()

    override suspend fun reconcilePendingMedia() {
        val selfId = userManager.accountId ?: return
        val records = pendingMedia.getAll().sortedBy { it.createdAt }
        if (records.isEmpty()) return

        val recentByChat = HashMap<String, List<ChatMessage>>()
        val resume = ArrayList<PendingMediaRecord>()
        for (record in records) {
            val chatId = mapper.chatIdFromHex(record.chatIdHex)
            val stored = record.storedBlobIdHex
            if (stored != null) {
                val recent = recentByChat.getOrPut(record.chatIdHex) {
                    messageDataSource.getRecentSentBy(chatId, selfId, RECENT_WINDOW)
                }
                val arrived = recent.firstOrNull { message -> message.carries(stored) }
                if (arrived != null) {
                    // The send got through before the process died; the stream already delivered it.
                    messageDataSource.confirmPending(chatId, mapper.clientMessageIdFromHex(record.clientIdHex), arrived, replaceContent = true)
                    discard(record)
                    continue
                }
                track(record)
                resume += record
            } else if (uploads.hasFile(record.fileName)) {
                track(record)
            } else {
                // Nothing to upload again: the file went with a cleared cache.
                discard(record)
            }
        }

        scope.launch {
            for (record in resume) {
                val chatId = mapper.chatIdFromHex(record.chatIdHex)
                // Stored rows survive the startup sweep as SENDING; a row that was failed anyway
                // goes back to it, since nothing here waits on the viewer.
                retryMedia(chatId, record.clientIdHex)
            }
        }
    }

    // region posting

    private class Queued(
        val clientMessageId: ClientMessageId,
        val message: ChatMediaSendPlan.Message.Media,
        val width: Int,
        val height: Int,
    )

    /** Waits for the photo of [record], then posts its message and settles the row. */
    private suspend fun post(chatId: ChatId, record: PendingMediaRecord) {
        val hex = record.clientIdHex
        val clientMessageId = mapper.clientMessageIdFromHex(hex)
        try {
            val chip = chipId(record)
            var recorded = false
            val settled = uploads.state(chip)
                .onEach { state ->
                    if (recorded) return@onEach
                    val photo = (state as? ChatMediaUploadState.Processing)?.photo
                        ?: (state as? ChatMediaUploadState.Uploaded)?.photo
                        ?: (state as? ChatMediaUploadState.Failed)?.stored
                    if (photo != null) {
                        recorded = true
                        recordStored(hex, photo)
                    }
                }
                .first { it == null || it is ChatMediaUploadState.Uploaded || it is ChatMediaUploadState.Failed }

            when (settled) {
                null -> fail(chatId, clientMessageId, hex, retryable = false, discard = record)
                is ChatMediaUploadState.Failed ->
                    fail(chatId, clientMessageId, hex, settled.retryable, discard = record.takeUnless { settled.retryable })
                is ChatMediaUploadState.Uploaded -> postMessage(chatId, record, clientMessageId, settled.photo)
                else -> Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            trace(tag = TAG, message = "Photo send failed in $chatId", type = TraceType.Error, error = e)
            fail(chatId, clientMessageId, hex, retryable = true, discard = null)
        } finally {
            synchronized(inFlight) { inFlight.remove(hex) }
        }
    }

    private suspend fun postMessage(
        chatId: ChatId,
        record: PendingMediaRecord,
        clientMessageId: ClientMessageId,
        photo: UploadedPhoto,
    ) {
        val hex = record.clientIdHex
        stages.update { it + (hex to Stage.Sending) }

        val content = sentContent(record, photo)
        val prepared = outgoing.prepare(chatId, content, blobSealedFor = photo.sealedFor).getOrElse { cause ->
            trace(tag = TAG, message = "Couldn't prepare photo in $chatId", type = TraceType.Error, error = cause)
            fail(chatId, clientMessageId, hex, retryable = true, discard = null)
            return
        }

        messagingController.sendMessage(chatId, prepared.wire, clientMessageId)
            .map(prepared::echo)
            .onSuccess { serverMessage ->
                messageDataSource.confirmPending(chatId, clientMessageId, serverMessage, replaceContent = true)
                messaging.advanceReadPointer(chatId, serverMessage.messageId)
                metadataDataSource.updateLastMessageId(chatId, serverMessage.messageId)
                metadataDataSource.updateLastActivity(chatId, serverMessage.timestamp.toEpochMilliseconds())
                discard(record, keepAs = ChatPhoto.cacheKeyOf(photo.blobId))
                // A photo uploaded while it sat in the composer confirms before Room has drawn its
                // row, so the bar would fade out unseen. Hold it until the row has been on screen.
                val hold = SENDING_VISIBLE_MILLIS - (now() - record.createdAt)
                scope.launch {
                    delay(hold)
                    stages.update { it + (hex to Stage.Sent) }
                }
            }
            .onFailure { cause ->
                // A chat that stopped taking ciphertext won't take this blob on a retry either.
                val terminal = cause is SendMessageError.EncryptionNotAllowed
                fail(chatId, clientMessageId, hex, retryable = !terminal, discard = record.takeIf { terminal })
            }
    }

    private suspend fun fail(
        chatId: ChatId,
        clientMessageId: ClientMessageId,
        hex: String,
        retryable: Boolean,
        discard: PendingMediaRecord?,
    ) {
        messageDataSource.failPending(chatId, clientMessageId)
        stages.update { it + (hex to Stage.Failed(retryable)) }
        // A terminal failure has nothing a retry could resend, so the queue entry and the file go.
        if (discard != null) discard(discard)
    }

    private suspend fun recordStored(hex: String, photo: UploadedPhoto) {
        pendingMedia.markStored(
            clientIdHex = hex,
            blobIdHex = hex(photo.blobId.bytes),
            sealedForHex = photo.sealedFor?.let { hex(it.bytes) },
            sizeBytes = photo.sizeBytes,
            blurhash = photo.blurhash,
        )
    }

    private suspend fun discard(record: PendingMediaRecord, keepAs: String? = null) {
        pendingMedia.delete(record.clientIdHex)
        uploads.remove(chipId(record), keepAs)
    }

    // endregion

    // region content

    private fun track(record: PendingMediaRecord) {
        val chip = chipId(record)
        uploads.restore(
            id = chip,
            chatId = mapper.chatIdFromHex(record.chatIdHex),
            fileName = record.fileName,
            width = record.width,
            height = record.height,
            blurhash = record.blurhash,
            stored = record.storedBlobIdHex?.let { blob ->
                UploadedPhoto(
                    blobId = BlobId(unhex(blob)),
                    width = record.width,
                    height = record.height,
                    blurhash = record.blurhash,
                    sizeBytes = record.sizeBytes ?: 0,
                    sealedFor = record.sealedForHex?.let { ChatId(unhex(it)) },
                )
            },
        )
        chips.update { it + (record.clientIdHex to chip) }
    }

    /**
     * The optimistic row's content: one ORIGINAL rendition with an empty blob id, since none is
     * known yet, and the local file as its URL for the bubble to draw.
     */
    private fun localContent(caption: String?, replyTo: Long?, width: Int, height: Int, uri: String): List<MessageContent> =
        wrap(
            replyTo,
            MessageContent.Media(
                items = listOf(
                    MediaItem(
                        listOf(
                            MediaItemRendition(
                                role = MediaItemRendition.Role.ORIGINAL,
                                blobId = BlobId(ByteArray(0)),
                                blob = BlobMetadata(
                                    mimeType = JPEG,
                                    sizeBytes = 0,
                                    downloadUrl = uri,
                                    image = ImageMetadata(width, height, ""),
                                ),
                            ),
                        ),
                    ),
                ),
                caption = caption?.let { MessageContent.Text(it) },
            ),
        )

    /**
     * What goes to the server. A sealed photo carries the full metadata its contract needs; a plain
     * one only its id, the server filling in the rest from the bytes.
     */
    private fun sentContent(record: PendingMediaRecord, photo: UploadedPhoto): List<MessageContent> {
        val sealed = photo.sealedFor != null
        val blob = if (sealed) {
            BlobMetadata(
                mimeType = JPEG,
                sizeBytes = photo.sizeBytes,
                downloadUrl = "",
                image = ImageMetadata(photo.width, photo.height, photo.blurhash.orEmpty()),
            )
        } else null
        return wrap(
            record.replyToMessageId,
            MessageContent.Media(
                items = listOf(
                    MediaItem(listOf(MediaItemRendition(MediaItemRendition.Role.ORIGINAL, photo.blobId, blob))),
                ),
                caption = record.caption?.let { MessageContent.Text(it) },
            ),
        )
    }

    private fun wrap(replyTo: Long?, media: MessageContent.Media): List<MessageContent> =
        listOf(replyTo?.let { MessageContent.Reply(it, listOf(media)) } ?: media)

    private fun ChatMessage.carries(blobIdHex: String): Boolean = content.any { c ->
        val media = (c as? MessageContent.Reply)?.content?.filterIsInstance<MessageContent.Media>()
            ?: listOfNotNull(c as? MessageContent.Media)
        media.any { m -> m.items.any { item -> item.renditions.any { hex(it.blobId.bytes) == blobIdHex } } }
    }

    private fun fileName(chip: String) = "$chip.jpg"
    private fun chipId(record: PendingMediaRecord) = record.fileName.removeSuffix(".jpg")
    private fun fileUri(chip: String) = "file://" + uploads.file(fileName(chip)).absolutePath
    private fun hex(id: ClientMessageId) = hex(id.bytes)
    private fun hex(bytes: ByteArray) = bytes.toList().hexEncodedString()
    private fun unhex(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    // endregion

    internal companion object {
        private const val TAG = "MediaSendDelegate"
        private const val JPEG = "image/jpeg"

        /** How far back a launch looks for the message a queued photo may already have become. */
        private const val RECENT_WINDOW = 50

        /** How long a sent photo's progress bar stays up, counted from when its row was inserted. */
        internal const val SENDING_VISIBLE_MILLIS = 800L
    }
}
