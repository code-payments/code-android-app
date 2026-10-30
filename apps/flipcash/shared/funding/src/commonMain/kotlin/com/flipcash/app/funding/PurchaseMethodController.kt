package com.flipcash.app.funding

import com.flipcash.app.core.AppRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PurchaseMethodController {
    val state: StateFlow<PurchaseMethodState>
    val selections: Flow<PurchaseMethodSelection>
    fun present(metadata: PurchaseMethodMetadata = PurchaseMethodMetadata())
    fun select(method: PurchaseMethod, metadata: PurchaseMethodMetadata)
    suspend fun presentDepositOptions(popToRoot: Boolean = false): AppRoute?
}
