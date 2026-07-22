package com.flipcash.shared.tipping

import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.controllers.ResolverController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TippingCoordinatorTest {

    private val profileController = mockk<ProfileController>()
    private val userManager = mockk<UserManager>()
    private val resolverController = mockk<ResolverController>()
    private val coordinator = TippingCoordinator(profileController, userManager, resolverController)

    private fun profile(name: String) = UserProfile(
        displayName = name,
        socialAccounts = emptyList(),
        phoneNumber = null,
        email = null,
    )

    @Test
    fun `resolveProfile delegates to ProfileController`() = runTest {
        val userId = listOf<Byte>(1, 2, 3)
        val expected = profile("Alice")
        coEvery { profileController.getProfileForUser(userId) } returns Result.success(expected)

        val result = coordinator.resolveProfile(userId)

        assertSame(expected, result.getOrNull())
    }

    @Test
    fun `currentUserProfile returns the cached profile when present`() = runTest {
        val cached = profile("Me")
        every { userManager.profile } returns cached

        val result = coordinator.currentUserProfile()

        assertSame(cached, result.getOrNull())
    }

    @Test
    fun `currentUserProfile refreshes from the server when nothing is cached`() = runTest {
        val fetched = profile("Fresh")
        every { userManager.profile } returns null
        coEvery { profileController.updateUserProfile() } returns Result.success(fetched)

        val result = coordinator.currentUserProfile()

        assertSame(fetched, result.getOrNull())
    }

    @Test
    fun `currentUserId reflects UserManager accountId`() {
        val id = listOf<Byte>(9, 9)
        every { userManager.accountId } returns id

        assertEquals(id, coordinator.currentUserId)
    }
}
