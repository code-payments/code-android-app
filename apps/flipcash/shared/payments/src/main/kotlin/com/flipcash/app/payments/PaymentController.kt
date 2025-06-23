package com.flipcash.app.payments

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface PaymentController {
    val state: StateFlow<PaymentState>
    val eventFlow: SharedFlow<PaymentEvent>
    fun presentPublicPaymentConfirmation(
        request: PaymentRequest,
    )
    fun completePublicPayment()
    fun cancelPayment(fromUser: Boolean = true)
}

private object StubPaymentController : PaymentController {
    override val state: StateFlow<PaymentState> = MutableStateFlow(PaymentState())
    override val eventFlow: SharedFlow<PaymentEvent> = MutableSharedFlow()

    override fun presentPublicPaymentConfirmation(request: PaymentRequest) = Unit
    override fun completePublicPayment() = Unit
    override fun cancelPayment(fromUser: Boolean) = Unit
}

val LocalPaymentController = staticCompositionLocalOf<PaymentController> { StubPaymentController }
