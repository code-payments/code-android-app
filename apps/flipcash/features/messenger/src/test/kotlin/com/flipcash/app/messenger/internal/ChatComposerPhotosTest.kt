package com.flipcash.app.messenger.internal

import android.net.Uri
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.media.ChatMediaUploadState
import com.flipcash.shared.chat.ui.media.MAX_STAGED_PHOTOS
import com.getcode.opencode.model.core.bytes
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The composer's photo chips are plain state, so the reducer holds the rules for them. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ChatComposerPhotosTest {

    private val chatId = ChatId(UUID.fromString("0189d5f1-3c6a-7b4e-8f21-9c3d4e5f6a7b").bytes)
    private val base = ChatViewModel.State(chatId = chatId)

    private fun photo(id: String) = ChatViewModel.StagedPhoto(id, Uri.parse("content://media/$id"))

    private fun reduce(state: ChatViewModel.State, event: ChatViewModel.Event) =
        ChatViewModel.updateStateForEvent(event)(state)

    private fun stage(state: ChatViewModel.State, vararg ids: String) =
        ids.fold(state) { acc, id -> reduce(acc, ChatViewModel.Event.PhotoStaged(photo(id))) }

    private val failed = ChatMediaUploadState.Failed(IllegalStateException("x"), retryable = true)

    @Test
    fun stagingAddsChip() {
        val state = stage(base, "a")
        assertEquals(listOf("a"), state.composerPhotos.map { it.id })
    }

    @Test
    fun chipsKeepStagingOrder() {
        val state = stage(base, "a", "b", "c")
        assertEquals(listOf("a", "b", "c"), state.composerPhotos.map { it.id })
    }

    @Test
    fun removingDropsOnlyThatChip() {
        val state = reduce(stage(base, "a", "b", "c"), ChatViewModel.Event.RemovePhoto("b"))
        assertEquals(listOf("a", "c"), state.composerPhotos.map { it.id })
    }

    @Test
    fun refusesChipPastMax() {
        val ids = (1..MAX_STAGED_PHOTOS + 2).map { "p$it" }.toTypedArray()
        val state = stage(base, *ids)
        assertEquals(MAX_STAGED_PHOTOS, state.composerPhotos.size)
        assertEquals("p$MAX_STAGED_PHOTOS", state.composerPhotos.last().id)
    }

    @Test
    fun chipsAloneEnableSend() {
        assertFalse(base.canSendComposer(hasText = false))
        assertTrue(stage(base, "a").canSendComposer(hasText = false))
        assertTrue(base.canSendComposer(hasText = true))
    }

    @Test
    fun retryableFailedChipStillSends() {
        // An upload that failed offline is sent anyway; the bubble retries it from the transcript.
        val state = reduce(
            stage(base, "a", "b"),
            ChatViewModel.Event.UploadStatesChanged(mapOf("a" to failed)),
        )
        assertTrue(state.canSendComposer(hasText = true))
    }

    @Test
    fun unretryableFailedChipDisablesSend() {
        val state = reduce(
            stage(base, "a", "b"),
            ChatViewModel.Event.UploadStatesChanged(mapOf("a" to failed.copy(retryable = false))),
        )
        assertFalse(state.canSendComposer(hasText = true))
        // Removing the failed chip frees the send.
        assertTrue(reduce(state, ChatViewModel.Event.RemovePhoto("a")).canSendComposer(hasText = true))
    }

    @Test
    fun sendClearsChips() {
        val state = reduce(stage(base, "a", "b"), ChatViewModel.Event.PhotosSent(setOf("a", "b")))
        assertTrue(state.composerPhotos.isEmpty())
    }

    @Test
    fun editModeHidesChips() {
        val staged = stage(base, "a")
        val editing = staged.copy(editing = ChatViewModel.EditingMessage(1L, "hi", ""))
        assertTrue(editing.composerPhotos.isEmpty())
        assertFalse(editing.acceptsMedia)
        // Staged photos are held, not dropped, while editing.
        assertEquals(1, editing.copy(editing = null).composerPhotos.size)
    }
}
