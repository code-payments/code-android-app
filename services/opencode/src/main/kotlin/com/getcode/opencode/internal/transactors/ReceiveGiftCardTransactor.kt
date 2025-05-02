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
import com.getcode.utils.CodeServerError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

internal class ReceiveGiftCardTransactor(
    private val accountController: AccountController,
    private val transactionController: TransactionController,
    private val scope: CoroutineScope,
    private val mnemonicManager: MnemonicManager,
    private val giftCardManager: GiftCardManager,
) {
    private var owner: AccountCluster? = null
    private var giftCardAccount: GiftCardAccount? = null

    fun with(owner: AccountCluster, entropy: String) {
        this.owner = owner
        val mnemonic = mnemonicManager.fromEntropyBase58(entropy)
        giftCardAccount = giftCardManager.createGiftCard(mnemonic)
    }

    suspend fun start(): Result<LocalFiat> {
        val ownerKey = owner ?: return Result.failure(ReceiveGiftTransactorError.Other(message = "No owner key. Did you call with() first?"))
        val giftCard = giftCardAccount ?: return Result.failure(
            ReceiveGiftTransactorError.Other(
                message = "No gift card account. Did you call with() first?"
            )
        )

        // before we can receive the gift card
        // we need to determine the balance of it
        val accounts = accountController.getAccounts(giftCard.cluster)
            .getOrElse { return Result.failure(ReceiveGiftTransactorError.FailedToQuery()) }
            .takeIf { it.isNotEmpty() }
            ?: return Result.failure(ReceiveGiftTransactorError.FailedToQuery())

        val info = accounts.values.first()

        if (info.claimState == AccountInfo.ClaimState.Claimed) {
            return Result.failure(ReceiveGiftTransactorError.AlreadyClaimed())
        }

        if (info.claimState == AccountInfo.ClaimState.Expired || info.claimState == AccountInfo.ClaimState.Unknown) {
            return Result.failure(ReceiveGiftTransactorError.Expired())
        }

        val exchangeData = info.originalExchangeData
        val amount = LocalFiat(exchangeData)

        return transactionController.receiveRemotely(
            giftCard = giftCard,
            amount = amount,
            owner = ownerKey
        ).fold(
            onSuccess = { Result.success(amount) },
            onFailure = { Result.failure(it) }
        )
    }

    fun dispose() {
        owner = null
        giftCardAccount = null

        scope.cancel()
    }
}

sealed class ReceiveGiftTransactorError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class FailedToQuery: GrabTransactorError()
    class AlreadyClaimed: GrabTransactorError()
    class Expired: GrabTransactorError()

    data class Other(
        override val message: String? = null,
        override val cause: Throwable? = null
    ) : GrabTransactorError()
}