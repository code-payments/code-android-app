package xyz.flipchat

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.getcode.services.db.Database
import kotlinx.datetime.Clock
import xyz.flipchat.internal.db.FcAppDatabase
import xyz.flipchat.workers.ChatSyncWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours

class FlipchatServices @Inject constructor() {

    companion object {
        fun openDatabase(context: Context, entropy: String) {
            if (!FcAppDatabase.isOpen()) {
                FcAppDatabase.init(context, entropy)
                Database.register(FcAppDatabase.requireInstance())
            }
        }

        /**
         * Schedules a worker to synchronize chats, members, and messages with the server.
         */
        fun scheduleChatSync(context: Context) {
            val request = PeriodicWorkRequestBuilder<ChatSyncWorker>(3, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    uniqueWorkName = ChatSyncWorker.WORK_NAME,
                    existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
                    request = request
                )
        }
    }
}