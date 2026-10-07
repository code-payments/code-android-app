package com.flipcash.app.messenger.internal.screens.profile.edit

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.models.EditChatError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.DescriptionEdit
import com.flipcash.services.models.chat.EditChatParameters
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertTrue

/** The iOS `EditGroupDescriptionModelTests`. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EditGroupDescriptionViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val chatId = ChatId(ByteArray(16) { 7 }.toList())

    @Before
    fun setUp() = BottomBarManager.clear()

    @After
    fun tearDown() = BottomBarManager.clear()

    private fun viewModel(description: String = "Hello") = EditGroupDescriptionViewModel(
        dispatchers = TestDispatcherProvider(mainCoroutineRule.dispatcher),
        chatCoordinator = chatCoordinator,
        resources = resources,
    ).apply {
        dispatchEvent(EditGroupDescriptionViewModel.Event.Initialize(chatId, description))
    }

    private fun EditGroupDescriptionViewModel.type(text: String) =
        stateFlow.value.fieldState.setTextAndPlaceCursorAtEnd(text)

    private val EditGroupDescriptionViewModel.draft get() = stateFlow.value.draft

    @Test
    fun `the field opens on the current description with the characters left`() =
        runTest(mainCoroutineRule.dispatcher) {
            val vm = viewModel("Hello")
            advanceUntilIdle()

            assertEquals("Hello", vm.stateFlow.value.fieldState.text.toString())
            assertEquals(155, vm.draft.remaining)
        }

    @Test
    fun `save is shut when unchanged or over the limit and open once cleared`() =
        runTest(mainCoroutineRule.dispatcher) {
            val vm = viewModel("Hello")
            advanceUntilIdle()
            assertFalse(vm.stateFlow.value.canSubmit)

            vm.type("x".repeat(161))
            advanceUntilIdle()
            assertFalse(vm.stateFlow.value.canSubmit)

            vm.type("")
            advanceUntilIdle()
            assertTrue(vm.stateFlow.value.canSubmit)
        }

    @Test
    fun `a changed description is sent trimmed`() = runTest(mainCoroutineRule.dispatcher) {
        val sent = slot<EditChatParameters>()
        coEvery { chatCoordinator.editChat(any(), capture(sent)) } returns
            Result.success(mockk<ChatMetadata>(relaxed = true))
        val vm = viewModel("Hello")
        advanceUntilIdle()

        vm.type("  Good boys only  ")
        advanceUntilIdle()
        vm.dispatchEvent(EditGroupDescriptionViewModel.Event.SubmitDescription)
        advanceUntilIdle()

        assertEquals(DescriptionEdit.Set("Good boys only"), sent.captured.description)
    }

    @Test
    fun `an emptied description is sent as a clear`() = runTest(mainCoroutineRule.dispatcher) {
        val sent = slot<EditChatParameters>()
        coEvery { chatCoordinator.editChat(any(), capture(sent)) } returns
            Result.success(mockk<ChatMetadata>(relaxed = true))
        val vm = viewModel("Hello")
        advanceUntilIdle()

        vm.type("   ")
        advanceUntilIdle()
        vm.dispatchEvent(EditGroupDescriptionViewModel.Event.SubmitDescription)
        advanceUntilIdle()

        assertEquals(DescriptionEdit.Clear, sent.captured.description)
    }

    @Test
    fun `a moderated description shows inline, with no dialog and save still open`() =
        runTest(mainCoroutineRule.dispatcher) {
            coEvery { chatCoordinator.editChat(any(), any()) } returns
                Result.failure(EditChatError.DescriptionModerated(mockk(relaxed = true)))
            val vm = viewModel("Hello")
            advanceUntilIdle()

            vm.type("Something else")
            advanceUntilIdle()
            vm.dispatchEvent(EditGroupDescriptionViewModel.Event.SubmitDescription)
            advanceUntilIdle()

            assertTrue(vm.draft.moderated)
            assertTrue(BottomBarManager.messages.value.isEmpty())
            assertTrue(vm.stateFlow.value.canSubmit)
        }

    @Test
    fun `editing the text clears the moderation error`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { chatCoordinator.editChat(any(), any()) } returns
            Result.failure(EditChatError.DescriptionModerated(mockk(relaxed = true)))
        val vm = viewModel("Hello")
        advanceUntilIdle()
        vm.type("Something else")
        advanceUntilIdle()
        vm.dispatchEvent(EditGroupDescriptionViewModel.Event.SubmitDescription)
        advanceUntilIdle()

        vm.type("Something kinder")
        advanceUntilIdle()

        assertFalse(vm.draft.moderated)
    }

    @Test
    fun `any other failure is a dialog and not an inline error`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { chatCoordinator.editChat(any(), any()) } returns Result.failure(RuntimeException("boom"))
        val vm = viewModel("Hello")
        advanceUntilIdle()

        vm.type("Something else")
        advanceUntilIdle()
        vm.dispatchEvent(EditGroupDescriptionViewModel.Event.SubmitDescription)
        advanceUntilIdle()

        assertFalse(vm.draft.moderated)
        assertEquals(1, BottomBarManager.messages.value.size)
        assertTrue(vm.stateFlow.value.canSubmit)
    }

    @Test
    fun `saving asks before it sends`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel("Hello")
        advanceUntilIdle()
        vm.type("Something else")
        advanceUntilIdle()

        vm.dispatchEvent(EditGroupDescriptionViewModel.Event.SaveClicked)
        advanceUntilIdle()

        coVerify(exactly = 0) { chatCoordinator.editChat(any(), any()) }
        assertEquals(1, BottomBarManager.messages.value.size)
    }
}
