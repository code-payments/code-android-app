package com.flipcash.app.core.userprofile

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Result returned by the UpdateUserProfile flow to its caller.
 */

@Serializable
sealed interface UpdateProfileResult : Parcelable {
    @Parcelize
    @Serializable
    data object Success : UpdateProfileResult

    @Parcelize
    @Serializable
    data object Canceled : UpdateProfileResult
}
