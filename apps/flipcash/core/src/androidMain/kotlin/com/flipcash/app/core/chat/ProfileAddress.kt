package com.flipcash.app.core.chat

import android.os.Parcelable
import com.flipcash.app.core.tipping.TipCardOwner
import com.getcode.opencode.model.core.ID
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Whose profile `AppRoute.Messaging.Profile` shows, in whichever way the link that opened it named
 * them: `flipcash.com/{uuid}` by account id, `flipcash.com/{username}` by handle.
 *
 * The same fork as [TipCardOwner], and for the same reason — turning a handle into an id is a
 * server round trip, which belongs to the screen rather than to the router. Its own type because
 * it is a navigation key, so it has to be [Serializable] and [Parcelable], which a tip card's
 * owner never needed to be.
 */
@Serializable
@Parcelize
sealed interface ProfileAddress : Parcelable {

    /** By account id. */
    @Serializable
    @Parcelize
    data class ById(val userId: ID) : ProfileAddress

    /** By claimed public handle, unresolved. */
    @Serializable
    @Parcelize
    data class ByUsername(val username: String) : ProfileAddress

    /**
     * The same person as the owner of a tip card — for the surfaces that still present the card
     * for a profile link, such as the scanner reading a printed one.
     */
    val tipCardOwner: TipCardOwner
        get() = when (this) {
            is ById -> TipCardOwner.ById(userId)
            is ByUsername -> TipCardOwner.ByUsername(username)
        }

    /** Whether this addresses the signed-in account. See [TipCardOwner.isSelf] for the rules. */
    fun isSelf(accountId: ID?, username: String?): Boolean =
        tipCardOwner.isSelf(accountId, username)
}
