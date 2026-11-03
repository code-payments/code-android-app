package com.flipcash.app.core.links

/**
 * The hosts that are Flipcash's own: the union of Android's manifest intent filters and iOS's
 * associated-domains entitlement. The chat card classifier and the external-link warning both
 * read this one list.
 */
val FIRST_PARTY_HOSTS: Set<String> = setOf(
    "app.flipcash.com",
    "send.flipcash.com",
    "flipcash.com",
    "www.flipcash.com",
    "jump.flipcash.com",
)
