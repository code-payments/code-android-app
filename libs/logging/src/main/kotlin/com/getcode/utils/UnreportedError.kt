package com.getcode.utils

/**
 * Marker interface for errors that are an expected outcome rather than a fault — a server result
 * the caller already handles and renders. [ErrorUtils] still logs these, so they stay in the trace
 * attached to a real report, but it does not hand them to the reporters and they never open a
 * Bugsnag error group.
 *
 * This is a third tier below [NotifiableError] (reported, WARNING, drives the Slack filter) and a
 * plain [CodeServerError] (reported, INFO, recorded for reference).
 */
interface UnreportedError
