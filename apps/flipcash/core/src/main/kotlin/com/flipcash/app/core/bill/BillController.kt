package com.flipcash.app.core.bill

import com.getcode.opencode.internal.model.account.AccountCluster
import com.getcode.opencode.managers.BillTransactionManager
import com.getcode.opencode.model.financial.LocalFiat
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

    fun reset() {
        _state.update { BillState.Default }
    }

    fun awaitGrab(
        amount: LocalFiat,
        owner: AccountCluster,
        present: (List<Byte>) -> Unit,
        onGrabbed: () -> Unit,
        onTimeout: () -> Unit,
        onError: (Throwable) -> Unit,
    ) = transactionManager.awaitGrabFromRecipient(amount, owner, present, onGrabbed, onTimeout, onError)
}