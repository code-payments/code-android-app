package com.flipcash.app.login.internal.accounts

import androidx.lifecycle.viewModelScope
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.auth.internal.accounts.AccountRecord
import com.flipcash.features.login.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicPhrase
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenResult
import com.getcode.opencode.model.financial.sum
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.providers.TokenMetadataProvider
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class AccountSelectionViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val mnemonicManager: MnemonicManager,
    private val tokenController: TokenController,
    private val resources: ResourceHelper,
    private val dispatchers: DispatcherProvider,
) : BaseViewModel<AccountSelectionViewModel.State, AccountSelectionViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {

    /**
     * TokenController does not implement [TokenMetadataProvider] itself (only TokenCoordinator
     * does, scoped to the single signed-in user, which is wrong for this screen's other, dormant
     * accounts), so this screen brings its own, delegating to TokenController's network-tier
     * lookup with no cache — there is no warm cache here to benefit from anyway.
     *
     * One instance per account fetch, because it also records whether any lookup failed.
     * `fetchTokenAccounts` resolves metadata through `mapNotNull`, so a failed lookup silently
     * drops that token: without this flag an account whose metadata we could not read is
     * indistinguishable from one the backend has never heard of, and the row would say
     * "Not Found" about a wallet that exists.
     */
    private inner class RecordingMetadataProvider : TokenMetadataProvider {
        var failed: Boolean = false
            private set

        override suspend fun getTokenMetadata(mint: Mint): Result<TokenResult> =
            tokenController.getTokenMetadata(mint).onFailure { failed = true }

        override fun observeTokenCache(): Flow<Map<Mint, Token>> = emptyFlow()
    }

    /**
     * One row. All three result fields false/null is the in-flight state.
     *
     * [notFound] is the terminal state for an account the backend confirms it does not know,
     * matching iOS's "Not Found" badge. [balanceUnavailable] is the separate case where the fetch
     * itself failed — offline, or the backend is down. Collapsing the two would tell a user with no
     * connection that every wallet they own is gone, which on this screen is the worst thing we
     * could say.
     */
    data class AccountUiModel(
        /**
         * The list's stable key. A LazyColumn key is written into the saved-instance-state
         * bundle, so it must not be the seed. The owner key is public.
         */
        val id: String,
        val entropy: String,
        val name: String,
        val ownerAddress: String,
        val creationDate: Long,
        val balance: Fiat? = null,
        val notFound: Boolean = false,
        val balanceUnavailable: Boolean = false,
    ) {
        // The default data class toString() would put a seed into every log line and crash report
        // that touches this state.
        override fun toString(): String = "AccountUiModel(owner=$ownerAddress)"
    }

    data class State(
        val accounts: List<AccountUiModel> = emptyList(),
        val loading: Boolean = true,
        val currentEntropy: String? = null,
    )

    sealed interface Event {
        /** Read the list and start a fresh balance pass. */
        data object Load : Event

        data class OnAccountsLoaded(
            val accounts: List<AccountUiModel>,
            val currentEntropy: String?,
        ) : Event

        data class OnBalanceResolved(val entropy: String, val balance: Fiat) : Event
        data class OnBalanceNotFound(val entropy: String) : Event
        data class OnBalanceUnavailable(val entropy: String) : Event
        data class OnAccountSelected(val entropy: String) : Event
        data class OnRemoveRequested(val entropy: String) : Event
        data class OnAccountRemoved(val entropy: String) : Event
    }

    private val switchInFlight = AtomicBoolean(false)

    init {
        // One pipeline for the list and its balances. `flatMapLatest` is what makes a reload
        // abandon the balance pass already in flight, so its late results cannot land on rows the
        // new list has just reset.
        eventFlow
            .filterIsInstance<Event.Load>()
            // Nothing outside drives this screen, so the pipeline seeds its own first read.
            .onStart { emit(Event.Load) }
            .map { derive(authManager.accounts.all()) }
            .onEach { derived ->
                dispatchEvent(
                    Event.OnAccountsLoaded(
                        accounts = derived.map { (record, cluster) ->
                            record.toUiModel(cluster.getOrNull())
                        },
                        currentEntropy = authManager.currentEntropy,
                    )
                )
            }
            .flatMapLatest { derived -> derived.asFlow().flatMapMerge { balance(it) } }
            .onEach { dispatchEvent(it) }
            // Derivation is PBKDF2 plus a SLIP-10 chain, and the fetches are network calls;
            // neither belongs on the thread drawing the list.
            .flowOn(dispatchers.IO)
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnRemoveRequested>()
            .onEach { event ->
                BottomBarManager.showAlert(
                    title = resources.getString(R.string.prompt_title_removeAccount),
                    message = resources.getString(R.string.prompt_description_removeAccount),
                    actions = listOf(
                        BottomBarAction(resources.getString(R.string.action_removeAccount)) {
                            dispatchEvent(Event.OnAccountRemoved(event.entropy))
                        }
                    ),
                    showCancel = true,
                )
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnAccountRemoved>()
            // Removing the account the user is signed into would strand them in a session whose
            // account is no longer listed. The row is also not clickable, so this is defence in
            // depth against a stale event rather than the primary guard.
            .filter { it.entropy != authManager.currentEntropy }
            // A throw here would cancel the collector, and with it every later removal on this
            // screen. The store is best effort; a failed removal is a reload that shows the row
            // still there, not a dead screen.
            .onEach { event ->
                runCatching { authManager.accounts.setDeleted(event.entropy, deleted = true) }
                    .onFailure { error ->
                        trace(
                            tag = TAG,
                            message = "Could not remove a stored account",
                            error = error,
                            type = TraceType.Error,
                        )
                    }
            }
            .onEach { dispatchEvent(Event.Load) }
            .flowOn(dispatchers.IO)
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnAccountSelected>()
            // Two taps land two logouts, each parking its own entropy, and the second wins the
            // race for what App.kt restarts onboarding with. The guard is never reset: a switch
            // tears this screen down, so there is no second legitimate selection to allow.
            .filter { switchInFlight.compareAndSet(false, true) }
            .onEach { event ->
                val mnemonic = mnemonicManager.fromEntropyBase64(event.entropy)
                // App.kt feeds this to OnboardingFlow(seed = ...), which base58-decodes it.
                authManager.logoutAndSwitchAccount(mnemonicManager.getEncodedBase58(mnemonic))
            }
            .flowOn(dispatchers.IO)
            .launchIn(viewModelScope)
    }

    /**
     * Derivation is done once per account and carried, not recomputed per use: it is a PBKDF2 seed
     * stretch plus a SLIP-10 chain, and this list can hold 50 accounts. A record that will not
     * derive is kept rather than dropped — the user should still see it.
     */
    private fun derive(records: List<AccountRecord>): List<Pair<AccountRecord, Result<AccountCluster>>> =
        records.map { record -> record to runCatching { clusterFor(record.entropy) } }

    private fun AccountRecord.toUiModel(cluster: AccountCluster?): AccountUiModel {
        val mnemonic = runCatching { mnemonicManager.fromEntropyBase64(entropy) }.getOrNull()
        val owner = cluster?.authorityPublicKey?.base58()
        return AccountUiModel(
            // A record that will not derive has no owner key to be keyed on; its creation
            // timestamp is the only other thing distinguishing it.
            id = owner ?: "underived-$creationDate",
            entropy = entropy,
            name = mnemonic?.let { displayName(it) }.orEmpty(),
            ownerAddress = owner?.let { truncateAddress(it) }.orEmpty(),
            creationDate = creationDate,
            // A record we cannot derive from is one we can never fetch a balance for.
            balanceUnavailable = cluster == null,
        )
    }

    private fun clusterFor(entropy: String): AccountCluster {
        val mnemonic = mnemonicManager.fromEntropyBase64(entropy)
        val authority = DerivedKey.derive(DerivePath.primary, mnemonic)
        return AccountCluster.newInstance(authority = authority, token = Token.usdf)
    }

    /**
     * One account's balance, as a single-event flow so the rows fill in as results land rather
     * than all at the end. Each account is fetched with its own owner key as both the account
     * owner and the requesting owner — a self-signed request, which is what makes this work while
     * logged out.
     */
    private fun balance(entry: Pair<AccountRecord, Result<AccountCluster>>): Flow<Event> = flow {
        val (record, cluster) = entry
        val owner = cluster.getOrElse { error ->
            trace(
                tag = TAG,
                message = "Could not derive an owner key for a stored account",
                error = error,
                type = TraceType.Error,
            )
            return@flow emit(Event.OnBalanceUnavailable(record.entropy))
        }

        val metadata = RecordingMetadataProvider()
        tokenController.fetchTokenAccounts(owner, metadata)
            .onSuccess { tokens ->
                when {
                    // A dropped token makes the total wrong as surely as it makes an empty list
                    // meaningless, so a failed lookup rules out both other answers.
                    metadata.failed -> emit(Event.OnBalanceUnavailable(record.entropy))
                    tokens.isEmpty() -> emit(Event.OnBalanceNotFound(record.entropy))
                    else -> emit(
                        Event.OnBalanceResolved(
                            entropy = record.entropy,
                            balance = tokens.map { it.balance }.sum(),
                        )
                    )
                }
            }
            .onFailure { error ->
                trace(
                    tag = TAG,
                    message = "Balance fetch failed for a stored account",
                    error = error,
                    type = TraceType.Error,
                )
                emit(Event.OnBalanceUnavailable(record.entropy))
            }
    }

    companion object {
        private const val TAG = "AccountSelection"

        /** iOS's `MnemonicPhrase.name`: first word, ellipsis, last word, both capitalised. */
        fun displayName(mnemonic: MnemonicPhrase): String =
            listOf(mnemonic.words.first(), mnemonic.words.last())
                .joinToString(" ... ") { word ->
                    word.lowercase().replaceFirstChar { it.titlecase() }
                }

        fun truncateAddress(address: String): String =
            if (address.length <= 8) address
            else "${address.take(4)}...${address.takeLast(4)}"

        private val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnAccountsLoaded -> { state ->
                    state.copy(
                        accounts = event.accounts,
                        loading = false,
                        currentEntropy = event.currentEntropy,
                    )
                }

                is Event.OnBalanceResolved -> { state ->
                    state.copy(
                        accounts = state.accounts.map {
                            if (it.entropy == event.entropy) it.copy(balance = event.balance) else it
                        }
                    )
                }

                is Event.OnBalanceNotFound -> { state ->
                    state.copy(
                        accounts = state.accounts.map {
                            if (it.entropy == event.entropy) it.copy(notFound = true) else it
                        }
                    )
                }

                is Event.OnBalanceUnavailable -> { state ->
                    state.copy(
                        accounts = state.accounts.map {
                            if (it.entropy == event.entropy) {
                                it.copy(balanceUnavailable = true)
                            } else {
                                it
                            }
                        }
                    )
                }

                is Event.Load,
                is Event.OnAccountSelected,
                is Event.OnRemoveRequested,
                is Event.OnAccountRemoved -> { state -> state }
            }
        }
    }
}
