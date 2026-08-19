package com.flipcash.app.bills.share

import com.flipcash.app.core.share.TipCodePreviewRenderer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TipCodePreviewModule {

    @Binds
    @Singleton
    abstract fun bindTipCodePreviewRenderer(
        impl: ComposeTipCodePreviewRenderer,
    ): TipCodePreviewRenderer
}
