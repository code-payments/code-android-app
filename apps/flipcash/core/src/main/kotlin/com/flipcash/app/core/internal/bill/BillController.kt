package com.flipcash.app.core.internal.bill

import com.flipcash.app.core.bill.BillState
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.managers.BillTransactionManager
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillController @Inject constructor(
    private val transactionManager: BillTransactionManager,
    private val userManager: UserManager,
) {
    private val _state = MutableStateFlow(BillState.Default)
    val state: StateFlow<BillState>
        get() = _state

    fun update(function: (BillState) -> BillState) {
        _state.update(function)
    }

    fun reset(showToast: Boolean = false) {
        _state.update { state ->
            BillState.Default.copy(showToast = showToast, toast = state.toast)
        }
        transactionManager.reset()
    }

    fun awaitGrab(
        amount: LocalFiat,
        token: Token,
        owner: AccountCluster,
        present: (List<Byte>) -> Unit,
        onGrabbed: suspend (LocalFiat) -> Unit,
        onTimeout: () -> Unit,
        onError: (Throwable) -> Unit,
    ) = transactionManager.awaitGrabFromRecipient(
        token = token,
        amount = amount,
        owner = owner,
        billExchangeDataTimeout = userManager.userFlags?.billExchangeDataTimeout,
        present = present,
        onGrabbed = onGrabbed,
        onTimeout = onTimeout,
        onError = onError,
    )

    fun cancelAwaitForGrab() = transactionManager.cancelAwaitForGrab()

    fun attemptGrab(
        owner: AccountCluster,
        payload: OpenCodePayload,
        onGrabbed: suspend (Token, LocalFiat) -> Unit,
        onError: (Throwable) -> Unit,
    ) = transactionManager.attemptGrabFromSender(owner, payload, onGrabbed, onError)

    fun fundGiftCard(
        giftCard: GiftCardAccount,
        amount: LocalFiat,
        token: Token,
        owner: AccountCluster,
        onFunded: suspend (LocalFiat) -> Unit,
        onError: (Throwable) -> Unit,
    ) = transactionManager.fundGiftCard(giftCard, amount, owner, token, onFunded, onError)

    fun receiveGiftCard(
        entropy: String,
        owner: AccountCluster,
        claimIfOwned: Boolean,
        onReceived: suspend (Token, LocalFiat) -> Unit,
        onError: (Throwable) -> Unit,
    ) = transactionManager.receiveGiftCard(owner, entropy, claimIfOwned, onReceived, onError)
}