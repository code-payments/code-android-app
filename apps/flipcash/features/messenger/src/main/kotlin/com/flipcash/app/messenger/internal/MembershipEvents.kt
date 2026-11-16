package com.flipcash.app.messenger.internal

import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.CommunityChatEvents
import com.flipcash.analytics.events.GroupEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.chatResult
import com.flipcash.app.analytics.gateMint
import com.flipcash.shared.chat.invite.InviteJoinResult

/**
 * `Community Chat: Joined` for the answer JoinChat gave. [group] is the one held before the call,
 * since the roster the count comes from moves with the join.
 */
internal fun FlipcashAnalytics.trackCommunityChatJoined(group: ChatSubject.Group?, result: Result<*>) {
    track(
        CommunityChatEvents.joined(
            state = if (result.isSuccess) AnalyticsState.SUCCESS else AnalyticsState.FAILURE,
            error = result.exceptionOrNull()?.chatResult,
            memberCount = group?.memberCount?.toInt() ?: 0,
            gateMint = group?.rules.gateMint,
        )
    )
}

/**
 * `Group: Joined` for the answer an invite join gave. [memberCount] is the preview's roster count
 * from before the call. A private Group has no balance gate, so `Gated` is always false.
 */
internal fun FlipcashAnalytics.trackGroupJoined(memberCount: Int, result: InviteJoinResult) {
    // Literals rather than the class name: R8 renames classes in release builds.
    val error = when (result) {
        is InviteJoinResult.Joined -> null
        is InviteJoinResult.Dead -> "Dead"
        InviteJoinResult.NotFound -> "NotFound"
        InviteJoinResult.GroupGone -> "GroupGone"
        InviteJoinResult.Banned -> "Banned"
        InviteJoinResult.Refused -> "Refused"
        InviteJoinResult.Failed -> "Failed"
    }
    track(
        GroupEvents.joined(
            state = if (result is InviteJoinResult.Joined) AnalyticsState.SUCCESS else AnalyticsState.FAILURE,
            error = error,
            memberCount = memberCount,
            gated = false,
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
