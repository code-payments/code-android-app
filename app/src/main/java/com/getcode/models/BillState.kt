package com.getcode.models

import androidx.compose.runtime.Composable
import com.getcode.model.CodePayload
import com.getcode.model.KinAmount
import com.getcode.network.repository.Request
import com.getcode.util.formatted

data class BillState(
    val bill: Bill? = null,
    val showToast: Boolean = false,
    val toast: BillToast? = null,
    val valuation: Valuation? = null,
    val paymentConfirmation: PaymentConfirmation? = null,
    val hideBillButtons: Boolean = false
) {
    val canSwipeToDismiss: Boolean
        get() = when (bill) {
            is Bill.Cash -> true
            else -> false
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
                    kinAmount = request.amount,
                    data = request.payload.codeData.toList(),
                )
            }
        }

    data class Cash(
        override val amount: KinAmount,
        override val didReceive: Boolean = false,
        override val data: List<Byte> = emptyList(),
        val kind: Kind = Kind.cash
    ) : Bill

    data class Payment(val request: Request) : Bill {
        override val didReceive: Boolean = false
        override val amount: KinAmount = request.amount
        override val data: List<Byte> = request.payload.codeData.toList()
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
            .append(amount.formatted(false))
            .toString()
}

data class PaymentConfirmation(
    val state: PaymentState,
    val payload: CodePayload,
    val requestedAmount: KinAmount,
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
)