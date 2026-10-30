package com.flipcash.app.messenger.internal

import com.flipcash.analytics.SilenceDuration
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.GroupEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.chatResult
import com.flipcash.shared.chat.InviteCreation

private fun Result<*>.state() = if (isSuccess) AnalyticsState.SUCCESS else AnalyticsState.FAILURE

private fun Result<*>.error() = exceptionOrNull()?.chatResult

/**
 * `Group: Invite Link Created` for the answer CreateInvite gave. [InviteCreation] folds the
 * server's TOO_MANY_INVITES and DENIED into its own cases, so they are named again here.
 */
internal fun FlipcashAnalytics.trackInviteLinkCreated(creation: InviteCreation) {
    val (state, error) = when (creation) {
        is InviteCreation.Created -> AnalyticsState.SUCCESS to null
        InviteCreation.TooMany -> AnalyticsState.FAILURE to "TooManyInvites"
        InviteCreation.NotAvailable -> AnalyticsState.FAILURE to "Denied"
        is InviteCreation.Failed -> AnalyticsState.FAILURE to creation.cause.chatResult
    }
    track(GroupEvents.inviteLinkCreated(state = state, error = error))
}

/** `Group: Invite Link Revoked` for the answer RevokeInvite gave, after the host confirmed. */
internal fun FlipcashAnalytics.trackInviteLinkRevoked(result: Result<*>) {
    track(GroupEvents.inviteLinkRevoked(state = result.state(), error = result.error()))
}

/** `Group: Join Request Approved` for the answer AdmitLobbyMember gave. */
internal fun FlipcashAnalytics.trackJoinRequestApproved(result: Result<*>) {
    track(GroupEvents.joinRequestApproved(state = result.state(), error = result.error()))
}

/** `Group: Join Request Denied` for the answer DenyLobbyMember gave. */
internal fun FlipcashAnalytics.trackJoinRequestDenied(result: Result<*>) {
    track(GroupEvents.joinRequestDenied(state = result.state(), error = result.error()))
}

/** `Group: Member Silenced` for the answer SilenceUser gave to the option picked. */
internal fun FlipcashAnalytics.trackMemberSilenced(option: SilenceOption, result: Result<*>) {
    track(
        GroupEvents.memberSilenced(
            duration = option.analytics,
            state = result.state(),
            error = result.error(),
        )
    )
}

/** `Group: Member Removed` for the answer BanUser gave after the host confirmed Remove. */
internal fun FlipcashAnalytics.trackMemberRemoved(result: Result<*>) {
    track(GroupEvents.memberRemoved(state = result.state(), error = result.error()))
}

private val SilenceOption.analytics: SilenceDuration
    get() = when (this) {
        SilenceOption.OneHour -> SilenceDuration.ONE_HOUR
        SilenceOption.EightHours -> SilenceDuration.EIGHT_HOURS
        SilenceOption.OneWeek -> SilenceDuration.ONE_WEEK
        SilenceOption.Forever -> SilenceDuration.FOREVER
    }
