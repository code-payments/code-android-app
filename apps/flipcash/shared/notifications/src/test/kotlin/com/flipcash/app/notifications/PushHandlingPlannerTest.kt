package com.flipcash.app.notifications

import com.flipcash.services.models.NavigationTrigger
import com.flipcash.services.models.NotificationCategory
import com.flipcash.services.models.NotificationPayload
import com.flipcash.services.models.PushChatMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.getcode.solana.keys.Mint
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PushHandlingPlannerTest {

    private fun payload(
        navigation: NavigationTrigger? = null,
        category: NotificationCategory = NotificationCategory.DEFAULT,
        chatMetadata: PushChatMetadata? = null,
    ) = NotificationPayload(
        navigation = navigation,
        category = category,
        chatMetadata = chatMetadata,
    )

    private fun inlinedMessage(messageId: Long = 42L, eventSequence: Long = 7L) = ChatMessage(
        messageId = messageId,
        senderId = null,
        content = listOf(MessageContent.Text("hello")),
        timestamp = Instant.fromEpochSeconds(1_757_000_000),
        unreadSeq = 1L,
        eventSequence = eventSequence,
    )

    private fun chatMetadata(message: ChatMessage?) = PushChatMetadata(
        sendingUserId = null,
        chatType = ChatType.CONTACT_DM,
        message = message,
    )

    // region A titled push

    @Test
    fun `currency info push updates tokens and posts`() {
        val p = payload(navigation = NavigationTrigger.CurrencyInfo(mint = TEST_MINT))
        val actions = planPushHandling("Title", "Body", p)
        assertTrue(PushAction.UpdateTokens in actions)
        assertTrue(actions.last() is PushAction.PostNotification)
    }

    @Test
    fun `contact join push refreshes feed and syncs contacts`() {
        val p = payload(category = NotificationCategory.CONTACT_JOIN)
        val actions = planPushHandling("Title", null, p)
        assertTrue(PushAction.RefreshFeed in actions)
        assertTrue(PushAction.SyncContacts in actions)
    }

    @Test
    fun `chat push refreshes feed then loads that chat`() {
        val chatId = ChatId("aa07")
        val p = payload(navigation = NavigationTrigger.Chat.ById(chatId))
        val actions = planPushHandling("Title", "Body", p)
        assertEquals(
            listOf(PushAction.RefreshFeed, PushAction.LoadMessages(chatId)),
            actions.filterNot { it is PushAction.PostNotification },
        )
    }

    @Test
    fun `a chat push carrying its message applies it instead of fetching`() {
        val chatId = ChatId("aa08")
        val message = inlinedMessage()
        val p = payload(
            navigation = NavigationTrigger.Chat.ById(chatId),
            chatMetadata = chatMetadata(message),
        )
        val actions = planPushHandling("Title", "Body", p)
        assertEquals(
            listOf(PushAction.RefreshFeed, PushAction.ApplyMessage(chatId, message)),
            actions.filterNot { it is PushAction.PostNotification },
        )
    }

    @Test
    fun `chat metadata without a message falls back to fetching`() {
        val chatId = ChatId("aa09")
        val p = payload(
            navigation = NavigationTrigger.Chat.ById(chatId),
            chatMetadata = chatMetadata(message = null),
        )
        val actions = planPushHandling("Title", "Body", p)
        assertEquals(
            listOf(PushAction.RefreshFeed, PushAction.LoadMessages(chatId)),
            actions.filterNot { it is PushAction.PostNotification },
        )
    }

    @Test
    fun `an inlined message on a push that names no chat plans nothing to apply`() {
        val p = payload(chatMetadata = chatMetadata(inlinedMessage()))
        val actions = planPushHandling("Title", null, p)
        assertEquals(listOf(PushAction.PostNotification("Title", null, p)), actions)
    }

    @Test
    fun `a silent chat push with an inlined message needs no network`() {
        val chatId = ChatId("aa10")
        val message = inlinedMessage()
        val p = payload(
            navigation = NavigationTrigger.Chat.ById(chatId),
            chatMetadata = chatMetadata(message),
        )
        val actions = planPushHandling(null, null, p)
        assertTrue(PushAction.ApplyMessage(chatId, message) in actions)
        assertTrue(actions.none { it is PushAction.LoadMessages })
    }

    @Test
    fun `a contact join that also names a chat refreshes the feed once`() {
        val chatId = ChatId("aa11")
        val p = payload(
            navigation = NavigationTrigger.Chat.ById(chatId),
            category = NotificationCategory.CONTACT_JOIN,
        )
        val actions = planPushHandling("Title", null, p)
        assertEquals(
            listOf(PushAction.RefreshFeed, PushAction.SyncContacts, PushAction.LoadMessages(chatId)),
            actions.filterNot { it is PushAction.PostNotification },
        )
    }

    @Test
    fun `a category with no sync of its own plans nothing`() {
        val p = payload(category = NotificationCategory.GAIN)
        val actions = planPushHandling("Title", null, p)
        assertEquals(listOf(PushAction.PostNotification("Title", null, p)), actions)
    }

    @Test
    fun `push with no payload still posts the notification`() {
        val actions = planPushHandling("Title", "Body", payload = null)
        assertEquals(
            listOf(PushAction.PostNotification("Title", "Body", null)),
            actions,
        )
    }

    @Test
    fun `post notification is always last so sync starts first`() {
        val p = payload(navigation = NavigationTrigger.Chat.ById(ChatId("0c")))
        val actions = planPushHandling("Title", "Body", p)
        assertTrue(actions.last() is PushAction.PostNotification)
    }

    // endregion

    // region A data-only push

    @Test
    fun `a data only push syncs chat without posting`() {
        val chatId = ChatId("aa09")
        val actions = planPushHandling(
            title = null,
            body = null,
            payload = payload(navigation = NavigationTrigger.Chat.ById(chatId)),
        )
        assertEquals(listOf(PushAction.RefreshFeed, PushAction.LoadMessages(chatId)), actions)
    }

    @Test
    fun `a data only push never posts a notification`() {
        val actions = planPushHandling(
            title = null,
            body = null,
            payload = payload(navigation = NavigationTrigger.CurrencyInfo(mint = TEST_MINT)),
        )
        assertTrue(actions.none { it is PushAction.PostNotification })
        assertEquals(listOf(PushAction.UpdateTokens), actions)
    }

    @Test
    fun `a data only push with no payload does nothing`() {
        val actions = planPushHandling(null, null, payload = null)
        assertEquals(emptyList(), actions)
    }

    @Test
    fun `a title changes only the notification, not the sync plan`() {
        val p = payload(navigation = NavigationTrigger.Chat.ById(ChatId("0c")))
        assertEquals(
            planPushHandling(null, null, p),
            planPushHandling("Title", "Body", p).filterNot { it is PushAction.PostNotification },
        )
    }

    // endregion

    companion object {
        private val TEST_MINT = Mint.usdc
    }
}
