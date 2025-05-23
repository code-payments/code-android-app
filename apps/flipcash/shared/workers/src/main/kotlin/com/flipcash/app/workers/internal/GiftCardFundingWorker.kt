package com.flipcash.app.workers.internal

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.flipcash.app.auth.AuthManager
import com.flipcash.services.user.UserManager
import com.getcode.opencode.managers.BillTransactionManager
import com.getcode.opencode.managers.GiftCardManager
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.accounts.entropy
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.utils.trace
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume

/**
 * A worker responsible for managing tasks related to gift card funding operations. It
 * coordinates its tasks with the provided `BillTransactionManager`.
 *
 * The primary usecase of this worker is to fund gift cards only when the user does not take
 * an explicit action in the confirmation modal after choosing to copy a cash link to their
 * clipboard _or_ selecting an app target.
 *
 */
@HiltWorker
internal class GiftCardFundingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val authManager: AuthManager,
    private val userManager: UserManager,
    private val transactionManager: BillTransactionManager,
    private val giftCardManager: GiftCardManager,
) : CoroutineWorker(appContext, workerParams) {
    internal companion object {
        fun tagFor(giftCard: GiftCardAccount) = "gift_card_funding-${giftCard.entropy}"
        fun buildInputData(giftCardAccount: GiftCardAccount, amount: LocalFiat): Data {
            return Data.Builder()
                .putString(KEY_GIFT_CARD, giftCardAccount.entropy)
                .putString(KEY_AMOUNT, Json.encodeToString(LocalFiat.serializer(), amount))
                .build()
        }

        private const val KEY_GIFT_CARD = "gift_card"
        private const val KEY_AMOUNT = "amount"
    }

    override suspend fun doWork(): Result {
        val giftCardValue = workerParams.inputData.getString(KEY_GIFT_CARD)
        if (giftCardValue == null) {
            trace(
                tag = "GiftCardFundingWorker",
                message = "Gift card value is null"
            )
            return Result.failure()
        }
        val amountValue = workerParams.inputData.getString(KEY_AMOUNT)
        if (amountValue == null) {
            trace(
                tag = "GiftCardFundingWorker",
                message = "Amount value is null"
            )
            return Result.failure()
        }

        val giftCard = giftCardManager.createGiftCardFromEntropy58(giftCardValue)
        val amount = runCatching { Json.decodeFromString<LocalFiat>(amountValue) }
            .getOrNull()

        if (amount == null) {
            trace(
                tag = "GiftCardFundingWorker",
                message = "Failed to deserialize amount from $amountValue"
            )
            return Result.failure()
        }

        return try {
            val result = fundGiftCard(giftCard, amount)
            if (result.isSuccess) {
                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private suspend fun fundGiftCard(
        giftCard: GiftCardAccount,
        amount: LocalFiat,
    ): kotlin.Result<LocalFiat> = suspendCancellableCoroutine { cont ->
        authenticateIfNeeded {
            try {
                transactionManager.fundGiftCard(
                    giftCard = giftCard,
                    amount = amount,
                    owner = userManager.accountCluster!!,
                    onFunded = {
                        trace(
                            tag = "GiftCardFundingWorker",
                            message = "Successfully funded gift card",
                            metadata = {
                                "giftCard" to giftCard.entropy
                            }
                        )
                        cont.resume(kotlin.Result.success(it))
                    },
                    onError = {
                        trace(
                            tag = "GiftCardFundingWorker",
                            message = "Failed to fund gift card",
                            metadata = {
                                "giftCard" to giftCard.entropy
                            },
                            error = it
                        )
                        cont.resume(kotlin.Result.failure(it))
                    }
                )
            } catch (e: Exception) {
                cont.resume(kotlin.Result.failure(e))
            }
        }
    }

    private fun authenticateIfNeeded(block: () -> Unit) {
        if (userManager.accountCluster == null) {
            authManager.init { block() }
        } else {
            block()
        }
    }
}