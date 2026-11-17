package com.flipcash.shared.chat.media

import coil3.ImageLoader
import coil3.disk.DiskCache
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.chat.BlobOpenFailure
import com.flipcash.services.chat.ChatContentCrypto
import com.flipcash.services.chat.GroupContentCrypto
import com.flipcash.services.chat.OpenedBlob
import com.flipcash.services.controllers.BlobStorageController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ImageMetadata
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.services.user.UserManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toPath
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.time.TestTimeSource

class ChatPhotoFetcherTest {
    private val chatId = ChatId(byteArrayOf(1))
    private val blobId = BlobId(byteArrayOf(0x0a, 0x0b))
    private val self = listOf<Byte>(1)
    private val peer = listOf<Byte>(2)
    private val now = Instant.fromEpochSeconds(10_000)
    private val signedUrl = "https://d1.cloudfront.net/chat/0a0b?Expires=1&Signature=s3cr3t&Key-Pair-Id=K1"

    private val time = TestTimeSource()
    private val storage = mockk<BlobStorageController>()
    private val crypto = mockk<ChatContentCrypto>()
    private val user = mockk<UserManager> { every { accountId } returns self }
    private val members = mockk<ChatMemberDataSource> {
        coEvery { getMembersForChat(chatId) } returns listOf(self, peer).map {
            ChatMember(it, UserProfile(displayName = "n", socialAccounts = emptyList(), phoneNumber = null, email = null), emptyList())
        }
    }
    private val metadata = mockk<ChatMetadataDataSource> { coEvery { privacyOf(any()) } returns false }
    private val loader = ChatPhotoLoader(storage, crypto, user, members, mockk<GroupContentCrypto>(), metadata) { now }

    private val responses = ArrayDeque<(Request) -> Response>()
    private val http = OkHttpClient.Builder()
        .addInterceptor { chain -> responses.removeFirst()(chain.request()) }
        .build()
    private val noCache = mockk<ImageLoader> { every { diskCache } returns null }
    private val reports = mutableListOf<ChatPhotoLoadTrace>()

    private fun blob(url: String) = BlobMetadata(
        mimeType = "image/jpeg", sizeBytes = 3, downloadUrl = url, expiresAtMillis = (now + 1.hours).toEpochMilliseconds(),
        image = ImageMetadata(1600, 1200, ""),
    )

    private fun photo(url: String?, sealed: Boolean = false) = ChatPhoto(
        chatId, MediaItemRendition(MediaItemRendition.Role.DISPLAY, blobId, url?.let(::blob)),
        senderId = peer, sealed = sealed,
    )

    private fun fetcher(photo: ChatPhoto, imageLoader: ImageLoader = noCache) =
        ChatPhotoFetcher(photo, loader, http, imageLoader, time) { reports += it }

    /** A CDN answer whose headers take [ttfb] and whose body takes [transfer] to read. */
    private fun cdn(
        status: Int = 200,
        ttfb: Duration = 200.milliseconds,
        transfer: Duration = 300.milliseconds,
        xCache: String = "Miss from cloudfront",
        pop: String = "ORD58-P16",
    ): (Request) -> Response = { request ->
        time += ttfb
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(status)
            .message("")
            .header("X-Cache", xCache)
            .header("X-Amz-Cf-Pop", pop)
            .body(SlowBody(byteArrayOf(1, 2, 3), transfer))
            .build()
    }

    private fun timeout(): (Request) -> Response = { throw SocketTimeoutException("timeout") }

    private inner class SlowBody(private val bytes: ByteArray, private val transfer: Duration) : ResponseBody() {
        private val source by lazy {
            time += transfer
            Buffer().write(bytes)
        }

        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = bytes.size.toLong()
        override fun source(): BufferedSource = source
    }

    @Test
    fun `a download records the first byte, the transfer and the edge that served it`() = runTest {
        responses += cdn()

        fetcher(photo(signedUrl)).fetch()

        val record = reports.single()
        assertEquals(ChatPhotoLoadTrace.Outcome.Ok, record.outcome)
        assertFalse(record.cacheHit)
        assertEquals(MediaItemRendition.Role.DISPLAY, record.role)
        assertEquals(1600, record.width)
        val attempt = record.attempts.single()
        assertNull(attempt.mint)
        assertEquals("d1.cloudfront.net", attempt.host)
        assertEquals(200.milliseconds, attempt.ttfb)
        assertEquals(300.milliseconds, attempt.transfer)
        assertEquals(200, attempt.status)
        assertEquals(3, attempt.bytes)
        assertEquals("Miss from cloudfront", attempt.xCache)
        assertEquals("ORD58-P16", attempt.pop)
        assertNull(record.decrypt)
        assertEquals(500.milliseconds, record.total)
    }

    @Test
    fun `the trace line has the host and never the signed query`() = runTest {
        responses += cdn()

        fetcher(photo(signedUrl)).fetch()

        val line = reports.single().message()
        assertEquals(
            "Chat photo chat-media-0a0b outcome=ok total=500ms role=DISPLAY width=1600 sealed=false cache=miss" +
                " | try1 url=stored host=d1.cloudfront.net ttfb=200ms transfer=300ms status=200 bytes=3" +
                " x-cache=\"Miss from cloudfront\" pop=ORD58-P16",
            line,
        )
        assertFalse("Signature" in line || "s3cr3t" in line || "Key-Pair-Id" in line)
    }

    @Test
    fun `a minted url records how long GetBlobs took`() = runTest {
        coEvery { storage.refreshMetadata(listOf(blobId), any()) } coAnswers {
            time += 150.milliseconds
            Result.success(mapOf(photo(null).rendition.cacheKey to blob(signedUrl)))
        }
        responses += cdn()

        fetcher(photo(null)).fetch()

        assertEquals(150.milliseconds, reports.single().attempts.single().mint)
    }

