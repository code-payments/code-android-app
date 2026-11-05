package com.flipcash.app.messenger.internal.screens.profile

import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.MemberActions
import com.flipcash.app.messenger.internal.MentionDestination
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

    @Test
    fun `a former member opens the sheet without a host section`() {
        val step = route(found(former))
        assertIs<ChatStep.MemberSheet>(step)
        val stillIn = isCurrentMember(group(), roster(creator, viewer, member), former)
        assertEquals(false, stillIn)
        val actions = memberActions(viewer, former, creator, true, true, true, null, now, targetIsMember = stillIn)
        assertEquals(MemberActions.None, actions)
        assertTrue(actions.report)
    }

    @Test
    fun `a current member keeps the host section`() {
        val stillIn = isCurrentMember(group(), roster(creator, viewer, member), member)
        assertTrue(stillIn)
        val actions = memberActions(viewer, member, creator, true, true, true, null, now, targetIsMember = stillIn)
        assertEquals(MemberActions(restrict = true, remove = true), actions)
    }

    @Test
    fun `membership is assumed when no roster is held`() {
        assertTrue(isCurrentMember(group(), emptyList(), former))
        assertTrue(isCurrentMember(group(isPrivate = false), roster(member), former))
    }
}
