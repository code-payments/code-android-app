package com.flipcash.app.messenger.internal

import com.flipcash.app.messenger.internal.ChatViewModel.Event
import com.flipcash.app.messenger.internal.ChatViewModel.State
import com.flipcash.shared.chat.ui.media.MAX_STAGED_PHOTOS
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.take

/** Photos staged in the composer, their uploads, and the composer's link preview. Null when [event] belongs to another area. */
internal fun reduceComposerPhotos(event: Event): ((State) -> State)? =
    when (event) {
        is Event.StagePhotos -> { state -> state }
        is Event.CaptureStarted -> { state -> state }
        is Event.CaptureFinished -> { state -> state }
        is Event.RetryPhoto -> { state -> state }
        is Event.PhotoStaged -> { state ->
            if (state.stagedPhotos.size >= MAX_STAGED_PHOTOS) state
            else state.copy(stagedPhotos = state.stagedPhotos + event.photo)
        }
        is Event.RemovePhoto -> { state ->
            state.copy(
                stagedPhotos = state.stagedPhotos.filterNot { it.id == event.id },
                uploadStates = state.uploadStates - event.id,
            )
        }
        is Event.UploadStatesChanged -> { state -> state.copy(uploadStates = event.states) }
        is Event.ComposerLinkChanged -> { state -> state.copy(composerLink = event.link) }
        Event.RemoveLinkPreview -> { state -> state }
        is Event.PhotosSent -> { state ->
            state.copy(stagedPhotos = state.stagedPhotos.filterNot { it.id in event.ids })
        }
        is Event.PhotosRestored -> { state ->
            state.copy(stagedPhotos = (event.photos + state.stagedPhotos).take(MAX_STAGED_PHOTOS))
        }
        else -> null
    }
