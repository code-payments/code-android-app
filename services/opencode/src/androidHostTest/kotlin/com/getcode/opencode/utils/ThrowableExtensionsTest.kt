package com.getcode.opencode.utils

import com.getcode.opencode.model.core.errors.ValidationException
import com.getcode.opencode.model.core.errors.Violation
import com.getcode.utils.CodeServerError
import dev.bmcreations.protovalidate.FieldViolation
import dev.bmcreations.protovalidate.ProtoValidationException
import org.junit.Test
import kotlin.test.assertEquals
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
            listOf(Violation(field = "phone.value", message = "must match pattern")),
            result.violations
        )
    }
}
