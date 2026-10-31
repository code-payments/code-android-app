package com.flipcash.app.userprofile.internal.name

internal const val DISPLAY_NAME_MAX_LENGTH = 64

/**
 * `SetDisplayNameRequest.display_name`: `min_len 1`, `max_len 64`, in the pinned Flipcash contract.
 *
 * Applies to the text as submitted. Sources other than onboarding send the field text as typed,
 * so it is measured as typed; a blank name is refused as well, as before. Length is counted in
 * code points, as protovalidate counts it, not in UTF-16 units, which would refuse emoji names the
 * server accepts.
 */
internal fun isWithinDisplayNameLimit(typed: String): Boolean =
    typed.isNotBlank() && typed.codePointCount(0, typed.length) <= DISPLAY_NAME_MAX_LENGTH
