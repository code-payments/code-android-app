package com.flipcash.app.core.ui.flow

import com.getcode.navigation.flow.FlowStep
import org.junit.Assert.assertEquals
import org.junit.Test

class FlowProgressTest {

    private object StepA : FlowStep
    private object StepB : FlowStep
    private object StepC : FlowStep
    private object OffListStep : FlowStep

    private val progressSteps = listOf(StepA::class, StepB::class, StepC::class)

    @Test
    fun `null step is zero progress`() {
        assertEquals(0f, flowProgressFor(null, progressSteps))
    }

    @Test
    fun `empty progress steps is zero progress`() {
        assertEquals(0f, flowProgressFor(StepA, emptyList()))
    }

    @Test
    fun `step not in list is zero progress`() {
        assertEquals(0f, flowProgressFor(OffListStep, progressSteps))
    }

    @Test
    fun `first step is one third`() {
        assertEquals(1f / 3f, flowProgressFor(StepA, progressSteps))
    }

    @Test
    fun `last step is full`() {
        assertEquals(1f, flowProgressFor(StepC, progressSteps))
    }
}
