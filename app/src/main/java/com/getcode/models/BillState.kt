package com.getcode.models

import androidx.compose.runtime.Composable
import com.getcode.model.CodePayload
import com.getcode.model.Domain
import com.getcode.model.KinAmount
import com.getcode.util.formatted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class BillState(
    val bill: Bill? = null,
    val showToast: Boolean = false,
    val toast: BillToast? = null,
    val valuation: Valuation? = null,
    val paymentConfirmation: PaymentConfirmation? = null,
    val loginConfirmation: LoginConfirmation? = null,
    val hideBillButtons: Boolean = false
) {
    val canSwipeToDismiss: Boolean
        get() = bill is Bill.Cash || bill is Bill.Login

    companion object {
        val Default = BillState()
    }
}

sealed interface Bill {
    val didReceive: Boolean
    val amount: KinAmount
    val data: List<Byte>
    val kind: Kind
    val metadata: Metadata
        get() = Metadata(
            kinAmount = amount,
            data = data,
            request = this as? HasDeepLinkRequest
        )

    enum class Kind {
        Cash, Remote, FirstKin, Referral
    }
}

interface HasDeepLinkRequest {
    val request: DeepLinkRequest?
}

data class Cash(
    override val amount: KinAmount,
    override val didReceive: Boolean = false,
    override val data: List<Byte> = emptyList(),
    override val kind: Bill.Kind = Bill.Kind.Cash
) : Bill

data class Payment(
    override val amount: KinAmount,
    val payload: CodePayload,
    override val request: DeepLinkRequest? = null
) : Bill, HasDeepLinkRequest {
    override val didReceive: Boolean = false
    override val data: List<Byte> = payload.codeData.toList()
    override val kind: Bill.Kind = Bill.Kind.Remote
}

data class Login(
    override val amount: KinAmount,
    val payload: CodePayload,
    override val request: DeepLinkRequest? = null
) : Bill, HasDeepLinkRequest {
    override val didReceive: Boolean = false
    override val data: List<Byte> = payload.codeData.toList()
    override val kind: Bill.Kind = Bill.Kind.Remote
}

val Bill.amountFloored: KinAmount
    get() = amount.copy(kin = amount.kin.toKinTruncating())

data class Valuation(
    val amount: KinAmount,
)

data class BillToast(
    val amount: KinAmount,
    val isDeposit: Boolean,
) {
    val formattedAmount: String
        @Composable get() = (if (isDeposit) "+" else "-") + amount.formatted()
}

data class PaymentConfirmation(
    val state: PaymentState,
    val payload: CodePayload,
    val requestedAmount: KinAmount,
    val localAmount: KinAmount,
)

data class LoginConfirmation(
    val state: LoginState,
    val payload: CodePayload,
    val domain: Domain,
)

sealed interface PaymentState {
    object AwaitingConfirmation : PaymentState
    object Sending : PaymentState
    object Sent : PaymentState
    data class Error(val errorType: PaymentError) : PaymentState
}

sealed interface LoginState {
    object AwaitingConfirmation : LoginState
    object Sending : LoginState
    object Sent : LoginState
    data class Error(val errorType: LoginError) : LoginState
}

sealed class PaymentError(val message: String) {
    class NetworkError(message: String) : PaymentError(message)
    class ValidationError(message: String) : PaymentError(message)
    // Add other specific error types as needed.
}

sealed class LoginError(val message: String) {
    class NetworkError(message: String) : LoginError(message)
    class CredentialsError(message: String) : LoginError(message)
    // Add other specific error types as needed.
}

data class Metadata(
    val kinAmount: KinAmount,
    val data: List<Byte>,
    val request: DeepLinkRequest? = null
)
