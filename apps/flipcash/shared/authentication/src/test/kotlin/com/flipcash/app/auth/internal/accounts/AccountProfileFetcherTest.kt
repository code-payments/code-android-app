package com.flipcash.app.auth.internal.accounts

import com.flipcash.services.models.GetUserProfileError
import com.flipcash.services.models.ProfileIdentifier
import com.flipcash.services.models.UserProfile
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
    private val profiles: ProfileRepository = mockk()
    private val cache: AccountProfileCache = mockk(relaxed = true)
    private val fetcher = AccountProfileFetcher(profiles, cache)

    @Test
    fun `fetches the profile by the stored user id, and caches it`() = runTest {
        coEvery { profiles.getProfile(ProfileIdentifier.UserId(userId), owner) } returns
            Result.success(UserProfile.Empty.copy(displayName = "Sally", username = "sally"))

        val name = fetcher.fetch(owner, "owner", userId).getOrThrow()

        assertEquals(AccountProfileName(username = "sally", displayName = "Sally"), name)
        coVerify { cache.put("owner", name) }
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

    /**
     * The fetcher runs for accounts the user has not switched to, so it must not be able to log in
     * as them. An earlier version resolved the user id through the account service's Login RPC;
     * any dependency beyond these two fails here until someone confirms it cannot do that.
     */
    @Test
    fun `depends on nothing that can log in`() {
        val injected = AccountProfileFetcher::class.java.constructors.single().parameterTypes.toList()
        assertEquals(listOf(ProfileRepository::class.java, AccountProfileCache::class.java), injected)
    }
}
