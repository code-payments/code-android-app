package com.flipcash.app.notifications

import com.flipcash.app.auth.AuthManager
import com.flipcash.services.controllers.PushController
import com.flipcash.services.user.UserManager
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.google.firebase.messaging.FirebaseMessagingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class NotificationService: FirebaseMessagingService(), CoroutineScope by CoroutineScope(Dispatchers.IO) {

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var pushController: PushController

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        authenticateIfNeeded {
            launch {
                pushController.addToken(token)
                    .onSuccess {
                        trace("push token updated", type = TraceType.Silent)
                    }.onFailure {
                        trace(message = "Failure updating push token", error = it)
                    }
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
}