    @Test
    fun `a refused download keeps its status and the re-minted try`() = runTest {
        coEvery { storage.refreshMetadata(listOf(blobId), any()) } returns
            Result.success(mapOf(photo(null).rendition.cacheKey to blob("https://d1.cloudfront.net/fresh")))
        responses += cdn(status = 403, xCache = "Error from cloudfront")
        responses += cdn()

        fetcher(photo(signedUrl)).fetch()

        val record = reports.single()
        assertEquals(ChatPhotoLoadTrace.Outcome.Ok, record.outcome)
        val (first, second) = record.attempts
        assertEquals(403, first.status)
        assertEquals("Error from cloudfront", first.xCache)
        assertEquals("ChatPhotoHttpException", first.error)
        assertNull(first.transfer)
        assertEquals(200, second.status)
        assertEquals(3, second.bytes)
    }

    @Test
    fun `a download refused twice surfaces the status as its cause`() = runTest {
        coEvery { storage.refreshMetadata(listOf(blobId), any()) } returns
            Result.success(mapOf(photo(null).rendition.cacheKey to blob("https://d1.cloudfront.net/fresh")))
        responses += cdn(status = 403)
        responses += cdn(status = 403)

        val failure = assertFailsWith<ChatPhotoUnavailable.Download> { fetcher(photo(signedUrl)).fetch() }

        assertEquals(403, assertIs<ChatPhotoHttpException>(failure.cause).status)
        assertEquals(
            ChatPhotoLoadTrace.Outcome.Failed(ChatPhotoLoadTrace.Step.Download, "ChatPhotoHttpException"),
            reports.single().outcome,
        )
    }

    @Test
    fun `a network error is kept as the cause`() = runTest {
        coEvery { storage.refreshMetadata(listOf(blobId), any()) } returns
            Result.success(mapOf(photo(null).rendition.cacheKey to blob("https://d1.cloudfront.net/fresh")))
        responses += timeout()
        responses += timeout()

        val failure = assertFailsWith<ChatPhotoUnavailable.Download> { fetcher(photo(signedUrl)).fetch() }

        assertIs<SocketTimeoutException>(failure.cause)
        val record = reports.single()
        assertEquals(ChatPhotoLoadTrace.Outcome.Failed(ChatPhotoLoadTrace.Step.Download, "SocketTimeoutException"), record.outcome)
        assertEquals(listOf("SocketTimeoutException", "SocketTimeoutException"), record.attempts.map { it.error })
        assertNull(record.attempts.first().status)
    }

    @Test
    fun `a failed mint is kept as the cause`() = runTest {
        val down = IllegalStateException("UNAVAILABLE: fc-v2")
        coEvery { storage.refreshMetadata(listOf(blobId), any()) } returns Result.failure(down)

        val failure = assertFailsWith<ChatPhotoUnavailable.NotFound> { fetcher(photo(null)).fetch() }

        assertEquals(down, failure.cause)
        val record = reports.single()
        assertEquals(ChatPhotoLoadTrace.Outcome.Failed(ChatPhotoLoadTrace.Step.Url, "IllegalStateException"), record.outcome)
        assertFalse("fc-v2" in record.message())
    }

    @Test
    fun `a sealed photo records its decrypt`() = runTest {
        coEvery { crypto.openBlob(chatId, self, peer, peer, blobId.bytes, 3, any()) } coAnswers {
            time += 40.milliseconds
            OpenedBlob.Plaintext(byteArrayOf(7, 8, 9))
        }
        responses += cdn()

        fetcher(photo(signedUrl, sealed = true)).fetch()

        val record = reports.single()
        assertTrue(record.sealed)
        assertEquals(40.milliseconds, record.decrypt)
        assertEquals(540.milliseconds, record.total)
    }

    @Test
    fun `a sealed photo that does not open says so at the decrypt step`() = runTest {
        coEvery { crypto.openBlob(chatId, self, peer, peer, blobId.bytes, 3, any()) } returns
            OpenedBlob.Failed(BlobOpenFailure.Authentication)
        responses += cdn()

        assertFailsWith<ChatPhotoUnavailable.Open> { fetcher(photo(signedUrl, sealed = true)).fetch() }

        assertEquals(
            ChatPhotoLoadTrace.Outcome.Failed(ChatPhotoLoadTrace.Step.Decrypt, "Open(Authentication)"),
            reports.single().outcome,
        )
    }

    @Test
    fun `a cancel says which step it interrupted`() = runTest {
        coEvery { storage.refreshMetadata(listOf(blobId), any()) } throws CancellationException("left the chat")

        assertFailsWith<CancellationException> { fetcher(photo(null)).fetch() }

        assertEquals(ChatPhotoLoadTrace.Outcome.Cancelled(ChatPhotoLoadTrace.Step.Url), reports.single().outcome)
    }

    @Test
    fun `a disk cache hit records the hit and nothing else`() = runTest {
        val snapshot = mockk<DiskCache.Snapshot>(relaxed = true) { every { data } returns "/cache/chat-media-0a0b".toPath() }
        val cache = mockk<DiskCache> {
            every { openSnapshot("chat-media-0a0b") } returns snapshot
            every { fileSystem } returns FileSystem.SYSTEM
        }
        val cached = mockk<ImageLoader> { every { diskCache } returns cache }

        fetcher(photo(signedUrl), cached).fetch()

        val record = reports.single()
        assertTrue(record.cacheHit)
        assertEquals(ChatPhotoLoadTrace.Outcome.Ok, record.outcome)
        assertTrue(record.attempts.isEmpty())
    }
}
