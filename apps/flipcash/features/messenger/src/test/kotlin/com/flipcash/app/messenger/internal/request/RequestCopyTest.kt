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
import kotlin.test.assertTrue

/** The summary copy and shares for the spec's cases 14–18 and 49–51. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RequestCopyTest {

    private val resources = AndroidResources(RuntimeEnvironment.getApplication())
    private val viewer = id(0)

    private fun id(n: Int): ID = listOf((n shr 8).toByte(), n.toByte())

    private fun draft(memberCount: Int, amount: Fiat = Fiat(20.0, CurrencyCode.USD)) = NewRequestDraft(
        viewerId = viewer,
        members = NewRequestDraft.members(
            viewer,
            (0 until memberCount).map { ChatMember(id(it), mockk<UserProfile>(relaxed = true), emptyList()) },
        ),
        title = "Saturday Request",
        amount = amount,
    )

    private fun button() = requestButtonText(resources)

    private fun usd(amount: Double) = Fiat(amount, CurrencyCode.USD)

    // 14
    @Test
    fun `20 each between everyone reads everyone including you and sends 26 shares`() {
        val draft = draft(26)

        assertEquals("Everyone, Including You", peopleValue(draft.people, resources))
        assertEquals("You'll be marked as paid", peopleDetail(draft.people, resources))
        assertEquals("Send Request", button())
        val shares = draft.split!!
        assertEquals(26, shares.size)
        assertEquals(viewer, shares.first().userId)
        assertTrue(shares.all { it.amount == usd(20.0) })
    }

    // 15
    @Test
    fun `creator and one other read in the singular and send 2 shares`() {
        val draft = draft(26).copy(selected = setOf(viewer, id(4)))

        assertEquals("1 Person, Including You", peopleValue(draft.people, resources))
        assertEquals("Send Request", button())
        assertEquals(listOf(usd(20.0), usd(20.0)), draft.split!!.map { it.amount })
    }

    // 49
    @Test
    fun `33_33 each for the creator and two others sends three equal shares, creator first`() {
        val draft = draft(5, amount = usd(33.33)).copy(selected = setOf(id(2), viewer, id(1)))

        assertEquals("Send Request", button())
        val shares = draft.split!!
        assertEquals(listOf(usd(33.33), usd(33.33), usd(33.33)), shares.map { it.amount })
        assertEquals(viewer, shares.first().userId)
    }

    // 50
    @Test
    fun `creator deselected with one other sends one share`() {
        val draft = draft(26).copy(selected = setOf(id(2)))

        assertEquals("Send Request", button())
        assertEquals(listOf(usd(20.0)), draft.split!!.map { it.amount })
    }

    // 51
    @Test
    fun `unchecking the creator leaves 25 shares and the button unchanged`() {
        val start = draft(26)
        assertEquals("Send Request", button())
        val draft = start.copy(selected = start.toggled(viewer)!!)

        assertEquals("Send Request", button())
        assertEquals(25, draft.split!!.size)
        assertTrue(draft.split!!.none { it.userId == viewer })
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
