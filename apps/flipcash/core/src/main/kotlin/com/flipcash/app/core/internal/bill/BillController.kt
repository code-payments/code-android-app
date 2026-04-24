package com.flipcash.app.core.internal.bill

import com.flipcash.app.core.bill.BillState
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.user.UserManager
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.internal.transactors.BillPresentationData
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

/**
 * UI-facing facade for bill transactions. Owns the [BillState] flow and
 * delegates to [BillTransactionManager] for all four payment flows (give,
 * grab, send cash link, receive cash link).
 */
@Singleton
class BillController @Inject constructor(
    private val transactionManager: BillTransactionManager,
    private val userFlags: UserFlagsCoordinator,
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

    /**
     * Initiates the **give** flow — presents a bill and waits for a recipient to
     * grab it. Delegates to [BillTransactionManager.awaitGrabFromRecipient],
     * injecting the configured exchange-data timeout from user flags.
     */
    fun awaitGrab(
        amount: LocalFiat,
        token: Token,
        owner: AccountCluster,
        verifiedState: VerifiedState?,
        nonce: List<Byte>? = null,
        present: (BillPresentationData) -> Unit,
        onGrabbed: suspend (LocalFiat) -> Unit,
        onTimeout: () -> Unit,
        onError: (Throwable) -> Unit,
    ) = transactionManager.awaitGrabFromRecipient(
        token = token,
        amount = amount,
        owner = owner,
        verifiedState = verifiedState,
        billExchangeDataTimeout = userFlags.resolvedFlags.value.billExchangeDataTimeout.effectiveValue,
        nonce = nonce,
        present = present,
        onGrabbed = onGrabbed,
        onTimeout = onTimeout,
        onError = onError,
    )

    fun cancelAwaitForGrab() = transactionManager.cancelAwaitForGrab()

    /** Initiates the **grab** flow — claims a scanned bill from a sender. */
    fun attemptGrab(
        owner: AccountCluster,
        payload: OpenCodePayload,
        onGrabbed: suspend (Token, LocalFiat, VerifiedState?) -> Unit,
        onError: (Throwable) -> Unit,
    ) = transactionManager.attemptGrabFromSender(owner, payload, onGrabbed, onError)

    /** Initiates the **send cash link** flow — funds a gift card for remote sending. */
    fun fundGiftCard(
        giftCard: GiftCardAccount,
        amount: LocalFiat,
        token: Token,
        owner: AccountCluster,
        verifiedState: VerifiedState,
        onFunded: suspend (LocalFiat) -> Unit,
        onError: (Throwable) -> Unit,
    ) = transactionManager.fundGiftCard(giftCard, amount, owner, token, verifiedState, onFunded, onError)

    /** Initiates the **receive cash link** flow — claims a gift card by entropy. */
    fun receiveGiftCard(
        entropy: String,
        owner: AccountCluster,
        claimIfOwned: Boolean,
        onReceived: suspend (Token, LocalFiat,) -> Unit,
        onError: (Throwable) -> Unit,
    ) = transactionManager.receiveGiftCard(owner, entropy, claimIfOwned, onReceived, onError)
}