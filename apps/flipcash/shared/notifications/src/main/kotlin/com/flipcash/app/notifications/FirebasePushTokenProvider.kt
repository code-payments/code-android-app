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
    // TODO(firebase-messaging): 25.1.0 deprecated token-based registration (getToken/deleteToken)
    //  in favor of FID-based register()/unregister(). Migrate once Firebase ships a stable guide and
    //  the backend accepts FID registration.
    //  Tracking: https://github.com/firebase/firebase-android-sdk/issues/8087
    @Suppress("DEPRECATION")
    override suspend fun getToken(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnCanceledListener { cont.resume(null) }
            .addOnFailureListener {
                ErrorUtils.handleError(it)
                cont.resume(null)
            }
            .addOnSuccessListener { cont.resume(it) }
    }

    @Suppress("DEPRECATION")
    override suspend fun deleteToken() {
        FirebaseMessaging.getInstance().deleteToken()
    }
}
