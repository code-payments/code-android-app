package com.flipcash.app.auth.internal.accounts

import com.flipcash.services.models.UserProfile
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountProfileCacheTest {

    private val signedIn = listOf<Byte>(1, 2, 3)

    @Test
    fun `attributes a profile to the signed-in owner`() {
        val profile = UserProfile.Empty.copy(displayName = "Sally", username = "sally", userId = signedIn)
        assertEquals(
            "owner" to AccountProfileName(username = "sally", displayName = "Sally"),
            AccountProfileCache.entryFor(owner = "owner", accountId = signedIn, profile = profile),
        )
    }

    @Test
    fun `attributes a restored profile with no user id`() {
        val profile = UserProfile.Empty.copy(displayName = "Sally")
        assertEquals(
            "owner" to AccountProfileName(username = null, displayName = "Sally"),
            AccountProfileCache.entryFor(owner = "owner", accountId = signedIn, profile = profile),
        )
    }

    /** A fetch that outlived an account switch must not name the new account after the old one. */
    @Test
    fun `skips a profile that belongs to another account`() {
        val profile = UserProfile.Empty.copy(displayName = "Sally", userId = listOf(9))
        assertNull(AccountProfileCache.entryFor(owner = "owner", accountId = signedIn, profile = profile))
    }

    @Test
    fun `skips a state with no owner or no profile`() {
        assertNull(AccountProfileCache.entryFor(owner = null, accountId = signedIn, profile = UserProfile.Empty))
        assertNull(AccountProfileCache.entryFor(owner = "owner", accountId = signedIn, profile = null))
    }

    @Test
    fun `stores blank names as absent`() {
        val profile = UserProfile.Empty.copy(displayName = " ", username = "")
        assertEquals(
            "owner" to AccountProfileName(username = null, displayName = null),
            AccountProfileCache.entryFor(owner = "owner", accountId = signedIn, profile = profile),
        )
    }
}
