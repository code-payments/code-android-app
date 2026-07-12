package com.getcode.navigation.core

import com.getcode.navigation.AppHome
import com.getcode.navigation.CallerScreen
import com.getcode.navigation.DemoFlow
import com.getcode.navigation.DemoResult
import com.getcode.navigation.DemoSheet
import com.getcode.navigation.RecordingFlowScope
import com.getcode.navigation.StepOne
import com.getcode.navigation.testNavigator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodeNavigatorIntentTest {

    @Test
    fun `navigateBackWithResult delivers through the flow scope`() {
        val scope = RecordingFlowScope()
        val root = testNavigator(CallerScreen, DemoFlow())
        val flow = testNavigator(StepOne, parent = root, isFlow = true, scope = scope)

        flow.navigateBackWithResult(DemoResult("done"))

        assertEquals(listOf("exitWithResult"), scope.calls)
        assertEquals(DemoResult("done"), scope.deliveredResult)
    }

    @Test
    fun `navigateBackWithResult outside a flow scope is a no-op`() {
        val root = testNavigator(AppHome)

        // Must not throw.
        root.navigateBackWithResult(DemoResult("ignored"))

        assertEquals(listOf(AppHome), root.backStack.toList())
    }

    @Test
    fun `dismiss in a flow delegates to the scope`() {
        val scope = RecordingFlowScope()
        val flow = testNavigator(StepOne, isFlow = true, scope = scope)

        flow.dismiss()

        assertEquals(listOf("dismiss"), scope.calls)
    }

    @Test
    fun `dismiss on a plain sheet requests an animated dismiss`() {
        val nav = testNavigator(AppHome, DemoSheet)
        assertNull(nav.pendingSheetDismiss)

        nav.dismiss()

        assertNotNull(nav.pendingSheetDismiss)
        // Backstack is untouched — the sheet scene pops it after the animation completes.
        assertEquals(listOf(AppHome, DemoSheet), nav.backStack.toList())
    }

    @Test
    fun `dismiss with no flow and no sheet pops the stack`() {
        val nav = testNavigator(AppHome, CallerScreen)

        nav.dismiss()

        assertEquals(listOf(AppHome), nav.backStack.toList())
    }

    @Test
    fun `dismiss at the app root reaches root instead of popping`() {
        var rootReached = false
        val nav = testNavigator(AppHome, onRootReached = { rootReached = true })

        nav.dismiss()

        assertTrue(rootReached)
        assertEquals(listOf(AppHome), nav.backStack.toList())
    }

    @Test
    fun `navigateBackWithResult with a parent but no flow scope is a no-op`() {
        val root = testNavigator(AppHome)
        val child = testNavigator(CallerScreen, parent = root)  // parent set, but scope = null

        // Must not throw and must not mutate either stack.
        child.navigateBackWithResult(DemoResult("ignored"))

        assertEquals(listOf(CallerScreen), child.backStack.toList())
        assertEquals(listOf(AppHome), root.backStack.toList())
    }
}
