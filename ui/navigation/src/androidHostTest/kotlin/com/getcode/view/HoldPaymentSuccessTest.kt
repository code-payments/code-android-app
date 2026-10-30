package com.getcode.view

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class HoldPaymentSuccessTest {

    @Test
    fun `release runs only after the hold`() = runTest {
        val log = mutableListOf<String>()
        launch { holdPaymentSuccess(show = { log += "show" }, release = { log += "release" }) }
        runCurrent()
        assertEquals(listOf("show"), log)
        advanceTimeBy(PaymentSuccessHoldDuration - 1.milliseconds)
        runCurrent()
        assertEquals(listOf("show"), log)
        advanceTimeBy(2.milliseconds)
        runCurrent()
        assertEquals(listOf("show", "release"), log)
    }

    @Test
    fun `cancelling during the hold skips release`() = runTest {
        val log = mutableListOf<String>()
        val job = launch { holdPaymentSuccess(show = { log += "show" }, release = { log += "release" }) }
        runCurrent()
        job.cancel()
        advanceTimeBy(PaymentSuccessHoldDuration * 2)
        runCurrent()
        assertEquals(listOf("show"), log)
    }
}
