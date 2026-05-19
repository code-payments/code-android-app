package com.flipcash.app.tokens

import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountFilter
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import javax.inject.Inject

class UsdcDepositSweep @Inject constructor(
    private val transactionOperations: TransactionOperations,
    private val accountController: AccountController,
    private val tokenCoordinator: TokenCoordinator,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeJob: Job? = null

    fun execute(owner: AccountCluster) {
        if (activeJob?.isActive == true) return
        activeJob = scope.launch {
            val usdcAccount = accountController.getAccount(
                accountOwner = owner,
                requestingOwner = owner,
                filter = AccountFilter.MintAddress(Mint.usdc),
            ).getOrNull()?.takeIf { account ->
                account.accountType == AccountType.AssociatedToken
            }

            usdcAccount?.let {
                trace(tag = TAG, message = "USDC ATA found. => ${it.address.base58()}")
            } ?: trace(tag = TAG, message = "USDC ATA not found")

            val amount = usdcAccount?.balance ?: 0L
            if (amount <= 0L) {
                trace(tag = TAG, message = "USDC balance <= 0. nothing to sweep")
                return@launch
            }

            coroutineContext.ensureActive()

            trace(tag = TAG, message = "Swapping $amount USDC quarks to USDF", type = TraceType.Process)

            transactionOperations.swapUsdc(
                owner = owner,
                amount = amount,
            ).onSuccess {
                trace(tag = TAG, message = "USDC→USDF sweep completed")
                tokenCoordinator.update()
            }.onFailure { error ->
                trace(tag = TAG, message = "USDC→USDF sweep failed: ${error.message}", error = error)
            }
        }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
    }

    companion object {
        private const val TAG = "UsdcDepositSweep"
    }
}
