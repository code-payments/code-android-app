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
import com.getcode.utils.NotifiableError
import com.getcode.utils.timedTraceSuspend

/**
 * Transactor for **receiving (claiming) a cash link** (gift card).
 *
 * Lifecycle: call [with] to set the owner and gift card entropy, then
 * [start] to look up the gift card on-chain, validate eligibility, and
 * transfer the balance into the receiver's vault. Call [dispose] to
 * clear state when finished.
 */
internal class ReceiveGiftCardTransactor(
    private val accountController: AccountController,
    private val transactionController: TransactionController,
    private val tokenProvider: TokenMetadataProvider,
    private val mnemonicManager: MnemonicManager,
    private val giftCardManager: GiftCardManager,
    private val accountClusterFactory: AccountClusterFactory,
) : Transactor<ReceiveGiftTransactorError>("Transactor::Receive") {
    private var owner: AccountCluster? = null
    private var mnemonic: MnemonicPhrase? = null
    private var giftCardAccount: GiftCardAccount? = null

    private var giftCardOwner: AccountCluster? = null

    /** Configures this transactor with the owner and gift card entropy. Must be called before [start]. */
    fun with(owner: AccountCluster, entropy: String) {
        this.owner = owner
        mnemonic = mnemonicManager.fromEntropyBase58(entropy)
        giftCardOwner = accountClusterFactory.create(
            DerivedKey.derive(DerivePath.primary, mnemonic = mnemonic!!),
        )
    }

    /**
     * Claims a gift card by its entropy — looks up the on-chain state, validates
     * eligibility, and transfers the balance into the receiver's vault.
     *
     * Flow:
     *  1. Derive the gift card's owner key pair from the entropy-based mnemonic.
     *  2. Query the server for the gift card's account info.
     *  3. Pre-claim checks:
     *     - Fail if already claimed.
     *     - Fail if expired or in an unknown state.
     *     - If the current user is the issuer and [claimIfOwned] is false,
     *       return [ReceiveGiftTransactorError.UsersGiftCard] so the caller
     *       can prompt for confirmation.
     *  4. Resolve the token mint and metadata from the gift card's account info.
     *  5. Create a user account for that token if one doesn't exist yet.
     *  6. Submit a receive-remotely intent to transfer the gift card balance
     *     into the receiver's token vault.
     *
     * Preconditions: [with] must be called first to set the owner cluster and
     * the gift card's base-58 entropy string.
     *
     * @param claimIfOwned when `true`, allows claiming even if the current user
     *   issued the gift card (self-claim).
     * @return the claimed [Token] and its [LocalFiat] value on success.
     */
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

            validateClaimEligibility(info, claimIfOwned)?.let { error ->
                onStep("pre-claim checks")
                return@timedTraceSuspend Result.failure(error)
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
                    logAndFail(it)
                }
            )
        }
    }

    /** Clears all held state. */
    fun dispose() {
        owner = null
        giftCardAccount = null
    }

    companion object {
        /**
         * Validates whether a gift card account is eligible to be claimed.
         *
         * @return null if eligible, or the appropriate error if not.
         */
        fun validateClaimEligibility(
            info: AccountInfo,
            claimIfOwned: Boolean,
        ): ReceiveGiftTransactorError? {
            if (info.claimState == AccountInfo.ClaimState.Claimed) {
                return ReceiveGiftTransactorError.AlreadyClaimed()
            }
            if (info.claimState == AccountInfo.ClaimState.Expired || info.claimState == AccountInfo.ClaimState.Unknown) {
                return ReceiveGiftTransactorError.Expired()
            }
            if (info.isGiftCardIssuer && !claimIfOwned) {
                return ReceiveGiftTransactorError.UsersGiftCard()
            }
            return null
        }
    }
}

sealed class ReceiveGiftTransactorError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class FailedToQuery(
        override val message: String? = null,
        override val cause: Throwable? = null
    ) : ReceiveGiftTransactorError(message = message?.let { "Failed to query account - $it" } ?: "Failed to query account"), NotifiableError
    class AlreadyClaimed : ReceiveGiftTransactorError(message = "Already claimed")
    class UsersGiftCard : ReceiveGiftTransactorError(message = "User is gift card issuer")
    class Expired : ReceiveGiftTransactorError(message = "Expired")

    data class Other(
        override val message: String? = null,
        override val cause: Throwable? = null
    ) : ReceiveGiftTransactorError(message, cause), NotifiableError
}