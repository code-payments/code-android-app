package com.flipcash.app.payments

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface PaymentController {
    val state: StateFlow<PaymentState>
    val paymentEvents: SharedFlow<PaymentEvent>
    val confirmationEvents: SharedFlow<ConfirmationEvent>
    fun requestPaymentConfirmation(request: PaymentRequest<*>)
    fun completeRequest()
    fun cancelRequest(fromUser: Boolean = true)
}

private object StubPaymentController : PaymentController {
    override val state: StateFlow<PaymentState> = MutableStateFlow(PaymentState.Default)
    override val paymentEvents: SharedFlow<PaymentEvent> = MutableSharedFlow()
    override val confirmationEvents: SharedFlow<ConfirmationEvent> = MutableSharedFlow()

    override fun requestPaymentConfirmation(request: PaymentRequest<*>) = Unit
    override fun completeRequest() = Unit
    override fun cancelRequest(fromUser: Boolean) = Unit
}

val LocalPaymentController = staticCompositionLocalOf<PaymentController> { StubPaymentController }
