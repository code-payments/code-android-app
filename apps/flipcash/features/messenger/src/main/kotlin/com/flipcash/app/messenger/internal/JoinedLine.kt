package com.flipcash.app.messenger.internal

import com.flipcash.features.messenger.R
import com.getcode.util.DateUtils
import com.getcode.util.resources.ResourceHelper
import kotlin.time.Instant

/**
 * "Joined March 2024": how a person's join date reads, on their profile and on a link to them.
 * One function so the two surfaces cannot drift apart in wording or format.
 */
internal fun joinedLine(joinedAt: Instant, resources: ResourceHelper): String =
    resources.getString(
        R.string.subtitle_joinedDate,
        DateUtils.getDate(joinedAt.toEpochMilliseconds(), "MMMM yyyy"),
    )
