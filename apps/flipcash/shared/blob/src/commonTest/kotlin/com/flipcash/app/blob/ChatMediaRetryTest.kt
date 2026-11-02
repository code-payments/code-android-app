package com.flipcash.app.blob

import com.flipcash.services.models.BlobNotReadyException
import com.flipcash.services.models.BlobRejectedException
import com.flipcash.services.models.CompleteExternalUploadError
import com.flipcash.services.models.GetBlobsError
import com.flipcash.services.models.InitiateExternalUploadError
import com.flipcash.services.models.ModerationResult
import com.flipcash.services.models.chat.BlobRejection
import com.flipcash.services.models.chat.RejectionReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ChatMediaRetryTest {

    private fun rejected(reason: RejectionReason) =
        BlobRejectedException(BlobRejection(reason, ModerationResult.FlaggedCategory.NONE))

    @Test
    fun `backoff is one, two, then four seconds`() {
        assertEquals(listOf(1.seconds, 2.seconds, 4.seconds), ChatMediaRetry.BACKOFFS)
    }

    @Test
    fun `refusals that repeating cannot change are not retried`() {
        listOf(
            InitiateExternalUploadError.Denied(),
            InitiateExternalUploadError.UnsupportedType(),
            InitiateExternalUploadError.TooLarge(),
            InitiateExternalUploadError.QuotaExceeded(),
            CompleteExternalUploadError.NotFound(),
            CompleteExternalUploadError.NotUploaded(),
            GetBlobsError.Denied(),
        ).forEach { assertFalse(ChatMediaRetry.isRetryable(it), it::class.simpleName) }
    }

    @Test
    fun `transport, timeout and unclassified failures are retried`() {
        listOf(
            java.io.IOException("offline"),
            BlobNotReadyException(),
            RuntimeException("unknown"),
            InitiateExternalUploadError.Other(RuntimeException("x")),
        ).forEach { assertTrue(ChatMediaRetry.isRetryable(it), it::class.simpleName) }
    }

    @Test
    fun `moderation rejection is final, other rejections are retried`() {
        assertFalse(ChatMediaRetry.isRetryable(rejected(RejectionReason.MODERATION)))
        RejectionReason.entries.filter { it != RejectionReason.MODERATION }.forEach {
            assertTrue(ChatMediaRetry.isRetryable(rejected(it)), it.name)
        }
    }

    @Test
    fun `encoding failures are final`() {
        assertFalse(ChatMediaRetry.isRetryable(ChatMediaEncodingException.TooLarge()))
        assertFalse(ChatMediaRetry.isRetryable(ChatMediaEncodingException.EncodingFailed()))
    }
}
