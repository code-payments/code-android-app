package com.flipcash.app.messenger.internal.link

import com.flipcash.libs.coroutines.DispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

/** The client for fetching outside pages: no cookies, public addresses only, bodies not logged. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class WebPreview

/** The client for the picture a page names: the page rules plus a cap on the body, see [webImageClient]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class WebPreviewImages

/** The guarded clients and lookup the sender's link preview is built with. */
@Module
@InstallIn(SingletonComponent::class)
internal object WebPreviewModule {

    // Not the app's singleton client: it logs bodies at Level.BODY and keeps cookies.
    @Provides
    @Singleton
    @WebPreview
    fun provideWebPreviewClient(): OkHttpClient = webPreviewClient()

    @Provides
    @Singleton
    @WebPreviewImages
    fun provideWebImageClient(): OkHttpClient = webImageClient()

    @Provides
    @Singleton
    fun provideWebLinkLookup(
        @WebPreview client: OkHttpClient,
        dispatchers: DispatcherProvider,
    ): WebLinkLookup = WebLinkLookup(client = client, dispatchers = dispatchers)
}
