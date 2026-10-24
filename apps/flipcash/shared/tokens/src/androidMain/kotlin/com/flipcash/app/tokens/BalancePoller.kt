package com.flipcash.app.tokens

import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Singleton
class BalancePoller @Inject constructor(
    private val tokenCoordinator: TokenCoordinator,
) {

    /**
     * Suspends until the wallet balance for [mint] satisfies [predicate] relative
     * to [baseline] (default: any change), refreshing the account from the network
     * on each attempt. Captures the current balance as baseline when [baseline] is null.
     *
     * Returns the new balance on success, or [BalancePollError.Timeout] after
     * [maxAttempts] attempts.
     */
    suspend fun awaitBalanceChange(
        mint: Mint,
        baseline: Fiat? = null,
        predicate: (baseline: Fiat, current: Fiat) -> Boolean = { b, c -> c != b },
        maxAttempts: Int = 30,
        interval: Duration = 10.seconds,
    ): Result<Fiat> = runCatching {
        val start = baseline ?: (tokenCoordinator.balanceForToken(mint).firstOrNull() ?: Fiat.Zero)
        pollUntil(mint, start, predicate, maxAttempts, interval, attempt = 0)
    }

    private tailrec suspend fun pollUntil(
        mint: Mint,
        baseline: Fiat,
        predicate: (Fiat, Fiat) -> Boolean,
        maxAttempts: Int,
        interval: Duration,
        attempt: Int,
    ): Fiat {
        if (attempt >= maxAttempts) {
            trace(
                tag = TAG,
                type = TraceType.Error,
                message = "Polling timed out after $maxAttempts attempts, Mint: ${mint.base58()}",
            )
            throw BalancePollError.Timeout(mint, maxAttempts)
        }

        tokenCoordinator.updateTokenAccount(mint)
        val current = tokenCoordinator.balanceForToken(mint).firstOrNull() ?: Fiat.Zero

        return if (predicate(baseline, current)) {
            trace(
                tag = TAG,
                type = TraceType.Log,
                message = "Balance change detected for ${mint.base58()} after ${attempt + 1} attempt(s)",
            )
            current
        } else {
            trace(
                tag = TAG,
                type = TraceType.Log,
                message = "Balance unchanged for ${mint.base58()}, Attempt ${attempt + 1}/$maxAttempts",
            )
            delay(interval)
            pollUntil(mint, baseline, predicate, maxAttempts, interval, attempt + 1)
        }
    }

    private companion object {
        const val TAG = "BalancePoller"
    }
}

sealed class BalancePollError(message: String) : Throwable(message) {
    data class Timeout(val mint: Mint, val attempts: Int) :
        BalancePollError("Balance for ${mint.base58()} did not change after $attempts attempts")
}
