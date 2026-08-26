package com.flipcash.app.core.tipping

import com.getcode.opencode.model.core.ID

/**
 * Who a tip card belongs to, in whichever of the two ways the holder of this value can name them.
 *
 * The fork exists because turning a handle into an id is a server round trip. Nothing between a
 * `flipcash.com/{username}` link and the session can make that call, so the username travels
 * un-resolved the whole way — through [com.flipcash.app.core.navigation.DeeplinkAction] and back
 * out through [com.flipcash.app.core.util.Linkify]. Naming it once keeps every stop on that path
 * from carrying its own parallel pair of "by id" / "by handle" entry points.
 */
sealed interface TipCardOwner {
    /** By account id — how the app addresses a card everywhere except a vanity link. */
    data class ById(val userId: ID) : TipCardOwner

    /** By claimed public handle, unresolved. */
    data class ByUsername(val username: String) : TipCardOwner

    /**
     * Whether this addresses the account signed in right now, which has no card to present to
     * itself — tipping yourself is a payment no-op.
     *
     * Takes both identities rather than a profile, because they do not become available together:
     * [accountId] is set the moment the account authenticates, while the handle arrives with the
     * profile fetch. A single nullable profile would make a self-link by id stop matching in the
     * window before its own profile loads.
     *
     * Handles are lowercase on the wire, but a link can be typed or pasted in any case.
     */
    fun isSelf(accountId: ID?, username: String?): Boolean = when (this) {
        is ById -> accountId != null && userId == accountId
        is ByUsername -> this.username.equals(username, ignoreCase = true)
    }

    companion object {
        /**
         * A card's preferred public address: the handle when the account has claimed one, the id
         * when it hasn't.
         *
         * The precedence is as much a display decision as a routing one — the You tab shows
         * `flipcash.com/<username>` under the code (node 9442:3673), so sharing or copying anything
         * else would hand out a second, unrecognisable address for a card that names itself once.
         */
        fun preferringUsername(username: String?, userId: ID): TipCardOwner =
            username?.takeIf { it.isNotBlank() }?.let(::ByUsername) ?: ById(userId)
    }
}
