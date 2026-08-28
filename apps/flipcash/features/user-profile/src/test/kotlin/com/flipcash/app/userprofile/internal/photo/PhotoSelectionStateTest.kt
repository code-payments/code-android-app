package com.flipcash.app.userprofile.internal.photo

import android.net.Uri
import com.flipcash.services.models.chat.MediaItem
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoSelectionStateTest {

    private val reduce = PhotoSelectionViewModel.updateStateForEvent

    private val storedPicture = MediaItem(renditions = emptyList())
    private val pick: Uri = mock()

    // imageMimeType is supplied so State's default doesn't reach for Android's MimeTypeMap,
    // which has no implementation under plain JVM unit tests.
    private fun state(name: String = "Ada") =
        PhotoSelectionViewModel.State(name = name, imageMimeType = "image/jpeg")

    private fun PhotoSelectionViewModel.State.reduced(event: PhotoSelectionViewModel.Event) =
        reduce(event)(this)

    @Test
    fun `an account with no picture opens on nothing to save`() {
        val state = state()
        assertNull(state.savedPicture)
        assertFalse(state.isChanged)
    }

    @Test
    fun `the stored picture is seeded without arming save`() {
        val seeded = state().reduced(
            PhotoSelectionViewModel.Event.OnSavedPictureLoaded(storedPicture)
        )

        assertEquals(storedPicture, seeded.savedPicture)
        assertFalse(seeded.isChanged)
    }

    @Test
    fun `a cached pick is what counts as a change`() {
        val picked = state()
            .reduced(PhotoSelectionViewModel.Event.OnSavedPictureLoaded(storedPicture))
            .reduced(PhotoSelectionViewModel.Event.OnImageCached(pick, "image/png"))

        assertTrue(picked.isChanged)
        assertEquals(pick, picked.image.dataOrNull)
        assertEquals("image/png", picked.imageMimeType)
        // The stored picture stays put — it's what a discard falls back to.
        assertEquals(storedPicture, picked.savedPicture)
    }

    @Test
    fun `a pick still being re-encoded has nothing to save yet`() {
        val selected = state().reduced(PhotoSelectionViewModel.Event.OnImageSelected(pick))

        assertFalse(selected.isChanged)
        assertEquals(pick, selected.image.dataOrNull)
    }

    @Test
    fun `clearing the pick leaves the stored picture showing`() {
        val cleared = state()
            .reduced(PhotoSelectionViewModel.Event.OnSavedPictureLoaded(storedPicture))
            .reduced(PhotoSelectionViewModel.Event.OnImageCached(pick, "image/png"))
            .reduced(PhotoSelectionViewModel.Event.OnImageCleared)

        assertFalse(cleared.isChanged)
        assertNull(cleared.image.dataOrNull)
        assertEquals(storedPicture, cleared.savedPicture)
    }

    @Test
    fun `a saved upload replaces the stored picture`() {
        val uploaded = MediaItem(renditions = emptyList())
        val state = state()
            .reduced(PhotoSelectionViewModel.Event.OnSavedPictureLoaded(storedPicture))
            .reduced(PhotoSelectionViewModel.Event.OnSavedPictureLoaded(uploaded))

        assertEquals(uploaded, state.savedPicture)
    }

    @Test
    fun `no-op events return state unchanged`() {
        val state = state().reduced(
            PhotoSelectionViewModel.Event.OnSavedPictureLoaded(storedPicture)
        )
        val noOpEvents = listOf(
            PhotoSelectionViewModel.Event.CheckImage,
            PhotoSelectionViewModel.Event.OnImageApproved,
            PhotoSelectionViewModel.Event.DiscardChanges,
        )
        noOpEvents.forEach { event ->
            assertEquals(state, state.reduced(event), "Event $event should be no-op")
        }
    }
}
