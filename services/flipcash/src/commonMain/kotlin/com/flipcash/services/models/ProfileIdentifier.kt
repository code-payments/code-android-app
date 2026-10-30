package com.flipcash.services.models

import com.getcode.opencode.model.core.ID

/**
 * Whose profile to fetch. Mirrors the `GetProfileRequest.identifier` oneof: a
 * profile is looked up by exactly one of a user ID or a username.
 */
sealed interface ProfileIdentifier {
    data class UserId(val userId: ID) : ProfileIdentifier
    data class Username(val username: String) : ProfileIdentifier
}
