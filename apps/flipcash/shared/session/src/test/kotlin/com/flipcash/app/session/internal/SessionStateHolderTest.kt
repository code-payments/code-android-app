package com.flipcash.app.session.internal

import app.cash.turbine.test
import com.flipcash.app.session.SessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SessionStateHolderTest {

    private fun holder() = SessionStateHolder()

    @Test
    fun `initial state is default SessionState`() {
        val holder = holder()
        assertEquals(SessionState(), holder.state.value)
    }

    @Test
    fun `current returns snapshot of state`() {
        val holder = holder()
        assertEquals(SessionState(), holder.current)
    }

    @Test
    fun `update transforms state atomically`() {
        val holder = holder()
        holder.update { it.copy(vibrateOnScan = true) }
        assertTrue(holder.state.value.vibrateOnScan)
    }

    @Test
    fun `multiple updates compose correctly`() {
        val holder = holder()
        holder.update { it.copy(vibrateOnScan = true) }
        holder.update { it.copy(showNetworkOffline = true) }
        val state = holder.state.value
        assertTrue(state.vibrateOnScan)
        assertTrue(state.showNetworkOffline)
    }

    @Test
    fun `reset clears account-scoped state`() {
        val holder = holder()
        holder.update { it.copy(hasGiveableBalance = true, contactDmUnreadCount = 3, isPhoneNumberSendEnabled = true) }
        holder.reset()
        val state = holder.state.value
        assertEquals(false, state.hasGiveableBalance)
        assertEquals(0, state.contactDmUnreadCount)
        assertEquals(false, state.isPhoneNumberSendEnabled)
    }

    @Test
    fun `reset preserves device-scoped feature-flag state`() {
        // These are driven only by observe(flag) StateFlows, which won't re-emit an
        // unchanged value to repopulate them after a blanket reset — so logout must
        // keep them, or the UI desyncs from the still-persisted flag (e.g. Tips tab).
        val holder = holder()
        holder.update { it.copy(isTippingEnabled = true, vibrateOnScan = true, showNetworkOffline = true) }
        holder.reset()
        val state = holder.state.value
        assertTrue(state.isTippingEnabled)
        assertTrue(state.vibrateOnScan)
        assertTrue(state.showNetworkOffline)
    }

    @Test
    fun `state is observable via Flow`() = runTest {
        val holder = holder()
        holder.state.test {
            assertEquals(SessionState(), awaitItem())
            holder.update { it.copy(vibrateOnScan = true) }
            val updated = awaitItem()
            assertTrue(updated.vibrateOnScan)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
