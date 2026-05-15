package com.getcode.opencode.utils

import com.getcode.opencode.model.core.errors.ValidationException
import com.getcode.opencode.model.core.errors.Violation
import com.getcode.utils.CodeServerError
import dev.bmcreations.protovalidate.ProtoValidationException

@Suppress("UNCHECKED_CAST")
fun <E : CodeServerError> Throwable.toValidationOrElse(fallback: (Throwable) -> E): E =
    when (this) {
        is ProtoValidationException -> ValidationException(
            violations.map { Violation(field = it.field, message = it.message) }
        ) as E
        else -> fallback(this)
    }
