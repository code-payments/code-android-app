package com.flipcash.app.notifications

import com.flipcash.app.push.PushTokenProvider
import com.getcode.utils.ErrorUtils
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class FirebasePushTokenProvider @Inject constructor() : PushTokenProvider {
    override suspend fun getToken(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnCanceledListener { cont.resume(null) }
            .addOnFailureListener {
                ErrorUtils.handleError(it)
                cont.resume(null)
            }
            .addOnSuccessListener { cont.resume(it) }
    }

    override suspend fun deleteToken() {
        FirebaseMessaging.getInstance().deleteToken()
    }
}
