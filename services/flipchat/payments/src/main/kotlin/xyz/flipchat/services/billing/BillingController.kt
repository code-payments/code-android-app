package xyz.flipchat.services.billing

import android.app.Activity
import androidx.compose.runtime.staticCompositionLocalOf

sealed interface IapPaymentEvent {
    data object OnSuccess : IapPaymentEvent
    data object OnCancelled : IapPaymentEvent
    data class OnError(val error: Throwable): IapPaymentEvent
}

class IapPaymentError(val code: Int, override val message: String): Throwable(message)

enum class BillingClientState {
    Disconnected,
    Connecting,
    Connected,
    ConnectionLost,
    Failed,
}

val LocalIapController = staticCompositionLocalOf<BillingController> { NoOpBillingController }

interface BillingController {
    fun connect()
    fun disconnect()
    fun hasPaidFor(product: IapProduct): Boolean
    fun costOf(product: IapProduct): String
    suspend fun purchase(activity: Activity, product: IapProduct)
}

object NoOpBillingController: BillingController {
    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun hasPaidFor(product: IapProduct): Boolean = false
    override fun costOf(product: IapProduct): String = "$0.00"
    override suspend fun purchase(activity: Activity, product: IapProduct) = Unit
}