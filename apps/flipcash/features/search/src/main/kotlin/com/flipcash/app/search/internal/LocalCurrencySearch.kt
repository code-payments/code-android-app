package com.flipcash.app.search.internal

import com.flipcash.app.tokens.TokenCoordinator
import com.getcode.opencode.controllers.CurrencyController
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.ui.DiscoverCategory
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CurrencySearch] over the currencies the app already has: the ones cached for the wallet, plus
 * what `Discover` returns for Popular and New. Only Discover carries weekly change and member
 * metrics, so its copy of a currency wins over the cached one.
 *
 * Discover is fetched once per process and kept; [warm] runs when Home appears so the first query
 * finds it loaded.
 */
@Singleton
internal class LocalCurrencySearch @Inject constructor(
    private val tokenCoordinator: TokenCoordinator,
    private val currencyController: CurrencyController,
) : CurrencySearch {

    private val lock = Mutex()
    private var discovered: List<Token>? = null

    override suspend fun warm() {
        discover()
    }

    override suspend fun search(query: String): List<Token> {
        val discovered = discover()
        val byMint = discovered.associateBy { it.address }
        val held = tokenCoordinator.cachedTokens().map { byMint[it.address] ?: it }
        return filterByNameOrSymbol(
            candidates = held + discovered,
            query = query,
            id = { it.address },
            name = { it.name },
            symbol = { it.symbol },
        )
    }

    private suspend fun discover(): List<Token> = lock.withLock {
        discovered ?: coroutineScope {
            val popular = async { currencyController.discoverTokens(DiscoverCategory.Popular) }
            val new = async { currencyController.discoverTokens(DiscoverCategory.New) }
            val results = listOf(popular.await(), new.await())
            val tokens = results.flatMap { it.getOrNull().orEmpty() }
            // Keep nothing after a failure, so the next search asks again.
            if (results.all { it.isSuccess }) discovered = tokens
            tokens
        }
    }
}
