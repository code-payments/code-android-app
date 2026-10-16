package com.flipcash.app.messenger.internal.link

import com.flipcash.app.persistence.sources.LinkPreviewDataSource
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.user.UserManager
import com.getcode.util.resources.ResourceHelper
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The process-wide half of link cards: the answers kept between visits. The per-visit half is
 * [LinkCardModule].
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class LinkCardMemoryModule {

    @Binds
    abstract fun bindLinkCardMemory(impl: PersistedLinkCardMemory): LinkCardMemory

    companion object {
        @Provides
        @Singleton
        fun providePersistedLinkCardMemory(
            store: LinkPreviewDataSource,
            userManager: UserManager,
            resources: ResourceHelper,
            dispatchers: DispatcherProvider,
        ): PersistedLinkCardMemory = PersistedLinkCardMemory(store, userManager, resources, dispatchers)
    }
}
