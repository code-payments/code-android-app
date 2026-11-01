package com.flipcash.app.internal.startup

import com.getcode.opencode.model.core.errors.SubmitIntentError
import com.getcode.opencode.model.core.errors.SwapError
import com.getcode.opencode.model.core.errors.ValidationException
import com.getcode.opencode.model.core.errors.Violation
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class BugsnagErrorReporterTest {

    private fun validation(field: String, rule: String) =
        ValidationException(listOf(Violation(field = field, rule = rule, message = "m")))

    private fun hash(error: Throwable, cause: Throwable = error) =
        BugsnagErrorReporter.groupingHashFor(error, cause)

    @Test
    fun `grouping hash comes from the validation error or its cause`() {
        val error = validation("messages[3].content", "string.max_len")
        val wrapped = RuntimeException("wrapped", error)

        assertEquals(error.groupingHash, BugsnagErrorReporter.groupingHashFor(error, error))
        assertEquals(error.groupingHash, BugsnagErrorReporter.groupingHashFor(wrapped, error))
    }

    @Test
    fun `validation hash stays field path and rule`() {
        assertEquals(
            "messages.content:string.max_len",
            hash(validation("messages[3].content", "string.max_len")),
        )
    }

    @Test
    fun `messages indexes with the same rule share a hash and other rules do not`() {
        val a = BugsnagErrorReporter.groupingHashFor(
            validation("messages[3].content", "string.max_len"), RuntimeException()
        )
        val b = BugsnagErrorReporter.groupingHashFor(
            validation("messages[7].content", "string.max_len"), RuntimeException()
        )
        val c = BugsnagErrorReporter.groupingHashFor(
            validation("messages[7].content", "string.min_len"), RuntimeException()
        )

        assertEquals(a, b)
        assert(a != c)
    }

    @Test
    fun `other errors keep Bugsnag default grouping`() {
        assertNull(BugsnagErrorReporter.groupingHashFor(RuntimeException(), RuntimeException()))
        assertNull(hash(IllegalStateException("gift card balance has already been claimed")))
    }

    private fun claimedFromSubmit() =
        SubmitIntentError.StaleState(listOf("gift card balance has already been claimed"))

    private fun claimedFromRetry() =
        SubmitIntentError.StaleState(listOf("gift card balance has already been claimed"))

    @Test
    fun `same server error built at different call sites shares a hash`() {
        val a = claimedFromSubmit()
        val b = claimedFromRetry()

        assertNotEquals(a.stackTrace.first(), b.stackTrace.first())
        assertEquals(hash(a), hash(b))
        assertEquals(
            "com.getcode.opencode.model.core.errors.SubmitIntentError\$StaleState:" +
                    "gift card balance has already been claimed",
            hash(a),
        )
    }

    @Test
    fun `hash leaves out the CodeServerError message prefix`() {
        assertFalse(hash(claimedFromSubmit())!!.contains("CodeServerError:"))
    }

    @Test
    fun `each stale state reason gets its own hash`() {
        val reasons = listOf(
            "race detected: $AddressA",
            "gift card balance has already been claimed",
            "gift card is expired",
            "pool balance has already been distributed",
            "intent already exists",
            "account is already opened",
        )

        val hashes = reasons.map { hash(SubmitIntentError.StaleState(listOf(it))) }.toSet()

        assertEquals(reasons.size, hashes.size)
    }

    @Test
    fun `messages differing only in amount or address share a hash`() {
        assertEquals(
            hash(SwapError.Denied(listOf("fee amount must be 184467440737095440 quarks"))),
            hash(SwapError.Denied(listOf("fee amount must be 5 quarks"))),
        )
        assertEquals(
            hash(SubmitIntentError.InvalidIntent(listOf("actions[0]: $AddressA has insufficient balance"))),
            hash(SubmitIntentError.InvalidIntent(listOf("actions[3]: $AddressB has insufficient balance"))),
        )
        assertEquals(
            hash(SubmitIntentError.Denied(listOf("payment amount is below the fee of 3 USDC"))),
            hash(SubmitIntentError.Denied(listOf("payment amount is below the fee of 0.25 USDC"))),
        )
    }

    @Test
    fun `same message on different classes stays apart`() {
        assertNotEquals(
            hash(SubmitIntentError.Denied(listOf("denied"))),
            hash(SwapError.Denied(listOf("denied"))),
        )
    }

    @Test
    fun `a server error is hashed from the cause when the reported error is not one`() {
        val cause = claimedFromSubmit()

        assertEquals(hash(cause), hash(RuntimeException("wrapped", cause), cause))
    }

    @Test
    fun `the reported server error wins over a server error cause`() {
        val cause = claimedFromSubmit()
        val error = SubmitIntentError.Other(cause = cause)

        assertEquals(
            "com.getcode.opencode.model.core.errors.SubmitIntentError\$Other:" +
                    "gift card balance has already been claimed",
            hash(error, cause),
        )
    }

    @Test
    fun `normalizer replaces indexes, addresses and numbers`() {
        assertEquals(
            "actions: <address> has <n> of <n> quarks",
            BugsnagErrorReporter.normalizeMessage("actions[12]: $AddressA has 1.5 of 184467440737095440 quarks"),
        )
        assertEquals("", BugsnagErrorReporter.normalizeMessage(null))
    }

    private companion object {
        // Synthetic base58 strings, the length of a Solana address.
        const val AddressA = "3xKq7VbYwZ9mNpRtUfGhJkLcDeA2sB4nP6oQ8rStUvWx"
        const val AddressB = "9ZyXwVuTsRqPoNmLkJhGfEdCbA87654321zyxwvutsrq"
    }
}
