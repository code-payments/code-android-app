package com.getcode.opencode.controllers

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
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.internal.model.LiveMintDataResponse
import com.getcode.opencode.internal.model.WindowedRange
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountFilter
import com.getcode.opencode.model.accounts.AccountType
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
import kotlinx.coroutines.flow.filterIsInstance
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

@Singleton
class TokenController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val accountController: AccountController,
    private val currencyController: CurrencyController,
    private val networkObserver: NetworkConnectivityListener,
    private val exchange: Exchange,
) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "TokenController"
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
            val appreciation = if (mint == Mint.usdf) Fiat.Zero else state.appreciation[mint] ?: Fiat.Zero
            TokenWithBalance(token, balance, appreciation)
        }
    }

    /**
     * Initializes the token controller state when a user logs in.
     *
     * This function is called to set the active [AccountCluster] for the current session,
     * which is necessary for subsequent token and account operations. It effectively signals
     * the start of an authenticated user's session to this controller.
     *
     * @param cluster The [AccountCluster] associated with the logged-in user.
     */
    fun onUserLoggedIn(cluster: AccountCluster) {
        trace(
            tag = TAG,
            message = "User logged in, initializing token state",
            type = TraceType.User
        )
        this.cluster.value = cluster
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        cluster.filterNotNull()
            .flatMapLatest { networkObserver.state }
            .filter { it.connected }
            .onEach {
                trace(
                    tag = TAG,
                    message = "Network connected, triggering token update",
                    type = TraceType.Process
                )
                retryable { update() }
            }
            .launchIn(scope)

        _state.map { it.tokens.keys.toList() }
            .debounce(500)
            .distinctUntilChanged()
            .filter { it.isNotEmpty() }
            .onEach { mints ->
                trace(
                    tag = TAG,
                    message = "Syncing ${mints.size} mints with exchange",
                    type = TraceType.Process
                )
                exchange.updateUserMints(mints)
            }
            .launchIn(scope)
    }

    override fun onResume(owner: LifecycleOwner) {
        trace(
            tag = TAG,
            message = "Lifecycle resumed, starting reserve state stream",
            type = TraceType.Process
        )
        streamReserveStateJob?.cancel()
        streamReserveStates()
    }

    override fun onStop(owner: LifecycleOwner) {
        trace(
            tag = TAG,
            message = "Lifecycle stopped, cancelling reserve state stream",
            type = TraceType.Process
        )
        streamReserveStateJob?.cancel()
    }

    /**
     * Retrieves the balance for a given token from the current state.
     *
     * This function provides a synchronous way to access the balance of a specific token.
     * It looks up the balance in the in-memory state cache. If the token is not found
     * or has no balance recorded, it returns [Fiat.Zero].
     *
     * For a reactive stream of balance updates, use [balanceForToken] with a [Mint] address.
     *
     * @param token The [Token] for which to retrieve the balance.
     * @return The [Fiat] balance of the token, or [Fiat.Zero] if not available.
     */
    fun balanceForToken(token: Token): Fiat = _state.value.balances[token.address] ?: Fiat.Zero

    /**
     * Observes the balance for a specific token as a [Flow] of [Fiat] values.
     *
     * This function is useful for UI components that need to reactively display
     * a token's balance as it changes. If the token is not found in the current
     * state, it will emit [Fiat.Zero].
     *
     * @param tokenAddress The [Mint] address of the token to observe.
     * @return A [Flow] that emits the latest balance of the specified token.
     */
    fun balanceForToken(tokenAddress: Mint): Flow<Fiat> =
        _state.map { it.balances[tokenAddress] ?: Fiat.Zero }

    /**
     * Observes the appreciation value for a specific token.
     *
     * Appreciation is calculated as the current market value of the user's balance
     * minus the original cost basis of that balance.
     *
     * @param tokenAddress The [Mint] address of the token to observe.
     * @return A [Flow] emitting the appreciation value as a [Fiat] amount. Emits [Fiat.Zero] if no
     * appreciation data is available for the given token.
     */
    fun appreciationForToken(tokenAddress: Mint): Flow<Fiat> =
        _state.map { it.appreciation[tokenAddress] ?: Fiat.Zero }
            .map { appreciation ->
                if (tokenAddress == Mint.usdf) {
                    Fiat.MIN_VALUE
                } else {
                    appreciation
                }
            }


    /**
     * Retrieves the current balance of the user's reserve account (USDF).
     *
     * This is a synchronous function that returns the last known balance.
     * For an observable flow of the balance, use [observeReservesBalance].
     *
     * @return The current reserve balance as a [Fiat] object. Returns [Fiat.Zero] if the balance is not yet available.
     */
    fun reservesBalance(): Fiat = balanceForToken(Token.usdf)

    /**
     * Observes the balance of the user's USDF reserves.
     *
     * This function provides a reactive stream ([Flow]) of the user's balance in USDF,
     * which is considered their primary reserve currency within the system. The balance
     * is represented as a [Fiat] value.
     *
     * @return A [Flow] that emits the latest USDF balance whenever it changes.
     */
    fun observeReservesBalance(): Flow<Fiat> = balanceForToken(Mint.usdf)

    /**
     * Increases the local balance of a specified token by a given fiat amount.
     *
     * This function is used for speculative balance updates, for example, after a successful
     * deposit. It converts the provided fiat amount to its USD equivalent and then adds it
     * to the current local balance of the token. It triggers a network
     * update for the token's account to eventually sync with the authoritative state.
     *
     * @param token The [Token] whose balance is to be increased.
     * @param fiat The [LocalFiat] amount to add, which will be converted to a USD value.
     */
    suspend fun add(token: Token, fiat: LocalFiat) {
        val amount = fiat.nativeAmount.convertingTo(exchange.rateToUsd(fiat.rate.currency)!!)
        trace(
            tag = TAG,
            message = "Adding ${amount.formatted()} to ${token.symbol}",
            type = TraceType.Process
        )
        modifyBalance(token, amount) { current, delta -> current + delta }
    }

    /**
     * Subtracts a specified amount from a token's balance locally.
     *
     * This function calculates the USD value of the fiat amount and then subtracts it
     * from the current in-memory balance of the given token. It triggers a network
     * update for the token's account to eventually sync with the authoritative state.
     *
     * @param token The token from which to subtract the balance.
     * @param fiat The amount to subtract, represented in a local fiat currency.
     */
    suspend fun subtract(token: Token, fiat: LocalFiat) {
        val amount = fiat.nativeAmount.convertingTo(exchange.rateToUsd(fiat.rate.currency)!!)
        trace(
            tag = TAG,
            message = "Subtracting ${amount.formatted()} from ${token.symbol}",
            type = TraceType.Process
        )
        modifyBalance(token, amount) { current, delta -> current - delta }
    }

    /**
     * Triggers a comprehensive update of all token accounts and their balances.
     * This function is the primary public entry point for forcing a refresh of the user's
     * entire token portfolio. It fetches all primary accounts, updates token metadata,
     * calculates balances and appreciation, and ensures a valid token selection is in place.
     *
     * This is useful after major state changes, on app foregrounding, or when a manual
     * refresh is requested by the user.
     */
    suspend fun update() = updateTokens()

    /**
     * Retrieves metadata for a given token mint.
     *
     * This function first checks a local cache for the token's metadata. If a cached
     * version is found, it's returned immediately with `DataSource.Cache`.
     *
     * If the token is not in the cache, it fetches the metadata from the network via
     * the `currencyController`. Upon a successful fetch, the new token metadata is
     * added to the local cache for future requests and returned with `DataSource.Network`.
     *
     * @param mint The [Mint] address of the token to retrieve metadata for.
     * @return A [Result] containing a [TokenResult] on success, which includes the
     *         [Token] metadata and the [DataSource] it was retrieved from. On failure,
     *         it returns a [Result] with an exception.
     */
    suspend fun getTokenMetadata(mint: Mint): Result<TokenResult> {
        _state.value.tokens[mint]?.let { cached ->
            trace(
                tag = TAG,
                message = "Token metadata cache hit for ${cached.symbol}",
                type = TraceType.Process
            )
            return Result.success(TokenResult(cached, DataSource.Cache))
        }

        trace(
            tag = TAG,
            message = "Token metadata cache miss for ${mint.base58()}..., fetching from network",
            type = TraceType.Process
        )

        return currencyController.getMintMetadata(listOf(mint))
            .onSuccess { tokens ->
                trace(
                    tag = TAG,
                    message = "Fetched metadata for ${tokens.size} token(s) from network",
                    type = TraceType.Process
                )
                _state.update { state ->
                    state.copy(tokens = state.tokens + tokens.associateBy { it.address })
                }
            }
            .onFailure { error ->
                trace(
                    tag = TAG,
                    message = "Failed to fetch token metadata for ${mint.base58()}...: ${error.message}",
                    type = TraceType.Error
                )
            }
            .mapCatching { it.first { t -> t.address == mint } }
            .map { TokenResult(it, DataSource.Network) }
    }

    /**
     * Sets the user's currently selected token. This selection is persisted locally
     * and used to determine the default token for various operations (currently limited to the default
     * currency during a Give).
     *
     * @param mint The [Mint] address of the token to be selected.
     */
    suspend fun selectToken(mint: Mint) {
        val token = _state.value.tokens[mint]
        trace(
            tag = TAG,
            message = "Token selected: ${token?.symbol ?: mint.base58()}...",
            type = TraceType.User
        )
        selectedToken.edit { it[mintPreferenceKey] = mint.base58() }
    }

    /**
     * Fetches historical market capitalization data for a given token mint.
     *
     * This function retrieves a list of historical data points (market cap),
     * for a specific token over a defined time window. It delegates the call to the
     * `CurrencyController`.
     *
     * @param mint The [Mint] address of the token for which to fetch historical data.
     * @param currencyCode The [CurrencyCode] in which the historical data should be represented.
     * @param windowedRange The [WindowedRange] (e.g., day, week, month) defining the time period for the data.
     * @return A [Result] containing a list of [HistoricalMintData] on success, or an error on failure.
     */
    suspend fun getHistoricalMarketCapData(
        mint: Mint,
        currencyCode: CurrencyCode,
        windowedRange: WindowedRange,
    ): Result<List<HistoricalMintData>> {
        trace(
            tag = TAG,
            message = "Fetching historical market cap for ${mint.base58()}..., range=$windowedRange",
            type = TraceType.Process
        )

        return currencyController.getHistoricalMintData(mint, currencyCode, windowedRange)
            .onSuccess { data ->
                trace(
                    tag = TAG,
                    message = "Received ${data.size} historical data points",
                    type = TraceType.Process
                )
            }
            .onFailure { error ->
                trace(
                    tag = TAG,
                    message = "Failed to fetch historical market cap: ${error.message}",
                    type = TraceType.Error
                )
            }
    }

    /**
     * Observes the currently selected token's mint address from user preferences.
     *
     * This function provides a reactive stream (`Flow`) that emits the `Mint` address
     * of the token the user has selected. It reads the value from the data store
     * and converts the stored public key string back into a `Mint` object.
     *
     * The flow will emit a new value whenever the selected token changes.
     *
     * @return A [Flow] emitting the [Mint] of the selected token.
     */
    fun observeSelectedTokenMint(): Flow<Mint> = selectedToken.data.mapNotNull { prefs ->
        prefs[mintPreferenceKey]?.let { Mint(it) }
    }

    /**
     * Convenience function to update the account information for a specific token.
     * This is an overload of [updateTokenAccount] that accepts a [Token] object.
     *
     * @param token The [Token] object whose account information needs to be updated.
     */
    suspend fun updateTokenAccount(token: Token) = updateTokenAccount(token.address)

    /**
     * Fetches and updates the account information for a single token.
     *
     * This function retrieves the primary account associated with the given [mint] address for the
     * currently authenticated user. It then calculates the token's balance and appreciation,
     * and updates the internal state.
     *
     * It handles cases where no user is authenticated, prevents duplicate concurrent fetches for the
     * same mint, and includes retry logic for network requests.
     *
     * @param mint The [Mint] address of the token account to update.
     */
    suspend fun updateTokenAccount(mint: Mint) {
        val owner = cluster.value ?: run {
            trace(
                tag = TAG,
                message = "Cannot update token account: no authenticated user",
                type = TraceType.Error
            )
            return
        }

        if (!fetchingMints.add(mint)) {
            trace(
                tag = TAG,
                message = "Skipping duplicate fetch for ${mint.base58()}...",
                type = TraceType.Process
            )
            return
        }

        try {
            trace(
                tag = TAG,
                message = "Fetching token account for ${mint.base58()}...",
                type = TraceType.Process
            )

            val account = retryable(maxRetries = 3) {
                accountController.getAccounts(owner, owner, AccountFilter.MintAddress(mint))
            }?.getOrNull()
                ?.accounts?.values
                ?.firstOrNull { it.accountType == AccountType.Primary && it.mint == mint }

            if (account == null) {
                trace(
                    tag = TAG,
                    message = "No primary account found for ${mint.base58()}...",
                    type = TraceType.Error
                )
                return
            }

            val tokenWithBalance = buildTokenWithBalance(mint, account.balance, account.usdCostBasis)

            if (tokenWithBalance != null) {
                applyTokenUpdates(listOf(tokenWithBalance))
            }
        } catch (e: Exception) {
            trace(
                tag = TAG,
                message = "Exception updating token account ${mint.base58()}",
                error = e,
                type = TraceType.Error
            )
        } finally {
            fetchingMints.remove(mint)
        }
    }

    /**
     * Resets the state of the controller to its initial default values.
     *
     * This function is typically called upon user logout. It clears all token data,
     * balances, appreciation information, the current user cluster, and the persisted
     * selected token preference.
     */
    suspend fun reset() {
        val previousTokenCount = _state.value.tokens.size
        _state.value = TokenState()
        cluster.value = null
        selectedToken.edit { it.clear() }

        trace(
            tag = TAG,
            message = "Token controller reset complete, cleared $previousTokenCount tokens",
            type = TraceType.Process
        )
    }

    private suspend fun buildTokenWithBalance(
        mint: Mint,
        balance: Long,
        usdCostBasis: Double
    ): TokenWithBalance? {
        val metadata = getTokenMetadata(mint).getOrNull()?.token ?: return null

        val currentSupply = _state.value.tokens[mint]?.launchpadMetadata?.currentCirculatingSupplyQuarks
            ?: metadata.launchpadMetadata?.currentCirculatingSupplyQuarks ?: 0

        val token = metadata.copy(
            launchpadMetadata = metadata.launchpadMetadata?.copy(currentCirculatingSupplyQuarks = currentSupply)
        )
        val tokenBalance = Fiat.tokenBalance(balance, token)
        val appreciation = tokenBalance - Fiat(fiat = usdCostBasis)

        return TokenWithBalance(token, tokenBalance, appreciation)
    }

    private fun applyTokenUpdates(updates: List<TokenWithBalance>) {
        if (updates.isEmpty()) {
            trace(
                tag = TAG,
                message = "No token updates to apply",
                type = TraceType.Process
            )
            return
        }

        _state.update { state ->
            state.copy(
                tokens = state.tokens + updates.associate { it.token.address to it.token },
                balances = state.balances + updates.associate { it.token.address to it.balance },
                appreciation = state.appreciation + updates.associate { it.token.address to it.appreciation }
            )
        }

        trace(
            tag = TAG,
            message = "Applied ${updates.size} token update(s), total tokens: ${_state.value.tokens.size}",
            type = TraceType.Process
        )
    }

    private suspend fun modifyBalance(token: Token, amount: Fiat, operation: (Fiat, Fiat) -> Fiat) {
        val currentBalance = _state.value.balances[token.address]

        if (currentBalance == null || currentBalance.decimalValue == 0.0) {
            trace(
                tag = TAG,
                message = "No existing balance for ${token.symbol}, fetching from network before modification",
                type = TraceType.Process
            )
            updateTokenAccount(token.address)
        } else {
            val newBalance = operation(currentBalance, amount)
            trace(
                tag = TAG,
                message = "Modified ${token.symbol} balance: ${currentBalance.formatted()} -> ${newBalance.formatted()}",
                type = TraceType.Process
            )
            _state.update { it.copy(balances = it.balances + (token.address to newBalance)) }
        }

        updateTokenAccount(token.address)
    }

    private fun streamReserveStates() {
        streamReserveStateJob = scope.launch {
            delay(100) // Small debounce
            trace(
                tag = TAG,
                message = "Reserve state stream started",
                type = TraceType.Process
            )

            currencyController.streamLiveMintData(
                scope = this,
                mints = _state.map { it.tokens.keys.toList() }
                    .distinctUntilChanged().debounce(300),
                tag = "token-reserves"
            ).filterIsInstance<LiveMintDataResponse.LaunchpadReserveState>()
                .collect { response ->
                    trace(
                        tag = TAG,
                        message = "Received ${response.reserveStates.size} reserve state updates",
                        type = TraceType.Process
                    )

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

                                    trace(
                                        tag = TAG,
                                        message = "Reserve state updated for ${token.symbol}: supply=${update.reserveState.currentSupply}, balance=${newBalance.formatted()}",
                                        type = TraceType.Process
                                    )
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
        if (state.balances.isEmpty()) {
            trace(
                tag = TAG,
                message = "No balances available, skipping token selection validation",
                type = TraceType.Process
            )
            return
        }

        val currentSelection = selectedToken.data.firstOrNull()
            ?.get(mintPreferenceKey)
            ?.let { Mint(it) }
            ?.takeIf { state.balances.containsKey(it) }

        if (currentSelection != null) {
            trace(
                tag = TAG,
                message = "Current token selection is valid: ${state.tokens[currentSelection]?.symbol}",
                type = TraceType.Process
            )
            return
        }

        val highestBalance = state.balances
            .filterKeys { it != Mint.usdf }
            .maxByOrNull { it.value }

        if (highestBalance != null) {
            val token = state.tokens[highestBalance.key]
            trace(
                tag = TAG,
                message = "Auto-selecting token with highest balance: ${token?.symbol} (${highestBalance.value.formatted()})",
                type = TraceType.Process
            )
            selectToken(highestBalance.key)
        } else {
            trace(
                tag = TAG,
                message = "No valid token available for auto-selection",
                type = TraceType.Error
            )
        }
    }

    private suspend fun updateTokens() {
        val owner = cluster.value ?: run {
            trace(
                tag = TAG,
                message = "Cannot update tokens: no authenticated user",
                type = TraceType.Error
            )
            return
        }

        trace(
            tag = TAG,
            message = "Fetching all token accounts",
            type = TraceType.Process
        )

        retryable(maxRetries = 3) {
            accountController.getAccounts(owner, owner, AccountFilter.AccountType(AccountType.Primary))
        }?.onSuccess { response ->
            trace(
                tag = TAG,
                message = "Received ${response.accounts.size} accounts, processing token balances",
                type = TraceType.Process
            )

            val updates = response.accounts.values.mapNotNull { account ->
                buildTokenWithBalance(account.mint, account.balance, account.usdCostBasis)
            }

            trace(
                tag = TAG,
                message = "Successfully built ${updates.size} token balances",
                type = TraceType.Process
            )

            applyTokenUpdates(updates)
            ensureValidTokenSelection()
        }?.onFailure { error ->
            trace(
                tag = TAG,
                message = "Failed to update tokens after retries: ${error.message}",
                type = TraceType.Error
            )
        }
    }
}