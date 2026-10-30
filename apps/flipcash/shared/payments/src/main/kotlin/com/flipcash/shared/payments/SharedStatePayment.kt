package com.flipcash.shared.payments

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.ResolverController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.PublicKey

/**
 * Pays several people in one intent, each their own fiat amount. Shared by reaction boosts and
 * group sends, which split a total before pricing it.
 *
 * Every part is priced to quarks from its own amount, all at one verified state: a multi-payment
 * intent carries one rate and reserve for every payment. On a bonding curve the parts' quarks need
 * not add up to what the whole would price to; the payer pays the sum of the parts.
 */
internal class SharedStatePayment(
    private val resolverController: ResolverController,
    private val transactionController: TransactionController,
    private val tokenCoordinator: TokenCoordinator,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
) {

    /**
     * Each of [amounts] priced in [token] at [rate], all at one verified state. A refresh between
     * two computes is retried once before giving up as stale.
     */
    suspend fun price(amounts: List<Fiat>, token: Token, rate: Rate): Result<List<VerifiedFiat>> {
        repeat(2) {
            val priced = amounts.map { amount ->
                verifiedFiatCalculator.compute(
                    amount = amount,
                    token = token,
                    balance = tokenCoordinator.balanceForToken(token),
                    rate = rate,
                ).getOrElse { return Result.failure(it) }
            }
            val states = priced.map { it.verifiedState }
            if (states.all { it != null && it == states.first() }) return Result.success(priced)
        }
        return Result.failure(ComputeVerifiedFiatError.StaleRate())
    }

    /**
     * Pays each of [payees] its [priced] amount from [source]: one payee is a single transfer, more
     * are one multi-transfer in the order given. Debits the local balance by every part.
     *
     * [intentId] applies to a single transfer only: the id the server issued for the payment.
     */
    suspend fun pay(
        payees: List<ID>,
        priced: List<VerifiedFiat>,
        token: Token,
        source: AccountCluster,
        appMetadata: ByteArray?,
        intentId: PublicKey? = null,
    ): Result<Unit> = runCatching {
        require(payees.size == priced.size) { "${payees.size} payees for ${priced.size} amounts" }
        val destinations = payees.map { resolverController.resolve(it).getOrThrow() }

        if (priced.size == 1) {
            transactionController.directTransfer(
                amount = priced.single(),
                token = token,
                source = source,
                destinationOwner = destinations.single(),
                appMetadata = appMetadata,
                intentId = intentId,
            ).getOrThrow()
        } else {
            require(intentId == null) { "An issued intent id pays one payee" }
            transactionController.directMultiTransfer(
                payments = destinations.zip(priced.map { it.localFiat }),
                verifiedState = requireNotNull(priced.first().verifiedState),
                token = token,
                source = source,
                appMetadata = appMetadata,
            ).getOrThrow()
        }
        priced.forEach { tokenCoordinator.subtract(token, it.localFiat) }
    }
}
