// kmp-audit: a Dagger module that binds the desktop implementations of the blob interfaces.
package com.flipcash.app.blob

import dagger.Binds
import dagger.Module
import javax.inject.Singleton

/**
 * The desktop bindings for [ImageCodec]. [UploadImageCache] and [PickedPhotos] depend on where the
 * app keeps its cache, so the app's own module provides [JvmUploadImageCache] and [FilePickedPhotos].
 */
@Module
abstract class JvmBlobModule {
    @Binds
    @Singleton
    abstract fun bindImageCodec(impl: SkiaImageCodec): ImageCodec
}
