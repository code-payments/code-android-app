package com.flipcash.services.models

import com.getcode.opencode.model.core.ID

/**
 * What to resolve to an on-chain address. Mirrors the resolver `Identifier` oneof:
 * a resolution is keyed by exactly one of a phone number or a user ID.
 */
sealed interface ResolveIdentifier {
    data class Phone(val phone: ContactMethod.Phone) : ResolveIdentifier
    data class UserId(val userId: ID) : ResolveIdentifier
}
