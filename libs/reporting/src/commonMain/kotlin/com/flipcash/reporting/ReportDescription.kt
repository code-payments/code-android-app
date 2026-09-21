package com.flipcash.reporting

object ReportDescription {

    /**
     * The contract's cap is 8192 (protovalidate `max_len`, counted in characters). This is the
     * cap the apps actually enforce as you type, chosen so nothing can approach the contract's.
     */
    const val MAX_DETAILS_LENGTH: Int = 1000

    /**
     * Assembles the `description` field of a `ReportRequest`: the reason's token on the first
     * line, and the person's own words, if any, from the second line on.
     *
     * Details are trimmed, and dropped entirely when they are blank -- an empty second line
     * carries nothing, and a reader splitting on the first newline should not have to decide
     * whether a trailing separator was meaningful. Details longer than [MAX_DETAILS_LENGTH]
     * are truncated rather than rejected: this runs after the UI has already capped the field,
     * so reaching it means a caller got past that, and losing the tail of an over-long report
     * is better than losing the report.
     */
    fun build(reason: ReportReason, details: String?): String {
        val trimmed = details?.trim().orEmpty()
        if (trimmed.isEmpty()) return reason.token
        return reason.token + "\n" + trimmed.take(MAX_DETAILS_LENGTH)
    }
}
