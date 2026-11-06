package com.flipcash.app.messenger.internal

import com.flipcash.app.messenger.internal.RestrictedMembersViewModel.Event
import com.flipcash.app.messenger.internal.RestrictedMembersViewModel.State
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.BanState
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.RestrictedUser
import com.flipcash.services.models.chat.SilenceState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class RestrictedMembersStateTest {

    private fun reduce(state: State, event: Event) = RestrictedMembersViewModel.updateStateForEvent(event)(state)

    private val chatId = ChatId(ByteArray(32) { 3 }.toList())
    private val banned = BanState(Clock.System.now())

    private fun user(id: Int, silence: SilenceState? = null, ban: BanState? = null) = RestrictedUser(
        userProfile = UserProfile(
            displayName = "User $id", socialAccounts = emptyList(), phoneNumber = null, email = null,
            userId = listOf(id.toByte()),
        ),
        silence = silence,
        ban = ban,
    )

    private val silenced = user(1, silence = SilenceState.Forever)
    private val removed = user(2, ban = banned)
    private val both = user(3, silence = SilenceState.Forever, ban = banned)
    private val loaded = reduce(State(), Event.Loaded(listOf(silenced, removed, both)))

    @Test
    fun `public actions leave state alone until their work reports back`() {
        val id = silenced.userProfile.userId!!
        assertEquals(loaded, reduce(loaded, Event.Load(chatId)))
        assertEquals(loaded, reduce(loaded, Event.Unsilence(chatId, id, "Ada")))
        assertEquals(loaded, reduce(loaded, Event.Unban(chatId, id)))
    }

    @Test
    fun `a new read starts from an empty loading state`() {
        val failed = reduce(loaded, Event.LoadFailed)
        assertTrue(failed.failed)
        assertFalse(failed.loading)

        assertEquals(State(), reduce(failed, Event.LoadStarted))
    }

    @Test
    fun `a failed read keeps the users it had`() {
        val failed = reduce(loaded, Event.LoadFailed)
        assertEquals(loaded.users, failed.users)
    }

    @Test
    fun `pending marks a row until it succeeds or fails`() {
        val id = silenced.userProfile.userId!!
        val pending = reduce(loaded, Event.RestrictionPending(id))
        assertEquals(setOf(id), pending.pending)

        val failed = reduce(pending, Event.RestrictionFailed(id))
        assertTrue(failed.pending.isEmpty())
        assertEquals(loaded.users, failed.users)
    }

    @Test
    fun `unsilenced drops a silenced row and strips the silence from a removed one`() {
        val afterSilenced = reduce(loaded, Event.Unsilenced(silenced.userProfile.userId!!))
        assertEquals(listOf(removed, both), afterSilenced.users)

        val afterBoth = reduce(afterSilenced, Event.Unsilenced(both.userProfile.userId!!))
        assertEquals(listOf(removed, both.copy(silence = null)), afterBoth.users)
    }

    @Test
    fun `unbanned drops a removed row and moves a silenced one back to silenced`() {
        val afterRemoved = reduce(loaded, Event.Unbanned(removed.userProfile.userId!!))
        assertEquals(listOf(silenced, both), afterRemoved.users)

        val afterBoth = reduce(afterRemoved, Event.Unbanned(both.userProfile.userId!!))
        assertEquals(listOf(silenced, both.copy(ban = null)), afterBoth.rows(RestrictedKind.Silenced))
        assertTrue(afterBoth.rows(RestrictedKind.Removed).isEmpty())
    }
}
