package com.flipcash.shared.chat.media

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.flipcash.services.chat.BlobOpenFailure
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.buffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.TimeSource

/**
 * Keys a [ChatPhoto] by its blob id, so the memory cache never keys on a signed URL that rotates
 * on every mint.
 */
class ChatPhotoKeyer : Keyer<ChatPhoto> {
    override fun key(data: ChatPhoto, options: Options): String = data.cacheKey
}

/**
 * Loads a [ChatPhoto]: the disk cache by blob id first, else a download from a resolved (and, if
 * need be, re-minted) URL, opened when the message is sealed, then cached. Loads of one photo at once,
 * such as its bubble's and its prefetch's, share that download through [downloads].
 *
 * Failures surface as [ChatPhotoUnavailable] so a UI can draw "This photo can't be displayed"
 * for any of them; a [ChatPhotoUnavailable.Open] carrying [BlobOpenFailure.KeyPending] may succeed
 * on a later attempt. Decode failures of opened bytes surface as Coil's own decode error and
 * are, to the UI, the same as [BlobOpenFailure.Undecodable].
 *
 * Each load reports one [ChatPhotoLoadTrace] with the time spent in each step.
 */
class ChatPhotoFetcher internal constructor(
    private val photo: ChatPhoto,
    private val loader: ChatPhotoLoader,
    private val downloads: ChatPhotoDownloads,
    private val http: OkHttpClient,
    private val imageLoader: ImageLoader,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val report: (ChatPhotoLoadTrace) -> Unit = ::emit,
) : Fetcher {

    private val record = ChatPhotoLoadTrace(photo)

    override suspend fun fetch(): FetchResult {
        val started = timeSource.markNow()
        return try {
            load().also { record.outcome = ChatPhotoLoadTrace.Outcome.Ok }
        } catch (e: Throwable) {
            record.fail(e, cancelled = e is CancellationException)
            throw e
        } finally {
            record.total = started.elapsedNow()
            report(record)
        }
    }

    private suspend fun load(): FetchResult {
        if (photo.redacted) throw ChatPhotoUnavailable.Redacted()
        val cache = imageLoader.diskCache
        val key = photo.cacheKey
        cache?.openSnapshot(key)?.let {
            record.cacheHit = true
            return it.asResult()
        }

        val opened = downloads.await(photo, record) { steps -> downloadAndOpen(steps, cache) }
        cache?.openSnapshot(key)?.let { return it.asResult() }
        val buffer = Buffer().write(opened)
        return SourceFetchResult(
            source = ImageSource(buffer, imageLoader.diskCacheFileSystem()),
            mimeType = "image/jpeg",
            dataSource = DataSource.NETWORK,
        )
    }

    /**
     * Downloads and opens the photo, then writes it to [cache], recording each step on [steps]: the
     * shared download's trace, which outlives this load when it is cancelled while another waits.
     */
    private suspend fun downloadAndOpen(steps: ChatPhotoLoadTrace, cache: DiskCache?): ByteArray {
        trace(
            tag = TAG,
            message = "Chat photo ${photo.cacheKey} downloading ${photo.rendition.role} (${photo.rendition.blob?.sizeBytes} bytes)",
            type = TraceType.Silent,
        )
        var url = resolveUrl(steps, failedUrl = null)
        val bytes = download(steps, url).getOrElse {
            // The signed URL can die before its stated expiry; one re-mint is worth trying.
            url = resolveUrl(steps, failedUrl = url)
            download(steps, url).getOrElse { throw ChatPhotoUnavailable.Download(it) }
        }
        steps.step = ChatPhotoLoadTrace.Step.Decrypt
        val opened = if (photo.sealed) {
            val decryptStarted = timeSource.markNow()
            loader.open(photo, bytes).also { steps.decrypt = decryptStarted.elapsedNow() }
        } else {
            loader.open(photo, bytes)
        }
        cache?.write(photo.cacheKey, opened)
        return opened
    }

    /** The URL to download, minting one when the stored URL is missing, expired or [failedUrl]. */
    private suspend fun resolveUrl(steps: ChatPhotoLoadTrace, failedUrl: String?): String {
        steps.step = ChatPhotoLoadTrace.Step.Url
        val attempt = steps.newAttempt()
        val started = timeSource.markNow()
        val resolution = loader.resolve(photo, failedUrl)
        if (resolution.minted) attempt.mint = started.elapsedNow()
        return resolution.url ?: throw ChatPhotoUnavailable.NotFound(resolution.failure)
    }

    /**
     * Downloads [url] into the latest attempt's timings. The call blocks its thread, so a cancel
     * only lands once it returns, and the last load to cancel still records the whole download.
     */
    private suspend fun download(steps: ChatPhotoLoadTrace, url: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        steps.step = ChatPhotoLoadTrace.Step.Download
        val attempt = steps.attempts.last()
        try {
            val request = Request.Builder().url(url).build()
            attempt.host = request.url.host
            val started = timeSource.markNow()
            http.newCall(request).execute().use { response ->
                attempt.ttfb = started.elapsedNow()
                attempt.status = response.code
                attempt.xCache = response.header("X-Cache")
                attempt.pop = response.header("X-Amz-Cf-Pop")
                if (!response.isSuccessful) throw ChatPhotoHttpException(response.code)
                val transferStarted = timeSource.markNow()
                val bytes = response.body.bytes()
                attempt.transfer = transferStarted.elapsedNow()
                attempt.bytes = bytes.size
                Result.success(bytes)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            attempt.error = e.failureName()
            Result.failure(e)
        }
    }

    /** Each load opens its own snapshot afterwards; none is made when another write holds [key]. */
    private fun DiskCache.write(key: String, bytes: ByteArray) {
        val editor = openEditor(key) ?: return
        try {
            fileSystem.sink(editor.data).buffer().use { it.write(bytes) }
            editor.commit()
        } catch (t: Throwable) {
            runCatching { editor.abort() }
        }
    }

    private fun DiskCache.Snapshot.asResult() = SourceFetchResult(
        source = ImageSource(file = data, fileSystem = imageLoader.diskCacheFileSystem(), diskCacheKey = photo.cacheKey, closeable = this),
        mimeType = "image/jpeg",
        dataSource = DataSource.DISK,
    )

    private fun ImageLoader.diskCacheFileSystem() = diskCache?.fileSystem ?: okio.FileSystem.SYSTEM

    private companion object {
        const val TAG = "ChatPhotoFetcher"

        /** Network loads go to breadcrumbs too; a disk cache hit only to the local log file. */
        fun emit(record: ChatPhotoLoadTrace) {
            trace(tag = TAG, message = record.message(), type = if (record.cacheHit) TraceType.Silent else TraceType.Log)
        }
    }

    @Singleton
    class Factory @Inject constructor(
        private val loader: ChatPhotoLoader,
    ) : Fetcher.Factory<ChatPhoto> {
        private val http by lazy { OkHttpClient() }

        /** One for every load the image loader runs, so the bubble and the prefetch of a photo share its download. */
        private val downloads = ChatPhotoDownloads(CoroutineScope(SupervisorJob() + Dispatchers.IO))

        override fun create(data: ChatPhoto, options: Options, imageLoader: ImageLoader): Fetcher =
            ChatPhotoFetcher(data, loader, downloads, http, imageLoader)
    }
}
