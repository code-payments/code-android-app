package com.flipcash.services.models

import kotlinx.serialization.Serializable

/**
 * A contact value (phone number or email address) paired with whether it has been
 * verified with the server. An unverified contact is one the user entered locally
 * (e.g. when server verification is skipped) but which has not been confirmed.
 */
@Serializable
data class VerifiableContactMethod(
    val value: String,
    val verified: Boolean,
)
