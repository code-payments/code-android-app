package com.flipcash.shared.chat.media

import android.net.Uri
import com.flipcash.app.blob.BlobStorageCoordinator
import com.flipcash.app.blob.ChatMediaEncoder
import com.flipcash.services.models.BlobRejectedException
import com.flipcash.services.models.blob.EncryptedConstraints
import com.flipcash.services.models.blob.MimeTypeConstraints
import com.flipcash.services.models.blob.UploadPolicy
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobRejection
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.ModerationResult
import com.flipcash.services.models.chat.RejectionReason
import com.flipcash.shared.chat.internal.BlobSealer
import com.flipcash.shared.chat.internal.OutgoingEncryption
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatMediaUploadsTest {

    @get:Rule val folder = TemporaryFolder()

    private val chatId = ChatId(byteArrayOf(1))
    private val blobId = BlobId(byteArrayOf(9, 9))
    private val jpeg = byteArrayOf(1, 2, 3, 4)
    private val uri = Uri.parse("content://photo/1")

    private val encoder = mockk<ChatMediaEncoder> {
        coEvery { encode(any(), any()) } returns Result.success(ChatMediaEncoder.Encoded(jpeg, 100, 50))
    }
    private val blobs = mockk<BlobStorageCoordinator> {
        every { policy } returns MutableStateFlow(policy(jpeg = true))
        coEvery { awaitChatMediaReady(any()) } returns Result.success(blobId)
    }
    private var sealer: BlobSealer? = null
    private val outgoing = object : OutgoingEncryption by OutgoingEncryption.None {
        override suspend fun blobSealer(chatId: ChatId) = Result.success(sealer)
    }

    private fun policy(jpeg: Boolean) = UploadPolicy(
        version = "v1",
        ttl = 1.hours,
        mimeTypeConstraints = listOf(MimeTypeConstraints(if (jpeg) "image/jpeg" else "image/png", 1_000_000, null)),
        encrypted = EncryptedConstraints(1_000_000, null),
    )

    private fun TestScope.uploads(scope: CoroutineScope = backgroundScope) = ChatMediaUploads(
        directory = { File(folder.root, "pending-media") },
        encoder = encoder,
        blobs = blobs,
        outgoing = outgoing,
        sourceSize = { 4000 to 3000 },
        blurhashOf = { "HASH" },
        scope = scope,
        newId = { "chip" },
    )

    private fun stores(block: suspend (onAttempt: (() -> Unit)?, onProgress: ((Long, Long) -> Unit)?) -> Result<BlobId>) {
        coEvery { blobs.storeChatMediaUnfinalized(any(), any(), any(), any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            block(arg(2) as (() -> Unit)?, arg(3) as ((Long, Long) -> Unit)?)
        }
    }

    @Test
    fun `happy path walks preparing, uploading, processing, uploaded`() = runTest {
        val seen = ArrayList<ChatMediaUploadState>()
        stores { onAttempt, onProgress ->
            onAttempt?.invoke()
            yield()
            onProgress?.invoke(2, 4)
            yield()
            onProgress?.invoke(4, 4)
            yield()
            Result.success(blobId)
        }
        val uploads = uploads()
        val id = uploads.stage(chatId, uri)
        launchCollect(uploads, id, seen)
        runCurrent()

        assertEquals(ChatMediaUploadState.Preparing(4000, 3000), seen.first { it is ChatMediaUploadState.Preparing && it.sourceWidth != null })
        assertTrue(seen.any { it == ChatMediaUploadState.Uploading(0.5f) } || seen.none { it is ChatMediaUploadState.Uploading })
        val done = assertIs<ChatMediaUploadState.Uploaded>(seen.last())
        assertEquals(UploadedPhoto(blobId, 100, 50, null, 4, null), done.photo)
        assertEquals(jpeg.toList(), File(folder.root, "pending-media/chip.jpg").readBytes().toList())
    }

    @Test
    fun `a shot staged at the shutter is not read until it is written`() = runTest {
        stores { _, _ -> Result.success(blobId) }
        val ready = CompletableDeferred<Boolean>()
        val uploads = uploads()
        val id = uploads.stage(chatId, uri, ready)
        runCurrent()

        coVerify(exactly = 0) { encoder.encode(any(), any()) }
        assertEquals(ChatMediaUploadState.Preparing(null, null), uploads.state(id).first())

        ready.complete(true)
        runCurrent()

        coVerify(exactly = 1) { encoder.encode(any(), any()) }
        assertIs<ChatMediaUploadState.Uploaded>(uploads.state(id).first())
    }

    @Test
    fun `a shot that failed to write is never read`() = runTest {
        val ready = CompletableDeferred<Boolean>()
        val uploads = uploads()
        val id = uploads.stage(chatId, uri, ready)
        ready.complete(false)
        runCurrent()

        coVerify(exactly = 0) { encoder.encode(any(), any()) }
        assertEquals(ChatMediaUploadState.Preparing(null, null), uploads.state(id).first())
    }

    private fun TestScope.launchCollect(uploads: ChatMediaUploads, id: String, into: MutableList<ChatMediaUploadState>) =
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            uploads.state(id).collect { it?.let(into::add) }
        }

    @Test
    fun `progress is monotonic within an attempt and ignores unknown length`() = runTest {
        val fractions = ArrayList<Float>()
        stores { onAttempt, onProgress ->
            onAttempt?.invoke()
            yield()
            onProgress?.invoke(1, 4)
            yield()
            onProgress?.invoke(3, 4)
            yield()
            onProgress?.invoke(2, 4) // backwards
            yield()
            onProgress?.invoke(4, 0) // unknown length
            yield()
            onProgress?.invoke(-1, 4)
            yield()
            Result.success(blobId)
        }
        val seen = ArrayList<ChatMediaUploadState>()
        val uploads = uploads()
        val id = uploads.stage(chatId, uri)
        launchCollect(uploads, id, seen)
        runCurrent()
        fractions += seen.filterIsInstance<ChatMediaUploadState.Uploading>().map { it.fraction }
        assertEquals(listOf(0.25f, 0.75f), fractions)
    }

    @Test
    fun `bytes reported after the store returned are ignored`() = runTest {
        var late: ((Long, Long) -> Unit)? = null
        stores { onAttempt, onProgress ->
            onAttempt?.invoke()
            late = onProgress
            Result.success(blobId)
        }
        coEvery { blobs.awaitChatMediaReady(any()) } coAnswers {
            late?.invoke(1, 4)
            awaitCancellation()
        }
        val uploads = uploads()
        val id = uploads.stage(chatId, uri)
        runCurrent()
        assertIs<ChatMediaUploadState.Processing>(uploads.current(id))
    }

    @Test
    fun `a retried store starts over from preparing`() = runTest {
        val seen = ArrayList<ChatMediaUploadState>()
        stores { onAttempt, onProgress ->
            onAttempt?.invoke()
            yield()
            onProgress?.invoke(3, 4)
            yield()
            onAttempt?.invoke()
            yield()
            onProgress?.invoke(1, 4)
            yield()
            Result.success(blobId)
        }
        val uploads = uploads()
        val id = uploads.stage(chatId, uri)
        launchCollect(uploads, id, seen)
        runCurrent()
        val states = seen.filter { it is ChatMediaUploadState.Preparing || it is ChatMediaUploadState.Uploading }
        val resetAt = states.indexOfLast { it is ChatMediaUploadState.Preparing }
        assertEquals(ChatMediaUploadState.Uploading(0.75f), states[resetAt - 1])
        assertEquals(ChatMediaUploadState.Uploading(0.25f), states[resetAt + 1])
    }

    @Test
    fun `a network failure that outlasts the retries is retryable`() = runTest {
        stores { _, _ -> Result.failure(IOException("offline")) }
        val uploads = uploads()
        val id = uploads.stage(chatId, uri)
        runCurrent()
        val failed = assertIs<ChatMediaUploadState.Failed>(uploads.current(id))
        assertTrue(failed.retryable)
        assertNull(failed.stored)
    }

    @Test
    fun `a moderation rejection is terminal`() = runTest {
        stores { _, _ -> Result.success(blobId) }
        coEvery { blobs.awaitChatMediaReady(any()) } returns Result.failure(
            BlobRejectedException(BlobRejection(RejectionReason.MODERATION, ModerationResult.FlaggedCategory.entries.first())),
        )
        val uploads = uploads()
        val id = uploads.stage(chatId, uri)
        runCurrent()
        val failed = assertIs<ChatMediaUploadState.Failed>(uploads.current(id))
        assertFalse(failed.retryable)
    }

    @Test
    fun `no jpeg constraint uploads nothing`() = runTest {
        every { blobs.policy } returns MutableStateFlow(policy(jpeg = false))
        val uploads = uploads()
        val id = uploads.stage(chatId, uri)
        runCurrent()
        assertFalse(assertIs<ChatMediaUploadState.Failed>(uploads.current(id)).retryable)
        coVerify(exactly = 0) { blobs.storeChatMediaUnfinalized(any(), any(), any(), any()) }
    }

    @Test
    fun `retrying a failed chip resumes the stored blob without uploading again`() = runTest {
        stores { _, _ -> Result.success(blobId) }
        coEvery { blobs.awaitChatMediaReady(any()) } returns Result.failure(IOException("timed out"))
        val uploads = uploads()
        val id = uploads.stage(chatId, uri)
        runCurrent()
        assertEquals(blobId, assertIs<ChatMediaUploadState.Failed>(uploads.current(id)).stored?.blobId)

        coEvery { blobs.awaitChatMediaReady(any()) } returns Result.success(blobId)
        uploads.retry(id)
        runCurrent()

        assertIs<ChatMediaUploadState.Uploaded>(uploads.current(id))
        coVerify(exactly = 1) { blobs.storeChatMediaUnfinalized(any(), any(), any(), any()) }
        coVerify(exactly = 1) { encoder.encode(any(), any()) }
    }

    @Test
    fun `retrying an unstored chip resends the written bytes without encoding again`() = runTest {
        var attempts = 0
        stores { _, _ -> if (attempts++ == 0) Result.failure(IOException("offline")) else Result.success(blobId) }
        val uploads = uploads()
        val id = uploads.stage(chatId, uri)
        runCurrent()
        uploads.retry(id)
        runCurrent()
        assertIs<ChatMediaUploadState.Uploaded>(uploads.current(id))
        coVerify(exactly = 1) { encoder.encode(any(), any()) }
    }

    @Test
    fun `a sealing chat seals and never falls back to plain`() = runTest {
        sealer = BlobSealer(chatId) { _, plain -> plain + 0 }
        var sealing: com.flipcash.app.blob.ChatMediaSealing? = null
        stores { _, _ -> Result.success(blobId) }
        coEvery { blobs.storeChatMediaUnfinalized(any(), any(), any(), any()) } coAnswers {
            sealing = secondArg()
            Result.success(blobId)
        }
        val uploads = uploads()
        val id = uploads.stage(chatId, uri)
        runCurrent()
        assertEquals(chatId, sealing?.chatId)
        val photo = assertIs<ChatMediaUploadState.Uploaded>(uploads.current(id)).photo
        assertEquals(chatId, photo.sealedFor)
        assertEquals("HASH", photo.blurhash)
    }

    @Test
    fun `removing a chip cancels the upload and deletes its file`() = runTest {
        stores { onAttempt, _ ->
            onAttempt?.invoke()
            awaitCancellation()
        }
        val uploads = uploads()
        val id = uploads.stage(chatId, uri)
        runCurrent()
        val file = File(folder.root, "pending-media/chip.jpg")
        assertTrue(file.exists())

        uploads.remove(id)
        runCurrent()

        assertNull(uploads.current(id))
        assertFalse(uploads.has(id))
        assertFalse(file.exists())
    }
}
