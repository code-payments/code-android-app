package com.flipcash.shared.chat.media

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import com.flipcash.services.controllers.BlobStorageController
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ImageMetadata
import com.flipcash.services.models.chat.MediaItemRendition
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.FileSystem
import okio.Path.Companion.toPath
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.Volatile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Loads of the same photo at once: a bubble's and its prefetch's, which both run [ChatPhotoFetcher]. */
class ChatPhotoSharedDownloadTest {
    private val chatId = ChatId(byteArrayOf(1))
    private val now = Instant.fromEpochSeconds(10_000)
    private val storage = mockk<BlobStorageController>()
    private val loader = ChatPhotoLoader(storage, mockk(), mockk(), mockk(), mockk(), mockk()) { now }

    /** Every request the CDN sees, and a gate each one waits at until the test opens it. */
    private val calls = AtomicInteger()
    private val entered = Channel<Unit>(Channel.UNLIMITED)
    private val gate = CountDownLatch(1)

    @Volatile
    private var status = 200
    private val http = OkHttpClient.Builder()
        .addInterceptor { chain ->
            calls.incrementAndGet()
            entered.trySend(Unit)
            gate.await(5, TimeUnit.SECONDS)
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_2)
                .code(status)
                .message("")
                .body(byteArrayOf(1, 2, 3).toResponseBody())
                .build()
        }
        .build()

    private val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "chat-photo-shared-${System.nanoTime()}".toPath()
    private val diskCache = DiskCache.Builder().directory(directory).maxSizeBytes(1024L * 1024).build()
    private val cached = mockk<ImageLoader> { every { diskCache } returns this@ChatPhotoSharedDownloadTest.diskCache }
    private val noCache = mockk<ImageLoader> { every { diskCache } returns null }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloads = ChatPhotoDownloads(scope)
    private val reports = mutableListOf<ChatPhotoLoadTrace>()

    @AfterTest
    fun cleanUp() {
        gate.countDown()
        scope.cancel()
        diskCache.shutdown()
        FileSystem.SYSTEM.deleteRecursively(directory)
    }

    /** A photo whose rendition is blob [blob], with a stored URL unless [stored] is false. */
    private fun photo(blob: Byte = 0x0a, stored: Boolean = true) = ChatPhoto(
        chatId,
        MediaItemRendition(
            MediaItemRendition.Role.DISPLAY,
            BlobId(byteArrayOf(blob)),
            BlobMetadata(
                mimeType = "image/jpeg",
                sizeBytes = 3,
                downloadUrl = "https://cdn.example/chat/$blob",
                expiresAtMillis = (now + 1.hours).toEpochMilliseconds(),
                image = ImageMetadata(1600, 1200, ""),
            ).takeIf { stored },
        ),
        senderId = null,
        sealed = false,
    )

    private fun fetcher(photo: ChatPhoto, imageLoader: ImageLoader = cached) =
        ChatPhotoFetcher(photo, loader, downloads, http, imageLoader) { reports += it }

    @Test
    fun `two loads of one photo at once make one download`() = runTest(timeout = 10.seconds) {
        val bubble = async { fetcher(photo()).fetch() }
        entered.receive()
        val prefetch = async { fetcher(photo()).fetch() }
        runCurrent()
        gate.countDown()

        val results = listOf(bubble.await(), prefetch.await()).map { it as SourceFetchResult }

        assertEquals(1, calls.get())
        results.forEach { result ->
            assertEquals(DataSource.DISK, result.dataSource)
            assertContentEquals(byteArrayOf(1, 2, 3), result.source.use { it.source().readByteArray() })
        }
        val (started, joined) = reports
        assertFalse(started.joined)
        assertTrue(joined.joined)
        assertEquals(listOf(200, 200), reports.map { it.attempts.single().status })
        assertTrue(" cache=miss joined=true | try1 url=stored host=cdn.example" in joined.message())
    }

    @Test
    fun `cancelling the load that started a download leaves it running for the other`() = runTest(timeout = 10.seconds) {
        val bubble = async { fetcher(photo()).fetch() }
        entered.receive()
        val prefetch = async { fetcher(photo()).fetch() }
        runCurrent()

        bubble.cancelAndJoin()
        gate.countDown()
        val result = prefetch.await() as SourceFetchResult

        assertEquals(1, calls.get())
        assertEquals(DataSource.DISK, result.dataSource)
        result.source.close()
        val (cancelled, finished) = reports
        assertEquals(ChatPhotoLoadTrace.Outcome.Cancelled(ChatPhotoLoadTrace.Step.Download), cancelled.outcome)
        assertTrue(cancelled.attempts.isEmpty())
        assertEquals(ChatPhotoLoadTrace.Outcome.Ok, finished.outcome)
        assertEquals(200, finished.attempts.single().status)
    }

    @Test
    fun `cancelling the only load cancels its download`() = runTest(timeout = 10.seconds) {
        val minting = CompletableDeferred<Unit>()
        val mintCancelled = CompletableDeferred<Unit>()
        coEvery { storage.refreshMetadata(any(), any()) } coAnswers {
            minting.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                mintCancelled.complete(Unit)
            }
        }
        val bubble = async { fetcher(photo(stored = false)).fetch() }
        minting.await()

        bubble.cancelAndJoin()

        assertTrue(mintCancelled.isCompleted)
        assertEquals(ChatPhotoLoadTrace.Outcome.Cancelled(ChatPhotoLoadTrace.Step.Url), reports.single().outcome)
    }

    @Test
    fun `a load after a failed download downloads again`() = runTest(timeout = 10.seconds) {
        gate.countDown()
        coEvery { storage.refreshMetadata(any(), any()) } returns Result.failure(IllegalStateException("down"))
        status = 403
        assertFailsWith<ChatPhotoUnavailable.NotFound> { fetcher(photo()).fetch() }

        status = 200
        (fetcher(photo()).fetch() as SourceFetchResult).source.close()

        assertEquals(2, calls.get())
        assertEquals(ChatPhotoLoadTrace.Outcome.Ok, reports.last().outcome)
    }

    @Test
    fun `a load after a download ended downloads again when there is no disk cache`() = runTest(timeout = 10.seconds) {
        gate.countDown()

        fetcher(photo(), noCache).fetch()
        fetcher(photo(), noCache).fetch()

        assertEquals(2, calls.get())
    }

    @Test
    fun `another rendition downloads on its own`() = runTest(timeout = 10.seconds) {
        val display = async { fetcher(photo(blob = 0x0a)).fetch() }
        val thumbnail = async { fetcher(photo(blob = 0x0b)).fetch() }
        entered.receive()
        entered.receive()
        gate.countDown()

        listOf(display, thumbnail).awaitAll().forEach { (it as SourceFetchResult).source.close() }

        assertEquals(2, calls.get())
    }
}
