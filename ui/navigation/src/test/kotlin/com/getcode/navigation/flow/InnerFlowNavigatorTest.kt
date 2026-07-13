package com.getcode.navigation.flow

import com.getcode.navigation.AppHome
import com.getcode.navigation.AppRegion
import com.getcode.navigation.DemoResult
import com.getcode.navigation.RecordingFlowScope
import com.getcode.navigation.StepOne
import com.getcode.navigation.StepTwo
import com.getcode.navigation.testNavigator
import com.getcode.navigation.core.CodeNavigator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InnerFlowNavigatorTest {

    private class Harness(vararg steps: FlowStep) {
        val exits = mutableListOf<FlowExitReason<DemoResult>>()
        val scope = RecordingFlowScope()
        val root: CodeNavigator = testNavigator(AppHome)
        val flow: CodeNavigator = testNavigator(
            *steps,
            parent = root,
            isFlow = true,
            scope = scope,
            // In production both onRootReached and onExit funnel into currentOnExit(BackedOutOfRoot);
            // wire them to the same recorder here so a root-level back() is observable.
            onRootReached = { exits += FlowExitReason.BackedOutOfRoot },
        )
        val nav = InnerFlowNavigator<FlowStep, DemoResult>(
            navigator = flow,
            onExit = { exits += it },
            steps = { emptyList() },
            completedResult = { null },
            onProceed = null,
        )
    }

    @Test
    fun `back pops the flow stack and returns true when not at root`() {
        val h = Harness(StepOne, StepTwo)
        val result = h.nav.back()
        assertTrue(result)
        assertEquals(listOf(StepOne), h.flow.backStack.toList())
        assertEquals(emptyList(), h.exits)
    }

    @Test
    fun `back at the flow root exits with BackedOutOfRoot and returns false`() {
        val h = Harness(StepOne)
        val result = h.nav.back()
        assertFalse(result)
        assertEquals(listOf<FlowExitReason<DemoResult>>(FlowExitReason.BackedOutOfRoot), h.exits)
        assertEquals(listOf(StepOne), h.flow.backStack.toList())
    }

    @Test
    fun `exitWithResult delivers the result through the flow scope`() {
        val h = Harness(StepOne)
        h.nav.exitWithResult(DemoResult("done"))
        assertEquals(listOf("exitWithResult"), h.scope.calls)
        assertEquals(DemoResult("done"), h.scope.deliveredResult)
    }

    @Test
    fun `exitCanceled exits with Canceled`() {
        val h = Harness(StepOne)
        h.nav.exitCanceled()
        assertEquals(listOf<FlowExitReason<DemoResult>>(FlowExitReason.Canceled), h.exits)
    }

    @Test
    fun `navigate sends an app route up to the parent stack`() {
        val h = Harness(StepOne)
        h.nav.navigate(AppRegion)
        assertEquals(listOf(AppHome, AppRegion), h.root.backStack.toList())
        assertEquals(listOf(StepOne), h.flow.backStack.toList())
    }

    @Test
    fun `navigateTo pushes a step onto the flow stack`() {
        val h = Harness(StepOne)
        h.nav.navigateTo(StepTwo)
        assertEquals(listOf(StepOne, StepTwo), h.flow.backStack.toList())
        assertEquals(listOf(AppHome), h.root.backStack.toList())
    }

    @Test
    fun `navigate with a FlowStep lands on the flow stack (callers must pass app routes)`() {
        val h = Harness(StepOne)

        // Misuse: navigate() is for app-level routes; a FlowStep is dispatched to the flow stack.
        h.nav.navigate(StepTwo)

        assertEquals(listOf(StepOne, StepTwo), h.flow.backStack.toList())
        assertEquals(listOf(AppHome), h.root.backStack.toList())
    }
}
