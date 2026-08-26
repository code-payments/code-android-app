package com.flipcash.app.menu.internal

import com.flipcash.services.models.isUsernameShaped

/** How much of an opaque id survives abbreviation. Matches iOS's `identifierStubLength`. */
private const val ABBREVIATED_ID_LENGTH = 5

/**
 * `https://app.flipcash.com/tip/<uuid>` -> `app.flipcash.com/tip/b0ced…` (node 9276:4753). The
 * user never types this — it's a recognisable stand-in for the link the copy button puts on the
 * clipboard, so it's cut short rather than ellipsized at whatever width the device happens to give.
 *
 * A vanity link is left whole: `flipcash.com/sally_streamer` (node 9442:3673) is the entire point
 * of claiming a handle, it fits, and abbreviating it would hide the part that identifies the
 * person. The test is the handle's own shape, so only an opaque id is ever cut.
 *
 * Mirrors iOS `TipCardLinkRow.displayText(for:)`.
 */
internal fun String.abbreviatedLink(): String {
    val withoutScheme = substringAfter("://")
    val lastSegment = withoutScheme.substringAfterLast('/')
    if (lastSegment.isUsernameShaped()) return withoutScheme
    if (lastSegment.length <= ABBREVIATED_ID_LENGTH) return withoutScheme
    val prefix = withoutScheme.removeSuffix(lastSegment)
    return "$prefix${lastSegment.take(ABBREVIATED_ID_LENGTH)}…"
}
