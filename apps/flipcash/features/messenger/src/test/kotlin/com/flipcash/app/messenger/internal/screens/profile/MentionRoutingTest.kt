package com.flipcash.app.messenger.internal.screens.profile

import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.MemberActions
import com.flipcash.app.messenger.internal.MembershipCheck
import com.flipcash.app.messenger.internal.MentionDestination
import com.flipcash.app.messenger.internal.membershipCheck
import com.flipcash.app.messenger.internal.link.UserLinkLookup
import com.flipcash.app.messenger.internal.memberActions
import com.flipcash.app.messenger.internal.mentionDestination
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.shared.chat.models.LinkCard
import com.getcode.opencode.model.core.ID
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Where a tapped @mention leads in a group transcript, from the handle lookup to the sheet. */
class MentionRoutingTest {

    private val viewer: ID = listOf(1)
    private val creator: ID = listOf(2)
    private val member: ID = listOf(3)
    private val former: ID = listOf(4)
    private val now = Instant.fromEpochSeconds(1_800_000_000)

    private fun profile(id: ID) = UserProfile.Empty.copy(displayName = "Sam", username = "sam", userId = id)

    private fun found(id: ID, isOwn: Boolean = false) = Result.success(
        LinkCard.User.State.Resolved(
            userId = id, isOwn = isOwn, profile = profile(id), name = "Sam", handle = "@sam", joined = null,
        )
    )

    private fun route(lookup: Result<LinkCard.User.State.Resolved>, inGroup: Boolean = true): Any {
        val destination = mentionDestination("sam", lookup, counterpart = null)
        return if (destination is MentionDestination.Profile) {
            mentionStep(destination, inGroup, viewer)
        } else {
            destination
        }
    }

    private fun roster(vararg ids: ID) = ids.map { ChatMember(it, profile(it), emptyList()) }

    private fun group(isPrivate: Boolean = true) = ChatSubject.Group(
        chatId = ChatId(listOf(9)), groupTitle = "Test", picture = null, memberCount = 3, rules = null,
        isMember = true, creator = creator, isPrivate = isPrivate,
    )

    @Test
    fun `a mention of another member opens their sheet with the avatar's origin`() {
        val step = route(found(member))
        assertIs<ChatStep.MemberSheet>(step)
        assertEquals(member, (step.contact as ChatParticipant.TipUser).userId)
        assertEquals(ProfileOrigin.Chat, step.origin)
    }

    @Test
    fun `a mention of yourself keeps today's behaviour`() {
        assertEquals(MentionDestination.OwnTipCard, route(found(viewer, isOwn = true)))
    }

    @Test
    fun `a mention in a DM keeps the full profile`() {
        val step = route(found(member), inGroup = false)
        assertIs<ChatStep.Profile>(step)
        assertEquals(ProfileOrigin.Mention, step.origin)
    }

    @Test
    fun `an unresolved username keeps today's behaviour`() {
        assertEquals(
            MentionDestination.NoSuchAccount("sam"),
            route(Result.failure(UserLinkLookup.NoSuchAccount(LinkCard.User.Identity.ByUsername("sam")))),
        )
    }

    private fun check(
        roster: List<ChatMember>?,
        failed: Boolean = false,
        isPrivate: Boolean = true,
        user: ID = member,
    ) = membershipCheck(isPrivate, roster, failed, user)

    private fun actionsFor(check: MembershipCheck) =
        memberActions(viewer, member, creator, true, true, true, null, now, check)

    @Test
    fun `a former member's sheet is a mention of someone off the roster`() {
        assertIs<ChatStep.MemberSheet>(route(found(former)))
        assertEquals(MembershipCheck.NotMember, check(roster(creator, viewer, member), user = former))
    }

    @Test
    fun `pending becomes confirmed when the roster lands`() {
        assertEquals(MembershipCheck.Pending, check(null))
        assertEquals(MembershipCheck.Confirmed, check(roster(creator, viewer, member)))
    }

    @Test
    fun `pending becomes not a member when the roster omits the person`() {
        assertEquals(MembershipCheck.Pending, check(null))
        assertEquals(MembershipCheck.NotMember, check(roster(creator, viewer)))
    }

    @Test
    fun `pending becomes failed when the read fails`() {
        assertEquals(MembershipCheck.Pending, check(null))
        assertEquals(MembershipCheck.Failed, check(null, failed = true))
    }

    @Test
    fun `a public group has no check`() {
        assertEquals(MembershipCheck.Confirmed, check(null, isPrivate = false))
        assertEquals(MembershipCheck.Confirmed, check(null, failed = true, isPrivate = false))
    }

    @Test
    fun `the sheet keeps the same rows in every state and enables them only once confirmed`() {
        val confirmed = actionsFor(MembershipCheck.Confirmed)
        assertEquals(MemberActions(restrict = true, remove = true), confirmed)
        assertTrue(confirmed.groupEnabled)
        for (state in listOf(MembershipCheck.Pending, MembershipCheck.NotMember, MembershipCheck.Failed)) {
            val actions = actionsFor(state)
            assertEquals(confirmed.restrict, actions.restrict)
            assertEquals(confirmed.remove, actions.remove)
            assertEquals(false, actions.groupEnabled)
        }
    }

    @Test
    fun `the profile menu states`() {
        fun group(roster: List<ChatMember>?, failed: Boolean = false, isPrivate: Boolean = true) = ProfileGroup(
            ChatId(listOf(9)), "Test", creator, canSilence = true, canBan = true,
            roster = roster, isPrivate = isPrivate, rosterFailed = failed,
        )
        fun items(g: ProfileGroup) = profileMenuItems(isBlocked = false, hasDm = false, userId = member, group = g)
        val both = setOf(ChatProfileAction.Silence, ChatProfileAction.Remove)

        // Confirmed: enabled.
        assertTrue(items(group(roster(creator, member))).containsAll(both))
        assertEquals(emptySet(), disabledProfileItems(group(roster(creator, member)), member))
        // Pending and failed: shown, disabled.
        for (g in listOf(group(null), group(null, failed = true))) {
            assertTrue(items(g).containsAll(both))
            assertEquals(both, disabledProfileItems(g, member))
        }
        // Not a member: none.
        assertTrue(items(group(roster(creator))).none { it in both })
        // Public: unchanged.
        assertTrue(items(group(null, isPrivate = false)).containsAll(both))
        assertEquals(emptySet(), disabledProfileItems(group(null, isPrivate = false), member))
    }
}
