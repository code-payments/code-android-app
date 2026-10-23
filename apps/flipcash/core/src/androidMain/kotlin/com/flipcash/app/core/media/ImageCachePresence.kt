package com.flipcash.app.core.media

import android.content.Context
import coil3.SingletonImageLoader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the image loader already holds the bytes filed under a stable cache key. Blob renditions
 * are cached by their durable blob id rather than their download URL, so a hit here means the URL
 * will never be dereferenced — and an expired one costs nothing.
 */
interface ImageCachePresence {
    suspend fun holds(cacheKey: String): Boolean
}

@Singleton
class CoilImageCachePresence @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ImageCachePresence {

    override suspend fun holds(cacheKey: String): Boolean = withContext(Dispatchers.IO) {
        val loader = SingletonImageLoader.get(context)
        if (loader.memoryCache?.keys.orEmpty().any { it.key == cacheKey }) {
            return@withContext true
        }
        // openSnapshot takes a read lock on the entry; close it immediately — we only wanted to
        // know it exists, the actual read happens inside Coil's own fetcher.
        loader.diskCache?.openSnapshot(cacheKey)?.use { true } ?: false
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ImageCachePresenceModule {
    @Binds
    abstract fun bindImageCachePresence(impl: CoilImageCachePresence): ImageCachePresence
}
