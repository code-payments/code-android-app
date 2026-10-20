package com.flipcash.app.search.internal

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SearchModule {
    @Binds
    abstract fun bindCurrencySearch(impl: LocalCurrencySearch): CurrencySearch
}
