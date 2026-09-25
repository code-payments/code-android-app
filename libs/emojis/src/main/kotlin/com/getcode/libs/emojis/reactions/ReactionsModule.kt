package com.getcode.libs.emojis.reactions

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReactionsModule {

    @Provides
    @Singleton
    fun providesRecentReactionsStore(@ApplicationContext context: Context): RecentReactionsStore =
        DataStoreRecentReactionsStore(context)

    @Provides
    @Singleton
    fun providesEmojiCatalogLoader(): EmojiCatalogLoader = EmojiCatalogLoader()
}
