package com.flipcash.app.core.bill

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.flipcash.core.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import kotlin.time.Duration

data class BillState(
    val bill: Bill?,
    val showToast: Boolean,
    val toast: BillToast?,
    val valuation: Valuation?,
    val primaryAction: Action?,
    val secondaryAction: Action?,
) {
    val canSwipeToDismiss: Boolean
        get() = bill?.canSwipeToDismiss == true

    val confirmationDelayMillis: Int
        get() = (bill?.confirmationDelay ?: Duration.ZERO).inWholeMilliseconds.toInt()

    companion object {
        val Default = BillState(
            bill = null,
            showToast = false,
            toast = null,
            valuation = null,
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
                @Composable get() = painterResource(id = R.drawable.ic_send_outlined)
        }

        data class Share(override val action: () -> Unit): Action {
            override val label: String
                @Composable get() = stringResource(R.string.action_shareAsURL)

            override val asset: Painter
                @Composable get() = painterResource(id = R.drawable.ic_remote_send)
        }

        data class Cancel(override val action: () -> Unit): Action {
            override val label: String
                @Composable get() = stringResource(R.string.action_cancel)
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
    val confirmationDelay: Duration
    val amount: LocalFiat
    val data: List<Byte>

    enum class Kind {
        cash, airdrop
    }

    val canSwipeToDismiss: Boolean
        get() = when (this) {
            is Cash -> true
        }

    val canFlip: Boolean

    val metadata: Metadata
        get() {
            return when (this) {
                is Cash -> Metadata(
                    amount = amount,
                    data = data
                )
            }
        }

    data class Cash(
        override val amount: LocalFiat,
        override val didReceive: Boolean = false,
        override val confirmationDelay: Duration = Duration.ZERO,
        override val data: List<Byte> = emptyList(),
        val kind: Kind = Kind.cash,
    ) : Bill {
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

data class Metadata(
    val amount: LocalFiat,
    val data: List<Byte>,
    val request: DeepLinkRequest? = null
)