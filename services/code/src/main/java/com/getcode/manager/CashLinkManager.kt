package com.getcode.manager

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.KinAmount
import com.getcode.network.repository.SendTransactionRepository
import com.getcode.solana.organizer.Organizer
import com.getcode.utils.ErrorUtils
import io.reactivex.rxjava3.disposables.Disposable
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.schedule

@Singleton
class CashLinkManager @Inject constructor(
    private val sendTransactionRepository: SendTransactionRepository,
) {
    private var billDismissTimer: TimerTask? = null
    private var sendTransactionDisposable: Disposable? = null

    val amount: KinAmount
        get() = sendTransactionRepository.getAmount()

    val rendezvous: KeyPair
        get() = sendTransactionRepository.getRendezvous()

    fun awaitBillGrab(
        amount: KinAmount,
        organizer: Organizer,
        owner: KeyPair,
        present: (List<Byte>) -> Unit,
        onGrabbed: () -> Unit,
        onTimeout: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        sendTransactionDisposable?.dispose()
        val payload = sendTransactionRepository.init(amount, organizer, owner)
        sendTransactionDisposable =
            sendTransactionRepository.startTransaction()
                .subscribe({
                    onGrabbed()
                }, {
                    ErrorUtils.handleError(it)
                    onError(it)
                })

        presentSend(onTimeout)

        present(payload)
    }

    private fun presentSend(onTimeout: () -> Unit) {
        cancelBillTimeout()
        billDismissTimer = Timer().schedule((1000 * 50).toLong()) {
            onTimeout()
        }
    }

    fun cancelSend() {
        cancelBillTimeout()
        sendTransactionDisposable?.dispose()
    }

    fun cancelBillTimeout() {
        billDismissTimer?.cancel()
    }
}