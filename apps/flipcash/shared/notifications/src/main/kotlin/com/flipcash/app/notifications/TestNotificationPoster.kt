package com.flipcash.app.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.flipcash.app.core.util.Linkify
import com.flipcash.shared.notifications.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Posts the notification status page's local test notification. It runs in its own scope, not the
 * page's, so it still posts if the user leaves the page (or locks the phone) during the delay.
 */
@Singleton
class TestNotificationPoster @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManagerCompat,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun schedule(delay: Duration = 3.seconds) {
        scope.launch {
            delay(delay)
            post()
        }
    }

    @SuppressLint("MissingPermission") // checked: areNotificationsEnabled covers POST_NOTIFICATIONS
    private fun post() {
        if (!notificationManager.areNotificationsEnabled()) return
        NotificationChannels.createAll(context, notificationManager)

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_CHAT)
            .setContentTitle(context.getString(R.string.notification_test_title))
            .setContentText(context.getString(R.string.notification_test_body))
            .setSmallIcon(R.drawable.flipcash_logo)
            .setColor(context.getColor(R.color.notification_color))
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()

        // A fixed id, so a second test replaces the first.
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    // Explicit component: the manifest's app-link filters don't cover this path, which keeps the
    // link from opening Flipcash from anywhere but this notification.
    private fun contentIntent(): PendingIntent {
        val launcher = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val intent = Intent(Intent.ACTION_VIEW, Linkify.notificationStatus().toUri()).apply {
            component = launcher?.component
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val NOTIFICATION_ID = 0x7E57
    }
}
