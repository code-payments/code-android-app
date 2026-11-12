package com.flipcash.shared.transactionhistory

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.app.core.feed.ActivityFeedMessageWithToken
import com.flipcash.app.core.feed.MessageMetadata
import com.flipcash.app.core.feed.MessageState
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.services.models.BoostedReaction
import com.flipcash.services.models.RequestedPayment
import com.flipcash.services.models.ReturnedPayment
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.Emoji
import com.flipcash.shared.transactionhistory.internal.TransactionItemMapper
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.util.resources.FakeResourceHelper
import com.getcode.utils.hexEncodedString
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Spec for the Money tab's activity rows (frame node 13729:744): a row names the group when the
 * activity carries a chat the app can name, says who and when underneath, and shows the signed
 * amount. Everything else keeps today's title and avatar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroupMoneyShellSpecTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val resources = FakeResourceHelper()
        .stub(R.string.title_activity_tipFrom, "Tip from %1\$s")
        .stub(R.string.title_activity_sentTo, "Sent to %1\$s")
        .stub(R.string.title_activity_receivedFrom, "Received from %1\$s")
        .stub(R.string.title_activity_sentBackTo, "Sent back to %1\$s")
        .stub(R.string.title_activity_sentBack, "%1\$s sent back")
        .stub(R.string.title_activity_convert, "%1\$s → %2\$s")
        .stub(R.string.title_activity_boostedFor, "Boosted %1\$s for %2\$s")
        .stub(R.string.title_activity_boostFrom, "Boost %1\$s from %2\$s")
        .stub(R.string.title_activity_boostedIn, "Boosted %1\$s in %2\$s")
        .stub(R.string.title_activity_boosted, "Boosted %1\$s")
        .stub(R.string.title_activity_boostReceived, "Boost %1\$s received")
    private val mapper = TransactionItemMapper(resources)

    private val knownUserId: ID = listOf<Byte>(0x0A, 0x0B, 0x0C)
    private val profiles = mapOf(
        knownUserId.hexEncodedString() to UserProfile.Empty.copy(displayName = "Ella"),
    )

    private val groupChat = ChatId(ByteArray(32) { 9 })
    private val boost = BoostedReaction(chatId = groupChat, messageId = 7L, emoji = Emoji("🔥"))
    private val request = RequestedPayment(chatId = groupChat, paymentRequestId = listOf<Byte>(5, 6))
    private val returned = ReturnedPayment(chatId = ChatId(ByteArray(32) { 4 }), messageId = 11L)

    private fun message(metadata: MessageMetadata?) = ActivityFeedMessage(
        id = listOf<Byte>(1, 2, 3),
        text = "Paid Ella",
        amount = LocalFiat(
            usdf = Fiat(20.0, CurrencyCode.USD),
            nativeAmount = Fiat(20.0, CurrencyCode.USD),
        ),
        timestamp = Instant.fromEpochSeconds(1700000000L),
        state = MessageState.COMPLETED,
        metadata = metadata,
    )

    private fun map(metadata: MessageMetadata?, chatName: String? = null): TransactionListItem =
        mapper.map(ActivityFeedMessageWithToken(message(metadata), token = null, chatName = chatName) to profiles)

    // region mapping

    @Test
    fun `a boost in a group carries the chat and the group name`() {
        val item = map(MessageMetadata.ReceivedCrypto(userId = knownUserId, boostedReaction = boost), "Weekend Trip")
        assertEquals(groupChat, item.chatId)
        assertEquals("Weekend Trip", item.chatName)
    }

    @Test
    fun `a request payment carries the chat and the group name`() {
        val item = map(MessageMetadata.DirectlySentCrypto(userId = knownUserId, requestedPayment = request), "Weekend Trip")
        assertEquals(groupChat, item.chatId)
        assertEquals("Weekend Trip", item.chatName)
    }

    @Test
    fun `a returned payment never counts as group activity`() {
        val item = map(MessageMetadata.DirectlySentCrypto(userId = knownUserId, returnedPayment = returned), "Weekend Trip")
        assertNull(item.chatId)
        assertNull(item.chatName)
    }

    @Test
    fun `a plain payment has no group and names the counterparty`() {
        val item = map(MessageMetadata.DirectlySentCrypto(userId = knownUserId), "Weekend Trip")
        assertNull(item.chatId)
        assertNull(item.chatName)
        assertEquals("Ella", item.counterpartyName)
    }

    @Test
    fun `an unknown counterparty has no name`() {
        val item = map(MessageMetadata.DirectlySentCrypto(userId = listOf<Byte>(0x7F)))
        assertNull(item.counterpartyName)
    }

    @Test
    fun `a group name never changes today's title or avatar`() {
        listOf(
            MessageMetadata.ReceivedCrypto(userId = knownUserId, boostedReaction = boost),
            MessageMetadata.DirectlySentCrypto(userId = knownUserId, requestedPayment = request),
        ).forEach { meta ->
            val named = map(meta, "Weekend Trip")
            val today = map(meta, chatName = null)
            assertEquals(today.title, named.title)
            assertEquals(today.avatar, named.avatar)
            assertEquals(today.signedAmountPrefix, named.signedAmountPrefix)
        }
    }

    // endregion

    // region secondary line

    private val now = Instant.parse("2026-11-12T15:00:00Z")

    @Test
    fun `activity from earlier today is Today`() {
        assertEquals(ActivityDay.Today, activityDay(now - 1.hours, now, TimeZone.UTC))
    }

    @Test
    fun `activity from the day before is Yesterday`() {
        assertEquals(ActivityDay.Yesterday, activityDay(now - 1.days, now, TimeZone.UTC))
    }

    @Test
    fun `older activity names its date`() {
        assertEquals(
            ActivityDay.OnDate(LocalDate(2026, 11, 3)),
            activityDay(Instant.parse("2026-11-03T10:00:00Z"), now, TimeZone.UTC),
        )
    }

    @Test
    fun `the day follows the viewer's time zone`() {
        val lateNight = Instant.parse("2026-11-12T03:00:00Z")
        val evening = Instant.parse("2026-11-11T23:00:00Z")
        assertEquals(ActivityDay.Yesterday, activityDay(evening, lateNight, TimeZone.UTC))
        assertEquals(ActivityDay.Today, activityDay(evening, lateNight, TimeZone.of("America/New_York")))
    }

    @Test
    fun `the secondary line joins the name and the day`() {
        assertEquals("Ella · Today", moneyActivitySubtitle(counterpartyName = "Ella", day = "Today"))
        assertEquals("Today", moneyActivitySubtitle(counterpartyName = null, day = "Today"))
        assertEquals("Today", moneyActivitySubtitle(counterpartyName = " ", day = "Today"))
    }

    // endregion

    // region row

    private val clicked = mutableListOf<TransactionListItem>()

    private fun setRow(item: TransactionListItem) {
        composeRule.setContent {
            FlipcashPreview {
                MoneyActivityRow(
                    item = item,
                    now = now,
                    timeZone = TimeZone.UTC,
                    onClick = { clicked += item },
                )
            }
        }
    }

    private fun groupRow(prefix: String = "+") =
        map(MessageMetadata.ReceivedCrypto(userId = knownUserId, boostedReaction = boost), "Weekend Trip")
            .copy(timestamp = now - 1.hours, signedAmountPrefix = prefix)

    @Test
    fun `a group row shows the group name, who and when, and the signed amount`() {
        setRow(groupRow())
        composeRule.onNodeWithText("Weekend Trip").assertExists()
        composeRule.onNodeWithText("Ella · Today").assertExists()
        composeRule.onNodeWithText("+$20.00").assertExists()
    }

    @Test
    fun `an outgoing row is signed negative`() {
        setRow(groupRow(prefix = "-"))
        composeRule.onNodeWithText("-$20.00").assertExists()
    }

    @Test
    fun `a row without a group name falls back to today's title`() {
        val item = map(MessageMetadata.ReceivedCrypto(userId = knownUserId, boostedReaction = boost), chatName = null)
            .copy(timestamp = now - 1.hours)
        setRow(item)
        composeRule.onNodeWithText(item.title).assertExists()
        composeRule.onNodeWithText("Weekend Trip").assertDoesNotExist()
    }

    @Test
    fun `tapping a row opens it`() {
        val item = groupRow()
        setRow(item)
        composeRule.onNodeWithText("Weekend Trip").performClick()
        assertEquals(listOf(item), clicked)
    }

    // endregion
}
