package com.flipcash.app.core.bill

import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.managers.BillTransactionManager
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.transactions.TransactionMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillController @Inject constructor(
    private val transactionManager: BillTransactionManager,
) {
    private val _state = MutableStateFlow(BillState.Default)
    val state: StateFlow<BillState>
        get() = _state

    fun update(function: (BillState) -> BillState) {
        _state.update(function)
    }

    fun reset(showToast: Boolean = false) {
        _state.update { BillState.Default.copy(showToast = showToast) }
        transactionManager.cancelSend()
    }

    fun awaitGrab(
        amount: LocalFiat,
        owner: AccountCluster,
        present: (List<Byte>) -> Unit,
        onGrabbed: () -> Unit,
        onTimeout: () -> Unit,
        onError: (Throwable) -> Unit,
    ) = transactionManager.awaitGrabFromRecipient(amount, owner, present, onGrabbed, onTimeout, onError)

    fun attemptGrab(
        owner: AccountCluster,
        payload: OpenCodePayload,
        onGrabbed: (LocalFiat) -> Unit,
        onError: (Throwable) -> Unit,
    ) = transactionManager.attemptGrabFromSender(owner, payload, onGrabbed, onError)
}