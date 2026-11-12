package com.flipcash.shared.transactionhistory

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.app.core.feed.ActivityFeedMessageWithToken
import com.flipcash.app.core.feed.MessageMetadata
import com.flipcash.app.core.feed.MessageState
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.services.models.BoostedReaction
import com.flipcash.services.models.ReturnedPayment
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.Emoji
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.shared.transactionhistory.internal.TransactionItemMapper
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.util.resources.FakeResourceHelper
import com.getcode.utils.hexEncodedString
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * A Money row titled by its group draws that group's avatar (node 13729:744), not the
 * counterparty's; every other row keeps today's avatar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MoneyActivityGroupAvatarTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val resources = FakeResourceHelper()
        .stub(R.string.title_activity_receivedFrom, "Received from %1\$s")
        .stub(R.string.title_activity_boostFrom, "Boost %1\$s from %2\$s")
        .stub(R.string.title_activity_boostReceived, "Boost %1\$s received")
    private val mapper = TransactionItemMapper(resources)

    private val ella: ID = listOf<Byte>(0x0A, 0x0B, 0x0C)
    private val profiles = mapOf(ella.hexEncodedString() to UserProfile.Empty.copy(displayName = "Ella"))

    private val groupChat = ChatId(ByteArray(32) { 9 })
    private val boost = BoostedReaction(chatId = groupChat, messageId = 7L, emoji = Emoji("🔥"))
    private val returned = ReturnedPayment(chatId = ChatId(ByteArray(32) { 4 }), messageId = 11L)
    private val picture = MediaItem(renditions = emptyList())
    private val now = Instant.parse("2026-11-12T15:00:00Z")

    private fun map(
        metadata: MessageMetadata,
        chatName: String? = null,
        chatPicture: MediaItem? = null,
    ): TransactionListItem = mapper.map(
        ActivityFeedMessageWithToken(
            message = ActivityFeedMessage(
                id = listOf<Byte>(1, 2, 3),
                text = "Received",
                amount = LocalFiat(
                    usdf = Fiat(20.0, CurrencyCode.USD),
                    nativeAmount = Fiat(20.0, CurrencyCode.USD),
                ),
                timestamp = now,
                state = MessageState.COMPLETED,
                metadata = metadata,
            ),
            token = null,
            chatName = chatName,
            chatPicture = chatPicture,
        ) to profiles
    )

    private fun setRow(item: TransactionListItem) {
        composeRule.setContent {
            FlipcashPreview {
                MoneyActivityRow(item = item, now = now, timeZone = TimeZone.UTC)
            }
        }
    }

    @Test
    fun `a named group carries its picture`() {
        val item = map(MessageMetadata.ReceivedCrypto(userId = ella, boostedReaction = boost), "Weekend Trip", picture)
        assertEquals(picture, item.chatPicture)
    }

    @Test
    fun `a group that can't be named carries no picture`() {
        val item = map(MessageMetadata.ReceivedCrypto(userId = ella, boostedReaction = boost), null, picture)
        assertNull(item.chatPicture)
    }

    @Test
    fun `a returned payment carries no group picture`() {
        val item = map(MessageMetadata.ReceivedCrypto(userId = ella, returnedPayment = returned), "Ella", picture)
        assertNull(item.chatPicture)
    }

    @Test
    fun `a group row draws the group's avatar, not the counterparty's`() {
        setRow(map(MessageMetadata.ReceivedCrypto(userId = ella, boostedReaction = boost), "Weekend Trip"))
        composeRule.onNodeWithText("WT").assertExists()
        composeRule.onNodeWithText("E").assertDoesNotExist()
    }

    @Test
    fun `a row without a group keeps the counterparty's avatar`() {
        setRow(map(MessageMetadata.ReceivedCrypto(userId = ella)))
        composeRule.onNodeWithText("E").assertExists()
        composeRule.onNodeWithText("WT").assertDoesNotExist()
    }
}
