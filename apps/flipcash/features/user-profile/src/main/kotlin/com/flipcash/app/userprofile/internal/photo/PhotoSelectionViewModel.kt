package com.flipcash.app.userprofile.internal.photo

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.flipcash.app.blob.BlobStorageCoordinator
import com.flipcash.app.blob.ImageUploadPreparer
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.data.isLoaded
import com.flipcash.app.core.extensions.flatMapResult
import com.flipcash.app.core.extensions.onResult
import com.flipcash.services.models.blob.UploadPolicy
import com.flipcash.features.userprofile.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.controllers.ModerationController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.BlobRejectedException
import com.flipcash.services.models.ImageModerationError
import com.flipcash.services.models.ModerationResult
import com.flipcash.services.models.TextModerationError
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.RejectionReason
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.core.errors.ValidationException
import com.getcode.util.resources.ContentReader
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.resources.uploadMimeFor
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class PhotoSelectionViewModel @Inject constructor(
    dispatchers: DispatcherProvider,
    userManager: UserManager,
    private val moderationController: ModerationController,
    private val profileController: ProfileController,
    private val blobStorage: BlobStorageCoordinator,
    private val imagePreparer: ImageUploadPreparer,
    private val resources: ResourceHelper,
    val contentReader: ContentReader,
) : BaseViewModel<PhotoSelectionViewModel.State, PhotoSelectionViewModel.Event>(
    initialState = State(
        name = userManager.profile?.displayName.orEmpty(),
        savedPicture = userManager.profile?.profilePicture,
    ),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val name: String,
        /**
         * The stored profile picture, shown until a pick replaces it and again if that pick is
         * discarded. Display only: it is a server-side [MediaItem], never a local [Uri], so it
         * can't arm Save — [image] holding a pick is still the only thing that counts as a change.
         */
        val savedPicture: MediaItem? = null,
        val image: Loadable<Uri> = Loadable.Loading(),
        val attestation: ModerationResult.Attestation = ModerationResult.Attestation.Empty,
        val processingState: LoadingSuccessState = LoadingSuccessState(),
        // Server upload constraints (accepted MIME types + size ceilings), used to filter selection.
        val uploadPolicy: UploadPolicy? = null,
        // MIME type of the re-encoded image bytes to upload; resolved from the selected image.
        val imageMimeType: String = uploadMimeFor(null),
    ) {
        /**
         * The image case of nodes 9553:113166 / 9553:113168: a pick is the only thing that arms
         * Save. [savedPicture] is display only, so opening the step on the stored avatar leaves
         * this false.
         */
        val isChanged: Boolean
            get() = image.isLoaded()
    }

    sealed interface Event {
        /**
         * Save was pressed. Replacing a stored picture asks first; a first picture has nothing to
         * overwrite and goes straight to [CheckImage].
         */
        data object ConfirmImageChange : Event

        data object CheckImage : Event

        /** The stored picture arrived, or changed — including to null when it is unset. */
        data class OnSavedPictureLoaded(val picture: MediaItem?) : Event
        data class UploadPolicyLoaded(val policy: UploadPolicy) : Event
        data class UpdateProcessingState(
            val loading: Boolean = false,
            val success: Boolean = false
        ) : Event

        data object OnImageApproved : Event

        /** Back was pressed with an unsaved pick: drop it, leaving the stored picture as it was. */
        data object DiscardChanges : Event
        data class OnImageSelected(val image: Uri) : Event
        data class OnImageCached(val image: Uri, val mimeType: String) : Event
        data object OnImageCleared : Event
    }

    init {
        // Keeps the seeded avatar current: a save merges the server's renditions back into the
        // profile, so this is also what swaps the stored picture in once an upload lands.
        userManager.state
            .map { it.userProfile?.profilePicture }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnSavedPictureLoaded(it)) }
            .launchIn(viewModelScope)

        // Observe the policy — the coordinator serves the launch-preloaded cache and self-refreshes
        // it if it has aged past its ttl, re-emitting the fresh value here.
        blobStorage.policy
            .filterNotNull()
            .onEach { dispatchEvent(Event.UploadPolicyLoaded(it)) }
            .launchIn(viewModelScope)


        eventFlow
            .filterIsInstance<Event.ConfirmImageChange>()
            .onEach {
                if (stateFlow.value.savedPicture == null) {
                    dispatchEvent(Event.CheckImage)
                    return@onEach
                }
                BottomBarManager.showMessage(
                    title = resources.getString(R.string.prompt_title_changeProfilePicture),
                    message = resources.getString(R.string.prompt_description_changeProfilePicture),
                    actions = listOf(
                        BottomBarAction(resources.getString(R.string.action_changeProfilePicture)) {
                            dispatchEvent(Event.CheckImage)
                        }
                    ),
                    showCancel = true,
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnImageSelected>()
            .mapNotNull { event ->
                // Re-encode within the policy's dimension + pixel caps and shrink to its byte
                // ceiling. The wording of a refusal is this screen's; the loop is not.
                when (val outcome = imagePreparer.prepare(
                    uri = event.image,
                    policy = stateFlow.value.uploadPolicy,
                    fileNamePrefix = "user_profile",
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
                    ImageUploadPreparer.Outcome.Unreadable -> null
                }
            }
            .flowOn(dispatchers.IO)
            .onEach { (cached, mime) -> dispatchEvent(Event.OnImageCached(cached, mime)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CheckImage>()
            .mapNotNull { stateFlow.value.image.dataOrNull }
            .onEach { dispatchEvent(Event.UpdateProcessingState(loading = true)) }
            .map {
                // Moderation passed — upload the image bytes to storage in one coordinated
                // call, then set the returned blob as the profile picture.
                val uri = stateFlow.value.image.dataOrNull
                    ?: return@map Result.failure(IllegalStateException("No image selected"))
                val bytes = contentReader.readBytes(uri)
                    ?: return@map Result.failure(IllegalStateException("Unable to read image"))
                blobStorage.upload(bytes = bytes, mimeType = stateFlow.value.imageMimeType)
            }
            .flatMapResult { blobId ->
                profileController.setProfilePicture(blobId)
            }
            .onResult(
                onSuccess = { _ ->
                    viewModelScope.launch {
                        dispatchEvent(Event.UpdateProcessingState(success = true))
                        delay(500.milliseconds)
                        dispatchEvent(Event.OnImageApproved)
                        dispatchEvent(Event.UpdateProcessingState())
                    }
                },
                onError = { cause ->
                    dispatchEvent(Event.UpdateProcessingState())
                    discardPendingImage()
                    handleUploadFailure(cause)
                }
            )
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.DiscardChanges>()
            .onEach { discardPendingImage() }
            .launchIn(viewModelScope)
    }

    /**
     * A pick that never reached the server is only a re-encoded file in the cache, so leaving the
     * step has to delete it — nothing else ever will. Covers the paths the back button doesn't:
     * a gesture back, and the successful upload that makes the local copy redundant.
     */
    override fun onCleared() {
        discardPendingImage()
        super.onCleared()
    }

    private fun discardPendingImage() {
        val pending = stateFlow.value.image.dataOrNull ?: return
        contentReader.removeFromCache(pending)
        dispatchEvent(Event.OnImageCleared)
    }

    /** Clears the pending selection and surfaces [title]/[message] to the user. */
    private fun rejectImage(@StringRes title: Int, @StringRes message: Int) {
        dispatchEvent(Event.OnImageCleared)
        BottomBarManager.showAlert(
            title = resources.getString(title),
            message = resources.getString(message),
        )
    }

    private fun handleUploadFailure(cause: Throwable) {
        when (cause) {
            is BlobRejectedException -> {
                when (cause.rejection.reason) {
                    RejectionReason.UNKNOWN -> {
                        BottomBarManager.showAlert(
                            title = resources.getString(R.string.error_title_imageNotAllowed),
                            message = resources.getString(R.string.error_description_imageNotAllowed)
                        )
                    }
                    RejectionReason.MODERATION -> {
                        when (cause.rejection.flaggedCategory) {
                            ModerationResult.FlaggedCategory.NONE -> {
                                BottomBarManager.showAlert(
                                    title = resources.getString(R.string.error_title_imageNotAllowed),
                                    message = resources.getString(R.string.error_description_imageNotAllowed)
                                )
                            }

                            ModerationResult.FlaggedCategory.OTHER -> {
                                BottomBarManager.showAlert(
                                    title = resources.getString(R.string.error_title_profilePhotoNotAllowed),
                                    message = resources.getString(R.string.error_description_profilePhotoNotAllowedFlaggedOther)
                                )
                            }

                            ModerationResult.FlaggedCategory.NSFW -> {
                                BottomBarManager.showAlert(
                                    title = resources.getString(R.string.error_title_profilePhotoNotAllowed),
                                    message = resources.getString(R.string.error_description_profilePhotoNotAllowedFlaggedNsfw)
                                )
                            }

                            ModerationResult.FlaggedCategory.IMPERSONATION -> {
                                BottomBarManager.showAlert(
                                    title = resources.getString(R.string.error_title_profilePhotoNotAllowed),
                                    message = resources.getString(R.string.error_description_profilePhotoNotAllowedFlaggedImpersonation)
                                )
                            }

                            ModerationResult.FlaggedCategory.MISLEADING -> {
                                BottomBarManager.showAlert(
                                    title = resources.getString(R.string.error_title_profilePhotoNotAllowed),
                                    message = resources.getString(R.string.error_description_profilePhotoNotAllowedFlaggedMisleading)
                                )
                            }

                            ModerationResult.FlaggedCategory.SPAM -> {
                                BottomBarManager.showAlert(
                                    title = resources.getString(R.string.error_title_profilePhotoNotAllowed),
                                    message = resources.getString(R.string.error_description_profilePhotoNotAllowedFlaggedSpam)
                                )
                            }
                        }
                    }
                    RejectionReason.UNSUPPORTED_TYPE -> {
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_moderationFailed),
                            message = resources.getString(R.string.error_description_moderationFailed),
                        )
                    }
                    RejectionReason.MISMATCHED_TYPE -> {
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_moderationFailed),
                            message = resources.getString(R.string.error_description_moderationFailed),
                        )
                    }
                    RejectionReason.TOO_LARGE -> {
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_moderationFailed),
                            message = resources.getString(R.string.error_description_moderationFailed),
                        )
                    }
                    RejectionReason.CORRUPT -> {
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_moderationFailed),
                            message = resources.getString(R.string.error_description_moderationFailed),
                        )
                    }
                    RejectionReason.INTERNAL -> {
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_moderationFailed),
                            message = resources.getString(R.string.error_description_moderationFailed),
                        )
                    }
                    RejectionReason.PRIVACY_METADATA -> {
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_moderationFailed),
                            message = resources.getString(R.string.error_description_moderationFailed),
                        )
                    }
                }
            }
            is ValidationException -> {
                BottomBarManager.showAlert(
                    title = resources.getString(R.string.error_title_imageNotSupported),
                    message = resources.getString(R.string.error_description_imageNotSupported)
                )
            }

            else -> {
                BottomBarManager.showError(
                    title = resources.getString(R.string.error_title_moderationFailed),
                    message = resources.getString(R.string.error_description_moderationFailed),
                )
            }
        }
    }

    companion object {

        internal val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                Event.ConfirmImageChange -> { state -> state }
                Event.CheckImage -> { state -> state }
                is Event.OnSavedPictureLoaded -> { state ->
                    state.copy(savedPicture = event.picture)
                }
                is Event.UploadPolicyLoaded -> { state -> state.copy(uploadPolicy = event.policy) }
                is Event.UpdateProcessingState -> { state ->
                    val current = state.processingState
                    state.copy(
                        processingState = current.copy(
                            loading = event.loading,
                            success = event.success
                        )
                    )
                }

                Event.OnImageApproved -> { state -> state }
                Event.DiscardChanges -> { state -> state }
                is Event.OnImageCached -> { state ->
                    state.copy(image = Loadable.Loaded(event.image), imageMimeType = event.mimeType)
                }
                Event.OnImageCleared -> { state ->
                    state.copy(image = Loadable.Loading())
                }
                is Event.OnImageSelected -> { state ->
                    state.copy(image = Loadable.Loading(event.image))
                }
            }
        }
    }
}