package xyz.flipchat.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.getcode.vendor.Base58
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import xyz.flipchat.app.auth.AuthManager
import xyz.flipchat.chat.RoomController
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@AndroidEntryPoint
class FcNotificationReceiver : BroadcastReceiver() {

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var roomController: RoomController

    @Inject
    lateinit var notificationManager: NotificationManagerCompat

    override fun onReceive(context: Context?, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        if (remoteInput != null) {
            val roomId = runCatching {
                Base58.decode(
                    intent.getStringExtra(FcNotificationService.KEY_ROOM_ID).orEmpty()
                ).toList()
            }.getOrNull()

            val notificationId = intent.getIntExtra(FcNotificationService.KEY_NOTIFICATION_ID, -1).takeIf { it > 0 }

            if (roomId != null) {
                val message =
                    remoteInput.getCharSequence(FcNotificationService.KEY_TEXT_REPLY).toString()
                authenticateIfNeeded {
                    goAsync {
                        roomController.sendMessage(roomId, message)
                            .onFailure {
                                it.printStackTrace()
                                if (notificationId != null) {
                                    notificationManager.cancel(notificationId)
                                }
                            }.onSuccess {
                                println("Message sent via notification!")
                                if (notificationId != null) {
                                    notificationManager.cancel(notificationId)
                                }
                            }
                    }
                }
            }
        }
    }

    private fun authenticateIfNeeded(block: () -> Unit) {
        if (userManager.userId == null) {
            authManager.init { block() }
        } else {
            block()
        }
    }
}

fun BroadcastReceiver.goAsync(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> Unit
) {
    val pendingResult = goAsync()
    @OptIn(DelicateCoroutinesApi::class) // Must run globally; there's no teardown callback.
    GlobalScope.launch(context) {
        try {
            block()
        } finally {
            pendingResult.finish()
        }
    }
}