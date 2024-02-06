package com.getcode.notifications

import com.getcode.network.repository.PushRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class CodePushMessagingService : FirebaseMessagingService(), CoroutineScope by CoroutineScope(Dispatchers.IO) {

    @Inject
    lateinit var pushRepository: PushRepository

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Timber.d("From: ${remoteMessage.from}")

        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            Timber.d("Message data payload: ${remoteMessage.data}")
        }

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {
            Timber.d("Message Notification Body: ${it.body}")
        }
    }

    override fun onNewToken(p0: String) {
        super.onNewToken(p0)
        launch {
            pushRepository.updateToken(p0)
                .onSuccess {
                    Timber.d("push token updated")
                }.onFailure {
                    Timber.e(t = it, message = "Failure updating push token")
                }
        }
    }
}