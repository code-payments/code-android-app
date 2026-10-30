package com.getcode.opencode.model.core.errors

import com.getcode.utils.CodeServerError

class ValidationException(
    val violations: List<Violation>,
    field: String? = null,
) : CodeServerError(
    message = field?.let { "Validation failed for $it" }
        ?: "Request validation failed: ${violations.joinToString { "${it.field}: ${it.message}" }}"
)

data class Violation(
    val field: String,
    val message: String,
)