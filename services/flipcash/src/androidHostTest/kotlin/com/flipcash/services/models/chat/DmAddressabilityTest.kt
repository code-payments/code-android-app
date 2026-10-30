package com.flipcash.services.models.chat

import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.VerifiableContactMethod
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rule the feed filters on and the open conversation deactivates on. They used to be two
 * copies that disagreed about tip DMs, so the cases below are written from the caller's side:
 * what should stay open, and what should not.
 */
class DmAddressabilityTest {

    private fun profile(
        displayName: String = "",
        phone: String? = null,
        phoneVerified: Boolean = true,
        username: String? = null,
    ) = UserProfile.Empty.copy(
        displayName = displayName,
        username = username,
        phoneNumber = phone?.let {
            VerifiableContactMethod(value = it, verified = phoneVerified)
        },
    )

    @Test
    fun `a tip DM counterparty with only a handle is addressable`() {
        assertTrue(isDmAddressable(ChatType.TIP_DM, profile(username = "sally_streamer")))
    }

    @Test
    fun `a tip DM counterparty with nothing at all is still addressable`() {
        // Addressed by user id, so there is nothing for a missing name to break.
        assertTrue(isDmAddressable(ChatType.TIP_DM, UserProfile.Empty))
    }

    @Test
    fun `a contact DM counterparty with a name is addressable`() {
        assertTrue(isDmAddressable(ChatType.CONTACT_DM, profile(displayName = "Ada Lovelace")))
    }

    @Test
    fun `a contact DM counterparty with only a verified phone is addressable`() {
        assertTrue(isDmAddressable(ChatType.CONTACT_DM, profile(phone = "+15551234567")))
    }

    @Test
    fun `a contact DM counterparty who unlinked their phone and has no name is not`() {
        assertFalse(isDmAddressable(ChatType.CONTACT_DM, UserProfile.Empty))
    }

    @Test
    fun `an unverified phone does not count as identity`() {
        assertFalse(
            isDmAddressable(
                ChatType.CONTACT_DM,
                profile(phone = "+15551234567", phoneVerified = false),
            )
        )
    }

    @Test
    fun `a blank name does not count as identity`() {
        assertFalse(isDmAddressable(ChatType.CONTACT_DM, profile(displayName = "   ")))
    }

    @Test
    fun `a handle alone does not rescue a contact DM`() {
        // Deliberate: a contact DM is reached through the phone number, so a handle is not a
        // substitute for one. Only the naming rule (nameOrHandle) treats them as interchangeable.
        assertFalse(isDmAddressable(ChatType.CONTACT_DM, profile(username = "ada")))
    }

    @Test
    fun `an unresolved chat type is left open`() {
        // The open conversation reports UNKNOWN until its kind settles; gating there would flash
        // the deactivated composer.
        assertTrue(isDmAddressable(ChatType.UNKNOWN, UserProfile.Empty))
    }
}
