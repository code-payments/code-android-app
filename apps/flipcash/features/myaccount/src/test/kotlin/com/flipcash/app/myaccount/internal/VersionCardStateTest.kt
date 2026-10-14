package com.flipcash.app.myaccount.internal

import com.flipcash.app.myaccount.internal.settings.VersionCardState
import com.flipcash.app.myaccount.internal.settings.versionCardState
import com.flipcash.app.updates.OptionalUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionCardStateTest {

    private val update = OptionalUpdate(versionCode = 11, priority = 0, stalenessDays = 0, versionName = "2026.10.4")

    private fun state(
        isDebug: Boolean = false,
        update: OptionalUpdate? = null,
        downloaded: Boolean = false,
        progress: Float? = null,
    ) = versionCardState(isDebug, update, downloaded, progress)

    @Test
    fun `no update is up to date and inline`() {
        assertEquals(VersionCardState.UpToDate, state())
        assertFalse(state().pinned)
    }

    @Test
    fun `an update is available and pinned`() {
        assertEquals(VersionCardState.Available("2026.10.4"), state(update = update))
        assertTrue(state(update = update).pinned)
    }

    @Test
    fun `an update the manifest can't name carries no version`() {
        assertEquals(VersionCardState.Available(null), state(update = update.copy(versionName = null)))
    }

    @Test
    fun `a running download shows its fraction`() {
        assertEquals(
            VersionCardState.Downloading("2026.10.4", 0.4f),
            state(update = update, progress = 0.4f),
        )
        assertTrue(state(update = update, progress = 0.4f).pinned)
    }

    @Test
    fun `a downloaded update is ready, and stays pinned`() {
        assertEquals(VersionCardState.Ready("2026.10.4"), state(update = update, downloaded = true))
        assertTrue(state(update = update, downloaded = true).pinned)
    }

    @Test
    fun `ready wins over a stale progress`() {
        assertEquals(
            VersionCardState.Ready("2026.10.4"),
            state(update = update, downloaded = true, progress = 1f),
        )
    }

    @Test
    fun `debug is checked first, inline, and never offers an update`() {
        assertEquals(VersionCardState.Debug, state(isDebug = true, update = update, downloaded = true))
        assertFalse(state(isDebug = true, update = update).pinned)
    }

    @Test
    fun `the card goes inline once the update is gone after a restart`() {
        assertEquals(VersionCardState.UpToDate, state(update = null, downloaded = false))
    }
}
