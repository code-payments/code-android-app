package com.flipcash.app.messenger.internal

import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.GroupEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.chatResult

/**
 * `Group: Joined` for the answer JoinChat gave. [group] is the one held before the call, since the
 * roster the count comes from moves with the join.
 */
internal fun FlipcashAnalytics.trackJoined(group: ChatSubject.Group?, result: Result<*>) {
    track(
        GroupEvents.joined(
            state = if (result.isSuccess) AnalyticsState.SUCCESS else AnalyticsState.FAILURE,
            error = result.exceptionOrNull()?.chatResult,
            memberCount = group?.memberCount?.toInt() ?: 0,
            gated = group?.rules?.listener.orEmpty().isNotEmpty(),
        )
    )
}

/** `Group: Left` for the answer LeaveChat gave, with the member count from before the call. */
internal fun FlipcashAnalytics.trackLeft(memberCount: Int, result: Result<*>) {
    track(
        GroupEvents.left(
            state = if (result.isSuccess) AnalyticsState.SUCCESS else AnalyticsState.FAILURE,
            error = result.exceptionOrNull()?.chatResult,
            memberCount = memberCount,
        )
    )
}
