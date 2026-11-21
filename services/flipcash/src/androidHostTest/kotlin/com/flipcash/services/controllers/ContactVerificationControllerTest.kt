package com.flipcash.services.controllers

import com.flipcash.services.models.ContactMethod
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.VerifiableContactMethod
import com.flipcash.services.repository.ContactVerificationRepository
import com.flipcash.services.user.UserManager
import com.getcode.ed25519kmp.Ed25519
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactVerificationControllerTest {

    private val repository = mockk<ContactVerificationRepository>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val controller = ContactVerificationController(repository, userManager)

    private fun emptyProfile() = UserProfile(
        displayName = "Test",
        socialAccounts = emptyList(),
        phoneNumber = null,
        email = null,
    )

    @Test
    fun `setLocalUnverified writes an unverified email to the profile`() {
        every { userManager.profile } returns emptyProfile()

        controller.setLocalUnverified(ContactMethod.Email("e@test.com"))

        verify {
            userManager.set(
                match<UserProfile> { it.email == VerifiableContactMethod("e@test.com", verified = false) }
            )
        }
    }

    @Test
    fun `setLocalUnverified writes an unverified phone to the profile`() {
        every { userManager.profile } returns emptyProfile()

        controller.setLocalUnverified(ContactMethod.Phone("+15551234567"))

        verify {
            userManager.set(
                match<UserProfile> { it.phoneNumber == VerifiableContactMethod("+15551234567", verified = false) }
            )
        }
    }

    @Test
    fun `setLocalUnverified is a no-op when there is no profile`() {
        every { userManager.profile } returns null

        controller.setLocalUnverified(ContactMethod.Email("e@test.com"))

        verify(exactly = 0) { userManager.set(any<UserProfile>()) }
    }

    private val owner = mockk<Ed25519.KeyPair>()

    private fun withOwner() {
        every { userManager.accountCluster?.authority?.keyPair } returns owner
    }

    @Test
    fun `linkUnverifiedEmail links on the server and records the address as unverified`() = runTest {
        withOwner()
        every { userManager.profile } returns emptyProfile()
        coEvery { repository.linkUnverified(any(), any()) } returns Result.success(Unit)

        val result = controller.linkUnverifiedEmail(ContactMethod.Email("e@example.com"))

        assertTrue(result.isSuccess)
        coVerify { repository.linkUnverified(ContactMethod.Email("e@example.com"), owner) }
        verify {
            userManager.set(
                match<UserProfile> { it.email == VerifiableContactMethod("e@example.com", verified = false) }
            )
        }
    }

    @Test
    fun `linkUnverifiedEmail keeps a verified address verified when it is re-linked unchanged`() = runTest {
        withOwner()
        every { userManager.profile } returns emptyProfile()
            .copy(email = VerifiableContactMethod("e@example.com", verified = true))
        coEvery { repository.linkUnverified(any(), any()) } returns Result.success(Unit)

        controller.linkUnverifiedEmail(ContactMethod.Email("e@example.com"))

        verify {
            userManager.set(
                match<UserProfile> { it.email == VerifiableContactMethod("e@example.com", verified = true) }
            )
        }
    }

    @Test
    fun `linkUnverifiedEmail replaces a verified address with a different unverified one`() = runTest {
        withOwner()
        every { userManager.profile } returns emptyProfile()
            .copy(email = VerifiableContactMethod("old@example.com", verified = true))
        coEvery { repository.linkUnverified(any(), any()) } returns Result.success(Unit)

        controller.linkUnverifiedEmail(ContactMethod.Email("new@example.com"))

        verify {
            userManager.set(
                match<UserProfile> { it.email == VerifiableContactMethod("new@example.com", verified = false) }
            )
        }
    }

    @Test
    fun `linkUnverifiedEmail leaves the profile alone when the server denies the link`() = runTest {
        withOwner()
        every { userManager.profile } returns emptyProfile()
        coEvery { repository.linkUnverified(any(), any()) } returns Result.failure(IllegalStateException("denied"))

        val result = controller.linkUnverifiedEmail(ContactMethod.Email("e@example.com"))

        assertTrue(result.isFailure)
        verify(exactly = 0) { userManager.set(any<UserProfile>()) }
    }

    @Test
    fun `linkUnverifiedEmail fails without calling the server when there is no account`() = runTest {
        every { userManager.accountCluster } returns null

        val result = controller.linkUnverifiedEmail(ContactMethod.Email("e@example.com"))

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.linkUnverified(any(), any()) }
    }
}
