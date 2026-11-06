package com.flipcash.shared.chat.media

import com.flipcash.services.models.chat.ChatId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Where one photo message is on its way out, for the overlay on its bubble. */
sealed interface MediaSendProgress {
    data object Preparing : MediaSendProgress

    /** [fraction] is 0..1 and only grows within an attempt. */
    data class Uploading(val fraction: Float) : MediaSendProgress

    data object Processing : MediaSendProgress
    data object Sending : MediaSendProgress
    data object Sent : MediaSendProgress

    /** [retryable] is whether `retryMedia` can help; a moderation rejection can't be retried. */
    data class Failed(val retryable: Boolean) : MediaSendProgress

    companion object {
        /**
         * The progress of a whole send, for the overlay on a stack's front photo, from the progress
         * of each photo in it. A photo with no progress is one this process didn't send or already
         * confirmed, and counts as sent; a failed photo has left the stack and is left out.
         *
         * The send reads as preparing only while every photo is, since uploads start one after
         * another. Once any is uploading, the fraction is the average across the photos, where a
         * photo still preparing counts 0 and one past its upload counts 1. Shared with iOS.
         *
         * Null when no photo has progress, so a stack sent earlier draws no overlay.
         */
        fun ofSend(members: List<MediaSendProgress?>): MediaSendProgress? {
            if (members.all { it == null }) return null
            val live = members.filter { it !is Failed }.map { it ?: Sent }
            if (live.isEmpty()) return null
            if (live.all { it == Preparing }) return Preparing
            if (live.any { it == Preparing || it is Uploading }) {
                val fraction = live.sumOf { p ->
                    when (p) {
                        Preparing -> 0.0
                        is Uploading -> p.fraction.toDouble()
                        else -> 1.0
                    }
                } / live.size
                return Uploading(fraction.toFloat())
            }
            return when {
                live.any { it == Processing } -> Processing
                live.any { it == Sending } -> Sending
                else -> Sent
            }
        }
    }
}

/**
 * Sending photos. Everything after [sendMedia] returns happens in the app's scope, so leaving the
 * chat doesn't stop a photo that is still going up.
 */
interface ChatMediaSending {

    /**
     * Sends the staged photos [chipIds] (see [ChatMediaUploads.stage]) and [text] to [chatId],
     * following [ChatMediaSendPlan]: with no chips, [text] is an ordinary message; with chips, one
     * photo message per chip, the caption on the last and the reply on the first.
     *
     * Returns once every photo message is on the transcript as a sending row, with the client id
     * hex of each in chip order (empty for a text-only send, which is sent before this returns).
     * The rows are posted in chip order as their uploads settle; one that fails leaves the others
     * going. Follow them with [observeMediaSendProgress].
     */
    suspend fun sendMedia(
        chatId: ChatId,
        chipIds: List<String>,
        text: String,
        replyToMessageId: Long?,
    ): Result<List<String>>

    /**
     * Retries a failed photo message by its pending client id: uploads the stored JPEG again if the
     * photo never reached storage, polls it if it did, or posts it again if only the post failed.
     * Fails if there is nothing left to retry, as after a moderation rejection.
     */
    suspend fun retryMedia(chatId: ChatId, pendingClientIdHex: String): Result<Unit>

    /** Progress of the photo messages sent or resumed in this process, by client id hex. */
    fun observeMediaSendProgress(): Flow<Map<String, MediaSendProgress>>

    /**
     * Picks up what a previous process left queued: stored photos are polled and posted, the rest
     * stay failed with a retry, and entries whose message already reached the chat are dropped.
     */
    suspend fun reconcilePendingMedia()

    /** Does nothing. The default for a coordinator built without photo sending, as in unit tests. */
    object None : ChatMediaSending {
        override suspend fun sendMedia(
            chatId: ChatId,
            chipIds: List<String>,
            text: String,
            replyToMessageId: Long?,
        ): Result<List<String>> = Result.failure(UnsupportedOperationException("Photos are not available"))

        override suspend fun retryMedia(chatId: ChatId, pendingClientIdHex: String): Result<Unit> =
            Result.failure(UnsupportedOperationException("Photos are not available"))

        override fun observeMediaSendProgress(): Flow<Map<String, MediaSendProgress>> = flowOf(emptyMap())
        override suspend fun reconcilePendingMedia() = Unit
    }
}
