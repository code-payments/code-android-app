package com.flipcash.app.tokens

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ProcessLifecycleOwner
import com.flipcash.app.persistence.sources.TokenDataSource
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.app.tokens.core.ReservesBalanceProvider
import com.flipcash.app.tokens.core.TotalBalanceProvider
import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.ui.WindowedRange
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.DataSource
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HistoricalMintData
import com.getcode.opencode.model.financial.LaunchpadReserveStateSnapshot
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
    /** No token fetch has ever completed for this account — whatever is cached is cache-only. */
    Unknown,

    /**
     * A fetch has succeeded: the cached token map reflects what the server last said. Persisted, so
     * it survives a relaunch. An account that genuinely holds nothing would otherwise be
     * indistinguishable from an un-fetched one at every cold start, and the wallet tab would hold
     * its spinner on a network round trip on every single launch.
     */
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
    private val dataSource: TokenDataSource,
    private val dispatchers: DispatcherProvider,
) : TokenMetadataProvider, SessionListener, DefaultLifecycleObserver, ReservesBalanceProvider,
    TotalBalanceProvider {

    companion object {
        private const val TAG = "TokenCoordinator"
        private val mintPreferenceKey = stringPreferencesKey("tokenMint")

        /**
         * Whether a token fetch has ever succeeded for the account that is signed in. Lives beside
         * the selected mint because it has the same lifetime: [reset] clears this store on logout,
         * so the next account starts without an answer of its own.
         */
        private val syncedPreferenceKey = booleanPreferencesKey("hasSyncedTokens")

        /** How long a refresh waits for a login that is still landing. See [awaitCluster]. */
        private val CLUSTER_WAIT = 5.seconds
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

    /**
     * What this account holds in one mint: its USD [value], and the on-chain [tokenQuarks] behind
     * it where they are known. A launchpad holding's value is those quarks sold down from the
     * token's supply, so a new supply re-prices them. iOS's `StoredBalance` keeps the same pair.
     *
     * [tokenQuarks] is null for a value restored from Room, which stores only the USD amount, and
     * for one a speculative [add] or [subtract] has moved away from the last fetch.
     */
    data class Holding(
        val value: Fiat,
        val tokenQuarks: Long? = null,
    )

    data class TokenState(
        val tokens: Map<Mint, Token> = emptyMap(),
        val holdings: Map<Mint, Holding> = emptyMap(),
        val appreciation: Map<Mint, Fiat> = emptyMap(),
    ) {
        /** Each holding's USD value. */
        val balances: Map<Mint, Fiat> = holdings.mapValues { (_, holding) -> holding.value }

        /**
         * Applies fetched accounts, each with the supply its metadata carried.
         *
         * That supply can be older than one the reserve stream already delivered. Neither source
         * is timestamped, and the fetch is the only one for a mint the stream has not reached, so
         * neither is preferred: the stream's next delivery re-prices the fetched quarks. iOS
         * settles the same two writers the same way (`VerifiedProtoService.saveReserveStates`).
         */
        internal fun withAccounts(updates: List<TokenWithBalance>): TokenState {
            var next = this
            for (update in updates) {
                val mint = update.token.address
                next = next.copy(
                    tokens = next.tokens + (mint to update.token),
                    holdings = next.holdings + (mint to Holding(update.balance, update.tokenQuarks)),
                    appreciation = next.appreciation + (mint to update.appreciation),
                )
            }
            return next
        }

        /**
         * Applies reserve-state updates: each token takes the new supply, and a holding whose
         * [Holding.tokenQuarks] are known is re-priced from them at that supply. Cost basis is held, so
         * the change lands in appreciation.
         *
         * A balance with no known quarks keeps its value until the next fetch. Converting the USD
         * value to quarks and back at the same supply cannot re-price it, and rounds down on both
         * legs: a $5 holding lost a micro-dollar on every tick.
         */
        internal fun withReserveStates(updates: List<LaunchpadReserveStateSnapshot>): TokenState {
            var next = this
            for (update in updates) {
                val mint = update.mint
                val token = next.tokens[mint] ?: continue
                val launchpad = token.launchpadMetadata ?: continue
                val updatedToken = token.copy(
                    launchpadMetadata = launchpad.copy(
                        currentCirculatingSupplyQuarks = update.currentSupply,
                    ),
                )
                next = next.copy(tokens = next.tokens + (mint to updatedToken))

                val holding = next.holdings[mint] ?: continue
                val quarks = holding.tokenQuarks ?: continue
                val newBalance = runCatching { Fiat.tokenBalance(quarks, updatedToken) }
                    .getOrNull() ?: continue
                val costBasis = holding.value - (next.appreciation[mint] ?: Fiat.Zero)
                next = next.copy(
                    holdings = next.holdings + (mint to holding.copy(value = newBalance)),
                    appreciation = next.appreciation + (mint to newBalance - costBasis),
                )
            }
            return next
        }
    }

    private val _state = MutableStateFlow(TokenState())
    private val _hydrated = MutableStateFlow(false)

    private val _syncState = MutableStateFlow(TokenSyncState.Unknown)

    /**
     * Whether the token set has ever been reconciled with the server for this account. See
     * [TokenSyncState]. Maintained by [updateTokens], which every full refresh funnels through, and
     * seeded on login from the persisted answer of the last session.
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

    /** Synchronous read of every cached token: the current value of [tokens]. */
    fun cachedTokens(): List<Token> = _state.value.tokens.values.toList()

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
        // A new session hasn't looked at the server yet, but a previous one may have, and an empty
        // token map is only ambiguous until the first fetch succeeds. Resetting to Unknown here made
        // every launch of a zero-balance account wait out a network round trip before the wallet tab
        // could draw -- the fetch still runs, it just no longer gates the tab.
        _syncState.value =
            if (hasEverSynced()) TokenSyncState.Synced else TokenSyncState.Unknown
        hydrateFromPersistence()
    }

    override suspend fun onBalanceUpdateRequested() {
        trace(tag = TAG, message = "Balance update requested via session event", type = TraceType.Process)
        update()
    }

    // endregion

    // region Lifecycle

    init {
        // Posted, not called directly: addObserver is main-thread-only, and this singleton is built
        // off the main thread so its construction stays off the startup path.
        Handler(Looper.getMainLooper()).post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        }

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

    /**
     * Waits out a login that is still landing, up to [CLUSTER_WAIT].
     *
     * [UserManager.set] publishes `AuthState.Ready` to its collectors *before* the cluster reaches
     * this coordinator: the cluster travels by event bus, on another dispatcher, via
     * [onUserLoggedIn]. The balance poller starts on the `Ready` edge with no initial delay, so its
     * first tick can arrive here before the cluster does and drop the first fetch of the session --
     * which is the one the wallet tab is waiting for. A null cluster on that edge means "not yet",
     * not "nobody is signed in", so wait for it rather than returning.
     *
     * Bounded, so a caller that really is logged out costs one timeout instead of hanging, and
     * still reaches the trace below. Returns null only in that case.
     */
    private suspend fun awaitCluster(): AccountCluster? =
        withTimeoutOrNull(CLUSTER_WAIT) { cluster.filterNotNull().first() }

    private suspend fun hasEverSynced(): Boolean =
        selectedToken.data.firstOrNull()?.get(syncedPreferenceKey) == true

    private suspend fun markSynced() {
        if (hasEverSynced()) return
        selectedToken.edit { it[syncedPreferenceKey] = true }
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
        val owner = cluster.value ?: awaitCluster() ?: run {
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
                markSynced()
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

        _state.update { it.withAccounts(updates) }

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
            // No quarks: they no longer describe this value, so the reserve stream must not
            // re-price it from them. The fetch launched below restores both.
            _state.update { it.copy(holdings = it.holdings + (token.address to Holding(newBalance))) }
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

                _state.update { state ->
                    state.withReserveStates(response.reserveStates.map { it.reserveState })
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
