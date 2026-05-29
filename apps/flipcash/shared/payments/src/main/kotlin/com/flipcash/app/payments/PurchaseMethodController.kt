package com.flipcash.app.payments

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PurchaseMethodController {
    val state: StateFlow<PurchaseMethodState>
    val selections: Flow<PurchaseMethodSelection>
    fun present(metadata: PurchaseMethodMetadata = PurchaseMethodMetadata())
    fun select(method: PurchaseMethod, metadata: PurchaseMethodMetadata)
}
