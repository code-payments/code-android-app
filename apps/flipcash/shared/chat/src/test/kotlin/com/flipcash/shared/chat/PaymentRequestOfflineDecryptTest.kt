package com.flipcash.shared.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.sources.GroupKeyEnvelopeDataSource
import com.flipcash.app.persistence.sources.PaymentRequestDataSource
import com.flipcash.services.chat.ChatKeySource
import com.flipcash.services.chat.FakeChatCipher
import com.flipcash.services.chat.FakeInviteCipher
import com.flipcash.services.chat.GroupContentCrypto
import com.flipcash.services.chat.GroupKeyEnvelopes
import com.flipcash.services.chat.WrongKeyChats
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.PaymentRequestController
import com.flipcash.services.models.GetKeyEnvelopeError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.KeyEnvelope
import com.flipcash.services.models.chat.StoredKeyEnvelope
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestPage
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.Share
import com.flipcash.services.models.paymentrequest.Split
import com.getcode.chatcipher.EncryptedPayload
import com.getcode.ed25519kmp.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

/**
 * A private group's payment request text opens offline after a restart: the stored request still
 * holds the sealed text, and the stored key envelope opens the chat key with no network. Real
 * [GroupContentCrypto] and [GroupKeyEnvelopes] over the real data sources.
 */
@RunWith(RobolectricTestRunner::class)
class PaymentRequestOfflineDecryptTest {

    private val chatId = ChatId(ByteArray(32) { 3 })
    private val requestId = List<Byte>(16) { 9 }
    private val creator: ID = List(16) { 1 }
    private val ownKeys = KeyPair(publicKey = ByteArray(32) { 11 }, privateKey = ByteArray(64) { 12 })
    private val groupKey = ByteArray(32) { 5 }
    private val keySource = object : ChatKeySource {
        override fun ownKeyPair() = ownKeys
        override fun ownUserId() = creator
        override suspend fun peerPublicKey(userId: ID): Result<ByteArray> = Result.failure(IOException("offline"))
    }
    private val paymentStore = PaymentRequestDataSource()
    private val envelopeStore = GroupKeyEnvelopeDataSource()

    // PaymentRequestText { title = 1, description = 2 }, encoded by hand.
    private fun textProto(title: String, description: String): ByteArray =
        byteArrayOf(0x0A, title.length.toByte()) + title.toByteArray() +
            byteArrayOf(0x12, description.length.toByte()) + description.toByteArray()

    private val sealedText = FakeChatCipher.encryptPaymentRequestText(
        textProto("Dinner", "Tacos"), groupKey, chatId.bytes, creator.toByteArray(),
    ).let { PaymentRequestText.Encrypted(PaymentRequestText.SCHEME_CHAT_KEY_XCHACHA20POLY1305, it.nonce, it.ciphertext) }

    @Before
    fun setUp() {
        FlipcashDatabase.init(ApplicationProvider.getApplicationContext<Context>(), "payment-request-offline-decrypt-test")
        runBlocking { paymentStore.clear(); envelopeStore.clear() }
    }

    @After
    fun tearDown() {
        FlipcashDatabase.closeDb()
    }

    private fun request() = PaymentRequest(
        id = requestId,
        creator = creator,
        createdAt = Instant.fromEpochSeconds(0),
        status = PaymentRequestStatus.Open,
        closedAt = null,
        version = 1,
        kind = PaymentRequest.Kind.GroupSplit(
            chatId = chatId,
            split = Split(
                text = sealedText,
                currency = CurrencyCode.USD,
                endsAt = Instant.fromEpochSeconds(4_000_000_000),
                shares = listOf(Share(List(16) { 2 }, Fiat(quarks = 10_000_000, currencyCode = CurrencyCode.USD), null)),
            ),
        ),
    )

    /** One app launch: its own in-memory caches over the shared databases. */
    private fun launch(online: Boolean): PaymentRequests {
        val chats = mockk<ChatController>()
        if (online) {
            val self = FakeChatCipher.wrapGroupKey(ownKeys, ownKeys.publicKey, chatId.bytes, groupKey)
            coEvery { chats.getKeyEnvelope(chatId) } returns Result.success(
                StoredKeyEnvelope(KeyEnvelope(KeyEnvelope.SCHEME_X25519_XCHACHA20POLY1305, self.nonce, self.ciphertext), wrappedBy = null),
            )
        } else {
            coEvery { chats.getKeyEnvelope(chatId) } returns Result.failure(GetKeyEnvelopeError.Other(IOException("offline")))
        }
        val keys = GroupKeyEnvelopes(FakeChatCipher, FakeInviteCipher(), keySource, chats, WrongKeyChats(), envelopeStore)
        val crypto = GroupContentCrypto(FakeChatCipher, keys)
        val controller = mockk<PaymentRequestController>()
        coEvery { controller.getChatPaymentRequests(any(), any(), any()) } returns
            if (online) Result.success(PaymentRequestPage(listOf(request()), null, false))
            else Result.failure(IOException("offline"))
        return PaymentRequests(controller, crypto, paymentStore)
    }

    @Test
    fun `encrypted text opens offline after a restart`() = runBlocking {
        launch(online = true).loadChat(chatId).getOrThrow()

        val restarted = launch(online = false)
        val shown = restarted.inChat(chatId).first().single()

        assertEquals(PaymentRequestText.Plain("Dinner", "Tacos"), shown.split?.text)
        assertIs<PaymentRequestText.Encrypted>(paymentStore.find(requestId)?.split?.text)
        Unit
    }

    @Test
    fun `without a stored envelope the text waits offline`() = runBlocking {
        launch(online = true).loadChat(chatId).getOrThrow()
        envelopeStore.clear()

        val shown = launch(online = false).inChat(chatId).first().single()

        assertIs<PaymentRequestText.Encrypted>(shown.split?.text)
        Unit
    }
}
