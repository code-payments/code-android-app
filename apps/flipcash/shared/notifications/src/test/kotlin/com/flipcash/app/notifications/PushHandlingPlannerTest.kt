package com.flipcash.app.notifications

import com.flipcash.services.models.NavigationTrigger
import com.flipcash.services.models.NotificationCategory
import com.flipcash.services.models.NotificationPayload
import com.flipcash.services.models.chat.ChatId
import com.getcode.solana.keys.Mint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PushHandlingPlannerTest {

    private fun payload(
        navigation: NavigationTrigger? = null,
        category: NotificationCategory = NotificationCategory.DEFAULT,
    ) = NotificationPayload(
        navigation = navigation,
        category = category,
    )

    // region Today's behaviour: a titleless push is dropped entirely

    @Test
    fun `no title yields no actions when silent sync is disabled`() {
        val actions = planPushHandling(
            title = null,
            body = "ignored",
            payload = payload(navigation = NavigationTrigger.CurrencyInfo(mint = TEST_MINT)),
            silentSyncEnabled = false,
        )
        assertEquals(emptyList(), actions)
    }

    @Test
    fun `no title drops chat sync too when silent sync is disabled`() {
        val actions = planPushHandling(
            title = null,
            body = null,
            payload = payload(navigation = NavigationTrigger.Chat.ById(ChatId("aa01"))),
            silentSyncEnabled = false,
        )
        assertEquals(emptyList(), actions)
    }

    // endregion

    // region Today's behaviour: a titled push

    @Test
    fun `currency info push updates tokens and posts`() {
        val p = payload(navigation = NavigationTrigger.CurrencyInfo(mint = TEST_MINT))
        val actions = planPushHandling("Title", "Body", p, silentSyncEnabled = false)
        assertTrue(PushAction.UpdateTokens in actions)
        assertTrue(actions.last() is PushAction.PostNotification)
    }

    @Test
    fun `contact join push refreshes feed and syncs contacts`() {
        val p = payload(category = NotificationCategory.CONTACT_JOIN)
        val actions = planPushHandling("Title", null, p, silentSyncEnabled = false)
        assertTrue(PushAction.RefreshFeed in actions)
        assertTrue(PushAction.SyncContacts in actions)
    }

    @Test
    fun `chat push refreshes feed then loads that chat`() {
        val chatId = ChatId("aa07")
        val p = payload(navigation = NavigationTrigger.Chat.ById(chatId))
        val actions = planPushHandling("Title", "Body", p, silentSyncEnabled = false)
        assertEquals(
            listOf(PushAction.RefreshFeed, PushAction.LoadMessages(chatId)),
            actions.filterNot { it is PushAction.PostNotification },
        )
    }

    @Test
    fun `push with no payload still posts the notification`() {
        val actions = planPushHandling("Title", "Body", payload = null, silentSyncEnabled = false)
        assertEquals(
            listOf(PushAction.PostNotification("Title", "Body", null)),
            actions,
        )
    }

    @Test
    fun `post notification is always last so sync starts first`() {
        val p = payload(navigation = NavigationTrigger.Chat.ById(ChatId("0c")))
        val actions = planPushHandling("Title", "Body", p, silentSyncEnabled = false)
        assertTrue(actions.last() is PushAction.PostNotification)
    }

    // endregion

    companion object {
        private val TEST_MINT = Mint.usdc
    }
}
