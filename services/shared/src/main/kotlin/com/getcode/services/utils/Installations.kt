package com.getcode.services.utils

import com.getcode.utils.ErrorUtils
import com.google.firebase.installations.FirebaseInstallations
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

suspend fun FirebaseInstallations.installationId(): String? = suspendCancellableCoroutine { cont ->
    this.id.addOnCanceledListener { cont.resume(null) }
        .addOnFailureListener {
            ErrorUtils.handleError(it)
            cont.resume(null)
        }.addOnSuccessListener { cont.resume(it) }
}