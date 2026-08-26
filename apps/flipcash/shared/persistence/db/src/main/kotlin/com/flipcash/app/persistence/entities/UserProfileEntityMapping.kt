package com.flipcash.app.persistence.entities

import com.flipcash.app.persistence.converters.ChatTypeConverters
import com.flipcash.app.persistence.converters.UserProfileSerialized
import com.flipcash.services.models.VerifiableContactMethod

// A single shared converter instance; only used to parse the transient legacy blob.
private val migrationConverters = ChatTypeConverters()

/**
 * Reconstructs the serialized profile from a normalized [UserProfileEntity].
 *
 * If the row still carries a [UserProfileEntity.pendingMigrationJson] blob (migrated from
 * v25 but not yet decomposed by the backfill), that blob wins — so reads render correctly
 * regardless of backfill timing. Otherwise the decomposed columns are used.
 */
fun UserProfileEntity.toSerialized(): UserProfileSerialized {
    pendingMigrationJson?.let { pending ->
        migrationConverters.fromUserProfile(pending)?.let { return it }
    }
    return UserProfileSerialized(
        displayName = displayName,
        socialAccounts = socialAccounts.orEmpty(),
        phoneNumber = phoneValue?.let { VerifiableContactMethod(it, phoneVerified ?: false) },
        email = emailValue?.let { VerifiableContactMethod(it, emailVerified ?: false) },
        profilePicture = profilePicture,
        username = username,
    )
}

/**
 * Decomposes a row's staged legacy blob into the real columns, clearing the staging field.
 * Returns the same row unchanged if there's nothing staged. Used by [backfillMigratedProfiles].
 */
internal fun UserProfileEntity.decomposePending(): UserProfileEntity {
    val pending = pendingMigrationJson ?: return this
    val parsed = migrationConverters.fromUserProfile(pending)
        ?: return copy(pendingMigrationJson = null)
    return copy(
        displayName = parsed.displayName?.takeIf { it.isNotEmpty() } ?: displayName,
        phoneValue = parsed.phoneNumber?.value,
        phoneVerified = parsed.phoneNumber?.verified,
        emailValue = parsed.email?.value,
        emailVerified = parsed.email?.verified,
        socialAccounts = parsed.socialAccounts,
        profilePicture = parsed.profilePicture,
        pendingMigrationJson = null,
    )
}
