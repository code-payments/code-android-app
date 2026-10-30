package com.flipcash.shared.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.sources.PaymentRequestDataSource
import com.flipcash.services.chat.GroupContentCrypto
import com.flipcash.services.controllers.PaymentRequestController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaidShare
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestPage
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.PaymentRequestUpdate
import com.flipcash.services.models.paymentrequest.Share
import com.flipcash.services.models.paymentrequest.Split
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
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Requests are written through to the database and read back before the network answers, so
 * a restarted app (a second [PaymentRequests] over the same database) still has them offline.
 */
@RunWith(RobolectricTestRunner::class)
class PaymentRequestsStorageTest {

    private val chatId = ChatId(ByteArray(32) { 3 })
    private val id = List<Byte>(16) { 9 }
    private val alice = List<Byte>(16) { 2 }
    private val sealed = PaymentRequestText.Encrypted(
        PaymentRequestText.SCHEME_CHAT_KEY_XCHACHA20POLY1305, ByteArray(24) { 7 }, ByteArray(40) { 9 },
    )
    private val store = PaymentRequestDataSource()
    private val crypto = mockk<GroupContentCrypto>()

    @Before
    fun setUp() {
        FlipcashDatabase.init(ApplicationProvider.getApplicationContext<Context>(), "payment-requests-storage-test")
        runBlocking { store.clear() }
        coEvery { crypto.openPaymentRequestText(any(), any(), any()) } returns
            Result.success(PaymentRequestText.Plain("Dinner", "Tacos"))
    }

    @After
    fun tearDown() {
        FlipcashDatabase.closeDb()
    }

    private fun request(version: Long, status: PaymentRequestStatus = PaymentRequestStatus.Open) = PaymentRequest(
        id = id,
        creator = List(16) { 1 },
        createdAt = Instant.fromEpochSeconds(0),
        status = status,
        closedAt = null,
        version = version,
        kind = PaymentRequest.Kind.GroupSplit(
            chatId = chatId,
            split = Split(
                text = sealed,
                currency = CurrencyCode.USD,
                endsAt = Instant.fromEpochSeconds(4_000_000_000),
                shares = listOf(Share(alice, Fiat(quarks = 10_000_000, currencyCode = CurrencyCode.USD), null)),
            ),
        ),
    )

    private fun requests(network: Result<PaymentRequestPage>): PaymentRequests {
        val controller = mockk<PaymentRequestController>()
        coEvery { controller.getChatPaymentRequests(any(), any(), any()) } returns network
        return PaymentRequests(controller, crypto, store)
    }

    private fun page(vararg requests: PaymentRequest) = Result.success(PaymentRequestPage(requests.toList(), null, false))

    @Test
    fun `a failed refetch leaves the stored requests showing after a restart`() = runBlocking {
        requests(page(request(version = 3))).loadChat(chatId).getOrThrow()

        val restarted = requests(Result.failure(RuntimeException("offline")))
        assertTrue(restarted.loadChat(chatId).isFailure)

        val shown = restarted.inChat(chatId).first().single()
        assertEquals(3, shown.version)
        assertEquals(PaymentRequestText.Plain("Dinner", "Tacos"), shown.split?.text)
        assertEquals(shown, restarted.request(id).first())
    }

    @Test
    fun `stored text stays sealed`() = runBlocking {
        requests(page(request(version = 3))).loadChat(chatId).getOrThrow()

        val stored = store.find(id)!!
        assertIs<PaymentRequestText.Encrypted>(stored.split?.text)
        Unit
    }

    @Test
    fun `an applied update is written through at its new version`() = runBlocking {
        val live = requests(page(request(version = 3)))
        live.loadChat(chatId).getOrThrow()
        val paid = PaidShare(Instant.fromEpochSeconds(500), PaidShare.How.Payment)

        live.onUpdates(
            listOf(PaymentRequestUpdate(id, PaymentRequestUpdate.Kind.Updates(4, listOf(PaymentRequestUpdate.Change.SharePaid(alice, paid))))),
        )

        val stored = store.find(id)!!
        assertEquals(4, stored.version)
        assertEquals(paid, stored.split?.shareOf(alice)?.paid)
        assertIs<PaymentRequestText.Encrypted>(stored.split?.text)
        Unit
    }

    @Test
    fun `an ignored stale update writes nothing`() = runBlocking {
        val live = requests(page(request(version = 3)))
        live.loadChat(chatId).getOrThrow()

        live.onUpdates(listOf(PaymentRequestUpdate(id, PaymentRequestUpdate.Kind.Updates(2, emptyList()))))

        assertEquals(3, store.find(id)!!.version)
    }

    @Test
    fun `a stale fetch does not overwrite a newer stored request`() = runBlocking {
        requests(page(request(version = 5))).loadChat(chatId).getOrThrow()

        requests(page(request(version = 4))).loadChat(chatId).getOrThrow()

        assertEquals(5, store.find(id)!!.version)
    }

    @Test
    fun `clearing the store removes the requests`() = runBlocking {
        val live = requests(page(request(version = 3)))
        live.loadChat(chatId).getOrThrow()

        live.clearStored()

        assertEquals(null, store.find(id))
    }
}
