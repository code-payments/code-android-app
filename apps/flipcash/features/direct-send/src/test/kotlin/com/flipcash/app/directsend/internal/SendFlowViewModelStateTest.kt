package com.flipcash.app.directsend.internal

import com.flipcash.app.core.send.SendStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SendFlowViewModelStateTest {

    private val reduce = SendFlowViewModel.Companion.updateStateForEvent

    @Test
    fun `default state has null currentStep`() {
        assertNull(SendFlowViewModel.State().currentStep)
    }

    @Test
    fun `OnStepChanged updates currentStep`() {
        val updated = reduce(
            SendFlowViewModel.Event.OnStepChanged(SendStep.ContactList)
        )(SendFlowViewModel.State())
        assertEquals(SendStep.ContactList, updated.currentStep)
    }

    @Test
    fun `OnStepChanged to PhoneGate updates currentStep`() {
        val updated = reduce(
            SendFlowViewModel.Event.OnStepChanged(SendStep.Intro)
        )(SendFlowViewModel.State())
        assertEquals(SendStep.Intro, updated.currentStep)
    }

    @Test
    fun `OnStepChanged to ContactsGate updates currentStep`() {
        val updated = reduce(
            SendFlowViewModel.Event.OnStepChanged(SendStep.ContactsGate)
        )(SendFlowViewModel.State())
        assertEquals(SendStep.ContactsGate, updated.currentStep)
    }

    @Test
    fun `OnStepChanged replaces previous step`() {
        val state = SendFlowViewModel.State(currentStep = SendStep.Intro)
        val updated = reduce(
            SendFlowViewModel.Event.OnStepChanged(SendStep.ContactList)
        )(state)
        assertEquals(SendStep.ContactList, updated.currentStep)
    }

    @Test
    fun `OnStepChanged does not affect other state`() {
        val state = SendFlowViewModel.State(
            steps = listOf(SendStep.Intro, SendStep.ContactList),
            isPickerMode = true,
        )
        val updated = reduce(
            SendFlowViewModel.Event.OnStepChanged(SendStep.ContactList)
        )(state)
        assertEquals(state.steps, updated.steps)
        assertEquals(state.isPickerMode, updated.isPickerMode)
        assertEquals(state.listItems, updated.listItems)
    }
}
