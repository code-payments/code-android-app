package com.flipcash.app.messenger.internal.screens.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.InviteLandingState
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.shared.chat.invite.DeadInviteReason
import kotlin.time.Instant

/** The states of the invite landing (nodes 12846:59 to 12846:259), shared by the previews and their render test. */
internal object InviteLandingSamples {
    private val chatId = ChatId(ByteArray(16) { 5 }.toList())

    private val names = listOf("Moonyversal", "Adam", "Jeff Yanta", "Alex", "Tokin", "Shebah", "Sam", "Ana", "Raj")

    private fun member(index: Int, name: String) = ChatMember(
        userId = listOf(index.toByte()),
        userProfile = UserProfile(
            displayName = name,
            socialAccounts = emptyList(),
            phoneNumber = null,
            email = null,
            userId = listOf(index.toByte()),
        ),
        pointers = emptyList(),
        joinedAt = Instant.fromEpochSeconds(100L + index),
        version = 1,
    )

    private val group = ChatSubject.Group(
        chatId = chatId,
        groupTitle = "Moony",
        picture = null,
        memberCount = 48,
        rules = null,
        isMember = false,
        description = "A place for people who hold and use Moony to meet, tip each other, and share what they're building. Hosted by Moonyversal.",
        isPrivate = true,
    )

    /** 3a: a live link. */
    val preview = InviteLandingState(
        phase = InviteLandingState.Phase.Preview,
        group = group,
        members = names.mapIndexed { i, n -> member(i + 1, n) },
        memberCount = 48,
    )

    /** 4a: Join Group pressed, the call in flight. */
    val joining = preview.copy(joining = true)

    /** 3c: a link that no longer admits anyone, one state per reason. */
    fun dead(reason: DeadInviteReason) = preview.copy(members = emptyList(), dead = true, deadReason = reason)

    /** 3e. */
    val notFound = InviteLandingState(phase = InviteLandingState.Phase.NotFound)

    /** JoinChat NOT_FOUND on a live link: the group is gone. */
    val groupGone = InviteLandingState(phase = InviteLandingState.Phase.GroupGone)

    /** 3f. */
    val banned = InviteLandingState(phase = InviteLandingState.Phase.Banned)

    /** 4c, shown under the Couldn't Join Group alert. */
    val loadFailed = InviteLandingState(phase = InviteLandingState.Phase.LoadFailed)
}

@Preview(name = "3a invite")
@Composable
private fun Preview_Invite() = FlipcashPreview(showBackground = true) { GroupInviteContent(InviteLandingSamples.preview, {}, {}, {}, {}) }

@Preview(name = "4a joining")
@Composable
private fun Preview_Joining() = FlipcashPreview(showBackground = true) { GroupInviteContent(InviteLandingSamples.joining, {}, {}, {}, {}) }

@Preview(name = "3c exhausted")
@Composable
private fun Preview_Exhausted() = FlipcashPreview(showBackground = true) { GroupInviteContent(InviteLandingSamples.dead(DeadInviteReason.EXHAUSTED), {}, {}, {}, {}) }

@Preview(name = "3c expired")
@Composable
private fun Preview_Expired() = FlipcashPreview(showBackground = true) { GroupInviteContent(InviteLandingSamples.dead(DeadInviteReason.EXPIRED), {}, {}, {}, {}) }

@Preview(name = "3c revoked")
@Composable
private fun Preview_Revoked() = FlipcashPreview(showBackground = true) { GroupInviteContent(InviteLandingSamples.dead(DeadInviteReason.REVOKED), {}, {}, {}, {}) }

@Preview(name = "3e not found")
@Composable
private fun Preview_NotFound() = FlipcashPreview(showBackground = true) { GroupInviteContent(InviteLandingSamples.notFound, {}, {}, {}, {}) }

@Preview(name = "3e group gone")
@Composable
private fun Preview_GroupGone() = FlipcashPreview(showBackground = true) { GroupInviteContent(InviteLandingSamples.groupGone, {}, {}, {}, {}) }

@Preview(name = "3f banned")
@Composable
private fun Preview_Banned() = FlipcashPreview(showBackground = true) { GroupInviteContent(InviteLandingSamples.banned, {}, {}, {}, {}) }
