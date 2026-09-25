package com.flipcash.app.messenger.internal.screens.profile.edit

import android.net.Uri
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.flipcash.analytics.GroupField
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.GroupEvents
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.blob.BlobStorageCoordinator
import com.flipcash.app.blob.ImageUploadPreparer
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.models.EditChatError
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ContentReader
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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

/** `Group: Edited` is sent when EditChat answers a rename or a picture change, either way. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GroupEditedAnalyticsTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val blobStorage = mockk<BlobStorageCoordinator>(relaxed = true)
    private val imagePreparer = mockk<ImageUploadPreparer>(relaxed = true)
    private val contentReader = mockk<ContentReader>(relaxed = true)
    private val analytics = RecordingAnalytics()

    private val chatId = ChatId(ByteArray(16) { 7 }.toList())

    @Before
    fun setUp() {
        BottomBarManager.clear()
        every { blobStorage.policy } returns flowOf(null)
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
    }

    private fun nameViewModel() = EditGroupNameViewModel(
        dispatchers = TestDispatcherProvider(mainCoroutineRule.dispatcher),
        chatCoordinator = chatCoordinator,
        resources = resources,
        analytics = analytics,
    )

    private fun pictureViewModel() = EditGroupPictureViewModel(
        dispatchers = TestDispatcherProvider(mainCoroutineRule.dispatcher),
        chatCoordinator = chatCoordinator,
        blobStorage = blobStorage,
        imagePreparer = imagePreparer,
        contentReader = contentReader,
        resources = resources,
        analytics = analytics,
    )

    @Test
    fun `a rename reports Name and Success`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { chatCoordinator.editChat(any(), any()) } returns Result.success(mockk<ChatMetadata>(relaxed = true))

        val vm = nameViewModel()
        vm.dispatchEvent(EditGroupNameViewModel.Event.Initialize(chatId, "Ballers"))
        vm.stateFlow.value.titleFieldState.setTextAndPlaceCursorAtEnd("Shot Callers")
        vm.dispatchEvent(EditGroupNameViewModel.Event.SubmitTitle)
        advanceUntilIdle()

        assertEquals(
            listOf(GroupEvents.edited(GroupField.NAME, AnalyticsState.SUCCESS, error = null)),
            analytics.events,
        )
    }

    @Test
    fun `a refused rename reports the result name`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { chatCoordinator.editChat(any(), any()) } returns Result.failure(EditChatError.Denied())

        val vm = nameViewModel()
        vm.dispatchEvent(EditGroupNameViewModel.Event.Initialize(chatId, "Ballers"))
        vm.stateFlow.value.titleFieldState.setTextAndPlaceCursorAtEnd("Shot Callers")
        vm.dispatchEvent(EditGroupNameViewModel.Event.SubmitTitle)
        advanceUntilIdle()

        assertEquals(
            listOf(GroupEvents.edited(GroupField.NAME, AnalyticsState.FAILURE, error = "Denied")),
            analytics.events,
        )
    }

    @Test
    fun `a picture change reports Picture`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { contentReader.readBytes(any()) } returns ByteArray(4)
        coEvery { blobStorage.upload(any(), any()) } returns Result.success(BlobId(ByteArray(32) { 5 }))
        coEvery { chatCoordinator.editChat(any(), any()) } returns
            Result.failure(EditChatError.PictureBlobNotAccepted())

        val vm = pictureViewModel()
        vm.dispatchEvent(EditGroupPictureViewModel.Event.Initialize(chatId, picture = null, title = "Ballers"))
        vm.dispatchEvent(EditGroupPictureViewModel.Event.OnImageCached(Uri.parse("file:///pick.jpg"), "image/jpeg"))
        vm.dispatchEvent(EditGroupPictureViewModel.Event.SubmitPicture)
        advanceUntilIdle()

        assertEquals(
            listOf(GroupEvents.edited(GroupField.PICTURE, AnalyticsState.FAILURE, error = "PictureBlobNotAccepted")),
            analytics.events,
        )
    }

    /** A picture the blob store refused never reached EditChat, so there is no edit to report. */
    @Test
    fun `a failed upload reports nothing`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { contentReader.readBytes(any()) } returns ByteArray(4)
        coEvery { blobStorage.upload(any(), any()) } returns Result.failure(RuntimeException("upload"))

        val vm = pictureViewModel()
        vm.dispatchEvent(EditGroupPictureViewModel.Event.Initialize(chatId, picture = null, title = "Ballers"))
        vm.dispatchEvent(EditGroupPictureViewModel.Event.OnImageCached(Uri.parse("file:///pick.jpg"), "image/jpeg"))
        vm.dispatchEvent(EditGroupPictureViewModel.Event.SubmitPicture)
        advanceUntilIdle()

        assertEquals(emptyList(), analytics.events)
    }
}
