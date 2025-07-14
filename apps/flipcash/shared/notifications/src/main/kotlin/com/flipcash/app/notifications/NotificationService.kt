package com.flipcash.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.flipcash.app.auth.AuthManager
import com.flipcash.services.controllers.PushController
import com.flipcash.services.user.UserManager
import com.flipcash.shared.notifications.R
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.inject.Inject

@AndroidEntryPoint
class NotificationService: FirebaseMessagingService(), CoroutineScope by CoroutineScope(Dispatchers.IO) {

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var pushController: PushController

    @Inject
    lateinit var notificationManager: NotificationManagerCompat

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        authenticateIfNeeded {
            launch {
                pushController.addToken(token)
                    .onSuccess {
                        userManager.set(pushToken = token)
                        trace("push token updated onNewToken", type = TraceType.Silent)
                    }.onFailure {
                        trace(message = "Failure updating push token", error = it)
                    }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        message.notification?.let { notification ->
            // dump everything into FCM fallback channel for now
            val channel = NotificationChannelCompat.Builder(
                "fcm_fallback_notification_channel",
                NotificationManagerCompat.IMPORTANCE_DEFAULT
            ).setName("Misc.").build()

            notificationManager.createNotificationChannel(channel)

            val notificationBuilder: NotificationCompat.Builder =
                NotificationCompat.Builder(this, channel.id)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                    .setSmallIcon(R.drawable.flipcash_logo)
                    .setColor(getColor(R.color.notification_color))
                    .setAutoCancel(true)
                    .setContentTitle(notification.title)
                    .setContentText(notification.body)
                    .setContentIntent(buildContentIntent())

            val random = SecureRandom()
            val notificationId = random.nextInt(256)

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(notificationId, notificationBuilder.build())
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

    internal fun Context.buildContentIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this,
            99,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}