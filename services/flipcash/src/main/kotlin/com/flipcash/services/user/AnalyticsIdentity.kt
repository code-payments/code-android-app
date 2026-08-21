package com.flipcash.services.user

import com.getcode.opencode.model.core.ID

/**
 * The Mixpanel/Bugsnag distinct id for an account.
 *
 * Lowercase, unseparated hex — byte-identical to iOS `Data.hexEncodedString()`.
 * Both platforms must produce the same string for the same account or a single
 * user becomes two analytics profiles. Do not change this encoding without
 * changing iOS in the same release.
 */
@OptIn(ExperimentalStdlibApi::class)
fun ID.analyticsDistinctId(): String = toByteArray().toHexString()
