package xyz.flipchat.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.getcode.utils.trace
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import xyz.flipchat.controllers.ChatsController

@HiltWorker
class ChatSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val chatsController: ChatsController,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val result = chatsController.updateRooms()

        if (result.isFailure) {
            trace(tag = "Chat-Sync", message = "Failed", error = result.exceptionOrNull())
            return Result.failure()
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "chat-sync"
    }
}