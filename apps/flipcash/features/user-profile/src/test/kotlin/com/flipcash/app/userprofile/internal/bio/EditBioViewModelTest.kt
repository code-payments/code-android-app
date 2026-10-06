package com.flipcash.app.userprofile.internal.bio

import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.ModerationResult
import com.flipcash.services.models.SetBioError
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.errors.ValidationException
import com.getcode.util.resources.FakeResourceHelper
import androidx.compose.runtime.snapshots.Snapshot
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class EditBioViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val userManager = mockk<UserManager>(relaxed = true)
    private val profileController = mock<ProfileController>()

    private fun profile(bio: String) = UserProfile.Empty.copy(bio = bio)

    private fun kotlinx.coroutines.test.TestScope.viewModel(stored: String = "Hello"): EditBioViewModel {
        every { userManager.state } returns MutableStateFlow(UserManager.State(userProfile = profile(stored)))
        return EditBioViewModel(
            userManager = userManager,
            profileController = profileController,
            resources = FakeResourceHelper(),
            dispatchers = TestDispatchers(testScheduler),
        )
    }

    // snapshotFlow only sees a field edit once the global snapshot's apply notifications go out,
    // which the Compose runtime does on a device and a plain JVM test has to do by hand.
    private fun EditBioViewModel.type(edit: androidx.compose.foundation.text.input.TextFieldBuffer.() -> Unit) {
        stateFlow.value.fieldState.edit(edit)
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun `opens on the stored bio with nothing to save`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel("Hello")
        advanceUntilIdle()

        assertEquals("Hello", vm.stateFlow.value.fieldState.text.toString())
        assertEquals(false, vm.stateFlow.value.draft.canSave)
    }

    @Test
    fun `a failed moderation keeps the text and shows the moderated error`() = runTest(mainCoroutineRule.dispatcher) {
        whenever(profileController.setBio(any())).thenReturn(
            Result.failure(SetBioError.FailedModerated(ModerationResult.FlaggedCategory.OTHER))
        )
        val vm = viewModel("Hello")
        advanceUntilIdle()
        vm.type { replace(0, length, "Something rude") }
        advanceUntilIdle()

        vm.dispatchEvent(EditBioViewModel.Event.Save)
        advanceUntilIdle()

        assertEquals(BioError.Moderated, vm.stateFlow.value.draft.error)
        assertEquals("Something rude", vm.stateFlow.value.draft.text)
        assertEquals(true, vm.stateFlow.value.processingState.isIdle)
    }

    @Test
    fun `an invalid bio and a validation failure both show the invalid error`() = runTest(mainCoroutineRule.dispatcher) {
        whenever(profileController.setBio(any())).thenReturn(Result.failure(SetBioError.InvalidBio()))
        val vm = viewModel("Hello")
        advanceUntilIdle()
        vm.type { replace(0, length, "x") }
        advanceUntilIdle()
        vm.dispatchEvent(EditBioViewModel.Event.Save)
        advanceUntilIdle()
        assertEquals(BioError.Invalid, vm.stateFlow.value.draft.error)

        whenever(profileController.setBio(any())).thenReturn(Result.failure(ValidationException(emptyList())))
        vm.type { replace(0, length, "y") }
        advanceUntilIdle()
        assertNull(vm.stateFlow.value.draft.error)
        vm.dispatchEvent(EditBioViewModel.Event.Save)
        advanceUntilIdle()
        assertEquals(BioError.Invalid, vm.stateFlow.value.draft.error)
    }

    @Test
    fun `editing after a failure clears the error`() = runTest(mainCoroutineRule.dispatcher) {
        whenever(profileController.setBio(any())).thenReturn(Result.failure(SetBioError.InvalidBio()))
        val vm = viewModel("Hello")
        advanceUntilIdle()
        vm.type { replace(0, length, "x") }
        advanceUntilIdle()
        vm.dispatchEvent(EditBioViewModel.Event.Save)
        advanceUntilIdle()

        vm.type { append("y") }
        advanceUntilIdle()

        assertNull(vm.stateFlow.value.draft.error)
    }

    @Test
    fun `an unchanged bio is never sent`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel("Hello")
        advanceUntilIdle()

        vm.dispatchEvent(EditBioViewModel.Event.Save)
        advanceUntilIdle()

        verify(profileController, never()).setBio(any())
    }

    @Test
    fun `a saved bio is sent trimmed and the step completes`() = runTest(mainCoroutineRule.dispatcher) {
        whenever(profileController.setBio(any())).thenReturn(Result.success(Unit))
        val vm = viewModel("Hello")
        advanceUntilIdle()
        vm.type { replace(0, length, "  New bio \n") }
        advanceUntilIdle()

        val saved = mutableListOf<EditBioViewModel.Event>()
        val collector = launch { vm.eventFlow.collect { saved += it } }
        vm.dispatchEvent(EditBioViewModel.Event.Save)
        advanceUntilIdle()
        collector.cancel()

        verify(profileController).setBio("New bio")
        assertEquals(true, saved.any { it is EditBioViewModel.Event.OnBioSaved })
    }
}
