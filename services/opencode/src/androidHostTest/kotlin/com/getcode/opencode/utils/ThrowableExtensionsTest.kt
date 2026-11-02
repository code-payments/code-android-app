package com.getcode.opencode.utils

import com.getcode.opencode.model.core.errors.ValidationException
import com.getcode.opencode.model.core.errors.Violation
import com.getcode.utils.CodeServerError
import dev.bmcreations.protovalidate.FieldViolation
import dev.bmcreations.protovalidate.ProtoValidationException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs

class ThrowableExtensionsTest {

    private class FallbackError(cause: Throwable? = null) : CodeServerError("fallback", cause)

    @Test
    fun `ProtoValidationException maps to ValidationException with violations`() {
        val violations = listOf(
            FieldViolation(field = "name", rule = "string.min_len", message = "too short"),
            FieldViolation(field = "symbol", rule = "string.max_len", message = "too long"),
        )
        val proto = ProtoValidationException(violations)

        val result: CodeServerError = proto.toValidationOrElse { FallbackError(it) }

        assertIs<ValidationException>(result)
        assertEquals(2, result.violations.size)
        assertEquals("name", result.violations[0].field)
        assertEquals("too short", result.violations[0].message)
        assertEquals("symbol", result.violations[1].field)
        assertEquals("too long", result.violations[1].message)
    }

    @Test
    fun `Non-ProtoValidationException falls through to fallback`() {
        val exception = RuntimeException("network failure")

        val result: CodeServerError = exception.toValidationOrElse { FallbackError(it) }

        assertIs<FallbackError>(result)
    }

    @Test
    fun `Violations are correctly mapped from FieldViolation to Violation`() {
        val fieldViolation = FieldViolation(
            field = "phone.value",
            rule = "string.pattern",
            message = "must match pattern",
        )
        val proto = ProtoValidationException(listOf(fieldViolation))

        val result: CodeServerError = proto.toValidationOrElse { FallbackError(it) }

        assertIs<ValidationException>(result)
        assertEquals(
            listOf(Violation(field = "phone.value", rule = "string.pattern", message = "must match pattern")),
            result.violations
        )
    }

    @Test
    fun `rule and field path survive into the exception`() {
        val proto = ProtoValidationException(
            listOf(FieldViolation(field = "user_id.value", rule = "bytes.min_len", message = "must be at least 1 byte"))
        )

        val result: CodeServerError = proto.toValidationOrElse { FallbackError(it) }

        assertIs<ValidationException>(result)
        assertEquals("bytes.min_len", result.violations.single().rule)
        assertEquals("user_id.value", result.violations.single().field)
    }

    private fun validation(field: String, rule: String, message: String = "m") =
        ValidationException(listOf(Violation(field = field, rule = rule, message = message)))

    @Test
    fun `same field and rule at different list indexes share a grouping hash`() {
        val a = validation("message_ids.message_ids[3].value", "int64.gte")
        val b = validation("message_ids.message_ids[7].value", "int64.gte")

        assertEquals(a.groupingHash, b.groupingHash)
    }

    @Test
    fun `grouping hash ignores the message so a changed limit does not split the group`() {
        val a = validation("username.value", "string.pattern", "must match {2,15}")
        val b = validation("username.value", "string.pattern", "must match {2,30}")

        assertEquals(a.groupingHash, b.groupingHash)
    }

    @Test
    fun `different rules on one field have different grouping hashes`() {
        val tooShort = validation("display_name", "string.min_len")
        val tooLong = validation("display_name", "string.max_len")

        assertNotEquals(tooShort.groupingHash, tooLong.groupingHash)
    }

    @Test
    fun `different fields have different grouping hashes`() {
        assertNotEquals(
            validation("addresses[0].value", "bytes.min_len").groupingHash,
            validation("address.value", "bytes.min_len").groupingHash,
        )
    }

    @Test
    fun `client-filled fields are notifiable`() {
        listOf("addresses[0].value", "address.value", "user_id.value").forEach {
            assertTrue(validation(it, "bytes.min_len").isNotifiable, it)
        }
    }

    @Test
    fun `user-input fields and username keep their current treatment`() {
        listOf("group.title", "display_name", "name", "username.value", "message_ids.message_ids[1].value").forEach {
            assertFalse(validation(it, "string.max_len").isNotifiable, it)
        }
    }
}
