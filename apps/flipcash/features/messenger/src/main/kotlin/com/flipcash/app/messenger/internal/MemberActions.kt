package com.flipcash.app.messenger.internal

import com.flipcash.services.models.chat.SilenceState
import com.flipcash.services.models.chat.isActiveAt
import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

/**
 * What the viewer can do to one member of a group from the Members list swipe and the member
 * sheet. [restrict] and [lift] never hold together: a restricted member is offered Lift in place
 * of Restrict.
 */
internal data class MemberActions(
    val restrict: Boolean = false,
    val lift: Boolean = false,
    val remove: Boolean = false,
    /** Whether the member's restriction is shown: only to a viewer who can see and change it. */
    val showsRestriction: Boolean = false,
) {
    /** The sheet's "In <group>" section has at least one row. */
    val hasGroupSection: Boolean get() = restrict || lift || remove

    /** Report stands in for the group section: everyone gets it, except a host who has that section. */
    val report: Boolean get() = !hasGroupSection

    companion object {
        val None = MemberActions()
    }
}

/**
 * The visibility rules for the host's shortcuts, the same as the full profile's ⋯ menu
 * (`profileMenuItems`): nothing on the group's creator or on the viewer themselves, and each
 * action only with its grant. A viewer who has left the group holds no grants worth showing.
 *
 * @param silence the member's silence as the host's restricted-users read has it, or null.
 */
internal fun memberActions(
    viewerId: ID?,
    memberId: ID,
    creatorId: ID?,
    isMember: Boolean,
    canSilence: Boolean,
    canBan: Boolean,
    silence: SilenceState?,
    now: Instant,
): MemberActions {
    if (!isMember || memberId == viewerId || memberId == creatorId) return MemberActions.None
    val restricted = canSilence && silence.isActiveAt(now)
    return MemberActions(
        restrict = canSilence && !restricted,
        lift = restricted,
        remove = canBan,
        showsRestriction = restricted,
    )
}

/**
 * The time left on a restriction, cut to the two largest units the row and sheet show
 * ("6h 12m", "3d 4h", "59m"). A zero second unit is dropped ("6h"). Rounded down, so a one-hour
 * restriction reads 59m straight after it is set, and never below a minute while it is in force.
 */
internal sealed interface TimeLeft {
    data class Days(val days: Int, val hours: Int) : TimeLeft
    data class Hours(val hours: Int, val minutes: Int) : TimeLeft
    data class Minutes(val minutes: Int) : TimeLeft
}

/** The time left on [silence] at [now]: null when it has lapsed or has no end. */
internal fun timeLeft(silence: SilenceState?, now: Instant): TimeLeft? {
    val until = (silence as? SilenceState.Until)?.until ?: return null
    val left = until - now
    if (!left.isPositive()) return null
    val totalMinutes = left.inWholeMinutes.coerceAtLeast(1)
    val days = (totalMinutes / (24 * 60)).toInt()
    val hours = ((totalMinutes / 60) % 24).toInt()
    val minutes = (totalMinutes % 60).toInt()
    return when {
        days > 0 -> TimeLeft.Days(days, hours)
        totalMinutes >= 60 -> TimeLeft.Hours(hours, minutes)
        else -> TimeLeft.Minutes(minutes)
    }
}
