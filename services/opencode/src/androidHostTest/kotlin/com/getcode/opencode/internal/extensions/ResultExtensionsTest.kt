package com.getcode.opencode.internal.extensions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResultExtensionsTest {

    // --- filter ---

    @Test
    fun filterPassingPredicateReturnsSuccess() {
        val result = Result.success(42).filter { it > 0 }
        assertEquals(42, result.getOrThrow())
    }

    @Test
    fun filterFailingPredicateReturnsFailure() {
        val result = Result.success(42).filter { it > 100 }
        assertTrue(result.isFailure)
    }

    @Test
    fun filterOnFailurePropagatesFailure() {
        val original = Result.failure<Int>(IllegalStateException("boom"))
        val result = original.filter { true }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    // --- filterIsInstance ---

    @Test
    fun filterIsInstanceMatchingTypeReturnsSuccess() {
        val result: Result<Any> = Result.success("hello")
        val filtered = result.filterIsInstance<String>()
        assertEquals("hello", filtered.getOrThrow())
    }

    @Test
    fun filterIsInstanceNonMatchingTypeReturnsFailure() {
        val result: Result<Any> = Result.success(42)
        val filtered = result.filterIsInstance<String>()
        assertTrue(filtered.isFailure)
    }

    @Test
    fun filterIsInstanceOnFailurePropagatesException() {
        val original = Result.failure<Any>(IllegalArgumentException("bad"))
        val filtered = original.filterIsInstance<String>()
        assertTrue(filtered.isFailure)
        assertTrue(filtered.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun filterIsInstanceSubtype() {
        val result: Result<Any> = Result.success(RuntimeException("test"))
        val filtered = result.filterIsInstance<Exception>()
        assertTrue(filtered.isSuccess)
    }
}
