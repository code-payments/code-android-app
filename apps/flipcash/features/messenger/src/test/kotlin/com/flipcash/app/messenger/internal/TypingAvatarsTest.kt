package com.flipcash.app.messenger.internal

import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatType
import com.flipcash.shared.chat.ActiveTypist
import com.getcode.opencode.model.core.ID
import com.getcode.utils.hexEncodedString
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The faces the typing indicator draws. Pinned: a group lists its typists oldest first so the
 * indicator's `takeLast` keeps the newest, a DM or tip chat lists nobody, and a typist who stops
 * drops out.
 */
class TypingAvatarsTest {

    private val ada: ID = List(16) { 1 }
    private val grace: ID = List(16) { 2 }
    private val linus: ID = List(16) { 3 }

    private val adaProfile = profile("Ada")
    private val graceProfile = profile("Grace")

    private val profiles = mapOf(
        ada.hexEncodedString() to adaProfile,
        grace.hexEncodedString() to graceProfile,
    )

    private fun profile(name: String) = UserProfile(
        displayName = name,
        socialAccounts = emptyList(),
        phoneNumber = null,
        email = null,
    )

    private fun typing(userId: ID, atMillis: Long) =
        ActiveTypist(userId, Instant.fromEpochMilliseconds(atMillis))

    @Test
    fun `a group lists its typists oldest first`() {
        val avatars = typingAvatars(
            // Out of order on purpose: a set carries no order, so `since` has to supply it.
            typists = setOf(typing(linus, 300), typing(ada, 100), typing(grace, 200)),
            chatType = ChatType.GROUP,
            profiles = profiles,
        )

        // Linus has no profile yet, and is still listed so the fallback can hold his place.
        assertEquals(
            listOf(
                TypingAvatar(ada, adaProfile),
                TypingAvatar(grace, graceProfile),
                TypingAvatar(linus, null),
            ),
            avatars,
        )
    }

    @Test
    fun `a DM with a typist lists nobody`() {
        val typists = setOf(typing(ada, 100))

        assertTrue(typingAvatars(typists, ChatType.CONTACT_DM, profiles).isEmpty())
        assertTrue(typingAvatars(typists, ChatType.TIP_DM, profiles).isEmpty())
    }

    @Test
    fun `a typist who stops typing drops out`() {
        val both = setOf(typing(ada, 100), typing(grace, 200))
        assertEquals(
            listOf(ada, grace),
            typingAvatars(both, ChatType.GROUP, profiles).map { it.userId },
        )

        val adaStopped = both.filterNot { it.userId == ada }.toSet()
        assertEquals(
            listOf(grace),
            typingAvatars(adaStopped, ChatType.GROUP, profiles).map { it.userId },
        )
    }

    @Test
    fun `a typist keeps their key when their profile resolves`() {
        // The indicator keys its lazy items on this; a key that moved when the profile landed
        // would animate the avatar out and back in mid-typing.
        val before = TypingAvatar(ada, profile = null)
        val after = TypingAvatar(ada, adaProfile)

        assertEquals(before.key, after.key)
    }
}
