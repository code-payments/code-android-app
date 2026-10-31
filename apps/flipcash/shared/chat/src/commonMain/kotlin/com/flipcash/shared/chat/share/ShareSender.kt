package com.flipcash.shared.chat.share

import com.flipcash.app.blob.PickedPhoto
import com.flipcash.services.models.chat.ChatId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Most images one share carries; the same ceiling as the composer's attachments. */
const val MAX_SHARE_IMAGES = 10

/** Why a chat didn't get the share, for the line under the compose bar. */
enum class ShareFailure {
    /** The send failed in a way trying again may fix. */
    Send,

    /** The server refused an image, as moderation does. */
    ImageRejected,

    /** An image couldn't be read or prepared. */
    ImageUnprepared,
}

/** Where one chosen chat stands once a send has started. */
sealed interface ShareChatStatus {
    data object Pending : ShareChatStatus
    data object Sent : ShareChatStatus
    data class Failed(val failure: ShareFailure, val canRetry: Boolean) : ShareChatStatus
}

/** Where every chosen chat stands, in the order they were picked. */
data class ShareSendProgress(
    val order: List<ChatId>,
    val statuses: Map<ChatId, ShareChatStatus> = order.associateWith { ShareChatStatus.Pending },
) {
    /** Chats still to send to: pending, or failed with an error trying again may fix. */
    val remaining: List<ChatId>
        get() = order.filter {
            when (val status = statuses[it]) {
                ShareChatStatus.Pending -> true
                is ShareChatStatus.Failed -> status.canRetry
                ShareChatStatus.Sent, null -> false
            }
        }

    val failed: List<ChatId>
        get() = order.filter { statuses[it] is ShareChatStatus.Failed }

    val isComplete: Boolean get() = order.all { statuses[it] == ShareChatStatus.Sent }

    /** Something failed and nothing retryable is left to try. */
    val isStuck: Boolean get() = !isComplete && remaining.isEmpty()

    fun with(chatId: ChatId, status: ShareChatStatus) = copy(statuses = statuses + (chatId to status))
}

/** The chat being sent to, `position` of `total`, counting from 1. */
data class ShareSending(val position: Int, val total: Int)

/** A photo message's end state once it has left the device. */
sealed interface PhotoOutcome {
    data object Sent : PhotoOutcome
    data class Failed(val retryable: Boolean) : PhotoOutcome
}

/**
 * The app's own send path as [ShareSender] needs it. Everything that seals content for an
 * encrypted chat lives behind these calls (see `OutgoingEncryption`); the sender decides only
 * what goes where and in which order.
 */
interface ShareGateway {
    /** Sends [text] to [chatId] the way typing it there would. */
    suspend fun sendText(chatId: ChatId, text: String): Result<Unit>

    /**
     * Starts preparing and uploading [photo] for [chatId] and returns its chip. The upload is sealed
     * for [chatId] when that chat encrypts, so a chip belongs to one chat and is never shared.
     */
    fun stagePhoto(chatId: ChatId, photo: PickedPhoto): String

    /**
     * Posts the staged [chipIds] to [chatId], one message each with [caption] on the last, and
     * returns the id of each message in chip order once they are queued.
     */
    suspend fun sendPhotos(chatId: ChatId, chipIds: List<String>, caption: String): Result<List<String>>

    /** Suspends until every message in [messageIds] has been sent or has failed. */
    suspend fun awaitPhotos(messageIds: List<String>): Map<String, PhotoOutcome>

    /** Tries a failed photo message again. */
    suspend fun retryPhoto(chatId: ChatId, messageId: String): Result<Unit>

    /** Drops chips that never made it into a send. */
    fun discardPhotos(chipIds: List<String>)
}

/**
 * Sends one share to several chats, as the iOS share extension does: chat by chat, in the order
 * they were picked, one failure never stopping the rest, and a retry going only to the chats that
 * haven't received it. Images become one photo message each with the message as the caption of the
 * last; with no images the message is a text. Nothing is sent twice: within a chat, a retry
 * re-attempts only the photo messages that failed.
 *
 * Holds the send's progress between calls, so one instance serves one share.
 */
class ShareSender(private val gateway: ShareGateway) {

    private val _progress = MutableStateFlow<ShareSendProgress?>(null)

    /** Null before the first send; after, where each chosen chat stands. */
    val progress: StateFlow<ShareSendProgress?> = _progress.asStateFlow()

    private val _sending = MutableStateFlow<ShareSending?>(null)

    /** Non-null while a send is under way. */
    val sending: StateFlow<ShareSending?> = _sending.asStateFlow()

    private class Work {
        var textSent = false
        var messages: List<String>? = null
        val sent = HashSet<String>()
    }

    private val work = HashMap<ChatId, Work>()

    /** Forgets progress, as when the chosen chats change after a failure. */
    fun reset() {
        _progress.value = null
        work.clear()
    }

    /**
     * Sends [text] and [images] to every chat in [recipients] that hasn't got them yet. True once
     * every chat has. Images past [MAX_SHARE_IMAGES] are not sent.
     */
    suspend fun send(recipients: List<ChatId>, images: List<PickedPhoto>, text: String): Boolean {
        val photos = images.take(MAX_SHARE_IMAGES)
        val message = text.trim()
        if (photos.isEmpty() && message.isEmpty()) return false

        var progress = _progress.value ?: ShareSendProgress(recipients)
        val targets = progress.remaining
        try {
            targets.forEachIndexed { index, chatId ->
                _sending.value = ShareSending(index + 1, targets.size)
                val status = try {
                    deliver(chatId, photos, message)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ShareChatStatus.Failed(ShareFailure.Send, canRetry = true)
                }
                progress = progress.with(chatId, status)
                _progress.value = progress
            }
        } finally {
            _sending.value = null
            _progress.value = progress
        }
        return progress.isComplete
    }

    private suspend fun deliver(chatId: ChatId, images: List<PickedPhoto>, text: String): ShareChatStatus {
        val state = work.getOrPut(chatId) { Work() }
        if (images.isEmpty()) {
            if (!state.textSent) {
                if (gateway.sendText(chatId, text).isFailure) return ShareChatStatus.Failed(ShareFailure.Send, true)
                state.textSent = true
            }
            return ShareChatStatus.Sent
        }

        val queued = state.messages
        val waitFor: List<String>
        if (queued == null) {
            // Staged per chat: a photo sealed for one chat is that chat's alone.
            val chips = images.map { gateway.stagePhoto(chatId, it) }
            val ids = gateway.sendPhotos(chatId, chips, text).getOrElse {
                gateway.discardPhotos(chips)
                return ShareChatStatus.Failed(ShareFailure.Send, true)
            }
            state.messages = ids
            waitFor = ids
        } else {
            waitFor = queued.filter { it !in state.sent }
            for (id in waitFor) {
                if (gateway.retryPhoto(chatId, id).isFailure) {
                    return ShareChatStatus.Failed(ShareFailure.ImageUnprepared, canRetry = false)
                }
            }
        }

        val outcomes = gateway.awaitPhotos(waitFor)
        outcomes.forEach { (id, outcome) -> if (outcome == PhotoOutcome.Sent) state.sent += id }
        val failures = outcomes.values.filterIsInstance<PhotoOutcome.Failed>()
        return when {
            failures.isEmpty() && state.sent.containsAll(waitFor) -> ShareChatStatus.Sent
            failures.any { it.retryable } || failures.isEmpty() -> ShareChatStatus.Failed(ShareFailure.Send, true)
            else -> ShareChatStatus.Failed(ShareFailure.ImageRejected, false)
        }
    }
}
