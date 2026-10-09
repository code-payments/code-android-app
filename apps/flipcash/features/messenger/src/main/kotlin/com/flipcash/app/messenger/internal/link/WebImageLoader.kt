package com.flipcash.app.messenger.internal.link

import android.content.Context
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.network.CacheStrategy
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.request.Options
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File

/** The most the preview pictures may take on disk; Coil drops the least recently used past it. */
internal const val WEB_PREVIEW_DISK_BYTES = 20L * 1024 * 1024

/**
 * The preview pictures' own store, next to and apart from the app loader's `image_cache`, so a
 * picture of an outside page never sits in a store the rest of the app reads.
 */
internal fun webPreviewDiskCache(context: Context): DiskCache =
    DiskCache.Builder()
        .directory(File(context.cacheDir, "web_preview_images").toOkioPath())
        .maxSizeBytes(WEB_PREVIEW_DISK_BYTES)
        .build()

/**
 * The loader for a preview's picture, on [client] from [webImageClient] and nothing else: it shares
 * neither the app's loader nor that loader's client, which keeps cookies and follows any redirect.
 *
 * Pictures are kept in [diskCache], a store of the preview's own, under [WebImageCacheStrategy].
 */
internal fun webPreviewImageLoader(
    context: Context,
    client: OkHttpClient,
    diskCache: DiskCache = webPreviewDiskCache(context),
): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { client }, cacheStrategy = { WebImageCacheStrategy })) }
        .diskCache(diskCache)
        .build()

/**
 * What goes to and comes from the preview store. A stored picture is always read, with no check of
 * its age or headers: its row decides how long it is wanted, and the picture goes when the row does.
 * Only a 2xx answer is written. Coil's default also keeps a 301, 404, 410 and a few more, for a
 * response cache to replay; a preview has no use for a stored failure.
 */
@OptIn(ExperimentalCoilApi::class)
internal object WebImageCacheStrategy : CacheStrategy {
    override suspend fun read(
        cacheResponse: NetworkResponse,
        networkRequest: NetworkRequest,
        options: Options,
    ) = CacheStrategy.ReadResult(cacheResponse)

    override suspend fun write(
        cacheResponse: NetworkResponse?,
        networkRequest: NetworkRequest,
        networkResponse: NetworkResponse,
        options: Options,
    ) = if (networkResponse.code in 200 until 300) {
        CacheStrategy.WriteResult(networkResponse)
    } else {
        CacheStrategy.WriteResult.DISABLED
    }
}
