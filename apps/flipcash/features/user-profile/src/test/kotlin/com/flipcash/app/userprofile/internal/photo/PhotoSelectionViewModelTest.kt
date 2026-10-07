package com.flipcash.app.userprofile.internal.photo

import android.net.Uri
import com.flipcash.app.blob.BlobStorageCoordinator
import com.flipcash.app.blob.ImageUploadPreparer
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.services.controllers.ModerationController
import com.flipcash.services.models.BlobRejectedException
import com.flipcash.services.models.SetCoverPictureError
import com.flipcash.services.models.chat.BlobRejection
import com.flipcash.services.models.chat.RejectionReason
import com.flipcash.services.models.ModerationResult
import com.getcode.manager.BottomBarManager
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.services.user.UserManager
import com.getcode.util.resources.ContentReader
import com.getcode.util.resources.FakeResourceHelper
import android.webkit.MimeTypeMap
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

/**
 * One view model serves each editor step, and the slot it was made for decides which picture it
 * seeds from and which one it writes. A cover step must never touch the avatar, and an avatar step
 * must never touch the cover.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoSelectionViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val avatar = MediaItem(renditions = emptyList())
    private val cover = MediaItem(
        renditions = listOf(MediaItemRendition(MediaItemRendition.Role.DISPLAY, BlobId(ByteArray(16) { 1 }), blob = null))
    )
    private val blobId = BlobId(ByteArray(16))
    private val pick: Uri = mock()

    private val userManager = mockk<UserManager>(relaxed = true)
    private val moderationController = mockk<ModerationController>(relaxed = true)
    private val profileController = mock<ProfileController>()
    private val blobStorage = mock<BlobStorageCoordinator>()
    private val imagePreparer = mockk<ImageUploadPreparer>(relaxed = true)
    private val contentReader = mockk<ContentReader>(relaxed = true)

    // The state's default MIME type reaches for Android's MimeTypeMap, which a JVM test doesn't have.
    @Before
    fun stubMimeTypeMap() {
        val map = mockk<MimeTypeMap>()
        every { map.getExtensionFromMimeType(any<String>()) } returns "jpg"
        every { map.getMimeTypeFromExtension(any()) } returns "image/jpeg"
        mockkStatic(MimeTypeMap::class)
        every { MimeTypeMap.getSingleton() } returns map
    }

    @Before
    fun clearAlerts() {
        BottomBarManager.clear()
    }

    @After
    fun unstubMimeTypeMap() {
        BottomBarManager.clear()
        unmockkStatic(MimeTypeMap::class)
    }

    private fun TestScope.viewModel(slot: PhotoSelectionViewModel.Slot): PhotoSelectionViewModel {
        every { userManager.profile } returns UserProfile.Empty.copy(
            profilePicture = avatar,
            coverPicture = cover,
        )
        every { userManager.state } returns MutableStateFlow(
            UserManager.State(userProfile = UserProfile.Empty.copy(profilePicture = avatar, coverPicture = cover))
        )
        every { contentReader.readBytes(any()) } returns ByteArray(4)
        whenever(blobStorage.policy).thenReturn(MutableStateFlow(null))
        return PhotoSelectionViewModel(
            dispatchers = TestDispatchers(testScheduler),
            userManager = userManager,
            moderationController = moderationController,
            profileController = profileController,
            blobStorage = blobStorage,
            imagePreparer = imagePreparer,
            resources = FakeResourceHelper(),
            contentReader = contentReader,
            slot = slot,
        )
    }

    private suspend fun stubUpload() {
        whenever(blobStorage.upload(any(), any(), anyOrNull())).thenReturn(Result.success(blobId))
    }

    @Test
    fun `an avatar step seeds from the profile picture`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel(PhotoSelectionViewModel.Slot.Avatar)
        advanceUntilIdle()

        assertEquals(avatar, vm.stateFlow.value.savedPicture)
    }

    @Test
    fun `a cover step seeds from the cover picture`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel(PhotoSelectionViewModel.Slot.Cover)
        advanceUntilIdle()

        assertEquals(cover, vm.stateFlow.value.savedPicture)
    }

    @Test
    fun `a cover step uploads to the cover and never the avatar`() = runTest(mainCoroutineRule.dispatcher) {
        stubUpload()
        whenever(profileController.setCoverPicture(any())).thenReturn(Result.success(cover))
        val vm = viewModel(PhotoSelectionViewModel.Slot.Cover)
        advanceUntilIdle()

        vm.dispatchEvent(PhotoSelectionViewModel.Event.OnImageCached(pick, "image/jpeg"))
        vm.dispatchEvent(PhotoSelectionViewModel.Event.CheckImage)
        advanceUntilIdle()

        verify(profileController).setCoverPicture(blobId)
        verify(profileController, never()).setProfilePicture(any())
    }

    @Test
    fun `an avatar step uploads to the avatar and never the cover`() = runTest(mainCoroutineRule.dispatcher) {
        stubUpload()
        whenever(profileController.setProfilePicture(any())).thenReturn(Result.success(avatar))
        val vm = viewModel(PhotoSelectionViewModel.Slot.Avatar)
        advanceUntilIdle()

        vm.dispatchEvent(PhotoSelectionViewModel.Event.OnImageCached(pick, "image/jpeg"))
        vm.dispatchEvent(PhotoSelectionViewModel.Event.CheckImage)
        advanceUntilIdle()

        verify(profileController).setProfilePicture(blobId)
        verify(profileController, never()).setCoverPicture(any())
    }

    @Test
    fun `replacing a cover saves without asking first, as iOS does`() = runTest(mainCoroutineRule.dispatcher) {
        stubUpload()
        whenever(profileController.setCoverPicture(any())).thenReturn(Result.success(cover))
        val vm = viewModel(PhotoSelectionViewModel.Slot.Cover)
        advanceUntilIdle()

        vm.dispatchEvent(PhotoSelectionViewModel.Event.OnImageCached(pick, "image/jpeg"))
        vm.dispatchEvent(PhotoSelectionViewModel.Event.ConfirmImageChange)
        advanceUntilIdle()

        assertEquals(null, BottomBarManager.messages.value.firstOrNull())
        verify(profileController).setCoverPicture(blobId)
    }

    @Test
    fun `replacing an avatar asks first`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel(PhotoSelectionViewModel.Slot.Avatar)
        advanceUntilIdle()

        vm.dispatchEvent(PhotoSelectionViewModel.Event.OnImageCached(pick, "image/jpeg"))
        vm.dispatchEvent(PhotoSelectionViewModel.Event.ConfirmImageChange)
        advanceUntilIdle()

        assertEquals("prompt_title_changeProfilePicture", BottomBarManager.messages.value.firstOrNull()?.title)
        verify(profileController, never()).setProfilePicture(any())
    }

    private fun rejection(reason: RejectionReason, category: ModerationResult.FlaggedCategory = ModerationResult.FlaggedCategory.NONE) =
        BlobRejectedException(BlobRejection(reason, category))

    /** Picks an image, fails the upload with [cause], and returns the title of the alert shown. */
    private suspend fun TestScope.failUploadWith(slot: PhotoSelectionViewModel.Slot, cause: Throwable): String? {
        whenever(blobStorage.upload(any(), any(), anyOrNull())).thenReturn(Result.failure(cause))
        val vm = viewModel(slot)
        advanceUntilIdle()
        vm.dispatchEvent(PhotoSelectionViewModel.Event.OnImageCached(pick, "image/jpeg"))
        vm.dispatchEvent(PhotoSelectionViewModel.Event.CheckImage)
        advanceUntilIdle()
        return BottomBarManager.messages.value.firstOrNull()?.title
    }

    @Test
    fun `a cover blob rejected by the profile call shows the cover alert`() = runTest(mainCoroutineRule.dispatcher) {
        stubUpload()
        whenever(profileController.setCoverPicture(any())).thenReturn(Result.failure(SetCoverPictureError.BlobRejected()))
        val vm = viewModel(PhotoSelectionViewModel.Slot.Cover)
        advanceUntilIdle()
        vm.dispatchEvent(PhotoSelectionViewModel.Event.OnImageCached(pick, "image/jpeg"))
        vm.dispatchEvent(PhotoSelectionViewModel.Event.CheckImage)
        advanceUntilIdle()

        assertEquals("error_title_coverNotAllowed", BottomBarManager.messages.value.firstOrNull()?.title)
    }

    @Test
    fun `a moderation rejection on a cover shows the cover alert`() = runTest(mainCoroutineRule.dispatcher) {
        val title = failUploadWith(
            PhotoSelectionViewModel.Slot.Cover,
            rejection(RejectionReason.MODERATION, ModerationResult.FlaggedCategory.NSFW),
        )
        assertEquals("error_title_coverNotAllowed", title)
    }

    @Test
    fun `an unclassified rejection on a cover shows the cover alert`() = runTest(mainCoroutineRule.dispatcher) {
        assertEquals(
            "error_title_coverNotAllowed",
            failUploadWith(PhotoSelectionViewModel.Slot.Cover, rejection(RejectionReason.UNKNOWN)),
        )
    }

    @Test
    fun `a non-moderation rejection on a cover gets the generic wording`() = runTest(mainCoroutineRule.dispatcher) {
        assertEquals(
            "error_title_moderationFailed",
            failUploadWith(PhotoSelectionViewModel.Slot.Cover, rejection(RejectionReason.TOO_LARGE)),
        )
    }

    @Test
    fun `a moderation rejection on an avatar keeps its per-category message`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = failUploadWith(
            PhotoSelectionViewModel.Slot.Avatar,
            rejection(RejectionReason.MODERATION, ModerationResult.FlaggedCategory.NSFW),
        )
        assertEquals("error_title_profilePhotoNotAllowed", vm)
        assertEquals(
            "error_description_profilePhotoNotAllowedFlaggedNsfw",
            BottomBarManager.messages.value.firstOrNull()?.subtitle,
        )
    }

    @Test
    fun `an unclassified rejection on an avatar keeps the image-not-allowed message`() = runTest(mainCoroutineRule.dispatcher) {
        assertEquals(
            "error_title_imageNotAllowed",
            failUploadWith(PhotoSelectionViewModel.Slot.Avatar, rejection(RejectionReason.UNKNOWN)),
        )
    }
}
