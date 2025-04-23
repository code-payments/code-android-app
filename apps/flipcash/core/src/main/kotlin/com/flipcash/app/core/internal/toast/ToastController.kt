package com.flipcash.app.core.internal.toast

import com.flipcash.app.core.PresentationStyle
import com.flipcash.app.core.bill.BillToast
import com.flipcash.app.core.internal.bill.BillController
import com.getcode.opencode.model.financial.LocalFiat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Singleton
class ToastController @Inject constructor(
    private val billController: BillController
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun showIfNeeded(
        style: PresentationStyle,
    ): Boolean {
        val billState = billController.state.value
        val bill = billState.bill ?: return false

        if (style is PresentationStyle.Pop || billState.showToast) {
            show(
                amount = bill.metadata.amount,
                isDeposit = when (style) {
                    PresentationStyle.Slide -> true
                    PresentationStyle.Pop -> false
                    else -> false
                },
            )

            return true
        }

        return false
    }

    fun show(
        amount: LocalFiat,
        isDeposit: Boolean = false,
        initialDelay: Duration = 500.milliseconds
    ) {
        if (amount.converted.doubleValue == 0.0) {
            return
        }

        scope.launch {
            delay(initialDelay)
            billController.update {
                it.copy(
                    showToast = true,
                    toast = BillToast(amount = amount.converted, isDeposit = isDeposit)
                )
            }

            delay(5.seconds)

            billController.update {
                it.copy(
                    showToast = false
                )
            }

            // wait for animation to run
            delay(500.milliseconds)
            billController.update {
                it.copy(toast = null)
            }
        }

    }
}