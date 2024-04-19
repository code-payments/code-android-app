package com.getcode.models

import androidx.compose.runtime.Composable
import com.getcode.R
import com.getcode.model.CodePayload
import com.getcode.model.TipMetadata
import com.getcode.model.Domain
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.model.Rate
import com.getcode.model.TwitterUser
import com.getcode.util.formatted

sealed interface ShareAction {

    val label: Int
    data object Send: ShareAction {
        override val label: Int
            get() = R.string.action_send
    }

    data object Share: ShareAction {
        override val label: Int
            get() = R.string.action_share
    }
}
data class BillState(
    val bill: Bill?,
    val showToast: Boolean,
    val toast: BillToast?,
    val valuation: Valuation?,
    val paymentConfirmation: PaymentConfirmation?,
    val loginConfirmation: LoginConfirmation?,
    val tipConfirmation: TipConfirmation?,
    val shareAction: ShareAction?,
    val showCancelAction: Boolean,
) {
    val hideBillButtons: Boolean
        get() = shareAction == null && !showCancelAction
    val canSwipeToDismiss: Boolean
        get() = bill?.canSwipeToDismiss ?: false

    companion object {
        val Default = BillState(
            bill = null,
            showToast = false,
            toast = null,
            valuation = null,
            paymentConfirmation = null,
            loginConfirmation = null,
            tipConfirmation = null,
            shareAction = null,
            showCancelAction = false
        )
    }
}

sealed interface Bill {
    val didReceive: Boolean
    val amount: KinAmount
    val data: List<Byte>

    enum class Kind {
        cash, remote, firstKin, referral, tip
    }

    val canSwipeToDismiss: Boolean
        get() = when (this) {
            is Cash -> true
            is Login -> false
            is Payment -> false
            is Tip -> true
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

                is Tip -> Metadata(
                    kinAmount = amount,
                    data = data
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
        val request: DeepLinkRequest? = null
    ) : Bill {
        override val didReceive: Boolean = false
        override val data: List<Byte> = payload.codeData.toList()
    }

    data class Login(
        override val amount: KinAmount,
        val payload: CodePayload,
        val request: DeepLinkRequest? = null
    ) : Bill {
        override val didReceive: Boolean = false
        override val data: List<Byte> = payload.codeData.toList()
    }

    data class Tip(
        val payload: CodePayload,
        val request: DeepLinkRequest? = null
    ) : Bill {
        override val amount: KinAmount = KinAmount.newInstance(Kin.fromKin(0), Rate.oneToOne)
        override val didReceive: Boolean = false
        override val data: List<Byte> = payload.codeData.toList()
    }
}

val Bill.amountFloored: KinAmount
    get() = amount.copy(kin = amount.kin.toKinTruncating())

sealed interface Valuation
data class PaymentValuation(val amount: KinAmount): Valuation

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
    val state: ConfirmationState,
    val payload: CodePayload,
    val requestedAmount: KinAmount,
    val localAmount: KinAmount,
)

data class LoginConfirmation(
    val state: ConfirmationState,
    val payload: CodePayload,
    val domain: Domain,
)

data class TipConfirmation(
    val state: ConfirmationState,
    val amount: KinAmount,
    val payload: CodePayload?,
    val metadata: TipMetadata,
) {
    val imageUrl: String?
        get() {
            return when (metadata) {
                is TwitterUser -> {
                    metadata.imageUrlSanitized
                }

                else -> null
            }
        }

    val followerCountFormatted: String?
        get() {
            return when (metadata) {
                is TwitterUser -> {
                    when  {
                        metadata.followerCount > 1000 -> {
                            val decimal = metadata.followerCount.toDouble() / 1_000
                            val formattedString = String.format("%.1fK", decimal)
                            formattedString
                        }
                        else -> metadata.followerCount.toString()
                    }
                }
                else -> null
            }
        }
}


sealed interface ConfirmationState {
    data object AwaitingConfirmation : ConfirmationState
    data object Sending : ConfirmationState
    data object Sent : ConfirmationState
}

data class Metadata(
    val kinAmount: KinAmount,
    val data: List<Byte>,
    val request: DeepLinkRequest? = null
)