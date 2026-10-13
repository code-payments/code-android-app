package com.flipcash.app.tipping.internal

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.blob.BlobStorageCoordinator
import com.flipcash.app.blob.ImageUploadPreparer
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.tipping.R
import com.flipcash.services.chat.GroupKeyEnvelopes
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.StartChatParameters
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.util.resources.ContentReader
import com.getcode.util.resources.FakeResourceHelper
import com.getcode.view.SuccessHoldDuration
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Creating a private group (node 11203:65): `StartChat(private_group)`, then the creator's key
 * envelope, with only the second step retried.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreatePrivateGroupViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val exchange = mockk<Exchange>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true)
    private val blobStorage = mockk<BlobStorageCoordinator>(relaxed = true)
    private val groupKeys = mockk<GroupKeyEnvelopes>()

    @Before
    fun setUp() {
        BottomBarManager.clear()
        every { exchange.observePreferredRate() } returns emptyFlow()
        every { tokenCoordinator.tokenBalances } returns flowOf(emptyList())
        every { blobStorage.policy } returns flowOf(null)
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
    }

    private fun TestDispatchers.viewModel() = CreateGroupViewModel(
        isPrivate = true,
        exchange = exchange,
        tokenCoordinator = tokenCoordinator,
        dispatchers = this,
        chatCoordinator = chatCoordinator,
        blobStorage = blobStorage,
        imagePreparer = mockk<ImageUploadPreparer>(relaxed = true),
        contentReader = mockk<ContentReader>(relaxed = true),
        resources = FakeResourceHelper(R::class.java),
        analytics = RecordingAnalytics(),
        groupKeys = groupKeys,
    )

    private val chat = ChatMetadata(
        chatId = ChatId(ByteArray(16) { 3 }.toList()),
        type = ChatType.GROUP,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochSeconds(1_000),
        title = "Ballers",
        latestEventSequence = 0,
    )

    @Test
    fun `a name alone enables Create`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = TestDispatchers(testScheduler).viewModel()
        assertFalse(vm.stateFlow.value.canCreate)

        vm.stateFlow.value.titleFieldState.setTextAndPlaceCursorAtEnd("Ballers")

        assertTrue(vm.stateFlow.value.canCreate)
        assertNull(vm.stateFlow.value.rules)
    }

    @Test
    fun `create starts a private group then stores the key`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = TestDispatchers(testScheduler).viewModel()
        vm.stateFlow.value.titleFieldState.setTextAndPlaceCursorAtEnd("Ballers")
        val parameters = mutableListOf<StartChatParameters>()
        coEvery { chatCoordinator.create(capture(parameters), any()) } returns Result.success(chat)
        coEvery { groupKeys.storeNewKey(chat.chatId) } returns Result.success(Unit)

        vm.dispatchEvent(CreateGroupViewModel.Event.CreateRequested)
        advanceTimeBy(SuccessHoldDuration)
        advanceUntilIdle()

        val sent = assertIs<StartChatParameters.PrivateGroup>(parameters.single())
        assertEquals("Ballers", sent.title)
        coVerify(exactly = 1) { groupKeys.storeNewKey(chat.chatId) }
        assertEquals(chat, vm.stateFlow.value.created)
    }

    @Test
    fun `a failed key is retried without starting a second chat`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = TestDispatchers(testScheduler).viewModel()
        vm.stateFlow.value.titleFieldState.setTextAndPlaceCursorAtEnd("Ballers")
        coEvery { chatCoordinator.create(any(), any()) } returns Result.success(chat)
        coEvery { groupKeys.storeNewKey(chat.chatId) } returns Result.failure(IllegalStateException())

        vm.dispatchEvent(CreateGroupViewModel.Event.CreateRequested)
        advanceUntilIdle()

        // The automatic retries ran out: the form is usable again and nothing was created.
        coVerify(exactly = 3) { groupKeys.storeNewKey(chat.chatId) }
        assertTrue(vm.stateFlow.value.processingState.isIdle)
        assertNull(vm.stateFlow.value.created)

        coEvery { groupKeys.storeNewKey(chat.chatId) } returns Result.success(Unit)
        vm.dispatchEvent(CreateGroupViewModel.Event.CreateRequested)
        advanceTimeBy(SuccessHoldDuration)
        advanceUntilIdle()

        coVerify(exactly = 1) { chatCoordinator.create(any(), any()) }
        assertEquals(chat, vm.stateFlow.value.created)
    }
}
