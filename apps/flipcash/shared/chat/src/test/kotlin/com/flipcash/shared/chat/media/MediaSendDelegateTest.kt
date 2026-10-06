package com.flipcash.shared.chat.media

import android.net.Uri
import com.flipcash.app.blob.BlobStorageCoordinator
import com.flipcash.app.blob.ChatMediaEncoder
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.PendingMediaDataSource
import com.flipcash.app.persistence.sources.PendingMediaRecord
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.models.BlobRejectedException
import com.flipcash.services.models.ModerationResult
import com.flipcash.services.models.blob.EncryptedConstraints
import com.flipcash.services.models.blob.MimeTypeConstraints
import com.flipcash.services.models.blob.UploadPolicy
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobRejection
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ClientMessageId
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.RejectionReason
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.OutgoingEncryption
import com.flipcash.shared.chat.internal.delegates.MediaSendDelegate
import com.flipcash.shared.chat.internal.delegates.MessagingDelegate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MediaSendDelegateTest {

    @get:Rule val folder = TemporaryFolder()

    private val chatId = ChatId(byteArrayOf(1, 1))
    private val self = listOf<Byte>(7, 7)

    /** `pending_media`, as the data source would hold it. */
    private val records = LinkedHashMap<String, PendingMediaRecord>()
    private val pendingMedia = mockk<PendingMediaDataSource>().also { m ->
        with(m) { stubPending() }
    }

    private fun PendingMediaDataSource.stubPending() {
        val self = this
        coEvery { self.insert(any()) } coAnswers { firstArg<List<PendingMediaRecord>>().forEach { records[it.clientIdHex] = it } }
        coEvery { self.get(any()) } coAnswers { records[firstArg()] }
        coEvery { self.getAll() } coAnswers { records.values.toList() }
        coEvery { self.delete(any()) } coAnswers { records.remove(firstArg<String>()); Unit }
        coEvery { self.markStored(any(), any(), any(), any(), any()) } coAnswers {
            val hex = firstArg<String>()
            records[hex] = records.getValue(hex).copy(
                storedBlobIdHex = secondArg(), sealedForHex = thirdArg(),
                sizeBytes = arg(3), blurhash = arg(4),
            )
        }
    }

    private class Inserted(val clientMessageId: ClientMessageId, val content: List<MessageContent>)

    private val inserted = ArrayList<Inserted>()
    private val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true) {
        coEvery { insertPending(any(), any(), any(), any(), any()) } coAnswers {
            inserted += Inserted(arg(3), secondArg())
            mockk(relaxed = true)
        }
        coEvery { getRecentSentBy(any(), any(), any()) } returns emptyList()
    }

    private val sent = ArrayList<Pair<ClientMessageId, List<MessageContent>>>()
    private var sendResult: (ClientMessageId) -> Result<ChatMessage> = { Result.success(serverMessage(5)) }
    private val controller = mockk<ChatMessagingController> {
        coEvery { sendMessage(any(), any(), any()) } coAnswers {
            val id = thirdArg<ClientMessageId>()
            sent += id to secondArg()
            sendResult(id)
        }
    }

    private val messaging = mockk<MessagingDelegate>(relaxed = true)
    private val userManager = mockk<UserManager> { every { accountId } returns self }

    private val encoder = mockk<ChatMediaEncoder>()
    private val blobs = mockk<BlobStorageCoordinator>()
    private val storeBehavior = HashMap<String, suspend () -> Result<BlobId>>()
    private var ready: (BlobId) -> Result<BlobId> = { Result.success(it) }
    private var next = 0

    private fun serverMessage(id: Long) = ChatMessage(
        messageId = id, senderId = self, content = emptyList(),
        timestamp = Instant.fromEpochMilliseconds(1_000), unreadSeq = 0,
    )

    private fun TestScope.delegate(): Pair<MediaSendDelegate, ChatMediaUploads> {
        every { blobs.policy } returns MutableStateFlow(
            UploadPolicy("v1", 1.hours, listOf(MimeTypeConstraints("image/jpeg", 1_000_000, null)), EncryptedConstraints(1_000_000, null)),
        )
        // The photo's bytes carry its name, so a store knows which chip it is for.
        coEvery { encoder.encode(any(), any()) } coAnswers {
            Result.success(ChatMediaEncoder.Encoded(firstArg<Uri>().lastPathSegment!!.toByteArray(), 100, 50))
        }
        coEvery { blobs.storeChatMediaUnfinalized(any(), any(), any(), any()) } coAnswers {
            val name = String(firstArg<ByteArray>())
            storeBehavior[name]?.invoke() ?: Result.success(BlobId(name.toByteArray()))
        }
        coEvery { blobs.awaitChatMediaReady(any()) } coAnswers { ready(firstArg()) }
        val uploads = ChatMediaUploads(
            directory = { File(folder.root, "pending-media") },
            encoder = encoder, blobs = blobs, outgoing = OutgoingEncryption.None,
            sourceSize = { 4000 to 3000 }, blurhashOf = { null },
            scope = backgroundScope, newId = { "chip${next++}" },
        )
        val delegate = MediaSendDelegate(
            messaging = messaging, messagingController = controller, messageDataSource = messageDataSource,
            metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true), pendingMedia = pendingMedia,
            uploads = uploads, outgoing = OutgoingEncryption.None, userManager = userManager,
            scope = backgroundScope, now = { 1_000 },
        )
        return delegate to uploads
    }

    private fun stage(uploads: ChatMediaUploads, name: String) = uploads.stage(chatId, Uri.parse("content://photos/$name"))

    private fun hex(id: ClientMessageId) = id.bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun `inserts every pending row up front`() = runTest {
        val (delegate, uploads) = delegate()
        val chips = listOf("a", "b", "c").map { stage(uploads, it) }
        runCurrent()

        val ids = delegate.sendMedia(chatId, chips, "hello", 42).getOrThrow()

        assertEquals(3, ids.size)
        assertEquals(3, inserted.size)
        assertEquals(0, sent.size)
        assertEquals(ids.toSet(), records.keys)
        val captions = inserted.map { (it.content.single().let { c -> (c as? MessageContent.Reply)?.content?.single() ?: c } as MessageContent.Media).caption }
        assertEquals(listOf(null, null, MessageContent.Text("hello")), captions)
        assertIs<MessageContent.Reply>(inserted.first().content.single())
        assertEquals(100, rendition(inserted.first()).blob?.image?.width)
    }

    private fun rendition(i: Inserted) =
        ((i.content.single().let { (it as? MessageContent.Reply)?.content?.single() ?: it }) as MessageContent.Media)
            .items.single().renditions.single()

    @Test
    fun `posts strictly in chip order even when a later upload finishes first`() = runTest {
        val (delegate, uploads) = delegate()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        storeBehavior["a"] = { gate.await(); Result.success(BlobId("a".toByteArray())) }
        val chips = listOf("a", "b", "c").map { stage(uploads, it) }
        runCurrent()
        val ids = delegate.sendMedia(chatId, chips, "", null).getOrThrow()
        runCurrent()
        assertEquals(0, sent.size) // b and c are uploaded, but a is first
        gate.complete(Unit)
        runCurrent()

        assertEquals(ids, sent.map { hex(it.first) })
        assertTrue(records.isEmpty())
        coVerify(exactly = 3) { messageDataSource.confirmPending(any(), any(), any(), true) }
    }

    @Test
    fun `a send that confirms at once holds its progress until the row has been seen`() = runTest {
        val (delegate, uploads) = delegate()
        val chip = stage(uploads, "a")
        runCurrent() // uploaded before the send, so the post confirms straight away
        val ids = delegate.sendMedia(chatId, listOf(chip), "", null).getOrThrow()
        runCurrent()

        assertEquals(1, sent.size)
        assertEquals(MediaSendProgress.Sending, delegate.progressOf(ids[0]))
        advanceTimeBy(MediaSendDelegate.SENDING_VISIBLE_MILLIS)
        runCurrent()
        assertEquals(MediaSendProgress.Sent, delegate.progressOf(ids[0]))
    }

    @Test
    fun `a failed upload fails only its row`() = runTest {
        val (delegate, uploads) = delegate()
        storeBehavior["b"] = { Result.failure(IOException("offline")) }
        val chips = listOf("a", "b", "c").map { stage(uploads, it) }
        val ids = delegate.sendMedia(chatId, chips, "", null).getOrThrow()
        runCurrent()

        assertEquals(listOf(ids[0], ids[2]), sent.map { hex(it.first) })
        coVerify(exactly = 1) { messageDataSource.failPending(chatId, match { hex(it) == ids[1] }) }
        assertEquals(setOf(ids[1]), records.keys) // kept for retry
    }

    @Test
    fun `a failed post fails the row and keeps the stored photo`() = runTest {
        val (delegate, uploads) = delegate()
        sendResult = { Result.failure(IOException("down")) }
        val ids = delegate.sendMedia(chatId, listOf(stage(uploads, "a")), "", null).getOrThrow()
        runCurrent()

        coVerify { messageDataSource.failPending(chatId, match { hex(it) == ids[0] }) }
        assertNotNull(records[ids[0]]?.storedBlobIdHex)
        assertEquals(MediaSendProgress.Failed(true), delegate.progressOf(ids[0]))
    }

    @Test
    fun `the pending row falls back to source pixels before encoding finishes`() = runTest {
        val (delegate, uploads) = delegate()
        coEvery { encoder.encode(any(), any()) } coAnswers { delay(10_000); Result.failure(IOException()) }
        val chip = stage(uploads, "a")
        runCurrent()
        delegate.sendMedia(chatId, listOf(chip), "", null)
        val image = rendition(inserted.single()).blob?.image
        assertEquals(4000 to 3000, image?.width to image?.height)
    }

    @Test
    fun `retry posts a stored photo again under the same client id`() = runTest {
        val (delegate, uploads) = delegate()
        sendResult = { Result.failure(IOException("down")) }
        val ids = delegate.sendMedia(chatId, listOf(stage(uploads, "a")), "", null).getOrThrow()
        runCurrent()
        sendResult = { Result.success(serverMessage(9)) }

        delegate.retryMedia(chatId, ids[0]).getOrThrow()
        runCurrent()

        assertEquals(listOf(ids[0], ids[0]), sent.map { hex(it.first) })
        coVerify(exactly = 1) { blobs.storeChatMediaUnfinalized(any(), any(), any(), any()) }
        assertTrue(records.isEmpty())
    }

    @Test
    fun `retry uploads the stored file again when nothing reached storage`() = runTest {
        val (delegate, uploads) = delegate()
        var attempts = 0
        storeBehavior["a"] = { if (attempts++ == 0) Result.failure(IOException("offline")) else Result.success(BlobId("a".toByteArray())) }
        val ids = delegate.sendMedia(chatId, listOf(stage(uploads, "a")), "", null).getOrThrow()
        runCurrent()
        assertEquals(0, sent.size)

        delegate.retryMedia(chatId, ids[0]).getOrThrow()
        runCurrent()

        assertEquals(1, sent.size)
        coVerify(exactly = 1) { encoder.encode(any(), any()) }
        coVerify(exactly = 2) { blobs.storeChatMediaUnfinalized(any(), any(), any(), any()) }
    }

    @Test
    fun `a moderation rejection is not retried and leaves the queue`() = runTest {
        val (delegate, uploads) = delegate()
        ready = {
            Result.failure(BlobRejectedException(BlobRejection(RejectionReason.MODERATION, ModerationResult.FlaggedCategory.entries.first())))
        }
        val ids = delegate.sendMedia(chatId, listOf(stage(uploads, "a")), "", null).getOrThrow()
        runCurrent()

        assertTrue(records.isEmpty())
        assertFalse(File(folder.root, "pending-media/chip0.jpg").exists())
        assertTrue(delegate.retryMedia(chatId, ids[0]).isFailure)
        assertEquals(0, sent.size)
        coVerify { messageDataSource.failPending(chatId, match { hex(it) == ids[0] }) }
    }

    // region launch reconciliation

    private fun record(hex: String, stored: String?, file: String = "old.jpg") = PendingMediaRecord(
        clientIdHex = hex, chatIdHex = "0101", fileName = file, caption = null, replyToMessageId = null,
        storedBlobIdHex = stored, sealedForHex = null, width = 100, height = 50, blurhash = null,
        sizeBytes = 4, createdAt = 1,
    )

    @Test
    fun `an unstored entry is restored failed and waits for a retry`() = runTest {
        val (delegate, uploads) = delegate()
        File(folder.root, "pending-media").mkdirs()
        File(folder.root, "pending-media/old.jpg").writeBytes(byteArrayOf(1))
        records["aa"] = record("aa", stored = null)

        delegate.reconcilePendingMedia()
        runCurrent()

        assertEquals(0, sent.size)
        assertEquals(MediaSendProgress.Failed(true), delegate.progressOf("aa"))
        assertTrue(uploads.has("old"))
        assertTrue(records.containsKey("aa"))
    }

    @Test
    fun `a stored entry resumes and posts without being asked`() = runTest {
        val (delegate, _) = delegate()
        File(folder.root, "pending-media").mkdirs()
        records["aa"] = record("aa", stored = "0909")

        delegate.reconcilePendingMedia()
        runCurrent()

        assertEquals(listOf("aa"), sent.map { hex(it.first) })
        coVerify(exactly = 0) { blobs.storeChatMediaUnfinalized(any(), any(), any(), any()) }
        coVerify { messageDataSource.retryPending(chatId, "aa") }
        assertTrue(records.isEmpty())
    }

    @Test
    fun `an entry whose message is already in the chat is dropped`() = runTest {
        val (delegate, _) = delegate()
        File(folder.root, "pending-media").mkdirs()
        File(folder.root, "pending-media/old.jpg").writeBytes(byteArrayOf(1))
        records["aa"] = record("aa", stored = "0909")
        val arrived = serverMessage(12).copy(
            content = listOf(
                MessageContent.Media(
                    listOf(
                        com.flipcash.services.models.chat.MediaItem(
                            listOf(
                                com.flipcash.services.models.chat.MediaItemRendition(
                                    com.flipcash.services.models.chat.MediaItemRendition.Role.ORIGINAL,
                                    BlobId(byteArrayOf(9, 9)), null,
                                ),
                            ),
                        ),
                    ),
                    null,
                ),
            ),
        )
        coEvery { messageDataSource.getRecentSentBy(any(), any(), any()) } returns listOf(arrived)

        delegate.reconcilePendingMedia()
        runCurrent()

        assertEquals(0, sent.size)
        assertTrue(records.isEmpty())
        assertFalse(File(folder.root, "pending-media/old.jpg").exists())
        coVerify { messageDataSource.confirmPending(chatId, any(), arrived, true) }
    }

    @Test
    fun `an unstored entry whose file is gone is dropped`() = runTest {
        val (delegate, _) = delegate()
        records["aa"] = record("aa", stored = null)
        delegate.reconcilePendingMedia()
        runCurrent()
        assertTrue(records.isEmpty())
    }

    // endregion

    private suspend fun MediaSendDelegate.progressOf(hex: String): MediaSendProgress? =
        observeMediaSendProgress().first()[hex]
}
