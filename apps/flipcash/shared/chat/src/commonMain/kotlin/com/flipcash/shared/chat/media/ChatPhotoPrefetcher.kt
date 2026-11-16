package com.flipcash.shared.chat.media

import com.flipcash.app.core.media.ImageCachePresence
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.chat.MessageEncryption
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MuteState
import com.flipcash.services.models.chat.isActiveAt
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ui.mediaBody
import com.getcode.opencode.model.core.ID
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource

/** What started a prefetch, for its log line. */
enum class PhotoPrefetchTrigger { Push, Stream }

/** How a prefetch download ended. */
sealed interface PhotoPrefetchResult {
    /** The opened bytes are in the disk cache; [bytes] is their size on disk, if it could be read. */
    data class Cached(val bytes: Long?) : PhotoPrefetchResult

    /** The download or open failed. [reason] names the failure's class, never its message. */
    data class Failed(val reason: String) : PhotoPrefetchResult
}

/** The platform half of a prefetch: the active network, and a download through the image loader. */
interface PhotoPrefetchPlatform {
    fun network(): PrefetchNetwork

    /** Loads [photo] into the disk cache the way its bubble would, without drawing it anywhere. */
    suspend fun download(photo: ChatPhoto): PhotoPrefetchResult
}

/** Where the event stream reports a message from someone else that arrived live. */
fun interface PhotoArrivalSink {
    fun arrived(chatId: ChatId, messageId: Long)

    companion object {
        val None = PhotoArrivalSink { _, _ -> }
    }
}

/** Whether [content] may hold a photo: a photo body, or ciphertext that has not been opened yet. */
fun mayHoldPhoto(content: List<MessageContent>): Boolean =
    content.any { it.mediaBody() != null || it is MessageContent.Encrypted }

/**
 * Downloads the photos of a message that just arrived, so they are on disk before its chat is
 * opened. Each photo takes the blob its bubble will request ([PhotoPrefetchTarget]); a skipped or
 * failed prefetch leaves nothing behind, and the bubble loads the photo the normal way.
 *
 * Reads the message as stored, so a sealed message is already opened. A chat that is not stored
 * yet is waited for, up to [readyTimeout], while the feed refresh already running stores it.
 */
