package com.flipcash.app.auth.internal.accounts

import com.flipcash.services.models.UserProfile
import com.getcode.opencode.model.core.NoId
import com.getcode.utils.hexEncodedString
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

    @Test
    fun `records the signed-in owner's user id`() {
        assertEquals("owner" to signedIn, AccountProfileCache.userIdFor(owner = "owner", accountId = signedIn))
    }

    /** Sign-out resets the id to NoId, which is empty; it must not be stored as an account's id. */
    @Test
    fun `skips a state with no owner or no user id`() {
        assertNull(AccountProfileCache.userIdFor(owner = null, accountId = signedIn))
        assertNull(AccountProfileCache.userIdFor(owner = "owner", accountId = null))
        assertNull(AccountProfileCache.userIdFor(owner = "owner", accountId = NoId))
    }

    @Test
    fun `decodes a stored user id`() {
        val id = listOf<Byte>(0, 15, -1, 127, -128)
        assertEquals(id, AccountProfileCache.decodeUserId(id.hexEncodedString()))
    }

    @Test
    fun `treats a malformed user id as absent`() {
        assertNull(AccountProfileCache.decodeUserId(""))
        assertNull(AccountProfileCache.decodeUserId("abc"))
        assertNull(AccountProfileCache.decodeUserId("zz"))
    }
}
