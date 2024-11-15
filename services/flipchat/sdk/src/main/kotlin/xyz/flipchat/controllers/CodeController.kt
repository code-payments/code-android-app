package xyz.flipchat.controllers

import android.annotation.SuppressLint
import com.getcode.model.KinAmount
import com.getcode.network.BalanceController
import com.getcode.network.client.Client
import com.getcode.network.client.receiveFromPrimaryIfWithinLimits
import com.getcode.network.repository.TransactionRepository
import io.reactivex.rxjava3.core.Completable
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject

class CodeController @Inject constructor(
    private val userManager: UserManager,
    private val balanceController: BalanceController,
    private val transactionRepository: TransactionRepository,
    private val client: Client,
) {
    @SuppressLint("CheckResult")
    suspend fun requestAirdrop(): Result<KinAmount> {
        val owner = userManager.keyPair ?: return Result.failure(Throwable("No owner"))
        return transactionRepository.requestFirstKinAirdrop(owner)
            .onSuccess {
                balanceController.fetchBalance()

                val organizer = userManager.organizer
                val receiveWithinLimits = organizer?.let {
                    client.receiveFromPrimaryIfWithinLimits(it)
                } ?: Completable.complete()
                receiveWithinLimits.subscribe({}, {})
            }
    }

    suspend fun fetchBalance(): Result<Unit> {
        return balanceController.fetchBalance()
    }
}
