package com.flipcash.app.messenger.internal.screens.profile.edit

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.flipcash.app.blob.BlobStorageCoordinator
import com.flipcash.app.blob.ImageUploadPreparer
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.moderation.moderationDescription
import com.flipcash.features.messenger.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.BlobRejectedException
import com.flipcash.services.models.EditChatError
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.RejectionReason
import com.flipcash.services.models.blob.UploadPolicy
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ContentReader
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * The group-picture edit behind node 10187:110373's Icon row.
 *
 * The pick-prepare-upload path is `CreateGroupViewModel`'s, because group creation already
 * collects a group picture and the two have to produce the same thing: an ORIGINAL rendition
 * inside the server's current [UploadPolicy], uploaded through [BlobStorageCoordinator], whose
 * `upload` returns only once the blob is READY. Nothing new uploads here.
 *
 * That READY guarantee is what makes the ordering correct: `EditChat` answers
 * `PICTURE_BLOB_NOT_ACCEPTED` for a blob still PENDING or PROCESSING, and the only way to reach
 * the edit call below is through a successful `upload`.
 */
@HiltViewModel
class EditGroupPictureViewModel @Inject constructor(
    dispatchers: DispatcherProvider,
    private val chatCoordinator: ChatCoordinator,
    private val blobStorage: BlobStorageCoordinator,
    private val imagePreparer: ImageUploadPreparer,
    private val contentReader: ContentReader,
    private val resources: ResourceHelper,
) : BaseViewModel<EditGroupPictureViewModel.State, EditGroupPictureViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    // The base hops to Dispatchers.Default to publish events. Taking it from the injected
    // provider instead keeps every dispatcher this class touches on one seam.
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val chatId: ChatId? = null,
        /** The group's stored picture — what the well shows until a pick replaces it. */
        val savedPicture: MediaItem? = null,
        val groupTitle: String = "",
        /** The pending pick, re-encoded and cached on disk. Null until one is made. */
        val image: Loadable<Uri> = Loadable.Loading(),
        val mimeType: String? = null,
        val uploadPolicy: UploadPolicy? = null,
        val processingState: LoadingSuccessState = LoadingSuccessState(),
    ) {
        /** Only a fresh pick can be saved; the stored picture is not a change to itself. */
        val isChanged: Boolean
            get() = image.dataOrNull != null

        val canSubmit: Boolean
            get() = isChanged && processingState.isIdle && chatId != null
    }

    sealed interface Event {
        data class Initialize(
            val chatId: ChatId,
            val picture: MediaItem?,
            val title: String,
        ) : Event

        data class OnImageSelected(val image: Uri) : Event
        data class OnImageCached(val image: Uri, val mimeType: String) : Event
        data object DiscardPendingImage : Event
        data class UploadPolicyLoaded(val policy: UploadPolicy) : Event
        /**
         * Save, pressed. The new picture is only proposed here: what uploads and sends it is
         * [SubmitPicture], dispatched by the confirmation's own action.
         */
        data object SaveClicked : Event

        /** The confirmed change. Reachable only through the prompt [SaveClicked] raises. */
        data object SubmitPicture : Event
        data class UpdateProcessingState(
            val loading: Boolean = false,
            val success: Boolean = false,
        ) : Event

        data object OnPictureAccepted : Event
    }

    init {
        blobStorage.policy
            .filterNotNull()
            .onEach { dispatchEvent(Event.UploadPolicyLoaded(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnImageSelected>()
            .mapNotNull { event ->
                // Re-encode inside the policy's dimension and byte caps, off the main thread —
                // the same call group creation makes, so both produce blobs the server will take.
                when (val outcome = imagePreparer.prepare(
                    uri = event.image,
                    policy = stateFlow.value.uploadPolicy,
                    fileNamePrefix = "group_picture",
                )) {
                    is ImageUploadPreparer.Outcome.Prepared -> outcome.uri to outcome.mimeType
                    ImageUploadPreparer.Outcome.Unsupported -> {
                        rejectImage(
                            title = R.string.error_title_imageNotSupported,
                            message = R.string.error_description_imageNotSupported,
                        )
                        null
                    }

                    ImageUploadPreparer.Outcome.TooLarge -> {
                        rejectImage(
                            title = R.string.error_title_imageTooLarge,
                            message = R.string.error_description_imageTooLarge,
                        )
                        null
                    }

                    // The preparer has already said why in the log; there is nothing to add.
                    ImageUploadPreparer.Outcome.Unreadable -> null
                }
            }
            .flowOn(dispatchers.IO)
            .onEach { (cached, mime) -> dispatchEvent(Event.OnImageCached(cached, mime)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.SaveClicked>()
            .onEach {
                if (!stateFlow.value.canSubmit) return@onEach

                BottomBarManager.showMessage(
                    title = resources.getString(R.string.prompt_title_changeGroupPicture),
                    message = resources.getString(R.string.prompt_description_changeGroupPicture),
                    actions = listOf(
                        BottomBarAction(resources.getString(R.string.action_changeGroupPicture)) {
                            viewModelScope.launch {
                                // The bar dismisses on an animation, and the upload's spinner
                                // belongs to the screen behind it — it would start underneath.
                                delay(150.milliseconds)
                                dispatchEvent(Event.SubmitPicture)
                            }
                        }
                    ),
                    showCancel = true,
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.SubmitPicture>()
            .onEach { submit() }
            .launchIn(viewModelScope)
    }

    private suspend fun submit() {
        val state = stateFlow.value
        val chatId = state.chatId ?: return
        val uri = state.image.dataOrNull ?: return
        val mimeType = state.mimeType ?: return

        dispatchEvent(Event.UpdateProcessingState(loading = true))

        val blobId = uploadPicture(uri, mimeType)
        if (blobId == null) {
            dispatchEvent(Event.UpdateProcessingState())
            return
        }

        // Picture only. The title field stays unset so the server leaves the title alone.
        chatCoordinator.editChat(
            chatId = chatId,
            parameters = pictureOnly(blobId),
        ).onSuccess {
            dispatchEvent(Event.UpdateProcessingState(success = true))
            delay(500.milliseconds)
            dispatchEvent(Event.OnPictureAccepted)
            dispatchEvent(Event.UpdateProcessingState())
        }.onFailure { cause ->
            dispatchEvent(Event.UpdateProcessingState())
            announceEditFailure(cause)
        }
    }

    /** Uploads the prepared picture and returns its READY blob, or null having reported why not. */
    private suspend fun uploadPicture(uri: Uri, mimeType: String): BlobId? {
        val bytes = contentReader.readBytes(uri)
        if (bytes == null) {
            BottomBarManager.showError(
                title = resources.getString(R.string.error_title_moderationFailed),
                message = resources.getString(R.string.error_description_moderationFailed),
            )
            return null
        }

        return blobStorage.upload(bytes = bytes, mimeType = mimeType)
            .onFailure { cause ->
                dispatchEvent(Event.DiscardPendingImage)
                announcePictureRejection(cause)
            }
            .getOrNull()
    }

    private fun announcePictureRejection(cause: Throwable) {
        val rejection = (cause as? BlobRejectedException)?.rejection
        when (rejection?.reason) {
            RejectionReason.MODERATION -> BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_imageNotAllowed),
                message = resources.getString(moderationDescription(rejection.flaggedCategory)),
            )

            else -> BottomBarManager.showError(
                title = resources.getString(R.string.error_title_moderationFailed),
                message = resources.getString(R.string.error_description_moderationFailed),
            )
        }
    }

    private fun announceEditFailure(cause: Throwable) {
        when (cause) {
            // Storage took the blob and EditChat would not have it, so the pick is what has to
            // change — it is dropped rather than left on screen inviting the same upload again.
            is EditChatError.PictureBlobNotAccepted -> {
                dispatchEvent(Event.DiscardPendingImage)
                BottomBarManager.showAlert(
                    title = resources.getString(R.string.error_title_imageNotAllowed),
                    message = resources.getString(R.string.error_description_imageNotAllowed),
                )
            }

            is EditChatError.Denied -> BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_groupEditDenied),
                message = resources.getString(R.string.error_description_groupEditDenied),
            )

            else -> BottomBarManager.showError(
                title = resources.getString(R.string.error_title_groupEditFailed),
                message = resources.getString(R.string.error_description_groupEditFailed),
            )
        }
    }

    private fun rejectImage(title: Int, message: Int) {
        BottomBarManager.showAlert(
            title = resources.getString(title),
            message = resources.getString(message),
        )
    }

    internal companion object {
        val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                is Event.Initialize -> { state ->
                    state.copy(
                        chatId = event.chatId,
                        savedPicture = event.picture,
                        groupTitle = event.title,
                    )
                }

                is Event.OnImageSelected -> { state ->
                    // Held as Loading with the source uri so the well can show the pick while it
                    // is being re-encoded, instead of going blank between tap and preview.
                    state.copy(image = Loadable.Loading(event.image))
                }

                is Event.OnImageCached -> { state ->
                    state.copy(
                        image = Loadable.Loaded(event.image),
                        mimeType = event.mimeType,
                    )
                }

                Event.DiscardPendingImage -> { state ->
                    state.copy(image = Loadable.Loading(), mimeType = null)
                }

                is Event.UploadPolicyLoaded -> { state -> state.copy(uploadPolicy = event.policy) }
                Event.SaveClicked -> { state -> state }
                Event.SubmitPicture -> { state -> state }
                Event.OnPictureAccepted -> { state -> state }
                is Event.UpdateProcessingState -> { state ->
                    state.copy(
                        processingState = state.processingState.copy(
                            loading = event.loading,
                            success = event.success,
                        )
                    )
                }
            }
        }
    }
}
