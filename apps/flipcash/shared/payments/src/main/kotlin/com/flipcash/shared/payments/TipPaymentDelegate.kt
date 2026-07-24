package com.flipcash.shared.payments

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.controllers.ResolverController
import com.flipcash.services.models.buildTipDmPaymentMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.opencode.model.financial.Token
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * The tip-DM payment domain: sending cash to a user id and the tippable-amount bounds (min preset,
 * max = send-limit ∧ balance) that an amount entry gates on.
 *
 * The user-id counterpart to [ContactPaymentDelegate] (phone number, contact-DM metadata). Shared by
 * the out-of-chat tip flow ([com.flipcash.shared.tipping.TippingCoordinator], which surfaces these
 * amounts through its selection state) and the in-chat send (a tip DM opened in the messenger, whose
 * amount entry drives its "Swipe to Tip" label and minimum off these). [send] performs no UI or
 * navigation — the caller owns send state, error presentation, and any post-send navigation.
 */
@Singleton
class TipPaymentDelegate @Inject constructor(
    private val resolverController: ResolverController,
    private val transactionController: TransactionController,
    private val tokenCoordinator: TokenCoordinator,
    private val chatCoordinator: ChatCoordinator,
    private val exchange: Exchange,
    userFlags: UserFlagsCoordinator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The server tip presets resolved for the user's current preferred currency: the [minimum]
     * tippable amount and the suggested [tiers] (low / medium / high). Region-matched once and shared
     * by [tipPresets] and [minTipAmount] so both stay consistent. Null until presets resolve.
     *
     * Derived from the server-provided per-region [com.flipcash.services.models.TipPresets] (via
     * [UserFlagsCoordinator]) and the preferred currency: selects the presets whose region matches
     * that currency (region is an ISO 4217 currency code), falls back to the USD presets, and — when
     * the server provides no presets at all — to the built-in [DEFAULT_USD_PRESETS], localized to the
     * preferred currency.
     */
    private val resolvedPresets: StateFlow<ResolvedPresets?> = combine(
        userFlags.resolvedFlags,
        // Drive off the preferred rate (a StateFlow that always emits its current value) rather than
        // observePreferredCurrency() — which can be an empty flow, and combine()'d with the hot
        // resolvedFlags that never completes would hang forever. Re-derives when the region changes.
        exchange.observePreferredRate(),
    ) { flags, rate ->
        val presets = flags.tipPresets.effectiveValue
        val preferred = rate.currency
        val (currency, matched) =
            presets.firstOrNull { it.region.equals(preferred.name, ignoreCase = true) }
                ?.let { preferred to it }
                ?: presets.firstOrNull { it.region.equals(CurrencyCode.USD.name, ignoreCase = true) }
                    ?.let { CurrencyCode.USD to it }
                ?: return@combine defaultPresets(preferred)

        ResolvedPresets(
            minimum = Fiat(fiat = matched.minimum, currencyCode = currency),
            tiers = listOf(matched.low, matched.medium, matched.high)
                .map { amount -> Fiat(fiat = amount, currencyCode = currency) },
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The user's suggested tip amounts — the low / medium / high tiers in the preferred currency,
     * shown as preset chips. See [resolvedPresets].
     */
    val tipPresets: StateFlow<List<Fiat>> =
        resolvedPresets.map { it?.tiers.orEmpty() }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The largest tippable amount, in the user's preferred currency: the smaller of the
     * per-transaction send limit and the selected token's balance — mirroring the give/cash/send
     * amount entries. Null until limits/balance/rate are known. The amount entry surfaces it as an
     * "enter up to" hint and blocks amounts above it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val maxTipAmount: StateFlow<Fiat?> =
        combine(
            transactionController.limits,
            tokenCoordinator.observeSelectedTokenMint()
                .flatMapLatest { mint -> tokenCoordinator.balanceForToken(mint) },
            exchange.observePreferredRate(),
        ) { limits, balance, rate ->
            val balanceInLocal = balance.convertingTo(rate)
            val sendLimit = limits?.sendLimitFor(rate.currency) ?: SendLimit.Zero
            Fiat(min(sendLimit.nextTransaction, balanceInLocal.toDouble()), rate.currency)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The smallest tippable amount — the presets' [com.flipcash.services.models.TipPresets.minimum]
     * in the user's preferred currency. This is the dedicated minimum, NOT the lowest tier (`low`),
     * which typically sits above it. The amount entry surfaces it as a "minimum tip" hint and blocks
     * custom amounts below it. Null until presets resolve.
     */
    val minTipAmount: StateFlow<Fiat?> =
        resolvedPresets.map { it?.minimum }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Whether [amount] exceeds the per-transaction send limit for its currency — the amount entry
     * gates on this before committing. Balance is enforced separately at send time; false when no
     * limit is known for the currency.
     */
    fun exceedsSendLimit(amount: Fiat): Boolean {
        val limit = transactionController.limits.value?.sendLimitFor(amount.currencyCode) ?: return false
        return amount.toDouble() > limit.nextTransaction
    }

    /**
     * Sends [verifiedFiat] of [token] from [source] to the user identified by [userId] as a tip DM:
     * derives the canonical tip chat, resolves the recipient's on-chain owner, attaches tip-DM app
     * metadata, transfers, debits the local balance, and syncs the chat feed. Returns the canonical
     * tip [ChatId] (for message reload / navigation), or null if it couldn't be derived.
     */
    suspend fun send(
        userId: ID,
        verifiedFiat: VerifiedFiat,
        token: Token,
        source: AccountCluster,
    ): Result<ChatId?> {
        val canonicalChatId = chatCoordinator.generateChatId(userId = userId).getOrNull()
        val appMetadataBytes = buildTipDmPaymentMetadata(chatId = canonicalChatId)

        return resolverController.resolve(userId = userId)
            .mapCatching { destination ->
                transactionController.directTransfer(
                    amount = verifiedFiat,
                    token = token,
                    source = source,
                    destinationOwner = destination,
                    appMetadata = appMetadataBytes,
                ).getOrThrow()
                tokenCoordinator.subtract(token, verifiedFiat.localFiat)
                if (canonicalChatId != null) {
                    chatCoordinator.loadMessages(canonicalChatId)
                } else {
                    // New conversation — server just created the DM chat.
                    // Sync the feed so it appears in the contact list.
                    chatCoordinator.refreshFeed()
                }
                canonicalChatId
            }
    }

    /**
     * The built-in fallback presets — the [DEFAULT_USD_MINIMUM] and [DEFAULT_USD_PRESETS] tiers (in
     * USD), localized to [preferred] via the current exchange rate when the user isn't on USD. Leaves
     * the amounts in USD if no rate is available for the preferred currency.
     */
    private fun defaultPresets(preferred: CurrencyCode): ResolvedPresets {
        fun localize(amount: Double, rate: Rate?): Fiat {
            val usd = Fiat(fiat = amount, currencyCode = CurrencyCode.USD)
            return rate?.let { usd.convertingTo(it).rounded() } ?: usd
        }

        val rate = if (preferred == CurrencyCode.USD) null else exchange.rateFor(preferred)
        return ResolvedPresets(
            minimum = localize(DEFAULT_USD_MINIMUM, rate),
            tiers = DEFAULT_USD_PRESETS.map { localize(it, rate) },
        )
    }

    /**
     * The region-matched tip presets in a single currency: the dedicated [minimum] tippable amount and
     * the suggested [tiers] (low / medium / high) shown as preset chips. Resolved once so [tipPresets]
     * and [minTipAmount] stay consistent.
     */
    private data class ResolvedPresets(
        val minimum: Fiat,
        val tiers: List<Fiat>,
    )

    companion object {
        /** Fallback tip amounts (USD) used when the server provides no presets. */
        private val DEFAULT_USD_PRESETS = listOf(5.0, 10.0, 20.0)

        /** Fallback minimum tip (USD) used when the server provides no presets. */
        private const val DEFAULT_USD_MINIMUM = 1.0
    }
}
