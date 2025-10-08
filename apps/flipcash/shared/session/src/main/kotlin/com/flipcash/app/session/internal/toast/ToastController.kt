package com.flipcash.app.session.internal.toast

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
    companion object {
        val INITIAL_DELAY = 500.milliseconds
        val SHOW_DELAY = 3.seconds
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val toastQueue = mutableListOf<ToastRequest>()
    private var isConsumingQueue = false

    val hasToasts: Boolean
        get() = toastQueue.isNotEmpty()

    private data class ToastRequest(
        val amount: LocalFiat,
        val isDeposit: Boolean,
        val initialDelay: Duration = INITIAL_DELAY
    )

    fun enqueue(
        amount: LocalFiat,
        isDeposit: Boolean,
        initialDelay: Duration = INITIAL_DELAY
    ) {
        if (amount.nativeAmount.doubleValue == 0.0) {
            return
        }

        synchronized(toastQueue) {
            // Check for matching toast (same amount, opposite isDeposit)
            val matchingToast = toastQueue.find { toast ->
                toast.amount.nativeAmount.doubleValue == amount.nativeAmount.doubleValue &&
                        toast.isDeposit != isDeposit
            }

            if (matchingToast != null) {
                // Cancel matching toast
                toastQueue.remove(matchingToast)
            } else {
                // Enqueue new toast
                toastQueue.add(ToastRequest(amount, isDeposit, initialDelay))
            }
        }
    }

    fun clear() {
        synchronized(toastQueue) {
            toastQueue.clear()
            if (isConsumingQueue) {
                scope.launch {
                    billController.update {
                        it.copy(showToast = false, toast = null)
                    }
                }
            }
        }
    }

    suspend fun consumeQueue() {
        isConsumingQueue = true
        while (hasToasts) {
            val toast = synchronized(toastQueue) { toastQueue.removeFirstOrNull() } ?: continue

            // Wait for bill animation to finish
            // before showing the toast
            delay(toast.initialDelay)
            billController.update {
                it.copy(
                    showToast = true,
                    toast = BillToast(amount = toast.amount.nativeAmount, isDeposit = toast.isDeposit)
                )
            }

            // Wait for display duration
            delay(SHOW_DELAY)
            billController.update {
                it.copy(showToast = false)
            }

            // Wait for animation to complete
            delay(INITIAL_DELAY)
            billController.update {
                it.copy(toast = null)
            }

            if (hasToasts) {
                delay(1.seconds)
            }
        }
        isConsumingQueue = false
    }
}