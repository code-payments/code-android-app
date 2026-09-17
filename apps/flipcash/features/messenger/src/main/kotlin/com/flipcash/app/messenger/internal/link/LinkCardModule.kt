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
     * Scoped to the chat rather than the process. The resolver memoizes by entropy and by mint,
     * which is what stops a scroll from re-querying the same link — but claim state moves, and a
     * cached `Claimable` outlives the claim that made it wrong.
     *
     * Two things bound that, both in `ChatViewModel.initLinkCardFreshness`: a claim on this
     * device is heard and evicted by entropy, and a claim by anyone else — which nothing tells
     * this device about — is caught by re-asking about claimable cards while the screen is
     * foregrounded. This scope is the backstop under both: the cache dies with the screen, so
     * re-entering the chat asks again regardless.
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
