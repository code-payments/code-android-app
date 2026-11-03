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

/** The summary copy for the spec's cases 14–18 and 49–51. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RequestCopyTest {

    private val resources = AndroidResources(RuntimeEnvironment.getApplication())
    private val viewer = id(0)

    private fun id(n: Int): ID = listOf((n shr 8).toByte(), n.toByte())

    // As the summary formats an amount: "$20" for a whole amount, "$33.34" otherwise.
    private fun Fiat.text(): String = formatted(Fiat.FormattingRule.Truncated)

    private fun draft(memberCount: Int, amount: Fiat = Fiat(20.0, CurrencyCode.USD)) = NewRequestDraft(
        viewerId = viewer,
        members = NewRequestDraft.members(
            viewer,
            (0 until memberCount).map { ChatMember(id(it), mockk<UserProfile>(relaxed = true), emptyList()) },
        ),
        title = "Saturday Request",
        amount = amount,
    )

    private fun button(draft: NewRequestDraft) = requestButtonText(draft.totalAmount!!.text(), resources)

    // 14
    @Test
    fun `20 each between everyone reads everyone including you and requests 520 total`() {
        val draft = draft(26)

        assertEquals("Everyone, Including You", peopleValue(draft.people, resources))
        assertEquals("You'll be marked as paid", peopleDetail(draft.people, resources))
        assertEquals("Request $520 Total", button(draft))
    }

    // 15
    @Test
    fun `creator and one other read in the singular and request 40 total`() {
        val draft = draft(26).copy(selected = setOf(viewer, id(4)))

        assertEquals("1 Person, Including You", peopleValue(draft.people, resources))
        assertEquals("Request $40 Total", button(draft))
    }

    // 49
    @Test
    fun `creator unchecked leaves 25 shares and requests 500 total`() {
        val start = draft(26)
        val draft = start.copy(selected = start.toggled(viewer)!!)

        assertEquals("Request $500 Total", button(draft))
    }

    // 50
    @Test
    fun `a cent amount multiplies exactly`() {
        val draft = draft(5, amount = Fiat(33.34, CurrencyCode.USD)).copy(selected = setOf(viewer, id(1), id(2)))

        assertEquals("Request $100.02 Total", button(draft))
    }

    // 51
    @Test
    fun `the total follows the selection`() {
        val draft = draft(26).copy(selected = setOf(id(2)))

        assertEquals("Request $20 Total", button(draft))
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
