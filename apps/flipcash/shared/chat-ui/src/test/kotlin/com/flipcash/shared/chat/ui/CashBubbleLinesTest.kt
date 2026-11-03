package com.flipcash.shared.chat.ui

import com.flipcash.services.models.chat.MessageContent
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The caption and foot line of every cash bubble in `docs/specs/cash-bubbles.md`, as the viewer
 * reads them: frames A–E for a group send to two or more, C and D for a DM, G1–G3 for a group send
 * to one person.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CashBubbleLinesTest {

    private val resources = RuntimeEnvironment.getApplication().resources

    private val alex = listOf<Byte>(2)
    private val viewer = listOf<Byte>(3)
    private val third = listOf<Byte>(4)

    private fun cash(
        receiver: List<Byte>? = null,
        multiReceiver: List<List<Byte>> = emptyList(),
        action: MessageContent.Cash.Action = MessageContent.Cash.Action.SENT,
    ) = MessageContent.Cash(
        intentId = listOf(0),
        amount = Fiat(fiat = 3.0, currencyCode = CurrencyCode.USD),
        mint = Mint("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAaaaaaaaaaaa"),
        action = action,
        receiver = receiver,
        multiReceiver = multiReceiver.map { MessageContent.Cash.Payment(it, 1.0, 1_000_000L) },
    )

    /** The bubble's lines as text: caption, then the foot line or null. */
    private fun MessageContent.Cash.read(
        isFromSelf: Boolean,
        viewerPaid: Boolean = false,
        people: CashBubblePeople?,
    ): Pair<String, String?> {
        val lines = bubbleLines(isFromSelf = isFromSelf, viewerPaid = viewerPaid, people = people)
        return lines.caption.text(resources) to lines.counterpart?.text(resources)
    }

    private val threeWay = cash(multiReceiver = listOf(alex, viewer, third))
    private val twoWay = cash(multiReceiver = listOf(alex, viewer))
    private val group = CashBubblePeople.Group(sender = "Fred Wilson", leadRecipient = "Alex Morgan")

    // Group send to two or more people.

    @Test
    fun `A - the sender reads the first recipient and a count of the rest`() {
        assertEquals(
            "You sent" to "to Alex Morgan and 2 others",
            threeWay.read(isFromSelf = true, people = group.copy(sender = null)),
        )
    }

    @Test
    fun `A1 - one other recipient is singular`() {
        assertEquals(
            "You sent" to "to Alex Morgan and 1 other",
            twoWay.read(isFromSelf = true, people = group.copy(sender = null)),
        )
    }

    @Test
    fun `B - a recipient reads themselves and the rest, and who sent it`() {
        assertEquals(
            "You and 2 others received" to "from Fred Wilson",
            threeWay.read(isFromSelf = false, viewerPaid = true, people = group),
        )
    }

    @Test
    fun `B1 - a recipient with one other is singular`() {
        assertEquals(
            "You and 1 other received" to "from Fred Wilson",
            twoWay.read(isFromSelf = false, viewerPaid = true, people = group),
        )
    }

    @Test
    fun `E - a member who was not paid reads who sent it and who it went to`() {
        assertEquals(
            "Fred Wilson sent" to "to Alex Morgan and 2 others",
            threeWay.read(isFromSelf = false, viewerPaid = false, people = group),
        )
    }

    @Test
    fun `an unknown first recipient keeps the head count`() {
        val unknownLead = group.copy(leadRecipient = null)
        assertEquals(
            "You sent" to "to 3 people",
            threeWay.read(isFromSelf = true, people = unknownLead.copy(sender = null)),
        )
        assertEquals(
            "Fred Wilson sent" to "to 3 people",
            threeWay.read(isFromSelf = false, people = unknownLead),
        )
    }

    @Test
    fun `an unknown sender is Someone`() {
        val unknownSender = group.copy(sender = null)
        assertEquals(
            "You and 2 others received" to "from Someone",
            threeWay.read(isFromSelf = false, viewerPaid = true, people = unknownSender),
        )
        assertEquals(
            "Someone sent" to "to Alex Morgan and 2 others",
            threeWay.read(isFromSelf = false, viewerPaid = false, people = unknownSender),
        )
    }

    // Group send to one person: the DM layout for everyone.

    private val toNoah = cash(receiver = alex)
    private val toNoahPeople = CashBubblePeople.Group(sender = "Fred Wilson", leadRecipient = "Noah Parker")

    @Test
    fun `G1 - the sender reads who it went to`() {
        assertEquals(
            "You sent" to "to Noah Parker",
            toNoah.read(isFromSelf = true, people = toNoahPeople.copy(sender = null)),
        )
    }

    @Test
    fun `G2 - the recipient reads who sent it`() {
        assertEquals(
            "You received" to "from Fred Wilson",
            cash(receiver = viewer).read(
                isFromSelf = false,
                viewerPaid = true,
                people = toNoahPeople.copy(leadRecipient = "You"),
            ),
        )
    }

    @Test
    fun `G3 - a member who was not paid reads who sent it and who it went to`() {
        assertEquals(
            "Fred Wilson sent" to "to Noah Parker",
            toNoah.read(isFromSelf = false, viewerPaid = false, people = toNoahPeople),
        )
    }

    @Test
    fun `a one-entry receiver vector reads as a payment to one person`() {
        assertEquals(
            "Fred Wilson sent" to "to Noah Parker",
            cash(multiReceiver = listOf(alex)).read(isFromSelf = false, people = toNoahPeople),
        )
    }

    @Test
    fun `an unknown recipient or sender of a one-person send is Someone`() {
        assertEquals(
            "You sent" to "to Someone",
            toNoah.read(isFromSelf = true, people = CashBubblePeople.Group(sender = null, leadRecipient = null)),
        )
        assertEquals(
            "Someone sent" to "to Someone",
            toNoah.read(isFromSelf = false, people = CashBubblePeople.Group(sender = null, leadRecipient = null)),
        )
    }

    // DM.

    private val dm = CashBubblePeople.Direct(other = "Noah Parker")

    @Test
    fun `C - the sender of a DM reads who it went to`() {
        assertEquals("You sent" to "to Noah Parker", cash().read(isFromSelf = true, people = dm))
    }

    @Test
    fun `D - the recipient of a DM reads who it came from`() {
        assertEquals("You received" to "from Noah Parker", cash().read(isFromSelf = false, people = dm))
    }

    @Test
    fun `an unknown DM member is Someone`() {
        val unknown = CashBubblePeople.Direct(other = null)
        assertEquals("You sent" to "to Someone", cash().read(isFromSelf = true, people = unknown))
        assertEquals("You received" to "from Someone", cash().read(isFromSelf = false, people = unknown))
    }

    @Test
    fun `a blank name is Someone`() {
        assertEquals(
            "You received" to "from Someone",
            cash().read(isFromSelf = false, people = CashBubblePeople.Direct(other = " ")),
        )
    }

    // Tips.

    @Test
    fun `a sent tip reads You tipped`() {
        val tip = cash(action = MessageContent.Cash.Action.TIPPED)
        assertEquals("You tipped" to "to Noah Parker", tip.read(isFromSelf = true, people = dm))
    }

    @Test
    fun `a received tip reads You received`() {
        val tip = cash(action = MessageContent.Cash.Action.TIPPED)
        assertEquals("You received" to "from Noah Parker", tip.read(isFromSelf = false, people = dm))
    }

    // No names resolved for the bubble at all.

    @Test
    fun `without people the bubble keeps its caption alone`() {
        val lines = cash().bubbleLines(isFromSelf = false, viewerPaid = false, people = null)
        assertEquals(CashCaption.YouReceived, lines.caption)
        assertNull(lines.counterpart)
    }

    @Test
    fun `a group payment that names nobody keeps its caption alone`() {
        val lines = cash().bubbleLines(isFromSelf = true, viewerPaid = false, people = group)
        assertEquals(CashCaption.YouSent, lines.caption)
        assertNull(lines.counterpart)
    }

    // The amount line of a group send to two or more.

    /** $1.00 split three ways, from before every recipient got the same amount. */
    private val uneven = cash(multiReceiver = listOf(alex, viewer, third)).copy(
        amount = Fiat(fiat = 1.0, currencyCode = CurrencyCode.USD),
        multiReceiver = listOf(
            MessageContent.Cash.Payment(alex, 0.33, 330_000L),
            MessageContent.Cash.Payment(viewer, 0.34, 340_000L),
            MessageContent.Cash.Payment(third, 0.33, 330_000L),
        ),
    )

    /** The large line as text. */
    private fun MessageContent.Cash.amountText(viewerPart: MessageContent.Cash.Payment?): String {
        fun usd(value: Double) = Fiat(fiat = value, currencyCode = CurrencyCode.USD).formatted()
        return when (val line = amountLine(viewerPart)) {
            CashAmountLine.Whole -> usd(amount.decimalValue)
            is CashAmountLine.Own -> usd(line.part.nativeAmount)
            is CashAmountLine.Each -> line.text(resources, usd(line.part.nativeAmount))
        }
    }

    @Test
    fun `equal parts read each without a tilde, for every viewer`() {
        assertEquals("$1.00 each", threeWay.amountText(viewerPart = null))
        assertEquals("$1.00 each", threeWay.amountText(viewerPart = threeWay.multiReceiver[1]))
    }

    @Test
    fun `uneven parts - the sender reads the smallest part with a tilde`() {
        assertEquals("~$0.33 each", uneven.amountText(viewerPart = null))
        assertEquals("You sent", uneven.read(isFromSelf = true, people = group.copy(sender = null)).first)
    }

    @Test
    fun `uneven parts - a member who was not paid reads the smallest part with a tilde`() {
        assertEquals("~$0.33 each", uneven.amountText(viewerPart = null))
        assertEquals("Fred Wilson sent", uneven.read(isFromSelf = false, people = group).first)
    }

    @Test
    fun `uneven parts - a recipient reads their own part, with no each`() {
        val own = uneven.multiReceiver[1]
        assertEquals(CashAmountLine.Own(own), uneven.amountLine(viewerPart = own))
        assertEquals("$0.34", uneven.amountText(viewerPart = own))
        assertEquals(
            "You and 2 others received" to "from Fred Wilson",
            uneven.read(isFromSelf = false, viewerPaid = true, people = group),
        )
    }

    @Test
    fun `a payment to one person reads the whole amount`() {
        assertEquals(CashAmountLine.Whole, cash(receiver = alex).amountLine(viewerPart = null))
        assertEquals(CashAmountLine.Whole, cash(multiReceiver = listOf(alex)).amountLine(viewerPart = null))
    }
}
