package com.getcode.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.getcode.R
import com.getcode.analytics.AnalyticsService
import com.getcode.manager.AuthManager
import com.getcode.manager.SessionManager
import com.getcode.model.notifications.NotificationType
import com.getcode.model.notifications.parse
import com.getcode.network.BalanceController
import com.getcode.network.ChatHistoryController
import com.getcode.network.TipController
import com.getcode.network.repository.AccountRepository
import com.getcode.network.repository.PushRepository
import com.getcode.network.repository.TransactionRepository
import com.getcode.ui.components.chat.utils.localizedText
import com.getcode.util.CurrencyUtils
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.resources.ResourceType
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.installationId
import com.getcode.utils.trace
import com.getcode.view.MainActivity
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
    lateinit var analyticsService: AnalyticsService

    @Inject
    lateinit var pushRepository: PushRepository

    @Inject
    lateinit var transactionRepository: TransactionRepository

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var resources: ResourceHelper

    @Inject
    lateinit var currencyUtils: CurrencyUtils

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var balanceController: BalanceController

    @Inject
    lateinit var historyController: ChatHistoryController

    @Inject
    lateinit var tipController: TipController

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Timber.d("onMessageReceived")
        if (SessionManager.isAuthenticated() == null) {
            // sodium initialized internally during init
            Timber.d("initializing session")
            authManager.init {
                handleMessage(remoteMessage)
            }
        } else {
            handleMessage(remoteMessage)
        }
    }

    private fun handleMessage(remoteMessage: RemoteMessage) {
        Timber.d("handling received message")
        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            Timber.d("Message data payload: ${remoteMessage.data}")
            val notification = remoteMessage.parse()

            if (notification != null) {
                val (type, titleKey, messageContent) = notification
                if (type.isNotifiable()) {
                    val title = titleKey.localizedStringByKey(resources) ?: titleKey
                    val body = messageContent.localizedText(title, resources, currencyUtils)
                    notify(type, title, body)
                }

                when (type) {
                    NotificationType.ChatMessage -> {
                        launch { historyController.fetchChats() }
                        launch { balanceController.fetchBalanceSuspend() }
                    }

                    NotificationType.ExecuteSwap -> {
                        analyticsService.backgroundSwapInitiated()
                        updateOrganizerAndSwap()
                    }

                    NotificationType.Twitter -> {
                        launch { tipController.checkForConnection() }
                    }

                    NotificationType.Unknown -> Unit
                }
            } else {
                notify(
                    NotificationType.Unknown,
                    resources.getString(R.string.app_name),
                    "You have a new message."
                )
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        launch {
            if (SessionManager.isAuthenticated() == true) {
                val installationId = Firebase.installations.installationId()
                pushRepository.updateToken(token, installationId)
                    .onSuccess {
                        Timber.d("push token updated")
                    }.onFailure {
                        ErrorUtils.handleError(it)
                        Timber.e(t = it, message = "Failure updating push token")
                    }
            }
        }
    }

    private fun notify(
        type: NotificationType,
        title: String,
        content: String,
    ) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    type.name,
                    type.name,
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

        val style = notificationManager.getActiveNotification(title.hashCode())?.let {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it)
        } ?: NotificationCompat.MessagingStyle(person)

        val notificationBuilder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, type.name)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setStyle(style.addMessage(message))
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setSmallIcon(R.drawable.ic_code_logo_outline)
                .setAutoCancel(true)
                .setContentIntent(buildContentIntent(type))

        notificationManager.notify(title.hashCode(), notificationBuilder.build())

        trace(
            tag = "Push",
            message = "Push notification shown",
            metadata = {
                "category" to type.name
            },
            type = TraceType.Process
        )
    }

    private fun updateOrganizerAndSwap() = launch {
        val owner = SessionManager.getKeyPair()
        if (owner == null) {
            ErrorUtils.handleError(Throwable("ExecuteSwap:: Missing owner"))
            return@launch
        }

        val organizer = SessionManager.getOrganizer()
        if (organizer == null) {
            ErrorUtils.handleError(Throwable("ExecuteSwap:: Missing organizer"))
            return@launch
        }

        val accountInfo = accountRepository.getTokenAccountInfos(owner).blockingGet()
        organizer.setAccountInfo(accountInfo)
        SessionManager.update { it.copy(organizer = organizer) }
        transactionRepository.swapIfNeeded(organizer)
    }
}

private fun Context.buildContentIntent(type: NotificationType): PendingIntent {
    val launchIntent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }

    return PendingIntent.getActivity(
        this,
        type.ordinal,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}


private fun NotificationManager.getActiveNotification(notificationId: Int): Notification? {
    val barNotifications = getActiveNotifications()
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
