package com.getcode.ui.components.toast

import androidx.compose.material.SnackbarResult
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The host draws above the nav content, so a sheet, the bill or a bottom-bar prompt covers it. A
 * toast left showing over one of those takes its taps, so covering has to take the toast down and
 * keep new ones from appearing until it is uncovered.
 */
class FloatingToastHostStateTest {

    @Test
    fun `covering the host dismisses the toast on screen`() = runTest {
        val host = FloatingToastHostState()
        val shown = async(start = CoroutineStart.UNDISPATCHED) { host.show("Chat archived") }
        assertEquals("Chat archived", host.current?.message)

        host.isCovered = true

        assertEquals(SnackbarResult.Dismissed, shown.await())
        assertNull(host.current)
    }

    @Test
    fun `a toast shown while covered never appears`() = runTest {
        val host = FloatingToastHostState()
        host.isCovered = true

        assertEquals(SnackbarResult.Dismissed, host.show("3 taps away"))
        assertNull(host.current)
    }

    @Test
    fun `an in-place update while covered does not bring the toast back`() = runTest {
        val host = FloatingToastHostState()
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            host.show("4 taps away", inPlace = true)
        }
        host.isCovered = true
        assertEquals(SnackbarResult.Dismissed, first.await())

        assertEquals(SnackbarResult.Dismissed, host.show("3 taps away", inPlace = true))
        assertNull(host.current)
    }

    @Test
    fun `uncovering lets the next toast show`() = runTest {
        val host = FloatingToastHostState()
        host.isCovered = true
        host.isCovered = false

        val shown = async(start = CoroutineStart.UNDISPATCHED) { host.show("Chat archived") }
        assertEquals("Chat archived", host.current?.message)
        host.current!!.performAction()

        assertEquals(SnackbarResult.ActionPerformed, shown.await())
    }

    @Test
    fun `a new toast replaces the one on screen`() = runTest {
        val host = FloatingToastHostState()
        val first = async(start = CoroutineStart.UNDISPATCHED) { host.show("Chat archived") }
        val second = async(start = CoroutineStart.UNDISPATCHED) { host.show("Chat archived again") }

        assertEquals(SnackbarResult.Dismissed, first.await())
        assertTrue(second.isActive)
        assertEquals("Chat archived again", host.current?.message)
        host.current!!.dismiss()
        second.await()
    }

    @Test
    fun `clearance is the tallest owner and drops back when one is cleared`() {
        val host = FloatingToastHostState()
        val a = Any()
        val b = Any()
        assertEquals(0.dp, host.bottomClearance)

        host.setBottomClearance(a, 80.dp)
        host.setBottomClearance(b, 120.dp)
        assertEquals(120.dp, host.bottomClearance)

        host.setBottomClearance(b, 40.dp)
        assertEquals(80.dp, host.bottomClearance)

        host.clearBottomClearance(a)
        host.clearBottomClearance(b)
        assertEquals(0.dp, host.bottomClearance)
    }
}
