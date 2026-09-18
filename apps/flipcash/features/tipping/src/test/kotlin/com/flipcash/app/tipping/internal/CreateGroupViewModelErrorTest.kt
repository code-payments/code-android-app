package com.flipcash.app.tipping.internal

import android.net.Uri
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.flipcash.app.blob.BlobStorageCoordinator
import com.flipcash.app.blob.ImageUploadPreparer
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.tipping.R
import com.flipcash.services.models.ModerationResult
import com.flipcash.services.models.StartChatError
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.models.chat.StartChatParameters
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.ContentReader
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * What the create call does with the answer it gets — the five non-OK arms of
 * `StartChatResponse.Result`, and the attempt that has to outlive a failure.
 *
 * [CreateGroupStateTest] pins what the form decides before the call and [GroupCreateAttemptTest]
 * pins the attempt in isolation; this is the seam between them, where the key the attempt holds has
 * to reach the coordinator and a retry has to be recognisable as the same attempt rather than a
 * second group.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateGroupViewModelErrorTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val resources = FakeResourceHelper(R::class.java)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true)
    private val blobStorage = mockk<BlobStorageCoordinator>(relaxed = true)
    private val imagePreparer = mockk<ImageUploadPreparer>(relaxed = true)
    private val contentReader = mockk<ContentReader>(relaxed = true)

    private lateinit var dispatchers: TestDispatchers

    private val badBoys = Mint(ByteArray(32) { 1 }.toList())

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

    private fun createViewModel() = CreateGroupViewModel(
        exchange = exchange,
        tokenCoordinator = tokenCoordinator,
        dispatchers = dispatchers,
        chatCoordinator = chatCoordinator,
        blobStorage = blobStorage,
        imagePreparer = imagePreparer,
        contentReader = contentReader,
        resources = resources,
    )

    private fun token(seed: Byte, symbol: String) = MintMetadata(
        address = Mint(ByteArray(32) { seed }.toList()),
        decimals = 6,
        name = symbol,
        symbol = symbol,
        createdAt = null,
        description = "",
        imageUrl = "",
        vmMetadata = VmMetadata(
            vm = PublicKey.fromBase58("11111111111111111111111111111111"),
            authority = PublicKey.fromBase58("11111111111111111111111111111111"),
            lockDurationInDays = 21,
        ),
        launchpadMetadata = null,
        billCustomizations = null,
        socialLinks = emptyList(),
        holderMetrics = HolderMetrics.None,
    )

    /** A draft that passes every pre-call check, so the only thing left to decide is the answer. */
    private fun CreateGroupViewModel.completeDraft() {
        stateFlow.value.titleFieldState.setTextAndPlaceCursorAtEnd("Ballers")
        dispatchEvent(
            CreateGroupViewModel.Event.OnBalancesChanged(
                listOf(TokenWithBalance(token = token(1, "BadBoys"), balance = Fiat(250.0)))
            )
        )
        dispatchEvent(CreateGroupViewModel.Event.OnMintSelected(badBoys))
        dispatchEvent(CreateGroupViewModel.Event.OnAmountSelected(Fiat(100)))
    }

    private fun created() = ChatMetadata(
        chatId = ChatId(ByteArray(16) { 3 }.toList()),
        type = ChatType.GROUP,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochSeconds(1_000),
        title = "Ballers",
        latestEventSequence = 0,
    )

    private fun failing(cause: Throwable) {
        coEvery { chatCoordinator.create(any(), any()) } returns Result.failure(cause)
    }

    private fun titles() = BottomBarManager.messages.value.map { it.title }

    /**
     * The client checked the balance before the call, so a server refusal on the same ground is a
     * race the check lost — not a mistake in the form. It reads as the pre-call refusal does.
     */
    @Test
    fun `a server rules refusal reads the same as the pre-call check`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            val vm = createViewModel()
            vm.completeDraft()
            failing(StartChatError.RulesNotSatisfied())

            vm.dispatchEvent(CreateGroupViewModel.Event.CreateRequested)
            advanceUntilIdle()

            assertTrue(titles().contains("error_title_groupRuleNotSelfSatisfied"))
            // The form is usable again: a refusal is not a spinner the user has to back out of.
            assertTrue(vm.stateFlow.value.processingState.isIdle)
            assertNull(vm.stateFlow.value.created)
        }

    @Test
    fun `a moderated title is reported with the category the server flagged`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            val vm = createViewModel()
            vm.completeDraft()
            failing(
                StartChatError.TitleModerated(ModerationResult.FlaggedCategory.NSFW)
            )

            vm.dispatchEvent(CreateGroupViewModel.Event.CreateRequested)
            advanceUntilIdle()

            assertTrue(titles().contains("error_title_groupTitleNotAllowed"))
            assertTrue(
                BottomBarManager.messages.value.any {
                    it.subtitle == "error_description_profileNameNotAllowedFlaggedNsfw"
                }
            )
        }

    @Test
    fun `invalid rules and a denial each say which it was`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            val vm = createViewModel()
            vm.completeDraft()

            failing(StartChatError.InvalidRules())
            vm.dispatchEvent(CreateGroupViewModel.Event.CreateRequested)
            advanceUntilIdle()
            assertTrue(titles().contains("error_title_groupRulesInvalid"))

            BottomBarManager.clear()
            failing(StartChatError.Denied())
            vm.dispatchEvent(CreateGroupViewModel.Event.CreateRequested)
            advanceUntilIdle()
            assertTrue(titles().contains("error_title_groupCreateDenied"))
        }

    @Test
    fun `an unrecognised failure falls back to the generic refusal`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            val vm = createViewModel()
            vm.completeDraft()
            failing(Throwable("unreachable"))

            vm.dispatchEvent(CreateGroupViewModel.Event.CreateRequested)
            advanceUntilIdle()

            assertTrue(titles().contains("error_title_groupCreateFailed"))
        }

    /**
     * Storage accepted the blob and `StartChat` refused it, so the picture is what has to change —
     * and dropping it is also what lets the next tap upload a different one rather than resend the
     * blob the server just rejected.
     */
    @Test
    fun `a rejected picture is dropped from the draft`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            val vm = createViewModel()
            vm.completeDraft()

            val uri = mockk<Uri>(relaxed = true)
            every { contentReader.readBytes(uri) } returns ByteArray(8)
            coEvery { blobStorage.upload(any(), any()) } returns
                Result.success(BlobId(ByteArray(32) { 5 }))
            vm.dispatchEvent(CreateGroupViewModel.Event.OnImageCached(uri, "image/jpeg"))
            failing(StartChatError.PictureBlobNotAccepted())

            vm.dispatchEvent(CreateGroupViewModel.Event.CreateRequested)
            advanceUntilIdle()

            assertTrue(titles().contains("error_title_imageNotAllowed"))
            assertNull(vm.stateFlow.value.image.dataOrNull)
            coVerify { contentReader.removeFromCache(uri) }
        }

    /**
     * The contract's guarantee, and the reason the key is minted at the tap: resending it returns
     * the chat the first attempt created instead of making a second one. So a retry has to carry
     * the key the first call carried, and the picture it already uploaded.
     */
    @Test
    fun `a retry carries the first attempt's key and its uploaded picture`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            val vm = createViewModel()
            vm.completeDraft()

            val uri = mockk<Uri>(relaxed = true)
            val blob = BlobId(ByteArray(32) { 5 })
            every { contentReader.readBytes(uri) } returns ByteArray(8)
            coEvery { blobStorage.upload(any(), any()) } returns Result.success(blob)
            vm.dispatchEvent(CreateGroupViewModel.Event.OnImageCached(uri, "image/jpeg"))

            val keys = mutableListOf<IdempotencyKey>()
            val parameters = mutableListOf<StartChatParameters>()
            coEvery {
                chatCoordinator.create(capture(parameters), capture(keys))
            } returns Result.failure(Throwable("response lost"))

            vm.dispatchEvent(CreateGroupViewModel.Event.CreateRequested)
            advanceUntilIdle()
            vm.dispatchEvent(CreateGroupViewModel.Event.CreateRequested)
            advanceUntilIdle()

            assertEquals(2, keys.size)
            assertContentEquals(keys[0].bytes, keys[1].bytes)
            // Uploaded once and remembered: a second upload would produce a second blob, and the
            // retry would then be a different request wearing the same key.
            coVerify(exactly = 1) { blobStorage.upload(any(), any()) }
            assertEquals(blob, (parameters[1] as StartChatParameters.Group).picture)
        }

    /**
     * The chat exists, so the key has done its job. The next Create is a different group and must
     * not be answered with this one.
     */
    @Test
    fun `a created chat ends the attempt`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        val vm = createViewModel()
        vm.completeDraft()

        val keys = mutableListOf<IdempotencyKey>()
        coEvery { chatCoordinator.create(any(), capture(keys)) } returns Result.success(created())

        vm.dispatchEvent(CreateGroupViewModel.Event.CreateRequested)
        advanceUntilIdle()
        assertEquals(created(), vm.stateFlow.value.created)

        vm.dispatchEvent(CreateGroupViewModel.Event.CreateRequested)
        advanceUntilIdle()

        assertEquals(2, keys.size)
        assertTrue(!keys[0].bytes.contentEquals(keys[1].bytes))
    }
}
