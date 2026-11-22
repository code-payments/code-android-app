package com.getcode.opencode.internal.network.executors

import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService.SubmitIntentResponse
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.internal.network.api.TransactionApi
import com.getcode.opencode.model.core.errors.SubmitIntentError
import com.getcode.opencode.model.core.errors.isUnansweredSubmission
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.solana.keys.PublicKey
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.TimeoutException
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class IntentExecutorTest {

    private val api = mockk<TransactionApi>()
    private val executor = IntentExecutor(api)
    private val owner = Ed25519.createKeyPair(ByteArray(32) { 1 })

    private class TestIntent : IntentType() {
        override val id = PublicKey(List(32) { 7.toByte() })
        override val metadata: TransactionMetadata = TransactionMetadata.Unknown
        override val actionGroup = ActionGroup()
    }

    private fun serverAnswers(responses: Flow<SubmitIntentResponse>) {
        every { api.submitIntent(any()) } returns responses
    }

    private fun TestScope.submit(streamTimeout: Duration?) =
        async { executor.execute(backgroundScope, TestIntent(), owner, streamTimeout) }

    @Test
    fun `a silent server ends a stream given 60 seconds at 60 seconds`() = runTest {
        serverAnswers(flow { awaitCancellation() })

        val submitting = submit(60.seconds)
        advanceTimeBy(59_999)
        runCurrent()
        assertFalse(submitting.isCompleted, "still waiting at 59.999 s")

        advanceTimeBy(1)
        runCurrent()
        assertTrue(submitting.isCompleted, "ended at 60 s")
        val error = submitting.await().exceptionOrNull()
        assertIs<SubmitIntentError.Other>(error)
        assertIs<TimeoutException>(error.cause)
        assertTrue(error.isUnansweredSubmission())
    }

    @Test
    fun `a stream given no timeout keeps the 15 second default`() = runTest {
        serverAnswers(flow { awaitCancellation() })

        val submitting = submit(streamTimeout = null)
        advanceTimeBy(14_999)
        runCurrent()
        assertFalse(submitting.isCompleted, "still waiting at 14.999 s")

        advanceTimeBy(1)
        runCurrent()
        assertTrue(submitting.isCompleted, "ended at 15 s")
        assertTrue(submitting.await().exceptionOrNull()!!.isUnansweredSubmission())
    }

    @Test
    fun `a dropped connection ends the submission when it drops, not at the timeout`() = runTest {
        val dropped = StatusException(Status.UNAVAILABLE)
        serverAnswers(flow { delay(1_000); throw dropped })

        val submitting = submit(60.seconds)
        advanceTimeBy(999)
        runCurrent()
        assertFalse(submitting.isCompleted, "still waiting at 0.999 s")

        advanceTimeBy(1)
        runCurrent()
        assertTrue(submitting.isCompleted, "ended at 1 s")
        val error = submitting.await().exceptionOrNull()
        assertSame(dropped, error)
        assertTrue(error!!.isUnansweredSubmission())
    }

    @Test
    fun `only a submission the server never answered counts as unanswered`() {
        assertTrue(SubmitIntentError.Other(TimeoutException("timed out")).isUnansweredSubmission())
        assertTrue(StatusRuntimeException(Status.DEADLINE_EXCEEDED).isUnansweredSubmission())
        assertTrue(SubmitIntentError.Other(StatusException(Status.UNAVAILABLE)).isUnansweredSubmission())

        assertFalse(SubmitIntentError.Denied(listOf("denied")).isUnansweredSubmission())
        assertFalse(SubmitIntentError.Other().isUnansweredSubmission())
        assertFalse(SubmitIntentError.Other(IllegalStateException("boom")).isUnansweredSubmission())
        assertFalse(IllegalStateException("boom").isUnansweredSubmission())
    }
}
