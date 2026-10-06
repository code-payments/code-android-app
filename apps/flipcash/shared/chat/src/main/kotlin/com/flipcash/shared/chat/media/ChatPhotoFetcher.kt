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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.buffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keys a [ChatPhoto] by its blob id, so the memory cache never keys on a signed URL that rotates
 * on every mint.
 */
class ChatPhotoKeyer : Keyer<ChatPhoto> {
    override fun key(data: ChatPhoto, options: Options): String = data.cacheKey
}

/**
 * Loads a [ChatPhoto]: the disk cache by blob id first, else a download from a resolved (and, if
 * need be, re-minted) URL, opened when the message is sealed, then cached.
 *
 * Failures surface as [ChatPhotoUnavailable] so a UI can draw "This photo can't be displayed"
 * for any of them; a [ChatPhotoUnavailable.Open] carrying [BlobOpenFailure.KeyPending] may succeed
 * on a later attempt. Decode failures of opened bytes surface as Coil's own decode error and
 * are, to the UI, the same as [BlobOpenFailure.Undecodable].
 */
class ChatPhotoFetcher(
    private val photo: ChatPhoto,
    private val loader: ChatPhotoLoader,
    private val http: OkHttpClient,
    private val imageLoader: ImageLoader,
) : Fetcher {

    override suspend fun fetch(): FetchResult = try {
        load()
    } catch (e: ChatPhotoUnavailable) {
        trace(tag = TAG, message = "Chat photo ${photo.cacheKey} unavailable: ${e.message}", type = TraceType.Log)
        throw e
    }

    private suspend fun load(): FetchResult {
        if (photo.redacted) throw ChatPhotoUnavailable.Redacted()
        val cache = imageLoader.diskCache
        val key = photo.cacheKey
        cache?.openSnapshot(key)?.let { return it.asResult() }

        var url = loader.resolveUrl(photo) ?: throw ChatPhotoUnavailable.NotFound()
        var bytes = download(url)
        if (bytes == null) {
            // The signed URL can die before its stated expiry; one re-mint is worth trying.
            url = loader.resolveUrl(photo, failedUrl = url) ?: throw ChatPhotoUnavailable.NotFound()
            bytes = download(url) ?: throw ChatPhotoUnavailable.Download(null)
        }
        val opened = loader.open(photo, bytes)

        if (cache != null) {
            cache.write(key, opened)?.let { return it.asResult() }
        }
        val buffer = Buffer().write(opened)
        return SourceFetchResult(
            source = ImageSource(buffer, imageLoader.diskCacheFileSystem()),
            mimeType = "image/jpeg",
            dataSource = DataSource.NETWORK,
        )
    }

    private suspend fun download(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        }.getOrNull()
    }

    private fun DiskCache.write(key: String, bytes: ByteArray): DiskCache.Snapshot? {
        val editor = openEditor(key) ?: return openSnapshot(key)
        return try {
            fileSystem.sink(editor.data).buffer().use { it.write(bytes) }
            editor.commitAndOpenSnapshot()
        } catch (t: Throwable) {
            runCatching { editor.abort() }
            null
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
    }

    @Singleton
    class Factory @Inject constructor(
        private val loader: ChatPhotoLoader,
    ) : Fetcher.Factory<ChatPhoto> {
        private val http by lazy { OkHttpClient() }

        override fun create(data: ChatPhoto, options: Options, imageLoader: ImageLoader): Fetcher =
            ChatPhotoFetcher(data, loader, http, imageLoader)
    }
}
