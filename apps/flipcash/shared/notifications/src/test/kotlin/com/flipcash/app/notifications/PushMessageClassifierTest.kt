package com.flipcash.app.notifications

import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.services.models.PushChatMetadata
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The two ways a message breaks through an archived chat's silence, and the default when the device cannot tell. Runs under Robolectric because
 * mention detection reads `android.util.Patterns` for the links it must skip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PushMessageClassifierTest {

    private val chatId = ChatId("aabbccdd")
    private val selfId = listOf<Byte>(1, 2, 3)
    private val otherId = listOf<Byte>(4, 5, 6)

    private val messages = mockk<ChatMessageDataSource>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true).also {
        every { it.accountId } returns selfId
        every { it.profile } returns UserProfile.Empty.copy(username = "Bmc")
    }
    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true)
    private val classifier = PushMessageClassifier(userManager, messages, chatCoordinator)

    private val sealed = MessageContent.Encrypted(scheme = 1, nonce = byteArrayOf(1), ciphertext = byteArrayOf(2))

    private fun message(vararg content: MessageContent, id: Long = 10) = ChatMessage(
        messageId = id,
        senderId = otherId,
        content = content.toList(),
        timestamp = Instant.fromEpochSeconds(1),
        unreadSeq = 1,
    )

    private fun metadata(message: ChatMessage?, messageId: Long? = null) = PushChatMetadata(
        sendingUserId = otherId,
        chatType = ChatType.TIP_DM,
        message = message,
        messageId = messageId,
    )

    @Test
    fun `a mention of the viewer is recognised whatever its case`() = runTest {
        val result = classifier.classify(chatId, metadata(message(MessageContent.Text("hey @BMC look"))))
        assertEquals(PushClassification(mentionsViewer = true, repliesToViewer = false), result)
    }

    @Test
    fun `a mention of someone else is not a mention of the viewer`() = runTest {
        val result = classifier.classify(chatId, metadata(message(MessageContent.Text("hey @ada"))))
        assertEquals(PushClassification.None, result)
    }

    @Test
    fun `an email address is not a mention`() = runTest {
        val result = classifier.classify(chatId, metadata(message(MessageContent.Text("write bmc@bmc.dev"))))
        assertEquals(PushClassification.None, result)
    }

    @Test
    fun `a mention in a media caption is recognised`() = runTest {
        val media = MessageContent.Media(items = emptyList(), caption = MessageContent.Text("for @bmc"))
        val result = classifier.classify(chatId, metadata(message(media)))
        assertEquals(PushClassification(mentionsViewer = true, repliesToViewer = false), result)
    }

    @Test
    fun `a reply to the viewer's own message is a reply to the viewer`() = runTest {
        coEvery { messages.getMessage(chatId, 7) } returns message(MessageContent.Text("mine"), id = 7).copy(senderId = selfId)
        val reply = message(MessageContent.Reply(repliedMessageId = 7, content = listOf(MessageContent.Text("agreed"))))
        val result = classifier.classify(chatId, metadata(reply))
        assertEquals(PushClassification(mentionsViewer = false, repliesToViewer = true), result)
    }

    @Test
    fun `a reply to someone else's message is not a reply to the viewer`() = runTest {
        coEvery { messages.getMessage(chatId, 7) } returns message(MessageContent.Text("theirs"), id = 7)
        val reply = message(MessageContent.Reply(repliedMessageId = 7, content = listOf(MessageContent.Text("agreed"))))
        assertEquals(PushClassification.None, classifier.classify(chatId, metadata(reply)))
    }

    @Test
    fun `a reply whose target is not stored locally counts as not addressed to the viewer`() = runTest {
        coEvery { messages.getMessage(chatId, 7) } returns null
        val reply = message(MessageContent.Reply(repliedMessageId = 7, content = listOf(MessageContent.Text("agreed"))))
        assertEquals(PushClassification.None, classifier.classify(chatId, metadata(reply)))
    }

    @Test
    fun `no message text at all is unknown and so false`() = runTest {
        assertEquals(PushClassification.None, classifier.classify(chatId, metadata(message = null, messageId = null)))
        val cash = MessageContent.Cash(intentId = listOf<Byte>(9), amount = Fiat(quarks = 100L), mint = Mint.usdc)
        assertEquals(PushClassification.None, classifier.classify(chatId, metadata(message(cash))))
    }

    @Test
    fun `a message that is only stored, not inlined, is read from the database`() = runTest {
        coEvery { messages.getMessage(chatId, 10) } returns message(MessageContent.Text("hi @bmc"))
        val result = classifier.classify(chatId, metadata(message = null, messageId = 10))
        assertEquals(PushClassification(mentionsViewer = true, repliesToViewer = false), result)
    }

    @Test
    fun `an encrypted message is opened before it is read for a mention`() = runTest {
        val pushed = message(sealed)
        coEvery { chatCoordinator.openPushedChatMessage(chatId, pushed, null) } returns
            message(MessageContent.Text("hey @bmc"))
        val result = classifier.classify(chatId, metadata(pushed))
        assertEquals(PushClassification(mentionsViewer = true, repliesToViewer = false), result)
    }

    @Test
    fun `an encrypted reply to the viewer is a reply to the viewer once opened`() = runTest {
        val pushed = message(sealed)
        coEvery { chatCoordinator.openPushedChatMessage(chatId, pushed, null) } returns
            message(MessageContent.Reply(repliedMessageId = 7, content = listOf(MessageContent.Text("agreed"))))
        coEvery { messages.getMessage(chatId, 7) } returns message(MessageContent.Text("mine"), id = 7).copy(senderId = selfId)
        val result = classifier.classify(chatId, metadata(pushed))
        assertEquals(PushClassification(mentionsViewer = false, repliesToViewer = true), result)
    }

    @Test
    fun `an encrypted message that cannot be opened is not addressed to the viewer`() = runTest {
        val pushed = message(sealed)
        coEvery { chatCoordinator.openPushedChatMessage(chatId, pushed, null) } returns null
        assertEquals(PushClassification.None, classifier.classify(chatId, metadata(pushed)))
    }

    @Test
    fun `an encrypted message whose opening throws is not addressed to the viewer`() = runTest {
        val pushed = message(sealed)
        coEvery { chatCoordinator.openPushedChatMessage(chatId, pushed, null) } throws
            IllegalStateException("decryption failed")
        assertEquals(PushClassification.None, classifier.classify(chatId, metadata(pushed)))
    }

    @Test
    fun `a viewer with no username cannot be mentioned`() = runTest {
        every { userManager.profile } returns UserProfile.Empty
        val result = classifier.classify(chatId, metadata(message(MessageContent.Text("hey @bmc"))))
        assertEquals(PushClassification.None, result)
    }
}
