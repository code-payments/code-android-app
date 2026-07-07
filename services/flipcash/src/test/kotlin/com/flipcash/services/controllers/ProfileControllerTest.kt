package com.flipcash.services.controllers

import com.flipcash.services.models.SocialAccount
import com.flipcash.services.models.SocialAccountLinkRequest
import com.flipcash.services.models.SocialAccountUnlinkRequest
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.VerifiableContactMethod
import com.flipcash.services.repository.ProfileRepository
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.ID
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileControllerTest {

    private val repository = FakeProfileRepository()
    private val userManager = mockk<UserManager>(relaxed = true)

    private val controller = ProfileController(repository, userManager)

    private fun stubOwner() {
        val keyPair = mockk<Ed25519.KeyPair>(relaxed = true)
        val cluster = mockk<AccountCluster>(relaxed = true) {
            every { authority } returns mockk { every { this@mockk.keyPair } returns keyPair }
        }
        every { userManager.accountCluster } returns cluster
    }

    // region updateUserProfile

    @Test
    fun `updateUserProfile fails when no account id`() = runTest {
        every { userManager.accountId } returns null

        val result = controller.updateUserProfile()

        assertTrue(result.isFailure)
    }

    @Test
    fun `updateUserProfile fails when no account cluster`() = runTest {
        every { userManager.accountId } returns listOf(1)
        every { userManager.accountCluster } returns null

        val result = controller.updateUserProfile()

        assertTrue(result.isFailure)
    }

    @Test
    fun `updateUserProfile caches profile on success`() = runTest {
        stubOwner()
        every { userManager.accountId } returns listOf(1)
        every { userManager.profile } returns null

        val profile = stubProfile()
        repository.getProfileResult = Result.success(profile)

        val result = controller.updateUserProfile()

        assertTrue(result.isSuccess)
        assertEquals(profile, result.getOrThrow())
        verify { userManager.set(profile) }
    }

    @Test
    fun `updateUserProfile preserves locally-entered unverified contact when server omits it`() = runTest {
        stubOwner()
        every { userManager.accountId } returns listOf(1)
        // Local profile has an unverified email the user entered; server profile has none.
        val localEmail = VerifiableContactMethod("entered@test.com", verified = false)
        every { userManager.profile } returns stubProfile().copy(email = localEmail)
        repository.getProfileResult = Result.success(stubProfile())

        val result = controller.updateUserProfile()

        assertEquals(localEmail, result.getOrThrow().email)
        verify { userManager.set(match<UserProfile> { it.email == localEmail }) }
    }

    @Test
    fun `updateUserProfile does not override a server-provided contact`() = runTest {
        stubOwner()
        every { userManager.accountId } returns listOf(1)
        every { userManager.profile } returns stubProfile()
            .copy(email = VerifiableContactMethod("stale@test.com", verified = false))
        val serverEmail = VerifiableContactMethod("verified@test.com", verified = true)
        repository.getProfileResult = Result.success(stubProfile().copy(email = serverEmail))

        val result = controller.updateUserProfile()

        assertEquals(serverEmail, result.getOrThrow().email)
    }

    @Test
    fun `updateUserProfile propagates failure without caching`() = runTest {
        stubOwner()
        every { userManager.accountId } returns listOf(1)
        repository.getProfileResult = Result.failure(RuntimeException("not found"))

        val result = controller.updateUserProfile()

        assertTrue(result.isFailure)
        verify(exactly = 0) { userManager.set(any<UserProfile>()) }
    }

    // endregion

    // region getProfileForUser

    @Test
    fun `getProfileForUser fails when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null

        val result = controller.getProfileForUser(listOf(1))

        assertTrue(result.isFailure)
    }

    @Test
    fun `getProfileForUser delegates to repository`() = runTest {
        stubOwner()
        val profile = stubProfile()
        repository.getProfileResult = Result.success(profile)

        val result = controller.getProfileForUser(listOf(1))

        assertTrue(result.isSuccess)
        assertEquals(profile, result.getOrThrow())
    }

    // endregion

    // region setDisplayName

    @Test
    fun `setDisplayName fails when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null

        val result = controller.setDisplayName("Test")

        assertTrue(result.isFailure)
    }

    @Test
    fun `setDisplayName delegates to repository`() = runTest {
        stubOwner()
        repository.setDisplayNameResult = Result.success(Unit)

        val result = controller.setDisplayName("Test")

        assertTrue(result.isSuccess)
    }

    // endregion

    // region linkTwitterXAccount

    @Test
    fun `linkTwitterXAccount fails when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null

        val result = controller.linkTwitterXAccount("token-123")

        assertTrue(result.isFailure)
    }

    @Test
    fun `linkTwitterXAccount delegates to repository`() = runTest {
        stubOwner()
        val account = stubTwitterXAccount()
        repository.linkSocialAccountResult = Result.success(account)

        val result = controller.linkTwitterXAccount("token-123")

        assertTrue(result.isSuccess)
        assertEquals(account, result.getOrThrow())
    }

    @Test
    fun `linkTwitterXAccount propagates failure`() = runTest {
        stubOwner()
        repository.linkSocialAccountResult = Result.failure(RuntimeException("denied"))

        val result = controller.linkTwitterXAccount("token-123")

        assertTrue(result.isFailure)
    }

    // endregion

    // region unlinkTwitterXAccount

    @Test
    fun `unlinkTwitterXAccount fails when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null

        val result = controller.unlinkTwitterXAccount(stubTwitterXAccount())

        assertTrue(result.isFailure)
    }

    @Test
    fun `unlinkTwitterXAccount delegates to repository`() = runTest {
        stubOwner()
        repository.unlinkSocialAccountResult = Result.success(Unit)

        val result = controller.unlinkTwitterXAccount(stubTwitterXAccount())

        assertTrue(result.isSuccess)
    }

    // endregion

    // region helpers

    private fun stubProfile() = UserProfile(
        displayName = "Test User",
        socialAccounts = emptyList(),
        phoneNumber = null,
        email = null,
    )

    private fun stubTwitterXAccount() = SocialAccount.TwitterX(
        id = "123",
        username = "testuser",
        name = "Test User",
        description = "",
        profilePicUrl = "",
        verifiedType = null,
        followerCount = 0,
    )

    // endregion
}

// region Fakes

private class FakeProfileRepository : ProfileRepository {
    var getProfileResult: Result<UserProfile> = Result.failure(RuntimeException("not configured"))
    var setDisplayNameResult: Result<Unit> = Result.success(Unit)
    var linkSocialAccountResult: Result<SocialAccount> = Result.failure(RuntimeException("not configured"))
    var unlinkSocialAccountResult: Result<Unit> = Result.success(Unit)

    override suspend fun getProfile(userId: ID, owner: Ed25519.KeyPair) = getProfileResult
    override suspend fun setDisplayName(displayName: String, owner: Ed25519.KeyPair) = setDisplayNameResult
    override suspend fun linkSocialAccount(request: SocialAccountLinkRequest, owner: Ed25519.KeyPair) =
        linkSocialAccountResult
    override suspend fun unlinkSocialAccount(request: SocialAccountUnlinkRequest, owner: Ed25519.KeyPair) =
        unlinkSocialAccountResult
}

// endregion
