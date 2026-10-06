package com.flipcash.app.messenger.internal

import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.app.messenger.internal.link.UserLinkLookup
import com.flipcash.services.models.GetUserProfileError
import com.flipcash.services.models.UserProfile
import com.flipcash.shared.chat.models.LinkCard
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Where a tapped `@handle` lands once its lookup answers. Ports iOS's `MentionDestinationTests`. */
class MentionDestinationTest {

    private val otherId = listOf(2.toByte())
    private val counterpartId = listOf(3.toByte())
    private val selfId = listOf(1.toByte())

    private fun profile(userId: List<Byte>) =
        UserProfile.Empty.copy(displayName = "Satoshi", username = "satoshi", userId = userId)

    private fun found(userId: List<Byte>, isOwn: Boolean = false) = Result.success(
        LinkCard.User.State.Resolved(
            userId = userId,
            isOwn = isOwn,
            profile = profile(userId),
            name = "Satoshi",
            handle = "@satoshi",
            joined = null,
        )
    )

    @Test
    fun `someone else opens their profile with chat actions`() {
        assertEquals(
            MentionDestination.Profile(ChatParticipant.TipUser(otherId, profile(otherId)), ProfileOrigin.Mention),
            mentionDestination("satoshi", found(otherId), counterpart = counterpartId),
        )
    }

    @Test
    fun `the DM counterpart opens the way the title does`() {
        assertEquals(
            MentionDestination.Profile(ChatParticipant.TipUser(counterpartId, profile(counterpartId)), ProfileOrigin.Chat),
            mentionDestination("satoshi", found(counterpartId), counterpart = counterpartId),
        )
    }

    @Test
    fun `the viewer's own handle opens their tip card`() {
        assertEquals(
            MentionDestination.OwnTipCard,
            mentionDestination("satoshi", found(otherId, isOwn = true), counterpart = null),
        )
    }

    @Test
    fun `an unclaimed handle is no such account`() {
        val noAccount = UserLinkLookup.NoSuchAccount(LinkCard.User.Identity.ByUsername("nobody"))
        assertEquals(
            MentionDestination.NoSuchAccount("nobody"),
            mentionDestination("nobody", Result.failure(noAccount), counterpart = null),
        )
        assertEquals(
            MentionDestination.NoSuchAccount("nobody"),
            mentionDestination("nobody", Result.failure(GetUserProfileError.NotFound()), counterpart = null),
        )
    }

    @Test
    fun `a failed lookup is not read as unclaimed`() {
        assertEquals(
            MentionDestination.LookupFailed,
            mentionDestination("satoshi", Result.failure(IOException()), counterpart = null),
        )
    }
}
