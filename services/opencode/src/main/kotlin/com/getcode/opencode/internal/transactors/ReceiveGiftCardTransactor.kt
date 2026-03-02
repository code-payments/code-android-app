package com.getcode.opencode.internal.transactors

import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicPhrase
import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.managers.GiftCardManager
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.providers.TokenMetadataProvider
import com.getcode.utils.CodeServerError
import com.getcode.utils.ErrorUtils
import com.getcode.utils.timedTraceSuspend

internal class ReceiveGiftCardTransactor(
    private val accountController: AccountController,
    private val transactionController: TransactionController,
    private val tokenProvider: TokenMetadataProvider,
    private val mnemonicManager: MnemonicManager,
    private val giftCardManager: GiftCardManager,
) : Transactor<ReceiveGiftTransactorError>("Transactor::Receive") {
    private var owner: AccountCluster? = null
    private var mnemonic: MnemonicPhrase? = null
    private var giftCardAccount: GiftCardAccount? = null

    private var giftCardOwner: AccountCluster? = null

    fun with(owner: AccountCluster, entropy: String) {
        this.owner = owner
        mnemonic = mnemonicManager.fromEntropyBase58(entropy)
        giftCardOwner = AccountCluster.newInstance(
            DerivedKey.derive(DerivePath.primary, mnemonic = mnemonic!!),
        )
    }

    suspend fun start(claimIfOwned: Boolean): Result<Pair<Token, LocalFiat>> {
        val requestingOwner = owner
            ?: return logAndFail(ReceiveGiftTransactorError.Other(message = "No owner key. Did you call with() first?"))
        val accountOwner = giftCardOwner ?: return logAndFail(
            ReceiveGiftTransactorError.Other(
                message = "No account owner. Did you call with() first?"
            )
        )
        val mnemonicPhrase = mnemonic
            ?: return logAndFail(ReceiveGiftTransactorError.Other(message = "No mnemonic. Did you call with() first?"))

        return timedTraceSuspend("Gift card claim processing") { onStep ->

            // before we can receive the gift card
            // we need to determine the balance of it
            val accounts = accountController.getAccounts(
                accountOwner = accountOwner,
                requestingOwner = requestingOwner
            ).getOrElse {
                onStep("account query")
                return@timedTraceSuspend logAndFail(ReceiveGiftTransactorError.FailedToQuery(cause = it))
            }.takeIf { it.accounts.isNotEmpty() }?.accounts
                ?: run {
                    onStep("account query")
                    return@timedTraceSuspend logAndFail(ReceiveGiftTransactorError.FailedToQuery(message = "No accounts found"))
                }

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

            val tokenMint = info.mint

            val token = tokenProvider.getTokenMetadata(tokenMint)
                .getOrNull()?.token

            if (token == null) {
                onStep("pre-claim checks")
                return@timedTraceSuspend logAndFail(ReceiveGiftTransactorError.Other(message = "Token not found"))
            }

            val giftCard = giftCardManager.createGiftCard(mnemonicPhrase, token)

            val amount = LocalFiat(info.originalExchangeData)

            val requestingTokenOwner = requestingOwner.withTimelockForToken(token)

            // 3. create an account if we don't currently have one for this token
            val userAccountResult = if (!accountController.hasAccountFor(token.address)) {
                accountController.createUserAccount(
                    ownerForMint = requestingTokenOwner,
                    mint = token.address
                )
            } else {
                Result.success(Unit)
            }

            return@timedTraceSuspend userAccountResult.map {
                transactionController.receiveRemotely(
                    giftCard = giftCard,
                    amount = amount,
                    owner = requestingTokenOwner,
                    mint = token.address
                )
            }.fold(
                onSuccess = {
                    onStep("intent")
                    Result.success(token to amount)
                },
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
    class FailedToQuery(
        override val message: String? = null,
        override val cause: Throwable? = null
    ) : GrabTransactorError(message = message?.let { "Failed to query account - $it" } ?: "Failed to query account")
    class AlreadyClaimed : GrabTransactorError(message = "Already claimed")
    class UsersGiftCard : GrabTransactorError(message = "User is gift card issuer")
    class Expired : GrabTransactorError(message = "Expired")

    data class Other(
        override val message: String? = null,
        override val cause: Throwable? = null
    ) : GrabTransactorError(message, cause)
}