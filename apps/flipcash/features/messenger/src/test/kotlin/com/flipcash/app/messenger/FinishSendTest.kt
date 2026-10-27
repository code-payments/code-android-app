package com.flipcash.app.messenger

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FinishSendTest {
    @Test
    fun `the sheet is hidden before the back stack changes`() = runTest {
        val events = mutableListOf<String>()
        finishSend(
            hide = { events += "hide start"; events += "hide end" },
            navigateBack = { events += "navigate" },
        )
        assertEquals(listOf("hide start", "hide end", "navigate"), events)
    }
}
