package com.flipcash.app.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.core.util.Linkify
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.PushController
import com.flipcash.services.models.NavigationTrigger
import com.flipcash.services.models.NotificationCategory
import com.flipcash.services.models.NotificationPayload
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
class NotificationService : FirebaseMessagingService(),
    CoroutineScope by CoroutineScope(Dispatchers.IO) {

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var pushController: PushController

    @Inject
    lateinit var notificationManager: NotificationManagerCompat

    @Inject
    lateinit var tokenCoordinator: TokenCoordinator

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        authenticateIfNeeded {
            launch {
                if (userManager.state.value.authState.canAccessAuthenticatedApis) {
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
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.data["push_notification_title"]?.ifEmpty { message.notification?.title }
        val body = message.data["push_notification_body"]?.ifEmpty { message.notification?.body }

        trace(
            message = "onMessageReceived",
            type = TraceType.Process,
            metadata = {
                "title" to title
                "body" to body
            }
        )

        if (title == null) {
            return
        }

        val payload = message.data.getOrDefault("flipcash_payload", "")
            .takeIf { it.isNotEmpty() }
            ?.let { protoString ->
                NotificationPayload.fromEncoded(protoString)
            }

        if (payload?.navigation is NavigationTrigger.CurrencyInfo) {
            launch {
                tokenCoordinator.update()
            }
        }

        val category = payload?.category ?: NotificationCategory.DEFAULT
        NotificationChannels.ensureChannelGroups(this, notificationManager)
        val channel = NotificationChannels.channelFor(this, category)
        notificationManager.createNotificationChannel(channel)

        val groupKey = payload?.groupKey?.takeIf { it.isNotEmpty() }

        val notificationBuilder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, channel.id)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setSmallIcon(R.drawable.flipcash_logo)
                .setColor(getColor(R.color.notification_color))
                .setAutoCancel(true)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(buildContentIntent(payload?.navigation))
                .apply { if (groupKey != null) setGroup(groupKey) }

        val notificationId = SecureRandom().nextInt(Int.MAX_VALUE)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(notificationId, notificationBuilder.build())

            if (groupKey != null) {
                val summary = NotificationCompat.Builder(this, channel.id)
                    .setSmallIcon(R.drawable.flipcash_logo)
                    .setColor(getColor(R.color.notification_color))
                    .setGroup(groupKey)
                    .setGroupSummary(true)
                    .setAutoCancel(true)
                    .build()
                notificationManager.notify(groupKey.hashCode(), summary)
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

    internal fun Context.buildContentIntent(navigation: NavigationTrigger?): PendingIntent {
        val target = when (navigation) {
            is NavigationTrigger.CurrencyInfo -> Intent(Intent.ACTION_VIEW).apply {
                data = Linkify.tokenInfo(navigation.mint).toUri()
            }

            else -> packageManager.getLaunchIntentForPackage(packageName)
        }

        return PendingIntent.getActivity(
            this,
            99,
            target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}