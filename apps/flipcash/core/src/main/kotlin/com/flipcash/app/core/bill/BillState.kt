package com.flipcash.app.core.bill

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.flipcash.core.R
import com.getcode.opencode.model.core.ExtendedMetadata
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.PublicKey

data class BillState(
    val bill: Bill?,
    val showToast: Boolean,
    val toast: BillToast?,
    val valuation: Valuation?,
    val publicPaymentConfirmation: PublicPaymentConfirmation?,
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
            publicPaymentConfirmation = null,
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
    val amount: LocalFiat
    val data: List<Byte>

    enum class Kind {
        cash, remote, airdrop
    }

    val canSwipeToDismiss: Boolean
        get() = when (this) {
            is Cash -> true
            is Payment -> false
        }

    val canFlip: Boolean

    val metadata: Metadata
        get() {
            return when (this) {
                is Cash -> Metadata(
                    amount = amount,
                    data = data
                )

                is Payment -> Metadata(
                    amount = amount,
                    data = payload.codeData.toList(),
                    request = request,
                )
            }
        }

    data class Cash(
        override val amount: LocalFiat,
        override val didReceive: Boolean = false,
        override val data: List<Byte> = emptyList(),
        val kind: Kind = Kind.cash,
    ) : Bill {
        override val canFlip: Boolean = false
    }

    data class Payment(
        override val amount: LocalFiat,
        val payload: OpenCodePayload,
        val request: DeepLinkRequest? = null
    ) : Bill {
        override val didReceive: Boolean = false
        override val data: List<Byte> = payload.codeData.toList()
        override val canFlip: Boolean = false
    }
}

sealed interface Valuation
data class PaymentValuation(val amount: Fiat): Valuation

data class BillToast(
    val amount: Fiat,
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
    open val cancellable: Boolean = false,
)


data class PublicPaymentConfirmation(
    override val state: ConfirmationState,
    val amount: Fiat,
    val destination: PublicKey,
    val metadata: ExtendedMetadata,
    override val showScrim: Boolean = true,
    override val cancellable: Boolean = true,
): Confirmation(showScrim, state)


sealed interface ConfirmationState {
    data object AwaitingConfirmation : ConfirmationState
    data object Sending : ConfirmationState
    data object Sent : ConfirmationState
}

data class Metadata(
    val amount: LocalFiat,
    val data: List<Byte>,
    val request: DeepLinkRequest? = null
)