package com.flipcash.app.messenger.internal.request

import com.flipcash.app.messenger.internal.request.RequestMoneyViewModel.Stage
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestStepTest {

    @Test
    fun `each stage puts its own step on top of the path to it`() {
        assertEquals(listOf(RequestStep.History), stackFor(Stage.History))
        assertEquals(listOf(RequestStep.History, RequestStep.Amount), stackFor(Stage.Amount))
        assertEquals(
            listOf(RequestStep.History, RequestStep.Amount, RequestStep.Summary, RequestStep.People),
            stackFor(Stage.People),
        )
        assertEquals(RequestStep.Name, stackFor(Stage.Name).last())
    }

    @Test
    fun `a step below the target's top pushes the next one`() {
        assertEquals(StackMove.Push(RequestStep.Amount), moveFrom(RequestStep.History, stackFor(Stage.Amount)))
        assertEquals(StackMove.Push(RequestStep.People), moveFrom(RequestStep.Summary, stackFor(Stage.People)))
    }

    @Test
    fun `the top step stays and a step off the target pops`() {
        assertEquals(StackMove.None, moveFrom(RequestStep.Summary, stackFor(Stage.Summary)))
        assertEquals(StackMove.Pop, moveFrom(RequestStep.People, stackFor(Stage.Summary)))
        assertEquals(StackMove.Pop, moveFrom(RequestStep.Amount, stackFor(Stage.History)))
    }
}
