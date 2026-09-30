package com.flipcash.shared.chat.internal

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.flipcash.services.models.chat.ChatId
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Queues a full read of a group's roster to run when it can. */
interface RosterReconcileScheduler {
    /** At most one read per chat is queued; asking again while one is waits on that one. */
    fun schedule(chatId: ChatId)
}

/**
 * Runs [RosterReconcileWorker] as unique work per chat, on any network, backing off on failure.
 * Not expedited: a roster read is never what the user is waiting on.
 */
@Singleton
class WorkManagerRosterReconcileScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : RosterReconcileScheduler {

    override fun schedule(chatId: ChatId) {
        val request = OneTimeWorkRequestBuilder<RosterReconcileWorker>()
            .setInputData(workDataOf(RosterReconcileWorker.KEY_CHAT_ID to chatId.hex))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(uniqueName(chatId), ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        private const val BACKOFF_SECONDS = 30L

        fun uniqueName(chatId: ChatId) = "roster-reconcile-${chatId.hex}"
    }
}

/** Reads a group's whole roster and drops who has left: [RosterSync.reconcileNow]. */
@HiltWorker
internal class RosterReconcileWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted private val params: WorkerParameters,
    private val rosterSync: RosterSync,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val chatId = params.inputData.getString(KEY_CHAT_ID)?.let(::ChatId)
            ?: return Result.failure()
        if (rosterSync.reconcileNow(chatId)) return Result.success()
        if (params.runAttemptCount + 1 >= MAX_ATTEMPTS) {
            // The pending flag stays set, so the chat's next open queues it again.
            trace(tag = TAG, message = "Roster reconcile for $chatId gave up", type = TraceType.Error)
            return Result.failure()
        }
        return Result.retry()
    }

    companion object {
        private const val TAG = "RosterReconcileWorker"
        const val KEY_CHAT_ID = "chat_id"
        private const val MAX_ATTEMPTS = 5
    }
}

@OptIn(ExperimentalStdlibApi::class)
private val ChatId.hex: String
    get() = bytes.toHexString()
