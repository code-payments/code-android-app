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
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.app.tokens.core.ReservesBalanceProvider
import com.flipcash.app.tokens.core.TotalBalanceProvider
import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.ui.WindowedRange
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
import com.getcode.opencode.model.financial.sum
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * How far the token set has got in reconciling itself with the server for the signed-in user.
 *
 * Mirrors the activity feed's `FeedSyncState`, and for the same reason: the token cache is a *cache*
 * that starts empty on every fresh install and login, so an empty token map means "we haven't looked
 * yet" just as often as it means "this account holds nothing". Surfaces that render token *metadata*
 * (names, icons) for data arriving from elsewhere — the activity feed's convert rows, which read
 * "USDF -> Dad Cash" only once both mints resolve — must wait for a real fetch rather than draw their
 * server-text fallback ("Converted") against a not-yet-populated cache.
 */
enum class TokenSyncState {
    /** No token fetch has completed yet this session — whatever is cached is cache-only. */
    Unknown,

    /** A fetch succeeded: the in-memory token map reflects the server. */
    Synced,

    /** A fetch completed without success. Callers should stop waiting; the next trigger retries. */
    Unavailable,
}

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
    private val accountController: AccountController,
    private val networkObserver: NetworkConnectivityListener,
    private val exchange: Exchange,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
    private val dataSource: TokenDataSource,
    private val dispatchers: DispatcherProvider,
) : TokenMetadataProvider, SessionListener, DefaultLifecycleObserver, ReservesBalanceProvider,
    TotalBalanceProvider {

    companion object {
        private const val TAG = "TokenCoordinator"
        private val mintPreferenceKey = stringPreferencesKey("tokenMint")
    }

    private val scope = CoroutineScope(dispatchers.IO + SupervisorJob())
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
    private val _hydrated = MutableStateFlow(false)

    private val _syncState = MutableStateFlow(TokenSyncState.Unknown)

    /**
     * Whether the token set has been reconciled with the server this session. See [TokenSyncState].
     * Maintained by [updateTokens], which every full refresh funnels through.
     */
    val syncState: StateFlow<TokenSyncState> = _syncState.asStateFlow()

    val tokens: Flow<List<Token>> = _state.map { it.tokens.values.toList() }

    /** Cache-only, network-free view of the in-memory token map (see [TokenMetadataProvider]). */
    override fun observeTokenCache(): Flow<Map<Mint, Token>> =
        _state.map { it.tokens }.distinctUntilChanged()

    /**
     * Synchronous, network-free read of the in-memory token cache — used to seed the currency-info
     * screen's hero card on the very first frame (so the wallet card-expand shared element has a target
     * to fly to) instead of waiting on the async [getTokenMetadata].
     */
    fun cachedToken(mint: Mint): Token? = _state.value.tokens[mint]

    val tokenBalances: Flow<List<TokenWithBalance>> = _hydrated
        .filter { it }
        .flatMapLatest {
            _state.map { state ->
                state.balances.mapNotNull { (mint, balance) ->
                    val token = state.tokens[mint] ?: return@mapNotNull null
                    val appreciation = if (mint == Mint.usdf) Fiat.MIN_VALUE else state.appreciation[mint] ?: Fiat.Zero
                    TokenWithBalance(token, balance, appreciation)
                }
            }
        }

    // region SessionListener

    override suspend fun onUserLoggedIn(cluster: AccountCluster) {
        trace(tag = TAG, message = "User logged in, hydrating from persistence", type = TraceType.User)
        this.cluster.value = cluster
        // A new session hasn't looked at the server yet, whatever the previous one concluded.
        _syncState.value = TokenSyncState.Unknown
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
            .flatMapLatest { networkObserver.state.map { it.connected } }
            .distinctUntilChanged()
            .filter { it }
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

    /** Can I hand money to a person right now? */
    suspend fun hasGiveableBalance(atLeast: Fiat = Fiat.Zero): Boolean {
        val state = _state.value
        return state.balances
            .values
            .any { balance ->
                if (atLeast > Fiat.Zero) balance.valueGreaterThanOrEqualTo(atLeast)
                else balance.hasDisplayableValue
            }
    }


    /** Do I have any balance at all, including reserves? */
    suspend fun hasBalance(): Boolean =
        _state.value.balances.values.any { it.hasDisplayableValue }

    /**
     * Observable "is there money in this account right now?", across every token including reserves.
     *
     * Deliberately [Fiat.isPositive] rather than [Fiat.hasDisplayableValue]: this answers whether the
     * account holds *anything*, so a dust balance that rounds away in the UI still counts. Callers
     * that need "enough to act on" want [hasGiveableBalance] instead.
     */
    val hasAnyBalance: Flow<Boolean> = _state
        .map { state -> state.balances.values.any { it.isPositive } }
        .distinctUntilChanged()

    fun balanceForToken(token: Token): Fiat = _state.value.balances[token.address] ?: Fiat.Zero

    fun balanceForToken(tokenAddress: Mint): Flow<Fiat> =
        _state.map { it.balances[tokenAddress] ?: Fiat.Zero }

    fun appreciationForToken(tokenAddress: Mint): Flow<Fiat> =
        _state.map { it.appreciation[tokenAddress] ?: Fiat.Zero }
            .map { appreciation ->
                if (tokenAddress == Mint.usdf) Fiat.MIN_VALUE else appreciation
            }

    fun reservesBalance(): Fiat = balanceForToken(Token.usdf)

    override fun observeReservesBalance(): Flow<Fiat> = balanceForToken(Mint.usdf)

    /**
     * Every token added together, reserves included — the number the username minimum-balance rule
     * is measured against.
     *
     * Summing the raw balances is safe because they are all USD-denominated: [Fiat.plus] refuses a
     * mismatched `currencyCode`, so a non-USD balance in this map would already be failing
     * elsewhere. Summing before rounding also matches how the token list computes its total, and
     * how iOS computes this one.
     */
    override fun observeTotalBalance(): Flow<Fiat> = _state
        .map { state -> state.balances.values.sum() }
        .distinctUntilChanged()

    /** Synchronous, network-free read of [observeTotalBalance]'s current value. */
    fun currentTotalBalance(): Fiat = _state.value.balances.values.sum()

    /** Synchronous, network-free read of what this account holds in [mint]. */
    fun currentBalance(mint: Mint): Fiat = _state.value.balances[mint] ?: Fiat.Zero

    /**
     * Whether [mint] currently reads as a held balance — i.e. whether the wallet's card deck already
     * has a card for it. Dust that rounds away in the UI counts as *not* held, matching the deck's
     * own `hasDisplayableValue` filter rather than [hasAnyBalance]'s "holds anything at all".
     */
    fun holdsDisplayableBalance(mint: Mint): Boolean =
        _state.value.balances[mint]?.hasDisplayableValue == true

    suspend fun add(token: Token, fiat: LocalFiat) {
        val rate = exchange.rateToUsd(fiat.rate.currency)
        val amount = rate?.let { fiat.nativeAmount.convertingTo(it) }
        if (amount != null) {
            trace(tag = TAG, message = "Adding ${amount.formatted()} to ${token.symbol}", type = TraceType.Process)
        }
        modifyBalance(token, amount) { current, delta -> current + delta }
    }

    suspend fun add(mint: Mint, nativeAmount: Fiat) {
        val token = getTokenMetadata(mint).getOrNull()?.token ?: return
        add(token, nativeAmount.toLocalFiat(mint))
    }

    suspend fun subtract(token: Token, fiat: LocalFiat) {
        val rate = exchange.rateToUsd(fiat.rate.currency)
        val amount = rate?.let { fiat.nativeAmount.convertingTo(it) }
        if (amount != null) {
            trace(tag = TAG, message = "Subtracting ${amount.formatted()} from ${token.symbol}", type = TraceType.Process)
        }
        modifyBalance(token, amount) { current, delta -> current - delta }
    }

    suspend fun subtract(mint: Mint, nativeAmount: Fiat) {
        val token = getTokenMetadata(mint).getOrNull()?.token ?: return
        subtract(token, nativeAmount.toLocalFiat(mint))
    }

    private fun Fiat.toLocalFiat(mint: Mint): LocalFiat {
        val rate = exchange.rateFor(currencyCode) ?: Rate.oneToOne
        return LocalFiat.fromNativeAmount(this, rate, mint)
    }

    // endregion

    // region Public API — Token Metadata (implements TokenMetadataProvider)

    /**
     * 3-tier lookup: in-memory → Room → network (via [TokenController]).
     * Each hit hydrates the layer above it.
     *
     * Cache hits return immediately for fast UI, but also trigger a background
     * network refresh so that stale metadata (icon, description, etc.) is
     * eventually replaced with fresh data.
     */
    override suspend fun getTokenMetadata(mint: Mint): Result<TokenResult> {
        // 1. In-memory cache
        _state.value.tokens[mint]?.let { cached ->
            return Result.success(TokenResult(cached, DataSource.Memory))
        }

        // 2. Room persistence
        dataSource.getById(mint)?.let { persisted ->
            _state.update { state ->
                state.copy(tokens = state.tokens + (persisted.address to persisted))
            }
            return Result.success(TokenResult(persisted, DataSource.Cache))
        }

        // 3. Network via TokenController
        return tokenController.getTokenMetadata(mint)
            .onSuccess { result ->
                val hasAccount = accountController.hasAccountFor(result.token.address)
                // if we have an account for this mint, persist and hydrate
                if (hasAccount) {
                    // Persist to Room
                    dataSource.upsert(listOf(result.token))
                    // Hydrate in-memory
                    _state.update { state ->
                        state.copy(tokens = state.tokens + (result.token.address to result.token))
                    }
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
        _hydrated.value = false
        _syncState.value = TokenSyncState.Unknown
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
            _hydrated.value = true
            return
        }

        applyTokenUpdates(persisted)
        ensureValidTokenSelection()
        _hydrated.value = true

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

        // Batch-refresh metadata for all known mints in a single RPC call,
        // hydrating memory and Room so that fetchTokenAccounts hits warm caches
        // without triggering per-mint background refreshes.
        refreshAllMetadata()

        // Pass `this` as metadataProvider so fetches benefit from the now-warm cache
        tokenController.fetchTokenAccounts(owner, metadataProvider = this)
            .onSuccess { updates ->
                trace(tag = TAG, message = "Successfully built ${updates.size} token balances", type = TraceType.Process)
                applyTokenUpdates(updates)
                persistTokenState(updates)
                ensureValidTokenSelection()
                _syncState.value = TokenSyncState.Synced
            }
            .onFailure { error ->
                trace(tag = TAG, message = "Failed to update tokens: ${error.message}", type = TraceType.Error)
                // Don't downgrade a sync that already succeeded — a later failure means this refresh
                // missed, not that the cache is unknown again.
                _syncState.update { current ->
                    if (current == TokenSyncState.Unknown) TokenSyncState.Unavailable else current
                }
            }
    }

    /**
     * Batch-fetches fresh metadata for all known mints in a single RPC call
     * and updates both in-memory state and Room persistence for any changes.
     */
    private suspend fun refreshAllMetadata() {
        val mints = _state.value.tokens.keys.toList()
        if (mints.isEmpty()) return

        val freshMetadata = tokenController.getTokenMetadata(mints)
            .getOrNull() ?: return

        trace(tag = TAG, message = "Batch-refreshed metadata for ${freshMetadata.size} mint(s)", type = TraceType.Process)

        val changed = freshMetadata.filter { fresh ->
            _state.value.tokens[fresh.address] != fresh
        }

        if (changed.isNotEmpty()) {
            _state.update { state ->
                state.copy(tokens = state.tokens + changed.associateBy { it.address })
            }
            dataSource.upsert(changed)
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

    private suspend fun modifyBalance(token: Token, amount: Fiat?, operation: (Fiat, Fiat) -> Fiat) {
        val currentBalance = _state.value.balances[token.address]

        if (currentBalance == null || currentBalance.decimalValue == 0.0 || amount == null) {
            trace(tag = TAG, message = "No existing balance for ${token.symbol}, fetching from network before modification", type = TraceType.Process)
            updateTokenAccount(token.address)
        } else {
            val newBalance = operation(currentBalance, amount)
            trace(tag = TAG, message = "Modified ${token.symbol} balance: ${currentBalance.formatted()} -> ${newBalance.formatted()}", type = TraceType.Process)
            _state.update { it.copy(balances = it.balances + (token.address to newBalance)) }
            ensureValidTokenSelection()

            scope.launch {
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

                val state = _state.value
                var updatedTokens = state.tokens
                var updatedBalances = state.balances
                var updatedAppreciation = state.appreciation

                for (update in response.reserveStates) {
                    val mint = update.reserveState.mint
                    val token = state.tokens[mint] ?: continue
                    val launchpad = token.launchpadMetadata ?: continue

                    val updatedToken = token.copy(
                        launchpadMetadata = launchpad.copy(
                            currentCirculatingSupplyQuarks = update.reserveState.currentSupply
                        )
                    )
                    updatedTokens = updatedTokens + (mint to updatedToken)

                    val balance = state.balances[mint] ?: continue
                    val exchangedValue = verifiedFiatCalculator.compute(
                        amount = balance,
                        token = updatedToken,
                        balance = balance,
                        rate = Rate.oneToOne,
                        trace = false,
                    ).getOrNull()?.localFiat?.underlyingTokenAmount

                    if (exchangedValue != null) {
                        val newBalance = Fiat.tokenBalance(
                            quarks = exchangedValue.quarks,
                            token = updatedToken
                        )
                        updatedBalances = updatedBalances + (mint to newBalance)

                        val currentAppreciation = state.appreciation[mint] ?: Fiat.Zero
                        val costBasis = balance - currentAppreciation
                        val newAppreciation = newBalance - costBasis
                        updatedAppreciation = updatedAppreciation + (mint to newAppreciation)
                    }
                }

                _state.update {
                    it.copy(tokens = updatedTokens, balances = updatedBalances, appreciation = updatedAppreciation)
                }
            }
        }
    }

    private suspend fun ensureValidTokenSelection() {
        val currentSelection = selectedToken.data.firstOrNull()
            ?.get(mintPreferenceKey)
            ?.let { Mint(it) }

        val resolved = resolveTokenSelection(
            balances = _state.value.balances,
            currentSelection = currentSelection,
            rate = exchange.preferredRate,
        )

        if (resolved != null && resolved != currentSelection) {
            selectToken(resolved)
        }
    }

    // endregion
}
