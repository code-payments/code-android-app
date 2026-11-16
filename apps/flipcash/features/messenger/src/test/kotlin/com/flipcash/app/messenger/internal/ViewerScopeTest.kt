package com.flipcash.app.messenger.internal

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.MuteState
import com.flipcash.services.models.chat.SilenceState
import com.flipcash.services.models.chat.ViewerState
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatMembership
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * [ViewerScope] on its own: its reducer over [ViewerSlice], and its effects bound to a test scope
 * with a fake coordinator and the scope's own slice standing in for the view model.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewerScopeTest {

    private val chatId = ChatId(ByteArray(16) { 5 }.toList())
    private val muted = ViewerState(mute = MuteState.Forever, version = 1L)
    private fun silenced(silence: SilenceState) =
        ViewerState(restrictions = ViewerState.Restrictions(silence))

    // Reducer

    @Test
    fun `resolving the viewer state stores it and restarts the silence clock`() {
        val before = ViewerSlice(silenceClock = Instant.fromEpochSeconds(0))

        val after = before.reduce(ViewerEvent.OnViewerStateResolved(muted))

        assertEquals(muted, after.viewerState)
        assertTrue(after.silenceClock > before.silenceClock)
    }

    @Test
    fun `a lapse moves only the clock`() {
        val before = ViewerSlice(viewerState = muted, silenceClock = Instant.fromEpochSeconds(0))

        val after = before.reduce(ViewerEvent.SilenceLapsed)

        assertEquals(muted, after.viewerState)
        assertTrue(after.silenceClock > before.silenceClock)
    }

    @Test
    fun `a lift leaves the slice alone`() {
        val before = ViewerSlice(viewerState = muted)
        assertSame(before, before.reduce(ViewerEvent.SilenceLifted))
    }

    @Test
    fun `a timed silence reads as lapsed once the clock passes its deadline`() {
        val deadline = Instant.fromEpochSeconds(1_000)
        val slice = ViewerSlice(viewerState = silenced(SilenceState.Until(deadline)), silenceClock = deadline - 1.minutes)

        assertEquals(SilenceState.Until(deadline), slice.silence)
        assertNull(slice.copy(silenceClock = deadline + 1.minutes).silence)
    }

    @Test
    fun `can pin reads the permission and nothing else`() {
        assertFalse(ViewerSlice().canPin)
        assertFalse(ViewerSlice(viewerState = muted).canPin)
        assertTrue(ViewerSlice(viewerState = ViewerState(permissions = ViewerState.Permissions(canPin = true))).canPin)
    }

    // Effects

    /** The scope bound to [coordinator], with a slice it reduces into itself in place of the view model. */
    private class Harness(scope: TestScope, coordinator: ChatCoordinator, val chatId: MutableStateFlow<ChatId?>) {
        val slice = MutableStateFlow(ViewerSlice())
        val dispatched = mutableListOf<ViewerEvent>()

        init {
            DefaultViewerScope(coordinator).bind(
                scope = scope.backgroundScope,
                chatId = chatId.filterNotNull(),
                slice = slice,
                dispatch = { event ->
                    dispatched += event
                    slice.value = slice.value.reduce(event)
                },
            )
        }
    }

    private fun membership(viewerState: ViewerState?) = ChatMembership(
        metadata = mockk<ChatMetadata>(relaxed = true) { every { this@mockk.viewerState } returns viewerState },
        isMember = true,
    )

    @Test
    fun `the viewer state follows the chat's metadata stream`() = runTest(UnconfinedTestDispatcher()) {
        val metadata = MutableStateFlow<ChatMembership?>(null)
        val coordinator = mockk<ChatCoordinator>(relaxed = true) {
            every { observeMetadata(chatId) } returns metadata
        }
        val harness = Harness(this, coordinator, MutableStateFlow(null))

        assertTrue("nothing is observed before the chat has an id", harness.dispatched.isEmpty())

        harness.chatId.value = chatId
        metadata.value = membership(muted)

        assertEquals(muted, harness.slice.value.viewerState)
        assertEquals(
            listOf(ViewerEvent.OnViewerStateResolved(null), ViewerEvent.OnViewerStateResolved(muted)),
            harness.dispatched,
        )
    }

    @Test
    fun `a host lifting a silence is one lift`() = runTest(UnconfinedTestDispatcher()) {
        val metadata = MutableStateFlow(membership(silenced(SilenceState.Forever)))
        val coordinator = mockk<ChatCoordinator>(relaxed = true) {
            every { observeMetadata(chatId) } returns metadata
        }
        val harness = Harness(this, coordinator, MutableStateFlow(chatId))
        assertEquals(SilenceState.Forever, harness.slice.value.silence)

        metadata.value = membership(ViewerState())
        metadata.value = membership(muted)

        assertEquals(1, harness.dispatched.count { it == ViewerEvent.SilenceLifted })
    }

    @Test
    fun `a chat that opens free of silence is never lifted`() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = mockk<ChatCoordinator>(relaxed = true) {
            every { observeMetadata(chatId) } returns MutableStateFlow(membership(muted))
        }
        val harness = Harness(this, coordinator, MutableStateFlow(chatId))

        assertFalse(ViewerEvent.SilenceLifted in harness.dispatched)
    }

    @Test
    fun `a silence whose deadline has passed lapses at once`() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = mockk<ChatCoordinator>(relaxed = true) {
            every { observeMetadata(chatId) } returns
                MutableStateFlow(membership(silenced(SilenceState.Until(Clock.System.now() - 1.minutes))))
        }
        val harness = Harness(this, coordinator, MutableStateFlow(chatId))

        assertTrue(ViewerEvent.SilenceLapsed in harness.dispatched)
        assertNull(harness.slice.value.silence)
    }
}
