package com.getcode.opencode.model.core.errors

import com.getcode.utils.CodeServerError
import com.getcode.utils.ConditionallyNotifiable

class ValidationException(
    val violations: List<Violation>,
    field: String? = null,
) : CodeServerError(
    message = field?.let { "Validation failed for $it" }
        ?: "Request validation failed: ${violations.joinToString { "${it.field}: ${it.message}" }}"
), ConditionallyNotifiable {

    /**
     * Reported when a violation sits on a field the client fills itself. Those failures are client
     * bugs (or contract pin drift). Failures on user-typed fields stay out: the UI owns those
     * checks, and `username` is left as it was.
     */
    override val isNotifiable: Boolean
        get() = violations.any { it.isClientFilled }

    /**
     * Stable Bugsnag grouping key: the sorted set of `<field path without [N]>:<rule>`. The
     * message is left out because it embeds the contract limit, which would split the group each
     * time a limit changes.
     */
    val groupingHash: String
        get() = violations
            .map { "${it.fieldPath}:${it.rule}" }
            .distinct()
            .sorted()
            .joinToString(",")
}

data class Violation(
    val field: String,
    val rule: String,
    val message: String,
) {
    /** [field] with list indexes (`[N]`) removed. */
    val fieldPath: String
        get() = this.field.replace(ListIndex, "")

    /** True when the request builder, not the user, supplies the value. */
    val isClientFilled: Boolean
        get() = fieldPath.substringBefore('.') in ClientFilledFields

    private companion object {
        val ListIndex = Regex("""\[\d+]""")
        val ClientFilledFields = setOf("addresses", "address", "user_id")
    }
}
