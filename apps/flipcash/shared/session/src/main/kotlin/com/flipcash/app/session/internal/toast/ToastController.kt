package com.flipcash.app.session.internal.toast

import com.flipcash.app.core.bill.BillToast
import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.session.PresentationStyle
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
    companion object {
        val INITIAL_DELAY = 500.milliseconds
        val SHOW_DELAY = 5.seconds
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun showIfNeeded(
        style: PresentationStyle,
        override: Boolean = false
    ): Boolean {
        val billState = billController.state.value
        val bill = billState.bill ?: return false

        if (!override) {
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
        }

        return false
    }

    fun show(
        amount: LocalFiat,
        isDeposit: Boolean = false,
        initialDelay: Duration = INITIAL_DELAY
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

            delay(SHOW_DELAY)

            billController.update {
                it.copy(
                    showToast = false
                )
            }

            // wait for animation to run
            delay(INITIAL_DELAY)
            billController.update {
                it.copy(toast = null)
            }
        }
    }
}