package com.flipcash.shared.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.init
import com.flipcash.app.persistence.sources.PaymentRequestDataSource
import com.flipcash.services.chat.GroupContentCrypto
import com.flipcash.services.controllers.PaymentRequestController
import com.flipcash.services.models.GetPaymentRequestError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.CollectionAmount
import com.flipcash.services.models.paymentrequest.PaymentCollection
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestDetails
import com.flipcash.services.models.paymentrequest.PaymentRequestPage
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * A fetch refused DENIED or NOT_FOUND marks the request missing, and its card draws as
 * unsupported. A refusal can come from a membership the server hasn't caught up with, as just
 * after joining, so the mark must not outlive the request turning up.
 */
@RunWith(RobolectricTestRunner::class)
class PaymentRequestsMissingTest {

    private val chatId = ChatId(ByteArray(32) { 3 })
    private val otherChat = ChatId(ByteArray(32) { 4 })
    private val id = List<Byte>(16) { 9 }
    private val creator = List<Byte>(16) { 1 }
    private val store = PaymentRequestDataSource()
    private val crypto = mockk<GroupContentCrypto>()
    private val controller = mockk<PaymentRequestController>()

    @Before
    fun setUp() {
        FlipcashDatabase.init(ApplicationProvider.getApplicationContext<Context>(), "payment-requests-missing-test")
        runBlocking { store.clear() }
        // The chat's sections don't list the request, as the OPEN section didn't on the device.
        coEvery { controller.getChatPaymentRequests(any(), any(), any()) } returns
            Result.success(PaymentRequestPage(emptyList(), null, false))
    }

    @After
    fun tearDown() {
        FlipcashDatabase.closeDb()
    }

    // A $50-each collection, as the iOS app made in the chat.
    private val collection = PaymentRequest(
        id = id,
        creator = creator,
        createdAt = Instant.fromEpochSeconds(0),
        status = PaymentRequestStatus.Open,
        closedAt = null,
        version = 1,
        kind = PaymentRequest.Kind.GroupCollection(
            chatId = chatId,
            collection = PaymentCollection(
                text = PaymentRequestText.Plain("", ""),
                currency = CurrencyCode.USD,
                endsAt = null,
                amount = CollectionAmount.Fixed(Fiat(quarks = 50_000_000, currencyCode = CurrencyCode.USD)),
            ),
        ),
    )

    private val found = Result.success(PaymentRequestDetails(collection, paymentIntentId = null))

    private fun requests() = PaymentRequests(controller, crypto, store)

    private fun PaymentRequests.card() =
        requestCardState(state.value[id], id in missing.value, chatId, creator, collections = true)

    @Test
    fun `a card refused before the viewer could see the request draws after the chat's next refetch`(): Unit = runBlocking {
        coEvery { controller.getPaymentRequest(id) } returnsMany listOf(Result.failure(GetPaymentRequestError.Denied()), found)
        val requests = requests()

        requests.fetchIfAbsent(id, chatId)
        assertEquals(RequestCardState.Unsupported, requests.card())

        requests.loadChat(chatId).getOrThrow()

        assertFalse(id in requests.missing.value)
        assertIs<RequestCardState.Ready>(requests.card())
    }

    @Test
    fun `a request still refused stays missing through the chat's refetch`(): Unit = runBlocking {
        coEvery { controller.getPaymentRequest(id) } returns Result.failure(GetPaymentRequestError.NotFound())
        val requests = requests()
        requests.fetchIfAbsent(id, chatId)

        // Every value the cards see while the chat refetches, so a card never flips to loading.
        val seen = mutableListOf<Set<List<Byte>>>()
        val watching = launch(Dispatchers.Unconfined) { requests.missing.toList(seen) }
        requests.loadChat(chatId).getOrThrow()
        watching.cancel()

        coVerify(exactly = 2) { controller.getPaymentRequest(id) }
        assertTrue(seen.isNotEmpty() && seen.all { id in it }, "missing was $seen")
        assertEquals(RequestCardState.Unsupported, requests.card())
    }

