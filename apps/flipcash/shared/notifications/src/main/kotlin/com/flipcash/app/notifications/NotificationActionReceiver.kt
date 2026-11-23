package com.flipcash.app.notifications

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.contacts.ContactResolver
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.notifications.R
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REPLY = "com.flipcash.app.notifications.ACTION_REPLY"
        const val ACTION_MARK_READ = "com.flipcash.app.notifications.ACTION_MARK_READ"
        const val KEY_CHAT_ID_HEX = "chat_id_hex"
        const val KEY_NOTIFICATION_ID = "notification_id"
        const val KEY_TEXT_REPLY = "key_text_reply"
    }

    @Inject lateinit var authManager: AuthManager
    @Inject lateinit var userManager: UserManager
    @Inject lateinit var chatCoordinator: ChatCoordinator
    @Inject lateinit var contactResolver: ContactResolver
    @Inject lateinit var notificationManager: NotificationManagerCompat

    override fun onReceive(context: Context, intent: Intent) {
        val chatIdHex = intent.getStringExtra(KEY_CHAT_ID_HEX) ?: return
        val chatId = ChatId(chatIdHex)
        val pendingResult = goAsync()

        authenticateIfNeeded {
            GlobalScope.launch {
                try {
                    when (intent.action) {
                        ACTION_REPLY -> handleReply(context, intent, chatId)
                        ACTION_MARK_READ -> handleMarkAsRead(chatId)
                    }
                } catch (e: Exception) {
                    trace(tag = "NotificationActionReceiver", message = "Action failed: ${e.message}", type = TraceType.Error)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun handleReply(context: Context, intent: Intent, chatId: ChatId) {
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_TEXT_REPLY)
            ?.toString()
            ?: return

        chatCoordinator.sendMessage(chatId, replyText)
            .onSuccess {
                val notificationId = intent.getIntExtra(KEY_NOTIFICATION_ID, chatId.hashCode())

                val selfPerson = buildSelfPerson(context, userManager.profile, contactResolver)

                val existingStyle = notificationManager.activeNotifications
                    .firstOrNull { it.id == notificationId }
                    ?.notification
                    ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
                    ?: NotificationCompat.MessagingStyle(selfPerson)

                existingStyle.addMessage(replyText, System.currentTimeMillis(), selfPerson)

                val channelId = notificationManager.activeNotifications
                    .firstOrNull { it.id == notificationId }
                    ?.notification?.channelId
                    ?: NotificationChannels.CHANNEL_CHAT

                val updated = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.flipcash_logo)
                    .setColor(context.getColor(R.color.notification_color))
                    .setStyle(existingStyle)
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                    .addAction(buildChatReplyAction(context, chatId, notificationId))
                    .addAction(buildChatMarkAsReadAction(context, chatId))
                    .setAutoCancel(true)
                    .build()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) return@onSuccess

                notificationManager.notify(notificationId, updated)
            }
            .onFailure {
                trace(tag = "NotificationActionReceiver", message = "Reply send failed: ${it.message}", type = TraceType.Error)
            }
    }

    private suspend fun handleMarkAsRead(chatId: ChatId) {
        chatCoordinator.markAsRead(chatId)
    }

    private fun authenticateIfNeeded(block: () -> Unit) {
        if (userManager.accountCluster == null) {
            authManager.init { block() }
        } else {
            block()
        }
    }

}
