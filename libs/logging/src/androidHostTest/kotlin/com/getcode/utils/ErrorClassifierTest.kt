package com.getcode.utils

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorClassifierTest {

    private class Dropped : Throwable("dropped")
    private class Quiet : Throwable("quiet")
    private class Fault : Throwable("fault")

    private val classifier = ErrorClassifier {
        when (it) {
            is Dropped -> ErrorClass.Ignore
            is Quiet -> ErrorClass.LogOnly
            else -> null
        }
    }

    private val reported = mutableListOf<Throwable>()
    private val reporter = object : ErrorReporter {
        override fun report(error: Throwable, cause: Throwable, isNotifiable: Boolean) {
            reported += error
        }
    }
    private var notifyErrors = false

    @BeforeTest
    fun setUp() {
        notifyErrors = BuildFlags.notifyErrors
        BuildFlags.notifyErrors = true
        ErrorUtils.addReporter(reporter)
    }

    @AfterTest
    fun tearDown() {
        ErrorUtils.removeReporter(reporter)
        ErrorUtils.removeClassifier(classifier)
        BuildFlags.notifyErrors = notifyErrors
    }

    @Test
    fun `unclassified errors are reported without a classifier`() {
        ErrorUtils.handleError(Dropped())
        ErrorUtils.handleError(Quiet())
        assertEquals(2, reported.size)
    }

    @Test
    fun `an Ignore error is not reported`() {
        ErrorUtils.addClassifier(classifier)
        ErrorUtils.handleError(Dropped())
        assertEquals(emptyList(), reported)
    }

    @Test
    fun `a LogOnly error is not reported, directly or as the cause`() {
        ErrorUtils.addClassifier(classifier)
        ErrorUtils.handleError(Quiet())
        ErrorUtils.handleError(RuntimeException("wrapped", Quiet()))
        assertEquals(emptyList(), reported)
    }

    @Test
    fun `an error the classifier has no opinion on is still reported`() {
        ErrorUtils.addClassifier(classifier)
        val fault = Fault()
        ErrorUtils.handleError(fault)
        assertEquals(listOf<Throwable>(fault), reported)
    }

    @Test
    fun `adding a classifier twice registers it once`() {
        ErrorUtils.addClassifier(classifier)
        ErrorUtils.addClassifier(classifier)
        ErrorUtils.removeClassifier(classifier)
        ErrorUtils.handleError(Dropped())
        assertEquals(1, reported.size)
    }
}
