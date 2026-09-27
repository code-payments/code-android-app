package com.flipcash.app.messenger.internal.link

import com.flipcash.app.messenger.internal.joinedLine
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.models.LinkCard
import com.getcode.opencode.model.core.bytes
import com.getcode.util.resources.ResourceHelper
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.io.IOException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class UserLinkLookupTest {

    private val theirId = UUID.fromString("2b0b4d1e-9f3e-4c21-9f1a-6d5f7c8e9a0b").bytes
    private val myId = UUID.fromString("6f1c3a9e-2b7d-4e0a-9c55-1d2e3f405162").bytes

    private fun profile(
        displayName: String = "Satoshi",
        username: String? = "satoshi",
        joinedAt: Instant? = Instant.fromEpochSeconds(1_710_000_000),
        userId: List<Byte>? = theirId,
    ) = UserProfile(
        displayName = displayName,
        socialAccounts = emptyList(),
        phoneNumber = null,
        email = null,
        joinedAt = joinedAt,
        userId = userId,
        username = username,
    )

    private fun lookup(
        profiles: ProfileController,
        viewer: List<Byte>? = myId,
    ) = UserLinkLookup(
        profiles = profiles,
        userManager = mock<UserManager> { on { accountId } doReturn viewer },
        resources = mock<ResourceHelper>(),
    )

    @Test
    fun `a link to someone else is not the viewer's own`() {
        val state = userCardState(profile(), viewerId = myId, joined = { "Joined March 2024" })

        assertEquals(theirId, state?.userId)
        assertFalse(state!!.isOwn)
        assertEquals("Satoshi", state.name)
        assertEquals("@satoshi", state.handle)
        assertEquals("Joined March 2024", state.joined)
    }

    @Test
    fun `a link to the viewer is their own`() {
        val state = userCardState(profile(userId = myId), viewerId = myId, joined = { "" })

        assertTrue(state!!.isOwn)
    }

    @Test
    fun `no join date leaves the detail line out`() {
        val state = userCardState(profile(joinedAt = null), viewerId = myId, joined = { "unused" })

        assertNull(state?.joined)
    }

    @Test
    fun `no handle leaves the handle line out`() {
        val state = userCardState(profile(username = null), viewerId = myId, joined = { "" })

        assertEquals("Satoshi", state?.name)
        assertNull(state?.handle)
    }

    @Test
    fun `an unclaimed handle is not found`() = runTest {
        val profiles = mock<ProfileController> {
            onBlocking { getProfileForUsername(any()) } doReturn
                Result.success(profile(displayName = "", username = null, joinedAt = null, userId = null))
        }

        val result = lookup(profiles)(LinkCard.User.Identity.ByUsername("nobody"))

        assertIs<UserLinkLookup.NoSuchAccount>(result.exceptionOrNull())
    }

    @Test
    fun `a failed fetch is not found`() = runTest {
        val profiles = mock<ProfileController> {
            onBlocking { getProfileForUser(any()) } doReturn Result.failure(IOException())
        }

        val result = lookup(profiles)(LinkCard.User.Identity.ById(theirId))

        assertIs<IOException>(result.exceptionOrNull())
    }

    /** An id link is fetched by id and a handle link by handle; neither goes through the other. */
    @Test
    fun `each identity is fetched the way the link names it`() = runTest {
        val profiles = mock<ProfileController> {
            // No join date: the mocked resources have no string to format it into.
            onBlocking { getProfileForUser(any()) } doReturn Result.success(profile(joinedAt = null))
        }

        val result = lookup(profiles)(LinkCard.User.Identity.ById(theirId))

        assertEquals(theirId, result.getOrNull()?.userId)
        verify(profiles, never()).getProfileForUsername(any())
    }
}
