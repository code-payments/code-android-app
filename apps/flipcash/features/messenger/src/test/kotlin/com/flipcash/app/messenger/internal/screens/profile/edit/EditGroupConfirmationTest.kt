package com.flipcash.app.messenger.internal.screens.profile.edit

import android.net.Uri
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Save proposes; the prompt commits.
 *
 * A rename and a new picture are both visible to everyone in the group, so neither leaves the
 * device on the strength of one tap. The gate lives in the view model rather than the screen
 * precisely so these can assert the part that matters — that Save on its own sends nothing — in a
 * plain unit test instead of an instrumented one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditGroupConfirmationTest {

    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val chatId = ChatId(ByteArray(32) { 9 }.toList())

    private val scheduler = TestCoroutineScheduler()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        BottomBarManager.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        BottomBarManager.clear()
    }

    private fun nameModel() = EditGroupNameViewModel(
        dispatchers = TestDispatchers(UnconfinedTestDispatcher(scheduler)),
        chatCoordinator = chatCoordinator,
        resources = resources,
        analytics = RecordingAnalytics(),
    ).apply {
        dispatchEvent(EditGroupNameViewModel.Event.Initialize(chatId, title = "Dogs"))
    }

    @Test
    fun `saving a rename asks before it sends`() = runTest(scheduler) {
        val model = nameModel()
        advanceUntilIdle()
        model.stateFlow.value.titleFieldState.setTextAndPlaceCursorAtEnd("Dog Park")

        model.dispatchEvent(EditGroupNameViewModel.Event.SaveClicked)
        advanceUntilIdle()

        coVerify(exactly = 0) { chatCoordinator.editChat(any(), any()) }
        assertEquals(1, BottomBarManager.messages.value.size)
    }

    @Test
    fun `confirming the rename is what sends it`() = runTest(scheduler) {
        val model = nameModel()
        advanceUntilIdle()
        model.stateFlow.value.titleFieldState.setTextAndPlaceCursorAtEnd("Dog Park")

        // The success path reads the edited chat back off the result, so a relaxed stub's
        // bare Object would fail there rather than at the assertion.
        coEvery { chatCoordinator.editChat(any(), any()) } returns
            Result.success(mockk<ChatMetadata>(relaxed = true))

        model.dispatchEvent(EditGroupNameViewModel.Event.SaveClicked)
        advanceUntilIdle()
        BottomBarManager.messages.value.last().actions.first().onClick()
        advanceUntilIdle()

        coVerify(exactly = 1) { chatCoordinator.editChat(chatId, any()) }
    }

    @Test
    fun `dismissing the rename prompt keeps the draft and sends nothing`() = runTest(scheduler) {
        val model = nameModel()
        advanceUntilIdle()
        model.stateFlow.value.titleFieldState.setTextAndPlaceCursorAtEnd("Dog Park")

        model.dispatchEvent(EditGroupNameViewModel.Event.SaveClicked)
        advanceUntilIdle()
        // What a cancel does: the prompt goes, nothing else is dispatched.
        BottomBarManager.clear()
        advanceUntilIdle()

        coVerify(exactly = 0) { chatCoordinator.editChat(any(), any()) }
        assertEquals("Dog Park", model.stateFlow.value.titleFieldState.text.toString())
    }

    @Test
    fun `an unchanged name raises no prompt`() = runTest(scheduler) {
        // Save is disabled for it, but the keyboard's Done action reaches the same event.
        val model = nameModel()
        advanceUntilIdle()

        model.dispatchEvent(EditGroupNameViewModel.Event.SaveClicked)
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.isEmpty())
        coVerify(exactly = 0) { chatCoordinator.editChat(any(), any()) }
    }

    private fun pictureModel() = EditGroupPictureViewModel(
        dispatchers = TestDispatchers(UnconfinedTestDispatcher(scheduler)),
        chatCoordinator = chatCoordinator,
        blobStorage = mockk(relaxed = true),
        imagePreparer = mockk(relaxed = true),
        contentReader = mockk(relaxed = true),
        resources = resources,
        analytics = RecordingAnalytics(),
    ).apply {
        dispatchEvent(
            EditGroupPictureViewModel.Event.Initialize(chatId, picture = null, title = "Dogs")
        )
    }

    @Test
    fun `saving a picture asks before it uploads`() = runTest(scheduler) {
        val model = pictureModel()
        advanceUntilIdle()
        model.dispatchEvent(
            EditGroupPictureViewModel.Event.OnImageCached(
                image = Uri.parse("file:///tmp/pick.jpg"),
                mimeType = "image/jpeg",
            )
        )
        advanceUntilIdle()

        model.dispatchEvent(EditGroupPictureViewModel.Event.SaveClicked)
        advanceUntilIdle()

        coVerify(exactly = 0) { chatCoordinator.editChat(any(), any()) }
        assertEquals(1, BottomBarManager.messages.value.size)
    }

    @Test
    fun `a picture with nothing picked raises no prompt`() = runTest(scheduler) {
        val model = pictureModel()
        advanceUntilIdle()

        model.dispatchEvent(EditGroupPictureViewModel.Event.SaveClicked)
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.isEmpty())
    }
}

private class TestDispatchers(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val Default: CoroutineDispatcher get() = dispatcher
    override val Main: CoroutineDispatcher get() = dispatcher
    override val IO: CoroutineDispatcher get() = dispatcher
}
