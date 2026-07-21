package com.flipcash.app.core.userprofile

import android.os.Parcelable
import com.getcode.navigation.flow.FlowStep
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
sealed interface UpdateProfileStep : FlowStep, Parcelable {
    @Parcelize
    @Serializable
    object Name : UpdateProfileStep

    @Parcelize
    @Serializable
    object Photo : UpdateProfileStep


}