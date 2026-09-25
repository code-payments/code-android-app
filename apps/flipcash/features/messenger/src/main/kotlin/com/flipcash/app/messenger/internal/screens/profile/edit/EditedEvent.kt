package com.flipcash.app.messenger.internal.screens.profile.edit

import com.flipcash.analytics.GroupField
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.GroupEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.chatResult

/** `Group: Edited` for the answer EditChat gave to a rename or a picture change. */
internal fun FlipcashAnalytics.trackEdited(field: GroupField, result: Result<*>) {
    track(
        GroupEvents.edited(
            field = field,
            state = if (result.isSuccess) AnalyticsState.SUCCESS else AnalyticsState.FAILURE,
            error = result.exceptionOrNull()?.chatResult,
        )
    )
}
