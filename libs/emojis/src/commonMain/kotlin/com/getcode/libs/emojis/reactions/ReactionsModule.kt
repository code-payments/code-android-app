package com.getcode.libs.emojis.reactions

import com.flipcash.app.core.storage.PreferencesFiles
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReactionsModule {

    @Provides
    @Singleton
    fun providesRecentReactionsStore(
        preferencesFiles: PreferencesFiles,
        owner: RecentReactionsOwner,
    ): RecentReactionsStore = DataStoreRecentReactionsStore(preferencesFiles, owner)

    @Provides
    @Singleton
    fun providesEmojiCatalogLoader(): EmojiCatalogLoader = EmojiCatalogLoader()
}
