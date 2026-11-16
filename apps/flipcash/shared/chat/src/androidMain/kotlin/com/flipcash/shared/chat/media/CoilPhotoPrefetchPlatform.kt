package com.flipcash.shared.chat.media

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.size.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * [PhotoPrefetchPlatform] over the system's connectivity state and the singleton Coil loader, whose
 * [ChatPhotoFetcher] writes the opened bytes to the disk cache the bubble reads.
 */
internal class CoilPhotoPrefetchPlatform @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PhotoPrefetchPlatform {

    override fun network(): PrefetchNetwork {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
            ?: return PrefetchNetwork(connected = false, dataSaver = false)
        val capabilities = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
        return PrefetchNetwork(
            connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            dataSaver = connectivity.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED,
        )
    }

    override suspend fun download(photo: ChatPhoto): PhotoPrefetchResult {
        val loader = SingletonImageLoader.get(context)
        val request = ImageRequest.Builder(context)
            .data(photo)
            // Only the disk entry is wanted. A 1px inexact decode keeps the decode Coil runs after
            // every fetch cheap, and nothing it produces is kept in memory.
            .size(Size(1, 1))
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        return when (val result = loader.enqueue(request).job.await()) {
            is SuccessResult -> PhotoPrefetchResult.Cached(
                bytes = loader.diskCache?.let { cache ->
                    cache.openSnapshot(photo.cacheKey)?.use { cache.fileSystem.metadataOrNull(it.data)?.size }
                },
            )
            is ErrorResult -> PhotoPrefetchResult.Failed(result.throwable.reason())
        }
    }

    /** The failure's class, and the open failure or download cause when there is one. Never a message. */
    private fun Throwable.reason(): String = when (this) {
        is ChatPhotoUnavailable.Open -> "Open(${reason})"
        is ChatPhotoUnavailable.Download -> "Download(${cause?.let { it::class.simpleName } ?: "none"})"
        else -> this::class.simpleName ?: "unknown"
    }
}
