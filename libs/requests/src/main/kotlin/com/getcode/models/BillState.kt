package com.getcode.models

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.getcode.extensions.formatted
import com.getcode.libs.requests.R
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.model.Rate
import com.getcode.model.SocialUser
import com.getcode.model.TwitterUser
import com.getcode.services.model.CodePayload
import com.getcode.services.model.ExtendedMetadata
import com.getcode.solana.keys.PublicKey

data class BillState(
    val bill: Bill?,
    val showToast: Boolean,
    val toast: BillToast?,
    val valuation: Valuation?,
    val privatePaymentConfirmation: PrivatePaymentConfirmation?,
    val publicPaymentConfirmation: PublicPaymentConfirmation?,
    val loginConfirmation: LoginConfirmation?,
    val socialUserPaymentConfirmation: SocialUserPaymentConfirmation?,
    val primaryAction: Action?,
    val secondaryAction: Action?,
) {
    val hideBillButtons: Boolean
        get() = primaryAction == null && secondaryAction == null

    val canSwipeToDismiss: Boolean
        get() = bill?.canSwipeToDismiss ?: false

    companion object {
        val Default = BillState(
            bill = null,
            showToast = false,
            toast = null,
            valuation = null,
            privatePaymentConfirmation = null,
            publicPaymentConfirmation = null,
            loginConfirmation = null,
            socialUserPaymentConfirmation = null,
            primaryAction = null,
            secondaryAction = null,
        )
    }

    sealed interface Action {

        val label: String?
            @Composable get
        val asset: Painter
            @Composable get

        val action: () -> Unit

        data class Send(override val action: () -> Unit): Action {
            override val label: String
                @Composable get() = stringResource(R.string.action_send)
            override val asset: Painter
                @Composable get() = painterResource(id = R.drawable.ic_remote_send)
        }

        data class Share(override val action: () -> Unit): Action {
            override val label: String
                @Composable get() = stringResource(R.string.action_shareAsURL)

            override val asset: Painter
                @Composable get() = painterResource(id = R.drawable.ic_remote_send)
        }

        data class Cancel(override val action: () -> Unit): Action {
            override val label: String?
                @Composable get() = null

            override val asset: Painter
                @Composable get() = painterResource(R.drawable.ic_bill_close)
        }

        data class Done(override val action: () -> Unit): Action {
            override val label: String
                @Composable get() = stringResource(R.string.action_done)

            override val asset: Painter
                @Composable get() = painterResource(R.drawable.ic_check_white)
        }
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

    val canFlip: Boolean

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
        val kind: Kind = Kind.cash,
    ) : Bill {
        override val canFlip: Boolean = false
    }

    data class Payment(
        override val amount: KinAmount,
        val payload: CodePayload,
        val request: DeepLinkRequest? = null
    ) : Bill {
        override val didReceive: Boolean = false
        override val data: List<Byte> = payload.codeData.toList()
        override val canFlip: Boolean = false
    }

    data class Login(
        override val amount: KinAmount,
        val payload: CodePayload,
        val request: DeepLinkRequest? = null
    ) : Bill {
        override val didReceive: Boolean = false
        override val data: List<Byte> = payload.codeData.toList()
        override val canFlip: Boolean = false
    }

    data class Tip(
        val payload: CodePayload,
        val request: DeepLinkRequest? = null,
        override val canFlip: Boolean = false
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

sealed class Confirmation(
    open val showScrim: Boolean = false,
    open val state: ConfirmationState,
)

data class PrivatePaymentConfirmation(
    override val state: ConfirmationState,
    val payload: CodePayload,
    val requestedAmount: KinAmount,
    val localAmount: KinAmount,
    override val showScrim: Boolean = false,
): Confirmation(showScrim, state)

data class LoginConfirmation(
    override val state: ConfirmationState,
    val payload: CodePayload,
    val domain: com.getcode.model.Domain,
    override val showScrim: Boolean = false,
): Confirmation(showScrim, state)


data class PublicPaymentConfirmation(
    override val state: ConfirmationState,
    val amount: KinAmount,
    val destination: PublicKey,
    val metadata: ExtendedMetadata,
    override val showScrim: Boolean = true,
): Confirmation(showScrim, state)

data class SocialUserPaymentConfirmation(
    override val state: ConfirmationState,
    val amount: KinAmount,
    val payload: CodePayload?,
    val metadata: SocialUser,
    val isPrivate: Boolean = false,
    override val showScrim: Boolean = false,
): Confirmation(showScrim, state) {
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