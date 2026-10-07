package com.flipcash.app.messenger.internal.screens.profile.edit

import android.net.Uri
import com.flipcash.app.blob.BlobStorageCoordinator
import com.flipcash.app.blob.ImageUploadPreparer
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.models.EditChatError
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.EditChatParameters
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ContentReader
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The cover editor: the iOS `EditGroupModelTests` cover cases, over Android's upload pipeline. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EditGroupCoverViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true)
    private val blobStorage = mockk<BlobStorageCoordinator>(relaxed = true)
    private val imagePreparer = mockk<ImageUploadPreparer>(relaxed = true)
    private val contentReader = mockk<ContentReader>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val chatId = ChatId(ByteArray(16) { 7 }.toList())
    private val blob = BlobId(ByteArray(32) { 5 })

    @Before
    fun setUp() {
        BottomBarManager.clear()
        every { blobStorage.policy } returns flowOf(null)
        coEvery { contentReader.readBytes(any()) } returns ByteArray(4)
    }

    @After
    fun tearDown() = BottomBarManager.clear()

    private fun viewModel() = EditGroupCoverViewModel(
        dispatchers = TestDispatcherProvider(mainCoroutineRule.dispatcher),
        chatCoordinator = chatCoordinator,
        blobStorage = blobStorage,
        imagePreparer = imagePreparer,
        contentReader = contentReader,
        resources = resources,
    ).apply {
        dispatchEvent(EditGroupCoverViewModel.Event.Initialize(chatId, cover = null))
    }

    private fun EditGroupCoverViewModel.pick() = dispatchEvent(
        EditGroupCoverViewModel.Event.OnImageCached(Uri.parse("file:///cover.jpg"), "image/jpeg")
    )

    @Test
    fun `save is shut until a cover is picked`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel()
        assertFalse(vm.stateFlow.value.canSubmit)

        vm.pick()
        advanceUntilIdle()

        assertTrue(vm.stateFlow.value.canSubmit)
    }

    @Test
    fun `a cover edit uploads once and sends the cover blob only`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { blobStorage.upload(any(), any()) } returns Result.success(blob)
        val sent = slot<EditChatParameters>()
        coEvery { chatCoordinator.editChat(any(), capture(sent)) } returns
            Result.success(mockk<ChatMetadata>(relaxed = true))

        val vm = viewModel()
        vm.pick()
        vm.dispatchEvent(EditGroupCoverViewModel.Event.SubmitCover)
        advanceUntilIdle()

        assertEquals(blob, sent.captured.coverPicture)
        assertNull(sent.captured.title)
        assertNull(sent.captured.picture)
        assertNull(sent.captured.description)
        coVerify(exactly = 1) { blobStorage.upload(any(), any()) }
        coVerify(exactly = 1) { chatCoordinator.editChat(chatId, any()) }
    }

    @Test
    fun `a cover the upload refused never reaches the edit`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { blobStorage.upload(any(), any()) } returns Result.failure(RuntimeException("refused"))

        val vm = viewModel()
        vm.pick()
        vm.dispatchEvent(EditGroupCoverViewModel.Event.SubmitCover)
        advanceUntilIdle()

        coVerify(exactly = 0) { chatCoordinator.editChat(any(), any()) }
    }

    @Test
    fun `a cover the server will not accept is dropped and explained`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { blobStorage.upload(any(), any()) } returns Result.success(blob)
        coEvery { chatCoordinator.editChat(any(), any()) } returns
            Result.failure(EditChatError.CoverPictureBlobNotAccepted())

        val vm = viewModel()
        vm.pick()
        vm.dispatchEvent(EditGroupCoverViewModel.Event.SubmitCover)
        advanceUntilIdle()

        assertFalse(vm.stateFlow.value.canSubmit, "the refused pick must not be left to save again")
        assertEquals(1, BottomBarManager.messages.value.size)
    }

    @Test
    fun `saving asks before it uploads`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel()
        vm.pick()

        vm.dispatchEvent(EditGroupCoverViewModel.Event.SaveClicked)
        advanceUntilIdle()

        coVerify(exactly = 0) { blobStorage.upload(any(), any()) }
        coVerify(exactly = 0) { chatCoordinator.editChat(any(), any()) }
        assertEquals(1, BottomBarManager.messages.value.size)
    }
}
