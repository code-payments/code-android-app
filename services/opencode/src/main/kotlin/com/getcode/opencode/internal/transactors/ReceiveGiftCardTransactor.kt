package com.getcode.opencode.internal.transactors

import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.managers.GiftCardManager
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.CodeServerError
import com.getcode.utils.ErrorUtils
import com.getcode.utils.timedTrace
import com.getcode.utils.timedTraceSuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

internal class ReceiveGiftCardTransactor(
    private val accountController: AccountController,
    private val transactionController: TransactionController,
    private val mnemonicManager: MnemonicManager,
    private val giftCardManager: GiftCardManager,
): Transactor<ReceiveGiftTransactorError>("Transactor::Receive") {
    private var owner: AccountCluster? = null
    private var giftCardAccount: GiftCardAccount? = null

    fun with(owner: AccountCluster, entropy: String) {
        this.owner = owner
        val mnemonic = mnemonicManager.fromEntropyBase58(entropy)
        giftCardAccount = giftCardManager.createGiftCard(mnemonic)
    }

    suspend fun start(claimIfOwned: Boolean): Result<LocalFiat> {
        val ownerKey = owner ?: return logAndFail(ReceiveGiftTransactorError.Other(message = "No owner key. Did you call with() first?"))
        val giftCard = giftCardAccount ?: return logAndFail(
            ReceiveGiftTransactorError.Other(
                message = "No gift card account. Did you call with() first?"
            )
        )

        return timedTraceSuspend("Gift card claim processing") { onStep ->

        // before we can receive the gift card
        // we need to determine the balance of it
        val accounts = accountController.getAccounts(
            accountOwner = giftCard.cluster,
            requestingOwner = ownerKey
        ).getOrElse {
            onStep("account query")
            return@timedTraceSuspend logAndFail(ReceiveGiftTransactorError.FailedToQuery())
        }.takeIf { it.isNotEmpty() }
            ?: run {
                onStep("account query")
                return@timedTraceSuspend  logAndFail(ReceiveGiftTransactorError.FailedToQuery())
            }

            onStep("account query")
            val info = accounts.values.first()

            if (info.claimState == AccountInfo.ClaimState.Claimed) {
                onStep("pre-claim checks")
                return@timedTraceSuspend logAndFail(ReceiveGiftTransactorError.AlreadyClaimed())
            }

            if (info.claimState == AccountInfo.ClaimState.Expired || info.claimState == AccountInfo.ClaimState.Unknown) {
                onStep("pre-claim checks")
                return@timedTraceSuspend logAndFail(ReceiveGiftTransactorError.Expired())
            }

            if (info.isGiftCardIssuer && !claimIfOwned) {
                onStep("pre-claim checks")
                return@timedTraceSuspend Result.failure(ReceiveGiftTransactorError.UsersGiftCard())
            }

            val exchangeData = info.originalExchangeData
            val amount = LocalFiat(exchangeData)

            return@timedTraceSuspend transactionController.receiveRemotely(
                giftCard = giftCard,
                amount = amount,
                owner = ownerKey
            ).fold(
                onSuccess = {
                    onStep("intent")
                    Result.success(amount) },
                onFailure = {
                    onStep("intent")
                    if (it !is ReceiveGiftTransactorError) {
                        ErrorUtils.handleError(it)
                    }
                    logAndFail(it)
                }
            )
        }
    }

    fun dispose() {
        owner = null
        giftCardAccount = null
    }
}

sealed class ReceiveGiftTransactorError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class FailedToQuery: GrabTransactorError(message = "Failed to query account")
    class AlreadyClaimed: GrabTransactorError(message = "Already claimed")
    class UsersGiftCard: GrabTransactorError(message = "User is gift card issuer")
    class Expired: GrabTransactorError(message = "Expired")

    data class Other(
        override val message: String? = null,
        override val cause: Throwable? = null
    ) : GrabTransactorError(message, cause)
}