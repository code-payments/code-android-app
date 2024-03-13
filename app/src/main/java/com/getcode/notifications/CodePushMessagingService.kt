package com.getcode.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat.getSystemService
import com.getcode.R
import com.getcode.model.notifications.parse
import com.getcode.network.repository.PushRepository
import com.getcode.ui.components.chat.utils.localizedText
import com.getcode.util.CurrencyUtils
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.resources.ResourceType
import com.getcode.utils.installationId
import com.google.firebase.Firebase
import com.google.firebase.installations.installations
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import timber.log.Timber
import javax.inject.Inject


@AndroidEntryPoint
class CodePushMessagingService : FirebaseMessagingService(),
    CoroutineScope by CoroutineScope(Dispatchers.IO) {

    @Inject
    lateinit var pushRepository: PushRepository

    @Inject
    lateinit var resources: ResourceHelper

    @Inject
    lateinit var currencyUtils: CurrencyUtils

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Timber.d("From: ${remoteMessage.from}")

        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            Timber.d("Message data payload: ${remoteMessage.data}")
            val notification = remoteMessage.parse()

            if (notification != null) {
                val (type, titleKey, messageContent) = notification
                val title = titleKey.localizedStringByKey(resources) ?: titleKey
                val body = messageContent.localizedText(resources, currencyUtils)
                notify(type, title, body)
            } else {
                notify("Unknown", resources.getString(R.string.app_name), "You have a new message.")
            }
        }

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {
            Timber.d("Message Notification Body: ${it.body}")
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        launch {
            val installationId = Firebase.installations.installationId()
            pushRepository.updateToken(token, installationId)
                .onSuccess {
                    Timber.d("push token updated")
                }.onFailure {
                    Timber.e(t = it, message = "Failure updating push token")
                }
        }
    }

    private fun notify(
        type: String,
        title: String,
        content: String,
    ) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    type,
                    type,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val person = Person.Builder()
            .setName(title)
            .build()

        val message = NotificationCompat.MessagingStyle.Message(
            content,
            Clock.System.now().toEpochMilliseconds(),
            person
        )

        val style =   notificationManager.getActiveNotification(title.hashCode())?.let {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it)
        } ?: NotificationCompat.MessagingStyle(person)

        val notificationBuilder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, type)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setStyle(style.addMessage(message))
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setSmallIcon(R.drawable.ic_code_logo_outline)
                .setAutoCancel(true)

        notificationManager.notify(title.hashCode(), notificationBuilder.build())
    }
}

private fun NotificationManager.getActiveNotification(notificationId: Int): Notification? {
    val barNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        getActiveNotifications()
    } else {
       emptyArray()
    }
    for (notification in barNotifications) {
        if (notification.id == notificationId) {
            return notification.notification
        }
    }
    return null
}
private fun String.localizedStringByKey(resources: ResourceHelper): String? {
    val name = this.replace(".", "_")
    val resId = resources.getIdentifier(
        name,
        ResourceType.String,
    ).let { if (it == 0) null else it }

    return resId?.let { resources.getString(it) }
}