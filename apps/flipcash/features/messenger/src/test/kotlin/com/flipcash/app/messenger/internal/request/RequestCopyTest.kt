package com.flipcash.app.messenger.internal.request

import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatMember
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.util.resources.AndroidResources
import io.mockk.mockk
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** The summary copy for the spec's cases 14, 15, 17 and 18. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RequestCopyTest {

    private val resources = AndroidResources(RuntimeEnvironment.getApplication())
    private val viewer = id(0)

    private fun id(n: Int): ID = listOf((n shr 8).toByte(), n.toByte())

    private fun draft(memberCount: Int) = NewRequestDraft(
        viewerId = viewer,
        members = NewRequestDraft.members(
            viewer,
            (0 until memberCount).map { ChatMember(id(it), mockk<UserProfile>(relaxed = true), emptyList()) },
        ),
        title = "Saturday Request",
        perPerson = Fiat(20.0, CurrencyCode.USD),
    )

    // 14
    @Test
    fun `everyone selected reads everyone including you and requests from 25 people`() {
        val draft = draft(26)

        assertEquals("Everyone, Including You", peopleValue(draft.people, resources))
        assertEquals("You'll be marked as paid", peopleDetail(draft.people, resources))
        assertEquals("Request $20 from 25 People", requestButtonText(draft, "$20", resources))
    }

    // 15
    @Test
    fun `creator and one other read in the singular`() {
        val draft = draft(26).copy(selected = setOf(viewer, id(4)))

        assertEquals("1 Person, Including You", peopleValue(draft.people, resources))
        assertEquals("Request $20 from 1 Person", requestButtonText(draft, "$20", resources))
    }

    // 16
    @Test
    fun `creator deselected has no second line`() {
        val start = draft(26)
        val draft = start.copy(selected = start.toggled(viewer)!!)

        assertEquals("Everyone Except You", peopleValue(draft.people, resources))
        assertNull(peopleDetail(draft.people, resources))
    }

    // 17: the Select People button takes canContinue as its enabled state.
    @Test
    fun `only the creator selected disables the button`() {
        assertFalse(draft(26).copy(selected = setOf(viewer)).canContinue)
    }

    // 18: the view model shows this notice when toggled() refuses the 101st share.
    @Test
    fun `the 101st share shows 100 people max`() {
        val draft = draft(150)
        val full = draft.copy(selected = draft.members.take(100).toSet())

        assertNull(full.toggled(draft.members[100]))
        assertEquals("100 People Max", maxPeopleNotice(resources).first)
    }
}
