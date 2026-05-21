package com.flipcash.shared.appfunctions.functions

import android.net.Uri
import androidx.appfunctions.service.AppFunction
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionElementNotFoundException
import com.flipcash.app.core.navigation.Key
import com.flipcash.app.core.navigation.fragments
import com.flipcash.app.core.util.Linkify
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.user.UserManager
import com.flipcash.shared.appfunctions.models.CashLinkResult
import com.flipcash.shared.appfunctions.models.ClaimResult
import com.getcode.opencode.managers.BillTransactionManager
import com.getcode.opencode.managers.GiftCardManager
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.core.net.toUri

@Singleton
class CashLinkFunctions @Inject constructor(
    private val userManager: UserManager,
    private val tokenCoordinator: TokenCoordinator,
    private val transactionManager: BillTransactionManager,
    private val giftCardManager: GiftCardManager,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
) : SendCashLinkSchema, ClaimCashLinkSchema {
    /**
     * Creates and funds a new cash link that can be shared with anyone to send money.
     *
     * @param amountUsd The amount in USD to send via the cash link.
     * @param tokenSymbol The token to use for the cash link (default "USDF").
     * Returns the shareable URL, amount, and token symbol. Requires the user to be logged in.
     */
    @AppFunction(isEnabled = false, isDescribedByKDoc = true)
    override suspend fun sendCashLink(
        context: AppFunctionContext,
        amountUsd: Double,
        tokenSymbol: String,
    ): CashLinkResult = withContext(Dispatchers.IO) {
        requireLoggedIn(userManager)
        val owner = userManager.accountCluster
            ?: throw AppFunctionElementNotFoundException("Account not available")
        val token = tokenCoordinator.getTokenBySymbol(tokenSymbol)
            ?: throw AppFunctionElementNotFoundException("Token '$tokenSymbol' not found")

        val giftCard = giftCardManager.createGiftCard(token = token)
        val usdFiat = Fiat(amountUsd)
        val usdRate = Rate.oneToOne
        val verified = verifiedFiatCalculator.compute(
            amount = usdFiat,
            token = token,
            rate = usdRate,
        ).getOrElse { throw IllegalStateException("Failed to compute verified fiat", it) }

        val amount = verified.localFiat
        val verifiedState = verified.verifiedState
            ?: throw IllegalStateException("Verified state unavailable")

        suspendCancellableCoroutine { cont ->
            transactionManager.fundGiftCard(
                giftCard = giftCard,
                amount = amount,
                owner = owner,
                token = token,
                verifiedState = verifiedState,
                onFunded = { funded ->
                    tokenCoordinator.subtract(token, funded)
                    val entropy = giftCardManager.getEntropy(giftCard)
                    val url = Linkify.cashLink(entropy)
                    cont.resume(
                        CashLinkResult(
                            url = url,
                            amountUsd = amountUsd,
                            tokenSymbol = token.symbol,
                        )
                    )
                },
                onError = { error ->
                    cont.resumeWithException(error)
                },
            )
        }
    }

    /**
     * Claims a cash link and adds the funds to the user's wallet.
     *
     * @param url The cash link URL to claim (e.g. "https://send.flipcash.com/c/#/e=...").
     * Returns the amount received and the token symbol. Requires the user to be logged in.
     */
    @AppFunction(isEnabled = false, isDescribedByKDoc = true)
    override suspend fun claimCashLink(
        context: AppFunctionContext,
        url: String,
    ): ClaimResult = withContext(Dispatchers.IO) {
        requireLoggedIn(userManager)
        val owner = userManager.accountCluster
            ?: throw AppFunctionElementNotFoundException("Account not available")
        val uri = url.toUri()
        val entropy = uri.fragments[Key.entropy]
            ?: throw IllegalArgumentException("Invalid cash link URL: missing entropy")

        suspendCancellableCoroutine { cont ->
            transactionManager.receiveGiftCard(
                owner = owner,
                entropy = entropy,
                claimIfOwned = false,
                onReceived = { token, amount ->
                    tokenCoordinator.add(token, amount)
                    cont.resume(
                        ClaimResult(
                            amountUsd = amount.underlyingTokenAmount.decimalValue,
                            tokenSymbol = token.symbol,
                        )
                    )
                },
                onError = { error ->
                    cont.resumeWithException(error)
                },
            )
        }
    }
}
