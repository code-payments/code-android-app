package com.flipcash.app.messenger.internal

import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.shared.chat.ActiveTypist
import com.getcode.opencode.model.core.ID
import com.getcode.utils.hexEncodedString
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
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

    private val adaPicture = MediaItem(renditions = emptyList())

    private val profiles = mapOf(
        ada.hexEncodedString() to profile("Ada", picture = adaPicture),
        grace.hexEncodedString() to profile("Grace", picture = null),
    )

    private fun profile(name: String, picture: MediaItem?) = UserProfile(
        displayName = name,
        socialAccounts = emptyList(),
        phoneNumber = null,
        email = null,
        profilePicture = picture,
    )

    private fun typing(userId: ID, atMillis: Long) =
        ActiveTypist(userId, Instant.fromEpochMilliseconds(atMillis))

    private val pictureUrl: suspend (ID, MediaItem) -> String? = { userId, _ ->
        "https://cdn/${userId.hexEncodedString()}"
    }

    @Test
    fun `a group lists its typists oldest first`() = runTest {
        val avatars = typingAvatars(
            // Out of order on purpose: a set carries no order, so `since` has to supply it.
            typists = setOf(typing(linus, 300), typing(ada, 100), typing(grace, 200)),
            chatType = ChatType.GROUP,
            profiles = profiles,
            pictureUrl = pictureUrl,
        )

        // Ada has a picture; Grace has a profile but no picture; Linus has no profile yet. The
        // last two fall back to their ids, which is what draws the Person icon.
        assertEquals(listOf("https://cdn/${ada.hexEncodedString()}", grace, linus), avatars)
    }

    @Test
    fun `a DM with a typist lists nobody`() = runTest {
        val typists = setOf(typing(ada, 100))

        assertEquals(
            emptyList(),
            typingAvatars(typists, ChatType.CONTACT_DM, profiles, pictureUrl),
        )
        assertEquals(
            emptyList(),
            typingAvatars(typists, ChatType.TIP_DM, profiles, pictureUrl),
        )
    }

    @Test
    fun `a typist who stops typing drops out`() = runTest {
        val both = setOf(typing(ada, 100), typing(grace, 200))
        assertEquals(
            listOf("https://cdn/${ada.hexEncodedString()}", grace),
            typingAvatars(both, ChatType.GROUP, profiles, pictureUrl),
        )

        val adaStopped = both.filterNot { it.userId == ada }.toSet()
        assertEquals(
            listOf<Any>(grace),
            typingAvatars(adaStopped, ChatType.GROUP, profiles, pictureUrl),
        )
    }

    @Test
    fun `a picture with no URL falls back to the id`() = runTest {
        val avatars = typingAvatars(
            typists = setOf(typing(ada, 100)),
            chatType = ChatType.GROUP,
            profiles = profiles,
            pictureUrl = { _, _ -> null },
        )

        assertEquals(listOf<Any>(ada), avatars)
    }
}
