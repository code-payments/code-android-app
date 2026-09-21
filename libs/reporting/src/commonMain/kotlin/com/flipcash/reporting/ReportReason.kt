package com.flipcash.reporting

/**
 * The reason a person gave for a report, and the token that carries it to the server.
 *
 * The contract (`flipcash2-client-protocol` 0.10.0) has no category field -- `ReportRequest`
 * carries only a free-form `description`. These tokens are how a category survives that,
 * so they are part of the wire format: renaming one changes what moderation receives, and
 * `ReportDescriptionTest` exists to make that change fail loudly rather than silently.
 *
 * Labels, ordering and icons are deliberately not here. Those are per-app UI.
 */
enum class ReportReason(val token: String) {
    Spam("spam"),
    ScamOrFraud("scam_or_fraud"),
    Harassment("harassment"),
    SexualContent("sexual_content"),
    Violence("violence"),
    Other("other"),
}
