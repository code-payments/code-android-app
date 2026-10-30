package com.getcode.navigation.core

import com.getcode.navigation.AppHome
import com.getcode.navigation.AppRegion
import com.getcode.navigation.DemoFlow
import com.getcode.navigation.DemoResult
import com.getcode.navigation.StepOne
import com.getcode.navigation.StepTwo
import com.getcode.navigation.testNavigator
import com.getcode.navigation.results.navigateForResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CodeNavigatorDispatchTest {

    @Test
    fun `flow step lands on the flow stack`() {
        val root = testNavigator(AppHome)
        val flow = testNavigator(StepOne, parent = root, isFlow = true)

        flow.navigate(StepTwo)

        assertEquals(listOf(StepOne, StepTwo), flow.backStack.toList())
        assertEquals(listOf(AppHome), root.backStack.toList())
    }

    @Test
    fun `app route from a flow bubbles up to the root stack`() {
        val root = testNavigator(AppHome)
        val flow = testNavigator(StepOne, parent = root, isFlow = true)

        flow.navigate(AppRegion)

        assertEquals(listOf(StepOne), flow.backStack.toList())
        assertEquals(listOf(AppHome, AppRegion), root.backStack.toList())
    }

    @Test
    fun `app route from a nested flow bubbles all the way to the root`() {
        val root = testNavigator(AppHome)
        val outerFlow = testNavigator(StepOne, parent = root, isFlow = true)
        val innerFlow = testNavigator(StepOne, parent = outerFlow, isFlow = true)

        innerFlow.navigate(AppRegion)

        assertEquals(listOf(AppHome, AppRegion), root.backStack.toList())
        assertEquals(listOf(StepOne), outerFlow.backStack.toList())
        assertEquals(listOf(StepOne), innerFlow.backStack.toList())
    }

    @Test
    fun `app route at the root lands on the root stack`() {
        val root = testNavigator(AppHome)

        root.navigate(AppRegion)

        assertEquals(listOf(AppHome, AppRegion), root.backStack.toList())
    }

    @Test
    fun `flow step at the root is dropped`() {
        val root = testNavigator(AppHome)

        root.navigate(StepTwo)

        assertEquals(listOf(AppHome), root.backStack.toList())
    }

    @Test
    fun `restoreRouting rebuilds this stack directly without type dispatch`() {
        val root = testNavigator(AppHome)
        val flow = testNavigator(StepOne, parent = root, isFlow = true)

        // Even though these are FlowSteps and the parent exists, restoreRouting must
        // rebuild the flow navigator's OWN stack, not bubble to the root.
        flow.replaceAll(listOf(StepTwo, StepOne))

        assertEquals(listOf(StepTwo, StepOne), flow.backStack.toList())
        assertEquals(listOf(AppHome), root.backStack.toList())
    }

    @Test
    fun `dispatchTarget returns this for a route that belongs here`() {
        val root = testNavigator(AppHome)
        assertSame(root, root.dispatchTarget(AppRegion))
    }

    @Test
    fun `dispatchTarget bubbles a non-FlowStep route to the root`() {
        val root = testNavigator(AppHome)
        val flow = testNavigator(StepOne, parent = root, isFlow = true)
        assertSame(root, flow.dispatchTarget(AppRegion))
    }

    @Test
    fun `dispatchTarget keeps a FlowStep on the flow navigator`() {
        val root = testNavigator(AppHome)
        val flow = testNavigator(StepOne, parent = root, isFlow = true)
        assertSame(flow, flow.dispatchTarget(StepTwo))
    }

    @Test
    fun `rootNavigator walks the parent chain to the top`() {
        val root = testNavigator(AppHome)
        val flow = testNavigator(StepOne, parent = root, isFlow = true)
        assertSame(root, flow.rootNavigator)
        assertSame(root, root.rootNavigator)
    }

    @Test
    fun `navigateForResult from a flow nav pushes the route onto the root, not the flow stack`() {
        val root = testNavigator(AppHome)
        val flow = testNavigator(StepOne, parent = root, isFlow = true)

        flow.navigateForResult<DemoResult>(DemoFlow()) { /* no-op */ }

        // The result route lands on the root (where the returning screen will deliver its result),
        // not on the flow navigator's own stack.
        assertEquals(DemoFlow(), root.backStack.last())
        assertEquals(listOf(StepOne), flow.backStack.toList())
    }

    // Coverage for dispatchTarget's else branch (FlowStep with no flow host), mirroring navigate's drop test.
    @Test
    fun `dispatchTarget returns this for a FlowStep with no flow host`() {
        val root = testNavigator(AppHome)
        assertSame(root, root.dispatchTarget(StepTwo))
    }
}
