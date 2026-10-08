package com.flipcash.app.messenger.internal.link

import android.content.Context
import coil3.ImageLoader
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.persistence.sources.LinkPreviewDataSource
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.MessageLinkPrefetch
import com.getcode.util.resources.ResourceHelper
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import kotlinx.coroutines.runBlocking
import dagger.hilt.android.qualifiers.ApplicationContext
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

/**
 * The process-wide half of link cards: the answers kept between visits, and the prefetch that
 * fills them as messages arrive. The per-visit half is [LinkCardModule].
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class LinkCardMemoryModule {

    @Binds
    abstract fun bindLinkCardMemory(impl: PersistedLinkCardMemory): LinkCardMemory

    @Binds
    abstract fun bindMessageLinkPrefetch(impl: MessageLinkPrefetcher): MessageLinkPrefetch

    companion object {
        @Provides
        @Singleton
        fun providePersistedLinkCardMemory(
            store: LinkPreviewDataSource,
            userManager: UserManager,
            resources: ResourceHelper,
            dispatchers: DispatcherProvider,
        ): PersistedLinkCardMemory = PersistedLinkCardMemory(store, userManager, resources, dispatchers)

        // Not the app's singleton client: it logs bodies at Level.BODY and keeps cookies.
        @Provides
        @Singleton
        @WebPreview
        fun provideWebPreviewClient(): OkHttpClient = webPreviewClient()

        @Provides
        @Singleton
        @WebPreviewImages
        fun provideWebImageClient(flags: FeatureFlagController): OkHttpClient =
            // The interceptor runs on OkHttp's own thread, where blocking for a flag read is fine.
            webImageClient(enabled = { runBlocking { flags.get(FeatureFlag.WebLinkPreviews) } })

        @Provides
        @Singleton
        @WebPreviewImages
        fun provideWebPreviewImageLoader(
            @ApplicationContext context: Context,
            @WebPreviewImages client: OkHttpClient,
        ): ImageLoader = webPreviewImageLoader(context, client)

        @Provides
        @Singleton
        fun provideWebLinkLookup(
            @WebPreview client: OkHttpClient,
            flags: FeatureFlagController,
            dispatchers: DispatcherProvider,
        ): WebLinkLookup = WebLinkLookup(
            client = client,
            enabled = { flags.get(FeatureFlag.WebLinkPreviews) },
            dispatchers = dispatchers,
        )

        @Provides
        @Singleton
        fun provideMessageLinkPrefetcher(
            classifier: LinkCardClassifier,
            memory: LinkCardMemory,
            group: GroupLinkLookup,
            user: UserLinkLookup,
            web: WebLinkLookup,
            flags: FeatureFlagController,
            dispatchers: DispatcherProvider,
        ): MessageLinkPrefetcher = MessageLinkPrefetcher(
            classifier = classifier,
            memory = memory,
            group = { group(it) },
            user = { user(it) },
            web = { web(it) },
            dispatchers = dispatchers,
            webEnabled = { flags.get(FeatureFlag.WebLinkPreviews) },
        )
    }
}
