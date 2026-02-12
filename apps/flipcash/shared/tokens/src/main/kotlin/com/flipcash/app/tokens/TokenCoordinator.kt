package com.flipcash.app.tokens

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.flipcash.app.persistence.sources.TokenDataSource
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.internal.model.WindowedRange
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.DataSource
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HistoricalMintData
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenResult
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.minus
import com.getcode.opencode.model.financial.plus
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.providers.SessionListener
import com.getcode.opencode.providers.TokenMetadataProvider
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.utils.TraceType
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.utils.network.retryable
import com.getcode.utils.trace
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-layer coordinator that wraps [TokenController] with persistence,
 * caching, lifecycle management, and balance tracking.
 *
 * [TokenController] is a stateless network gateway suitable for a public SDK.
 * This coordinator adds:
 * - 3-tier metadata lookup: in-memory → Room → network (via TokenController)
 * - Room persistence for tokens and balances (via [TokenDataSource])
 * - In-memory [TokenState] as the single source of truth for the UI
 * - DataStore-backed selected token preference
 * - Lifecycle-aware reserve state streaming
 * - Speculative balance mutations with eventual network consistency
 * - Exchange rate sync
 */
@Singleton
class TokenCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tokenController: TokenController,
    private val networkObserver: NetworkConnectivityListener,
    private val exchange: Exchange,
    private val dataSource: TokenDataSource,
) : TokenMetadataProvider, SessionListener, DefaultLifecycleObserver {

    companion object {
        private const val TAG = "TokenCoordinator"
        private val mintPreferenceKey = stringPreferencesKey("tokenMint")
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var streamReserveStateJob: Job? = null

    private val selectedToken = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("selected-token") }
    )

    private val cluster = MutableStateFlow<AccountCluster?>(null)
    private val fetchingMints = ConcurrentHashMap.newKeySet<Mint>()

    data class TokenState(
        val tokens: Map<Mint, Token> = emptyMap(),
        val balances: Map<Mint, Fiat> = emptyMap(),
        val appreciation: Map<Mint, Fiat> = emptyMap(),
    )

    private val _state = MutableStateFlow(TokenState())

    val tokens: Flow<List<Token>> = _state.map { it.tokens.values.toList() }

    val tokenBalances: Flow<List<TokenWithBalance>> = _state.map { state ->
        state.balances.mapNotNull { (mint, balance) ->
            val token = state.tokens[mint] ?: return@mapNotNull null
            val appreciation = if (mint == Mint.usdf) Fiat.MIN_VALUE else state.appreciation[mint] ?: Fiat.Zero
            TokenWithBalance(token, balance, appreciation)
        }
    }

    // region SessionListener

    override suspend fun onUserLoggedIn(cluster: AccountCluster) {
        trace(tag = TAG, message = "User logged in, hydrating from persistence", type = TraceType.User)
        this.cluster.value = cluster
        hydrateFromPersistence()
    }

    override suspend fun onBalanceUpdateRequested() {
        trace(tag = TAG, message = "Balance update requested via session event", type = TraceType.Process)
        update()
    }

    // endregion

    // region Lifecycle

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        cluster.filterNotNull()
            .flatMapLatest { networkObserver.state }
            .distinctUntilChanged()
            .filter { it.connected }
            .onEach {
                trace(tag = TAG, message = "Network connected, triggering token update", type = TraceType.Process)
                retryable { update() }
            }
            .launchIn(scope)

        _state.map { it.tokens.keys.toList() }
            .debounce(500)
            .distinctUntilChanged()
            .filter { it.isNotEmpty() }
            .onEach { mints ->
                trace(tag = TAG, message = "Syncing ${mints.size} mints with exchange", type = TraceType.Process)
                exchange.updateUserMints(mints)
            }
            .launchIn(scope)
    }

    override fun onStart(owner: LifecycleOwner) {
        trace(tag = TAG, message = "Lifecycle resumed, starting reserve state stream", type = TraceType.Process)
        streamReserveStateJob?.cancel()
        streamReserveStates()
    }

    override fun onStop(owner: LifecycleOwner) {
        trace(tag = TAG, message = "Lifecycle stopped, cancelling reserve state stream", type = TraceType.Process)
        streamReserveStateJob?.cancel()
    }

    // endregion

    // region Public API — Balances

    fun balanceForToken(token: Token): Fiat = _state.value.balances[token.address] ?: Fiat.Zero

    fun balanceForToken(tokenAddress: Mint): Flow<Fiat> =
        _state.map { it.balances[tokenAddress] ?: Fiat.Zero }

    fun appreciationForToken(tokenAddress: Mint): Flow<Fiat> =
        _state.map { it.appreciation[tokenAddress] ?: Fiat.Zero }
            .map { appreciation ->
                if (tokenAddress == Mint.usdf) Fiat.MIN_VALUE else appreciation
            }

    fun reservesBalance(): Fiat = balanceForToken(Token.usdf)

    fun observeReservesBalance(): Flow<Fiat> = balanceForToken(Mint.usdf)

    suspend fun add(token: Token, fiat: LocalFiat) {
        val amount = fiat.nativeAmount.convertingTo(exchange.rateToUsd(fiat.rate.currency)!!)
        trace(tag = TAG, message = "Adding ${amount.formatted()} to ${token.symbol}", type = TraceType.Process)
        modifyBalance(token, amount) { current, delta -> current + delta }
    }

    suspend fun subtract(token: Token, fiat: LocalFiat) {
        val amount = fiat.nativeAmount.convertingTo(exchange.rateToUsd(fiat.rate.currency)!!)
        trace(tag = TAG, message = "Subtracting ${amount.formatted()} from ${token.symbol}", type = TraceType.Process)
        modifyBalance(token, amount) { current, delta -> current - delta }
    }

    // endregion

    // region Public API — Token Metadata (implements TokenMetadataProvider)

    /**
     * 3-tier lookup: in-memory → Room → network (via [TokenController]).
     * Each hit hydrates the layer above it.
     */
    override suspend fun getTokenMetadata(mint: Mint): Result<TokenResult> {
        // 1. In-memory cache
        _state.value.tokens[mint]?.let { cached ->
            trace(tag = TAG, message = "Token metadata memory hit for ${cached.symbol}", type = TraceType.Process)
            return Result.success(TokenResult(cached, DataSource.Cache))
        }

        // 2. Room persistence
        dataSource.getById(mint)?.let { persisted ->
            trace(tag = TAG, message = "Token metadata DB hit for ${persisted.symbol}", type = TraceType.Process)
            _state.update { state ->
                state.copy(tokens = state.tokens + (persisted.address to persisted))
            }
            return Result.success(TokenResult(persisted, DataSource.Cache))
        }

        // 3. Network via TokenController
        trace(tag = TAG, message = "Token metadata cache miss for ${mint.base58()}, delegating to network", type = TraceType.Process)

        return tokenController.getTokenMetadata(mint)
            .onSuccess { result ->
                // Persist to Room
                dataSource.upsert(listOf(result.token))
                // Hydrate in-memory
                _state.update { state ->
                    state.copy(tokens = state.tokens + (result.token.address to result.token))
                }
            }
    }

    // endregion

    // region Public API — Selection & Updates

    suspend fun selectToken(mint: Mint) {
        val token = _state.value.tokens[mint]
        trace(tag = TAG, message = "Token selected: ${token?.symbol ?: mint.base58()}", type = TraceType.User)
        selectedToken.edit { it[mintPreferenceKey] = mint.base58() }
    }

    fun observeSelectedTokenMint(): Flow<Mint> = selectedToken.data.mapNotNull { prefs ->
        prefs[mintPreferenceKey]?.let { Mint(it) }
    }

    suspend fun update() = updateTokens()

    suspend fun updateTokenAccount(token: Token) = updateTokenAccount(token.address)

    suspend fun getHistoricalMarketCapData(
        mint: Mint,
        currencyCode: CurrencyCode,
        windowedRange: WindowedRange,
    ): Result<List<HistoricalMintData>> {
        return tokenController.getHistoricalMarketCapData(mint, currencyCode, windowedRange)
    }

    suspend fun reset() {
        val previousTokenCount = _state.value.tokens.size
        _state.value = TokenState()
        cluster.value = null
        selectedToken.edit { it.clear() }
        dataSource.clear()

        trace(tag = TAG, message = "Token coordinator reset complete, cleared $previousTokenCount tokens", type = TraceType.Process)
    }

    // endregion

    // region Internal — Persistence hydration

    /**
     * Loads tokens and balances from Room into in-memory state.
     * Called on login before network is available, giving the UI
     * instant data from the last session.
     *
     * Uses the same [applyTokenUpdates] path as network updates,
     * ensuring a single codepath for state hydration.
     */
    private suspend fun hydrateFromPersistence() {
        val persisted = dataSource.getTokensWithBalances()

        if (persisted.isEmpty()) {
            trace(tag = TAG, message = "No persisted tokens found", type = TraceType.Process)
            return
        }

        applyTokenUpdates(persisted)

        trace(tag = TAG, message = "Hydrated ${persisted.size} tokens from persistence", type = TraceType.Process)
    }

    // endregion

    // region Internal — Network updates

    private suspend fun updateTokens() {
        val owner = cluster.value ?: run {
            trace(tag = TAG, message = "Cannot update tokens: no authenticated user", type = TraceType.Error)
            return
        }

        trace(tag = TAG, message = "Fetching all token accounts", type = TraceType.Process)

        // Pass `this` as metadataProvider so network fetches benefit from memory/Room cache
        tokenController.fetchTokenAccounts(owner, metadataProvider = this)
            .onSuccess { updates ->
                trace(tag = TAG, message = "Successfully built ${updates.size} token balances", type = TraceType.Process)
                applyTokenUpdates(updates)
                persistTokenState(updates)
                ensureValidTokenSelection()
            }
            .onFailure { error ->
                trace(tag = TAG, message = "Failed to update tokens: ${error.message}", type = TraceType.Error)
            }
    }

    suspend fun updateTokenAccount(mint: Mint) {
        val owner = cluster.value ?: run {
            trace(tag = TAG, message = "Cannot update token account: no authenticated user", type = TraceType.Error)
            return
        }

        if (!fetchingMints.add(mint)) {
            trace(tag = TAG, message = "Skipping duplicate fetch for ${mint.base58()}", type = TraceType.Process)
            return
        }

        try {
            tokenController.fetchTokenAccount(owner, mint, metadataProvider = this)
                .onSuccess { tokenWithBalance ->
                    applyTokenUpdates(listOf(tokenWithBalance))
                    persistTokenState(listOf(tokenWithBalance))
                    ensureValidTokenSelection()
                }
                .onFailure { error ->
                    trace(tag = TAG, message = "Failed to update token account ${mint.base58()}: ${error.message}", type = TraceType.Error)
                }
        } catch (e: Exception) {
            trace(tag = TAG, message = "Exception updating token account ${mint.base58()}", error = e, type = TraceType.Error)
        } finally {
            fetchingMints.remove(mint)
        }
    }

    // endregion

    // region Internal — State management

    private fun applyTokenUpdates(updates: List<TokenWithBalance>) {
        if (updates.isEmpty()) return

        _state.update { state ->
            state.copy(
                tokens = state.tokens + updates.associate { it.token.address to it.token },
                balances = state.balances + updates.associate { it.token.address to it.balance },
                appreciation = state.appreciation + updates.associate { it.token.address to it.appreciation }
            )
        }

        trace(tag = TAG, message = "Applied ${updates.size} token update(s), total tokens: ${_state.value.tokens.size}", type = TraceType.Process)
    }

    private suspend fun persistTokenState(updates: List<TokenWithBalance>) {
        dataSource.upsertWithBalances(updates)
        trace(tag = TAG, message = "Persisted ${updates.size} token(s) to Room", type = TraceType.Process)
    }

    private suspend fun modifyBalance(token: Token, amount: Fiat, operation: (Fiat, Fiat) -> Fiat) {
        val currentBalance = _state.value.balances[token.address]

        if (currentBalance == null || currentBalance.decimalValue == 0.0) {
            trace(tag = TAG, message = "No existing balance for ${token.symbol}, fetching from network before modification", type = TraceType.Process)
            updateTokenAccount(token.address)
        } else {
            val newBalance = operation(currentBalance, amount)
            trace(tag = TAG, message = "Modified ${token.symbol} balance: ${currentBalance.formatted()} -> ${newBalance.formatted()}", type = TraceType.Process)
            _state.update { it.copy(balances = it.balances + (token.address to newBalance)) }

            scope.launch(Dispatchers.IO) {
                updateTokenAccount(token.address)
            }
        }
    }

    private fun streamReserveStates() {
        streamReserveStateJob = scope.launch {
            delay(100)
            trace(tag = TAG, message = "Reserve state stream started", type = TraceType.Process)

            tokenController.streamReserveStates(
                scope = this,
                mints = _state.map { it.tokens.keys.toList() }
                    .distinctUntilChanged().debounce(300),
            ).collect { response ->
                trace(tag = TAG, message = "Received ${response.reserveStates.size} reserve state updates", type = TraceType.Process)

                _state.update { state ->
                    var updatedTokens = state.tokens
                    var updatedBalances = state.balances

                    response.reserveStates.forEach { update ->
                        val mint = update.reserveState.mint
                        val token = state.tokens[mint] ?: return@forEach
                        val launchpad = token.launchpadMetadata ?: return@forEach

                        val updatedToken = token.copy(
                            launchpadMetadata = launchpad.copy(
                                currentCirculatingSupplyQuarks = update.reserveState.currentSupply
                            )
                        )
                        updatedTokens = updatedTokens + (mint to updatedToken)

                        state.balances[mint]?.let { balance ->
                            val exchangedValue = runCatching {
                                LocalFiat.valueExchangeIn(
                                    amount = balance,
                                    token = token,
                                    balance = balance,
                                    rate = Rate.oneToOne,
                                    debug = false,
                                    trace = false,
                                ).underlyingTokenAmount
                            }.getOrNull()

                            if (exchangedValue != null) {
                                val newBalance = Fiat.tokenBalance(
                                    quarks = exchangedValue.quarks,
                                    token = token
                                )
                                updatedBalances = updatedBalances + (mint to newBalance)
                            }
                        }
                    }

                    state.copy(tokens = updatedTokens, balances = updatedBalances)
                }
            }
        }
    }

    private suspend fun ensureValidTokenSelection() {
        val state = _state.value
        if (state.balances.isEmpty()) return

        val currentSelection = selectedToken.data.firstOrNull()
            ?.get(mintPreferenceKey)
            ?.let { Mint(it) }
            ?.takeIf { state.balances.containsKey(it) }

        if (currentSelection != null) return

        val highestBalance = state.balances
            .filterKeys { it != Mint.usdf }
            .maxByOrNull { it.value }

        if (highestBalance != null) {
            selectToken(highestBalance.key)
        }
    }

    // endregion
}