package com.flipcash.app.messenger.internal.link

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(ViewModelComponent::class)
internal object LinkCardModule {

    /**
     * Scoped to the chat rather than the process. The resolver memoizes by entropy and by mint
     * and never evicts, which is what stops a scroll from re-querying the same link — but claim
     * state moves: a link claimed elsewhere, or on this device, stays `Claimable` for as long as
     * the cache holds it. Tying the cache to the screen bounds that to one visit, and re-entering
     * the chat asks again.
     */
    @Provides
    @ViewModelScoped
    fun provideLinkCardResolver(
        giftCard: GiftCardLookup,
        token: TokenLinkLookup,
    ): LinkCardResolver =
        LinkCardResolver(
            // The resolver's own scope, ended by `ChatViewModel.onCleared`. A query outlives the
            // paging pass that asked for it -- see `LinkCardResolver` -- so it needs a scope that
            // is not the transform's, and one bounded by the same screen as the cache it fills.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            giftCard = { giftCard(it) },
            tokenMetadata = { token(it) },
        )
}
