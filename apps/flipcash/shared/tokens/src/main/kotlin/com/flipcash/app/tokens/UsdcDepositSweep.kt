package com.flipcash.app.tokens

import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountFilter
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.utils.TraceType
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.utils.network.retryable
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Singleton
class UsdcDepositSweep(
    private val transactionOperations: TransactionOperations,
    private val accountController: AccountController,
    private val balancePoller: BalancePoller,
    private val dispatchers: DispatcherProvider,
    private val maxRetries: Int = MAX_RETRIES,
    private val initialDelay: Duration = INITIAL_DELAY,
    private val backoffFactor: Double = BACKOFF_FACTOR,
    private val pollInterval: Duration = POLL_INTERVAL,
    private val pollMaxAttempts: Int = POLL_MAX_ATTEMPTS,
) {
    @Inject constructor(
        transactionOperations: TransactionOperations,
        accountController: AccountController,
        balancePoller: BalancePoller,
        dispatchers: DispatcherProvider,
    ) : this(
        transactionOperations = transactionOperations,
        accountController = accountController,
        balancePoller = balancePoller,
        dispatchers = dispatchers,
        maxRetries = MAX_RETRIES,
        initialDelay = INITIAL_DELAY,
        backoffFactor = BACKOFF_FACTOR,
        pollInterval = POLL_INTERVAL,
        pollMaxAttempts = POLL_MAX_ATTEMPTS,
    )

    private val scope = CoroutineScope(dispatchers.IO + SupervisorJob())
    private var activeJob: Job? = null

    fun execute(owner: AccountCluster) {
        if (activeJob?.isActive == true) return
        activeJob = scope.launch {
            val amount = retryable(
                maxRetries = maxRetries,
                delayDuration = initialDelay,
                backoffFactor = backoffFactor,
            ) {
                val usdcAccount = accountController.getAccount(
                    accountOwner = owner,
                    requestingOwner = owner,
                    filter = AccountFilter.MintAddress(Mint.usdc),
                ).getOrNull()?.takeIf { account ->
                    account.accountType == AccountType.AssociatedToken
                }

                usdcAccount?.let {
                    trace(tag = TAG, message = "USDC ATA found. => ${it.address.base58()}")
                } ?: trace(tag = TAG, message = "USDC ATA not found")

                val balance = usdcAccount?.balance ?: 0L
                check(balance > 0L) { "USDC balance <= 0. nothing to sweep" }
                balance
            } ?: return@launch

            trace(tag = TAG, message = "Swapping $amount USDC to USDF", type = TraceType.Process)

            transactionOperations.swapUsdc(
                owner = owner,
                amount = amount,
            ).onSuccess {
                trace(tag = TAG, message = "USDC→USDF sweep completed")
                balancePoller.awaitBalanceChange(
                    mint = Mint.usdf,
                    baseline = Fiat.Zero,
                    predicate = { _, current -> current.hasDisplayableValue },
                    interval = pollInterval,
                    maxAttempts = pollMaxAttempts,
                ).onFailure { error ->
                    trace(
                        tag = TAG,
                        message = "USDF balance poll timed out: ${error.message}",
                        type = TraceType.Log,
                    )
                }
            }.onFailure { error ->
                trace(tag = TAG, message = "USDC→USDF sweep failed: ${error.message}", error = error)
            }
        }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
    }

    companion object {
        private const val TAG = "UsdcDepositSweep"
        private const val MAX_RETRIES = 5
        private val INITIAL_DELAY = 5.seconds
        private const val BACKOFF_FACTOR = 2.0
        internal val POLL_INTERVAL = 5.seconds
        internal const val POLL_MAX_ATTEMPTS = 12
    }
}
