package com.flipcash.app.messenger.internal.link

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
internal object LinkCardModule {

    /**
     * Scoped to the chat rather than the process. The resolver memoizes by entropy and never
     * evicts, which is what stops a scroll from re-querying the same link — but claim state moves:
     * a link claimed on another device stays `Claimable` for as long as the cache holds it. Tying
     * the cache to the screen bounds that to one visit, and re-entering the chat asks again.
     */
    @Provides
    @ViewModelScoped
    fun provideLinkCardResolver(lookup: GiftCardLookup): LinkCardResolver =
        LinkCardResolver(lookup = { lookup(it) })
}
