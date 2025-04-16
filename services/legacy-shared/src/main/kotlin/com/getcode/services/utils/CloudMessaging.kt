package com.getcode.services.utils

import com.getcode.utils.ErrorUtils
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

suspend fun FirebaseMessaging.token(): String? = suspendCancellableCoroutine { cont ->

    this.token.addOnCanceledListener { cont.resume(null) }
        .addOnFailureListener {
            ErrorUtils.handleError(it)
            cont.resume(null)
        }.addOnSuccessListener { cont.resume(it) }
}