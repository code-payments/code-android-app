package com.flipcash.app.messenger.internal.invites

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.Invite
import com.flipcash.shared.chat.HostInvite
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

/** "Nov 3": a link's dates are read as days, so the year and time are left out. */
internal fun shortDate(
    at: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = DateTimeFormatter.ofPattern("MMM d", locale)
    .withZone(zone)
    .format(java.time.Instant.ofEpochMilli(at.toEpochMilliseconds()))

/** How many have joined through a link: "12 joined" for no limit, "3 of 10 joined" for a cap. */
internal data class JoinedCount(val useCount: Long, val maxUses: Long) {
    val isCapped: Boolean get() = maxUses > 0
}

/** What a link's line says about when it ends, or why it can't be used. */
internal sealed interface LinkStatus {
    data class Expires(val at: Instant) : LinkStatus
    data object NeverExpires : LinkStatus
    data class Expired(val at: Instant?) : LinkStatus
    data object Revoked : LinkStatus
    data object ReachedLimit : LinkStatus
}

internal fun Invite.linkStatus(): LinkStatus = when (status) {
    Invite.Status.ACTIVE, Invite.Status.UNKNOWN -> expiresAt?.let(LinkStatus::Expires) ?: LinkStatus.NeverExpires
    Invite.Status.EXPIRED -> LinkStatus.Expired(expiresAt)
    Invite.Status.REVOKED -> LinkStatus.Revoked
    Invite.Status.EXHAUSTED -> LinkStatus.ReachedLimit
}

@Composable
internal fun JoinedCount.label(): String =
    if (isCapped) {
        stringResource(R.string.label_inviteJoinedOf, useCount.toInt(), maxUses.toInt())
    } else {
        stringResource(R.string.label_inviteJoined, useCount.toInt())
    }

@Composable
internal fun LinkStatus.label(): String = when (this) {
    is LinkStatus.Expires -> stringResource(R.string.label_inviteExpires, shortDate(at))
    LinkStatus.NeverExpires -> stringResource(R.string.label_inviteNeverExpires)
    is LinkStatus.Expired -> at?.let { stringResource(R.string.label_inviteExpired, shortDate(it)) }
        ?: stringResource(R.string.label_inviteExpired, "")
    LinkStatus.Revoked -> stringResource(R.string.label_inviteRevoked)
    LinkStatus.ReachedLimit -> stringResource(R.string.label_inviteReachedLimit)
}

/** The invite sheet's line under the link: "Expires Nov 3 · 0 joined". */
@Composable
internal fun inviteMetaLine(expiresAt: Instant?, useCount: Long, maxUses: Long): String {
    val ends = (expiresAt?.let(LinkStatus::Expires) ?: LinkStatus.NeverExpires).label()
    return "$ends · ${JoinedCount(useCount, maxUses).label()}"
}

/** Manage Invites' second line: joined first, then when it ends (or why it can't be shared). */
@Composable
internal fun HostInvite.detailLine(): String {
    val joined = JoinedCount(invite.useCount, invite.maxUses).label()
    val second = when {
        !isActive -> invite.linkStatus().label()
        !canShare -> stringResource(R.string.label_inviteCantShare)
        else -> invite.linkStatus().label()
    }
    return if (isActive) "$joined · $second" else "$second · $joined"
}

/** The link as the pill shows it: scheme dropped and the middle of the secret elided (node 12933:113). */
internal fun elideInviteUrl(url: String, head: Int = 32, tail: Int = 4): String {
    val bare = url.removePrefix("https://").removePrefix("http://")
    return if (bare.length <= head + tail + 1) bare else bare.take(head) + "…" + bare.takeLast(tail)
}
