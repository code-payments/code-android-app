package com.flipcash.app.auth.internal.accounts

import com.flipcash.services.models.GetUserProfileError
import com.flipcash.services.models.ProfileIdentifier
import com.flipcash.services.models.UserProfile
import com.flipcash.services.repository.AccountRepository
import com.flipcash.services.repository.ProfileRepository
import com.getcode.ed25519.Ed25519.KeyPair
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountProfileFetcherTest {

    private val owner: KeyPair = mockk()
    private val userId = listOf<Byte>(1, 2, 3)
    private val accounts: AccountRepository = mockk()
    private val profiles: ProfileRepository = mockk()
    private val cache: AccountProfileCache = mockk(relaxed = true)
    private val fetcher = AccountProfileFetcher(accounts, profiles, cache)

    @Test
    fun `fetches the profile by the stored user id, and caches it`() = runTest {
        coEvery { profiles.getProfile(ProfileIdentifier.UserId(userId), owner) } returns
            Result.success(UserProfile.Empty.copy(displayName = "Sally", username = "sally"))

        val name = fetcher.fetch(owner, "owner", userId).getOrThrow()

        assertEquals(AccountProfileName(username = "sally", displayName = "Sally"), name)
        coVerify { cache.put("owner", name) }
        coVerify(exactly = 0) { accounts.login(any()) }
    }

    /** No profile is an answer, not a failure: the account has no names, so a cached one must go. */
    @Test
    fun `an account with no profile has no names`() = runTest {
        coEvery { profiles.getProfile(any(), owner) } returns Result.failure(GetUserProfileError.NotFound())

        val name = fetcher.fetch(owner, "owner", userId).getOrThrow()

        assertEquals(AccountProfileName(username = null, displayName = null), name)
        coVerify { cache.put("owner", name) }
    }

    @Test
    fun `a failed fetch leaves the cache alone`() = runTest {
        coEvery { profiles.getProfile(any(), owner) } returns Result.failure(GetUserProfileError.Other())

        assertTrue(fetcher.fetch(owner, "owner", userId).isFailure)
        coVerify(exactly = 0) { cache.put(any(), any()) }
    }

    /** Login is the last resort: only an account with no stored user id goes through it. */
    @Test
    fun `resolves a missing user id with Login, and caches the id`() = runTest {
        val resolved = listOf<Byte>(9, 9)
        coEvery { accounts.login(owner) } returns Result.success(resolved)
        coEvery { profiles.getProfile(ProfileIdentifier.UserId(resolved), owner) } returns
            Result.success(UserProfile.Empty.copy(displayName = "Sally", username = null))

        val name = fetcher.fetch(owner, "owner", userId = null).getOrThrow()

        assertEquals(AccountProfileName(username = null, displayName = "Sally"), name)
        coVerify(exactly = 1) { accounts.login(owner) }
        coVerify { cache.putUserId("owner", resolved) }
        coVerify { cache.put("owner", name) }
    }

    @Test
    fun `a failed Login fetches nothing and leaves the cache alone`() = runTest {
        coEvery { accounts.login(owner) } returns Result.failure(IllegalStateException())

        assertTrue(fetcher.fetch(owner, "owner", userId = null).isFailure)
        coVerify(exactly = 0) { profiles.getProfile(any(), any()) }
        coVerify(exactly = 0) { cache.put(any(), any()) }
        coVerify(exactly = 0) { cache.putUserId(any(), any()) }
    }
}