@Singleton
class ChatPhotoPrefetcher internal constructor(
    private val messages: ChatMessageDataSource,
    private val metadata: ChatMetadataDataSource,
    private val members: ChatMemberDataSource,
    private val userManager: UserManager,
    private val target: PhotoPrefetchTarget,
    private val platform: PhotoPrefetchPlatform,
    private val cache: ImageCachePresence,
    private val scope: CoroutineScope,
    private val readyTimeout: Duration,
    private val now: () -> Instant,
) : PhotoArrivalSink {
    @Inject
    constructor(
        messages: ChatMessageDataSource,
        metadata: ChatMetadataDataSource,
        members: ChatMemberDataSource,
        userManager: UserManager,
        target: PhotoPrefetchTarget,
        platform: PhotoPrefetchPlatform,
        cache: ImageCachePresence,
    ) : this(
        messages, metadata, members, userManager, target, platform, cache,
        CoroutineScope(SupervisorJob() + Dispatchers.IO), READY_TIMEOUT, { Clock.System.now() },
    )

    private val inFlightLock = Mutex()
    private val inFlight = mutableSetOf<String>()

    override fun arrived(chatId: ChatId, messageId: Long) {
        scope.launch { prefetch(PhotoPrefetchTrigger.Stream, chatId, messageId, inline = null, pushedMuted = false) }
    }

    /**
     * Prefetches the photos of a pushed message. Call it once the push's sync work has finished, so
     * the message and its chat are stored. [inline] is the message the push carried, read only when
     * it is plaintext and the stored row is missing; [muted] is the push's own mute flag.
     */
    suspend fun prefetchPushed(chatId: ChatId, messageId: Long, inline: ChatMessage?, muted: Boolean) {
        prefetch(PhotoPrefetchTrigger.Push, chatId, messageId, inline, muted)
    }

    /** Never throws, short of cancellation: a stream arrival runs it on a scope nothing watches. */
    internal suspend fun prefetch(
        trigger: PhotoPrefetchTrigger,
        chatId: ChatId,
        messageId: Long,
        inline: ChatMessage?,
        pushedMuted: Boolean,
    ) {
        try {
            prefetchMessage(trigger, chatId, messageId, inline, pushedMuted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The class only: a download failure's message can carry a signed URL.
            log(trigger, role = null, "result=failed reason=${e::class.simpleName}")
        }
    }

    private suspend fun prefetchMessage(
        trigger: PhotoPrefetchTrigger,
        chatId: ChatId,
        messageId: Long,
        inline: ChatMessage?,
        pushedMuted: Boolean,
    ) {
        val message = messages.getMessage(chatId, messageId)
            ?: inline?.takeIf { it.encryption == null }
        if (message == null) {
            log(trigger, role = null, "skipped=MessageNotStored")
            return
        }
        val items = message.content.mapNotNull { it.mediaBody()?.items?.firstOrNull() }
        if (items.isEmpty()) {
            // Still ciphertext: its key was not ready when it was stored, so whether it holds a photo
            // is unknown until the chat opens it again.
            val unopened = message.encryption != null && message.encryption !is MessageEncryption.Decrypted
            if (unopened) log(trigger, role = null, "skipped=NotOpened")
            return
        }

        val chat = withTimeoutOrNull(readyTimeout) { metadata.observeById(chatId).filterNotNull().first() }
        if (chat == null) {
            log(trigger, role = null, "skipped=ChatNotStored")
            return
        }
        val selfId = userManager.accountId
        val groupIncoming = chat.chatType == ChatType.GROUP.name && message.senderId != selfId
        val muted = pushedMuted || chat.muteState().isActiveAt(now())
        val sealed = message.encryption != null

        for (item in items) {
            val rendition = target.rendition(item, groupIncoming)
            if (rendition == null || rendition.blobId.bytes.isEmpty()) {
                log(trigger, role = null, "skipped=NoRendition")
                continue
            }
            val role = rendition.role.name
            val photo = ChatPhoto(chatId, rendition, message.senderId, sealed, message.redacted)
            val skip = photoPrefetchSkip(
                redacted = photo.redacted,
                muted = muted,
                cached = cache.holds(photo.cacheKey),
                network = platform.network(),
            )
            if (skip != null) {
                log(trigger, role, "skipped=$skip")
                continue
            }
            // Opening sealed bytes needs their size and the key; the fetcher would find either missing
            // only after downloading the blob, then discard it.
            if (sealed && rendition.blob?.sizeBytes == null) {
                log(trigger, role, "skipped=NoSize")
                continue
            }
            if (sealed && !chat.isPrivate && !awaitPeer(chatId, selfId)) {
                log(trigger, role, "skipped=KeyPending")
                continue
            }
            if (!claim(photo.cacheKey)) {
                log(trigger, role, "skipped=InFlight")
                continue
            }
            val started = TimeSource.Monotonic.markNow()
            val result = try {
                platform.download(photo)
            } finally {
                release(photo.cacheKey)
            }
            val ms = started.elapsedNow().inWholeMilliseconds
            val outcome = when (result) {
                is PhotoPrefetchResult.Cached -> "result=cached bytes=${result.bytes ?: "unknown"}"
                is PhotoPrefetchResult.Failed -> "result=failed reason=${result.reason}"
            }
            log(trigger, role, "$outcome sealed=$sealed metadata=${rendition.blob != null} ms=$ms")
        }
    }

    /** Waits for the DM's other member, which keys a sealed DM photo, to be stored. */
    private suspend fun awaitPeer(chatId: ChatId, selfId: ID?): Boolean {
        if (selfId == null) return false
        return withTimeoutOrNull(readyTimeout) {
            members.observeMembers(chatId).first { stored -> stored.any { it.userId != selfId } }
        } != null
    }

    private suspend fun claim(key: String): Boolean = inFlightLock.withLock { inFlight.add(key) }

    private suspend fun release(key: String) {
        inFlightLock.withLock { inFlight.remove(key) }
    }

    private fun log(trigger: PhotoPrefetchTrigger, role: String?, detail: String) {
        trace(
            tag = TAG,
            message = "trigger=${trigger.name.lowercase()} role=${role ?: "none"} $detail",
            type = TraceType.Log,
        )
    }

    companion object {
        private const val TAG = "ChatPhotoPrefetch"

        /** How long a prefetch waits for its chat, or a DM's other member, to be stored. */
        val READY_TIMEOUT: Duration = 10.seconds
    }
}

private fun ChatMetadataEntity.muteState(): MuteState? {
    val until = muteUntilEpochMs
    return when {
        muteForever -> MuteState.Forever
        until != null -> MuteState.Until(Instant.fromEpochMilliseconds(until))
        else -> null
    }
}
