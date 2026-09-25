package com.flipcash.app.tipping.internal

import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.GroupEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.chatResult
import com.flipcash.app.analytics.gateMint
import com.flipcash.services.models.chat.ChatRules

/** `Group: Created` for the answer StartChat gave to the create form. */
internal fun FlipcashAnalytics.trackCreated(rules: ChatRules?, hasPicture: Boolean, result: Result<*>) {
    track(
        GroupEvents.created(
            state = if (result.isSuccess) AnalyticsState.SUCCESS else AnalyticsState.FAILURE,
            error = result.exceptionOrNull()?.chatResult,
            gateMint = rules.gateMint,
            hasPicture = hasPicture,
        )
    )
}
