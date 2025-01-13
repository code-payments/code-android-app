package xyz.flipchat.app.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.getcode.ui.components.chat.utils.localizedText
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.resources.ResourceType
import com.getcode.utils.CurrencyUtils
import com.getcode.utils.TraceType
import com.getcode.utils.base58
import com.getcode.utils.trace
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import timber.log.Timber
import xyz.flipchat.app.MainActivity
import xyz.flipchat.app.R
import xyz.flipchat.app.auth.AuthManager
import xyz.flipchat.app.theme.FC_Primary
import xyz.flipchat.controllers.ChatsController
import xyz.flipchat.controllers.PushController
import xyz.flipchat.internal.db.FcAppDatabase
import xyz.flipchat.notifications.FcNotificationType
import xyz.flipchat.notifications.parse
import xyz.flipchat.services.user.AuthState
import xyz.flipchat.services.user.UserManager
import java.security.SecureRandom
import javax.inject.Inject

@AndroidEntryPoint
class FcNotificationService : FirebaseMessagingService(),
    CoroutineScope by CoroutineScope(Dispatchers.IO) {

    companion object {
        const val KEY_NOTIFICATION_ID = "key_notification_id"
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val KEY_ROOM_ID = "key_room_id"
    }

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var pushController: PushController

    @Inject
    lateinit var chatsController: ChatsController

    @Inject
    lateinit var resources: ResourceHelper

    @Inject
    lateinit var currencyUtils: CurrencyUtils

    @Inject
    lateinit var notificationManager: NotificationManagerCompat

    private val db: FcAppDatabase
        get() = FcAppDatabase.requireInstance()

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        authenticateIfNeeded { handleMessage(message) }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        authenticateIfNeeded {
            launch {
                pushController.addToken(token)
                    .onSuccess { trace("push token updated", type = TraceType.Process) }
            }
        }
    }

    private fun handleMessage(remoteMessage: RemoteMessage) {
        launch {
            trace("handling received message", type = TraceType.Silent)
            if (remoteMessage.data.isNotEmpty()) {
                Timber.d("Message data payload: ${remoteMessage.data}")
                val notification = remoteMessage.parse()

                if (notification != null) {
                    val (type, titleKey, messageContent) = notification
                    if (type.isNotifiable()) {
                        val title = titleKey.localizedStringByKey(resources) ?: titleKey
                        val body = messageContent.localizedText(
                            resources = resources,
                            currencyUtils = currencyUtils
                        )

                        val result = buildNotification(type, title, body)
                        if (result != null) {
                            notify(result.first, result.second, type)
                        }
                    }

                    when (type) {
                        is FcNotificationType.ChatMessage -> {
                            val roomId = type.id
                            if (roomId != null) {
                                launch { chatsController.updateRoom(roomId) }
                            }
                        }

                        FcNotificationType.Unknown -> Unit
                    }
                } else {
                    val result = buildNotification(
                        FcNotificationType.Unknown,
                        resources.getString(R.string.app_name),
                        "You have a new message."
                    )
                    if (result != null) {
                        notify(result.first, result.second, FcNotificationType.Unknown)
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

    private suspend fun buildNotification(
        type: FcNotificationType,
        title: String,
        content: String,
    ): Pair<Int, Notification>? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    type.name,
                    type.name,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        if (type is FcNotificationType.ChatMessage && type.id == userManager.openRoom) {
            return null
        }


        with(notificationManager) {
            val (id, notification) = when (type) {
                is FcNotificationType.ChatMessage -> {
                    val roomNumber = type.id?.let { db.conversationDao().findConversationRaw(it)?.roomNumber }
                    buildChatNotification(
                        applicationContext,
                        resources,
                        type,
                        roomNumber,
                        title,
                        content,
                        userManager.authState is AuthState.LoggedIn
                    )
                }

                FcNotificationType.Unknown -> buildMiscNotification(applicationContext, type, title, content)
            }

            return id to notification.build()
        }
    }

    private fun notify(
        id: Int,
        notification: Notification,
        type: FcNotificationType,
    ) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(id, notification)
            trace(
                tag = "Push",
                message = "Push notification shown",
                metadata = {
                    "category" to type.name
                },
                type = TraceType.Process
            )
        } else {
            trace(
                tag = "Push",
                message = "Push notification NOT shown - missing permission",
                metadata = {
                    "category" to type.name
                },
                type = TraceType.Process
            )
        }
    }
}

private fun String.localizedStringByKey(resources: ResourceHelper): String? {
    val name = this.replace(".", "_")
    val resId = resources.getIdentifier(
        name,
        ResourceType.String,
    ).let { if (it == 0) null else it }

    return resId?.let { resources.getString(it) }
}