package com.flipcash.app.core.tipping

/**
 * A tip card link turned out to address the account that followed it.
 *
 * Raised only by the resolve-by-handle path, and only after the round trip. Both earlier
 * self-checks — `AppRouter`'s, and [com.flipcash.app.core.tipping.TipCardOwner.isSelf] in the
 * session's tip card delegate — compare handles, which they can only do once this account's own
 * profile has loaded. A link followed before that gets past both; the id the profile fetch answers
 * with settles it.
 *
 * A failure rather than a card so the resolve stops short of its side effects — arming the tip
 * modal, buzzing the phone — for a card that will never be tipped.
 */
class OwnTipCard(val username: String) :
    IllegalStateException("@$username is the signed-in account's own handle")
