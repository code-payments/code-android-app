package com.flipcash.app.tokens

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionNotificationPromptTest {

    private fun inMemoryPrefs(): SharedPreferences {
        val store = mutableMapOf<String, Boolean>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putBoolean(any(), any()) } answers {
            store[firstArg()] = secondArg(); editor
        }
        return mockk {
            every { getBoolean(any(), any()) } answers { store[firstArg()] ?: secondArg() }
            every { edit() } returns editor
        }
    }

    @Test
    fun `requests until shown, then never again`() = runTest(UnconfinedTestDispatcher()) {
        val prompt = TransactionNotificationPrompt(inMemoryPrefs())
        var count = 0
        prompt.requests.onEach { count++ }.launchIn(backgroundScope)

        prompt.onTransactionCompletedInPlace()
        assertEquals(1, count)
        assertFalse(prompt.hasBeenShown)

        prompt.markShown()
        assertTrue(prompt.hasBeenShown)

        prompt.onTransactionCompletedInPlace()
        assertEquals(1, count)
    }
}
