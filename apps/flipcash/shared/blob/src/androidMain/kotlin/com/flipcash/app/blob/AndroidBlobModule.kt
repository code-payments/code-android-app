// kmp-audit: a Hilt module that binds the Android implementations of the blob interfaces.
package com.flipcash.app.blob

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AndroidBlobModule {
    @Binds
    @Singleton
    abstract fun bindImageCodec(impl: AndroidImageCodec): ImageCodec

    @Binds
    @Singleton
    abstract fun bindUploadImageCache(impl: AndroidUploadImageCache): UploadImageCache

    @Binds
    @Singleton
    abstract fun bindPickedPhotos(impl: ContentResolverPickedPhotos): PickedPhotos
}
