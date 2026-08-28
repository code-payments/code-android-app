package com.flipcash.app.core.userprofile

import android.os.Parcelable
import com.flipcash.app.core.DisplayNameSource
import com.getcode.navigation.flow.FlowStep
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * One screen of the profile editor. A caller asks for the steps it wants, in order, and each step
 * carries whatever it alone needs — so a flow that skips the name step never has to name a
 * [DisplayNameSource] for it.
 */
@Serializable
sealed interface UpdateProfileStep : FlowStep, Parcelable {
    /** @param source where the entry was launched from; reported as the `Source` analytics property. */
    @Parcelize
    @Serializable
    data class Name(val source: DisplayNameSource) : UpdateProfileStep

    /**
     * Claiming the public `@handle`. Never part of onboarding — the server gates it behind a
     * minimum balance, so it is reached from My Account or the "You" tab once the account
     * qualifies.
     */
    @Parcelize
    @Serializable
    object Username : UpdateProfileStep

    @Parcelize
    @Serializable
    object Photo : UpdateProfileStep

    /**
     * The fee another user has to pay to open a DM, which the profile carries as
     * `minDmChatInitFee`.
     */
    @Parcelize
    @Serializable
    object MinimumTip : UpdateProfileStep
}
