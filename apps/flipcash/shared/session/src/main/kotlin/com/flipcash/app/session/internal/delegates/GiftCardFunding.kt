package com.flipcash.app.session.internal.delegates

import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.tokens.TokenCoordinator
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Funds a cash link's gift card and takes it back, keeping the local balance in step.
 *
 * Shared by the two ways a cash link leaves the app: the bill's "Send as Link", which shares it
 * ([GiftCardSharingDelegate]), and a group chat, which posts it ([ChatCashLinkDelegate]). Neither
 * call presents anything; each caller owns what the user sees when a step fails.
 */
@Singleton
class GiftCardFunding @Inject constructor(
    private val billController: BillController,
    private val tokenCoordinator: TokenCoordinator,
    private val transactionController: TransactionController,
) {

    /** Funds [giftCard] with [amount] and debits the local balance once the intent lands. */
    suspend fun fund(
        giftCard: GiftCardAccount,
        owner: AccountCluster,
        amount: LocalFiat,
        token: Token,
        verifiedState: VerifiedState,
    ): Result<LocalFiat> = suspendCancellableCoroutine { cont ->
        billController.fundGiftCard(
            giftCard = giftCard,
            amount = amount,
            token = token,
            owner = owner,
            verifiedState = verifiedState,
            onFunded = {
                tokenCoordinator.subtract(token, amount)
                cont.resume(Result.success(it))
            },
            onError = { cont.resume(Result.failure(it)) },
        )
    }

    /** Cancels [giftCard]'s remote send, returning its funds to [owner], and refreshes balances. */
    suspend fun cancel(
        owner: AccountCluster,
        giftCard: GiftCardAccount,
    ): Result<Unit> = transactionController.cancelRemoteSend(
        vault = giftCard.cluster.vaultPublicKey,
        owner = owner,
    ).onSuccess { tokenCoordinator.update() }
}
