package com.flipcash.app.core.internal.extensions

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResultExtensionsTest {

    @Test
    fun `mapResult transforms success to new success`() = runTest {
        val result = Result.success(42)
            .mapResult { Result.success(it.toString()) }

        assertTrue(result.isSuccess)
        assertEquals("42", result.getOrThrow())
    }

    @Test
    fun `mapResult transforms success to failure`() = runTest {
        val result = Result.success(42)
            .mapResult<Int, String> { Result.failure(RuntimeException("mapped error")) }

        assertTrue(result.isFailure)
        assertEquals("mapped error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `mapResult preserves failure without calling transform`() = runTest {
        var transformCalled = false
        val result = Result.failure<Int>(RuntimeException("original error"))
            .mapResult<Int, String> {
                transformCalled = true
                Result.success(it.toString())
            }

        assertTrue(result.isFailure)
        assertEquals("original error", result.exceptionOrNull()?.message)
        assertEquals(false, transformCalled)
    }

    @Test
    fun `mapResult catches exception thrown by transform`() = runTest {
        val result = Result.success(42)
            .mapResult<Int, String> { throw IllegalStateException("transform threw") }

        assertTrue(result.isFailure)
        assertEquals("transform threw", result.exceptionOrNull()?.message)
    }
}
