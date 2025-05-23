package com.flipcash.app.workers

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.flipcash.app.workers.internal.GiftCardFundingWorker
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.financial.LocalFiat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Singleton
class WorkCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun scheduleGiftCardFunding(
        giftCardAccount: GiftCardAccount,
        amount: LocalFiat,
        initialDelay: Duration = 70.seconds,
    ) {
        val request = OneTimeWorkRequestBuilder<GiftCardFundingWorker>()
            .setInitialDelay(initialDelay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .setInputData(
                GiftCardFundingWorker.buildInputData(
                    giftCardAccount = giftCardAccount,
                    amount = amount
                )
            ).addTag(GiftCardFundingWorker.tagFor(giftCardAccount))
            .build()

        WorkManager.getInstance(context)
            .enqueue(request)
    }

    fun cancelGiftCardFunding(giftCardAccount: GiftCardAccount) {
        WorkManager.getInstance(context)
            .cancelAllWorkByTag(GiftCardFundingWorker.tagFor(giftCardAccount))
    }
}