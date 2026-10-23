package com.flipcash.services.controllers

import com.flipcash.services.models.ContactMethod
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.VerifiableContactMethod
import com.flipcash.services.repository.ContactVerificationRepository
import com.flipcash.services.user.UserManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
}
