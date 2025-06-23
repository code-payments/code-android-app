package com.flipcash.app.payments

import com.flipcash.app.core.bill.BillState

data class PaymentState(
    val billState: BillState = BillState.Default,
)