package com.flipcash.desktop

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath

/**
 * Desktop counterpart of `FlipcashApp.newImageLoader`. Coil 3 has no network fetcher unless one is
 * added, so without this every token logo (an https URL) fails with "Unable to create a fetcher".
 * The chat photo keyer/fetcher are left out: chat is not on desktop.
 */
internal fun installImageLoader() {
    SingletonImageLoader.setSafe { context -> newImageLoader(context) }
}

private fun newImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context)
        .crossfade(true)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCache {
            DiskCache.Builder()
                .directory(DesktopModule.appDir.resolve("image_cache").toOkioPath())
                .maxSizeBytes(50L * 1024 * 1024)
                .build()
        }
        .components { add(OkHttpNetworkFetcherFactory()) }
        .build()
