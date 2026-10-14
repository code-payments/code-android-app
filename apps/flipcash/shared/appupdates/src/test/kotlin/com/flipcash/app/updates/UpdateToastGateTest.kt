package com.flipcash.app.updates

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateToastGateTest {
    private val update = OptionalUpdate(versionCode = 2, priority = 5, stalenessDays = 0)
    private val home = UpdateToastScene(onTabHome = true, onSettings = false)

    @Test
    fun `settings suppresses both toasts`() {
        assertFalse(home.copy(onSettings = true).allowsOneTimeToast)
        assertTrue(home.allowsOneTimeToast)
        assertFalse(allowsReadyToast(downloaded = true, covered = false, onSettings = true))
        assertTrue(allowsReadyToast(downloaded = true, covered = false, onSettings = false))
    }

    @Test
    fun `a timer running when settings opens never shows or marks seen, and it shows later on a home`() = runTest {
        val scene = MutableStateFlow(home)
        val shown = mutableListOf<OptionalUpdate>()
        val job = launch {
            scene.collectLatest { awaitOneTimeToast(update, it, cameBack = false) { u -> shown += u } }
        }
        advanceTimeBy(UpdateToastTiming.DWELL_MILLIS - 500)
        scene.value = home.copy(onSettings = true)
        advanceTimeBy(UpdateToastTiming.DWELL_MILLIS * 3)
        assertEquals(emptyList(), shown)

        scene.value = home
        advanceTimeBy(UpdateToastTiming.DWELL_MILLIS + 1)
        assertEquals(listOf(update), shown)
        job.cancel()
    }

    @Test
    fun `coming back settles faster than dwelling`() = runTest {
        val shown = mutableListOf<OptionalUpdate>()
        val job = launch { awaitOneTimeToast(update, home, cameBack = true) { shown += it } }
        advanceTimeBy(UpdateToastTiming.SETTLE_MILLIS + 1)
        assertEquals(listOf(update), shown)
        job.cancel()
    }
}