    @Test
    fun `a retry that fails on the network leaves the mark and the chat's refetch succeeds`(): Unit = runBlocking {
        coEvery { controller.getPaymentRequest(id) } returnsMany listOf(
            Result.failure(GetPaymentRequestError.Denied()),
            Result.failure(GetPaymentRequestError.Other(IllegalStateException("offline"))),
        )
        val requests = requests()
        requests.fetchIfAbsent(id, chatId)

        assertTrue(requests.loadChat(chatId).isSuccess)
        assertTrue(id in requests.missing.value)
    }

    @Test
    fun `a refusal is retried until a fetch succeeds`(): Unit = runBlocking {
        coEvery { controller.getPaymentRequest(id) } returnsMany listOf(
            Result.failure(GetPaymentRequestError.Denied()),
            Result.failure(GetPaymentRequestError.Denied()),
            found,
        )
        val requests = requests()
        requests.fetchIfAbsent(id, chatId)

        requests.loadChat(chatId).getOrThrow()
        assertEquals(RequestCardState.Unsupported, requests.card())
        requests.loadChat(chatId).getOrThrow()

        assertIs<RequestCardState.Ready>(requests.card())
    }

    @Test
    fun `only the chat the card was drawn in retries it`(): Unit = runBlocking {
        coEvery { controller.getPaymentRequest(id) } returns Result.failure(GetPaymentRequestError.Denied())
        val requests = requests()
        requests.fetchIfAbsent(id, chatId)

        requests.loadChat(otherChat).getOrThrow()

        coVerify(exactly = 1) { controller.getPaymentRequest(id) }
    }

    @Test
    fun `a fetch that names a chat is retried by that chat`(): Unit = runBlocking {
        coEvery { controller.getPaymentRequest(id) } returnsMany listOf(Result.failure(GetPaymentRequestError.Denied()), found)
        val requests = requests()
        // As a push for the chat's new request fetches it.
        requests.fetch(id, chatId)

        requests.loadChat(chatId).getOrThrow()

        assertIs<RequestCardState.Ready>(requests.card())
    }

    @Test
    fun `a fetch that names no chat is not retried`(): Unit = runBlocking {
        coEvery { controller.getPaymentRequest(id) } returns Result.failure(GetPaymentRequestError.Denied())
        val requests = requests()
        requests.fetch(id)

        requests.loadChat(chatId).getOrThrow()

        coVerify(exactly = 1) { controller.getPaymentRequest(id) }
        assertTrue(id in requests.missing.value)
    }

    @Test
    fun `a request that arrives by another path is no longer missing`(): Unit = runBlocking {
        coEvery { controller.getPaymentRequest(id) } returns Result.failure(GetPaymentRequestError.Denied())
        val requests = requests()
        requests.fetch(id)

        // As a stream `created` or a section listing delivers it.
        requests.ingest(listOf(collection))

        assertFalse(id in requests.missing.value)
        assertIs<RequestCardState.Ready>(requests.card())
    }

    @Test
    fun `a retry refused after the request arrived elsewhere is still retried by its chat`(): Unit = runBlocking {
        val retrying = CompletableDeferred<Unit>()
        val refused = CompletableDeferred<Result<PaymentRequestDetails>>()
        var calls = 0
        coEvery { controller.getPaymentRequest(id) } coAnswers {
            when (++calls) {
                1 -> Result.failure(GetPaymentRequestError.Denied())
                2 -> { retrying.complete(Unit); refused.await() }
                else -> found
            }
        }
        val requests = requests()
        requests.fetchIfAbsent(id, chatId)

        // The chat's retry is in flight when a stream `created` delivers the request, then the
        // retry comes back refused, which marks it again.
        val refetch = async { requests.loadChat(chatId).getOrThrow() }
        retrying.await()
        requests.ingest(listOf(collection))
        refused.complete(Result.failure(GetPaymentRequestError.Denied()))
        refetch.await()
        assertTrue(id in requests.missing.value)

        requests.loadChat(chatId).getOrThrow()

        assertIs<RequestCardState.Ready>(requests.card())
    }

    @Test
    fun `leaving the chat drops its marks`(): Unit = runBlocking {
        coEvery { controller.getPaymentRequest(id) } returns Result.failure(GetPaymentRequestError.Denied())
        val requests = requests()
        requests.fetchIfAbsent(id, chatId)

        requests.forget(chatId)

        assertFalse(id in requests.missing.value)
    }
}
