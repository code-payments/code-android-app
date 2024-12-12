package xyz.flipchat.services.billing

import android.app.Activity
import androidx.compose.runtime.staticCompositionLocalOf
import com.android.billingclient.api.BillingResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.seconds

sealed interface IapPaymentEvent {
    data class OnSuccess(val productId: String) : IapPaymentEvent
    data object OnCancelled : IapPaymentEvent
    data class OnError(val productId: String, val error: Throwable): IapPaymentEvent
}

class IapPaymentError(val code: Int, override val message: String): Throwable(message) {
    constructor(result: BillingResult): this(result.responseCode, result.debugMessage)
}

enum class BillingClientState {
    Disconnected,
    Connecting,
    Connected,
    ConnectionLost,
    Failed;

    fun canConnect() = this == Disconnected || this == ConnectionLost || this == Failed
}

val LocalIapController = staticCompositionLocalOf<BillingController> { StubBillingController }

interface BillingController {
    val eventFlow: SharedFlow<IapPaymentEvent>
    val state: StateFlow<BillingClientState>

    fun connect()
    fun disconnect()
    fun hasPaidFor(product: IapProduct): Boolean
    fun costOf(product: IapProduct): String
    suspend fun purchase(activity: Activity, product: IapProduct)
}

object StubBillingController: BillingController {
    private val _eventFlow: MutableSharedFlow<IapPaymentEvent> = MutableSharedFlow()
    override val eventFlow: SharedFlow<IapPaymentEvent> = _eventFlow.asSharedFlow()

    private val _stateFlow = MutableStateFlow(BillingClientState.Disconnected)
    override val state: StateFlow<BillingClientState> = _stateFlow.asStateFlow()

    data class State(
        val connected: Boolean = false,
        val failedToConnect: Boolean = false,
    )

    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun hasPaidFor(product: IapProduct): Boolean = false
    override fun costOf(product: IapProduct): String = "NOT_DEFINED"
    override suspend fun purchase(activity: Activity, product: IapProduct) {
        delay(1.seconds)
        _eventFlow.emit(IapPaymentEvent.OnSuccess(product.productId))
    }
}