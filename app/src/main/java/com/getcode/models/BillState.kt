package com.getcode.models

import androidx.compose.runtime.Composable
import com.getcode.model.CodePayload
import com.getcode.model.Domain
import com.getcode.model.KinAmount
import com.getcode.util.formatted

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
        get() = when (bill) {
            is Bill.Cash -> true
            is Bill.Login -> true
            else -> false
        }

    companion object {
        val Default = BillState(
            bill = null,
            showToast = false,
            toast = null,
            valuation = null,
            paymentConfirmation = null,
            loginConfirmation = null,
            hideBillButtons = false
        )
    }
}

sealed interface Bill {
    val didReceive: Boolean
    val amount: KinAmount
    val data: List<Byte>

    enum class Kind {
        cash, remote, firstKin, referral
    }

    val metadata: Metadata
        get() {
            return when (this) {
                is Cash -> Metadata(
                    kinAmount = amount,
                    data = data
                )

                is Payment -> Metadata(
                    kinAmount = amount,
                    data = payload.codeData.toList(),
                    request = request,
                )

                is Login -> Metadata(
                    kinAmount = amount,
                    data = payload.codeData.toList(),
                    request = request,
                )
            }
        }

    data class Cash(
        override val amount: KinAmount,
        override val didReceive: Boolean = false,
        override val data: List<Byte> = emptyList(),
        val kind: Kind = Kind.cash
    ) : Bill

    data class Payment(
        override val amount: KinAmount,
        val payload: CodePayload,
        val request: DeepLinkPaymentRequest? = null
    ) : Bill {
        override val didReceive: Boolean = false
        override val data: List<Byte> = payload.codeData.toList()
    }

    data class Login(
        override val amount: KinAmount,
        val payload: CodePayload,
        val request: DeepLinkPaymentRequest? = null
    ) : Bill {
        override val didReceive: Boolean = false
        override val data: List<Byte> = payload.codeData.toList()
    }
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
        @Composable get() = StringBuilder()
            .append(if (isDeposit) "+" else "-")
            .append(amount.formatted())
            .toString()
}

data class PaymentConfirmation(
    val state: PaymentState,
    val payload: CodePayload,
    val requestedAmount: KinAmount,
    val localAmount: KinAmount,
)

data class LoginConfirmation(
    val payload: CodePayload,
    val domain: Domain,
)

sealed interface PaymentState {
    data object AwaitingConfirmation : PaymentState
    data object Sending : PaymentState
    data object Sent : PaymentState
    data class Error(val exception: Throwable) : PaymentState
}

data class Metadata(
    val kinAmount: KinAmount,
    val data: List<Byte>,
    val request: DeepLinkPaymentRequest? = null
)