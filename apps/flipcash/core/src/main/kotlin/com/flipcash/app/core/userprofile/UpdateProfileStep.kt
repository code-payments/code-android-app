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

    /**
     * Claiming the public `@handle`. Optional and off by default: unlike the display name it is
     * never part of onboarding — the server gates it behind a minimum balance, so it is reached
     * from My Account or the "You" tab once the account qualifies.
     */
    @Parcelize
    @Serializable
    object Username : UpdateProfileStep

    @Parcelize
    @Serializable
    object Photo : UpdateProfileStep


}
