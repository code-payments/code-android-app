package com.flipcash.app.notifications

import android.app.Application
import androidx.core.app.NotificationCompat
import org.robolectric.RuntimeEnvironment
import com.flipcash.services.models.chat.ChatId
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Android Auto reads a messaging notification's actions by semantic tag, and won't launch the app
 * to run them. Both have to survive into the built [android.app.Notification], which is what the
 * car reads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatNotificationActionsTest {

    private val context: Application = RuntimeEnvironment.getApplication()
    private val chatId = ChatId("00112233445566778899aabbccddeeff")

    @Test
    fun `reply is tagged as a reply with one remote input and no UI`() {
        val action = buildChatReplyAction(context, chatId, notificationId = 7)

        assertEquals(NotificationCompat.Action.SEMANTIC_ACTION_REPLY, action.semanticAction)
        assertFalse(action.showsUserInterface)
        assertEquals(1, action.remoteInputs?.size)
        assertEquals(NotificationActionReceiver.KEY_TEXT_REPLY, action.remoteInputs?.single()?.resultKey)
    }

    @Test
    fun `mark as read is tagged as mark-as-read with no UI`() {
        val action = buildChatMarkAsReadAction(context, chatId)

        assertEquals(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ, action.semanticAction)
        assertFalse(action.showsUserInterface)
    }

    @Test
    fun `the built notification keeps both tags`() {
        val notification = NotificationCompat.Builder(context, "chat")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(buildChatReplyAction(context, chatId, notificationId = 7))
            .addAction(buildChatMarkAsReadAction(context, chatId))
            .build()

        val reply = assertNotNull(NotificationCompat.getAction(notification, 0))
        val markRead = assertNotNull(NotificationCompat.getAction(notification, 1))
        assertEquals(NotificationCompat.Action.SEMANTIC_ACTION_REPLY, reply.semanticAction)
        assertEquals(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ, markRead.semanticAction)
        assertFalse(reply.showsUserInterface)
        assertFalse(markRead.showsUserInterface)
    }
}
