package com.flipcash.app.userprofile.internal.photo

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.flipcash.app.blob.BlobStorageCoordinator
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.extensions.flatMapResult
import com.flipcash.app.core.extensions.onResult
import com.flipcash.services.models.blob.UploadPolicy
import com.flipcash.features.userprofile.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.controllers.ModerationController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.ImageModerationError
import com.flipcash.services.models.ModerationResult
import com.flipcash.services.models.TextModerationError
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.core.errors.ValidationException
import com.getcode.util.resources.ContentReader
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.resources.uploadMimeFor
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
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
    private val resources: ResourceHelper,
    val contentReader: ContentReader,
) : BaseViewModel<PhotoSelectionViewModel.State, PhotoSelectionViewModel.Event>(
    initialState = State(name = userManager.profile?.displayName.orEmpty()),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val name: String,
        val image: Loadable<Uri> = Loadable.Loading(),
        val attestation: ModerationResult.Attestation = ModerationResult.Attestation.Empty,
        val processingState: LoadingSuccessState = LoadingSuccessState(),
        // Server upload constraints (accepted MIME types + size ceilings), used to filter selection.
        val uploadPolicy: UploadPolicy? = null,
        // MIME type of the re-encoded image bytes to upload; resolved from the selected image.
        val imageMimeType: String = uploadMimeFor(null),
    )

    sealed interface Event {
        data object CheckImage : Event
        data class UploadPolicyLoaded(val policy: UploadPolicy) : Event
        data class UpdateProcessingState(
            val loading: Boolean = false,
            val success: Boolean = false
        ) : Event

        data object OnImageApproved : Event
        data class OnImageSelected(val image: Uri) : Event
        data class OnImageCached(val image: Uri, val mimeType: String) : Event
        data object OnImageCleared : Event
    }

    init {
        // Observe the policy — the coordinator serves the launch-preloaded cache and self-refreshes
        // it if it has aged past its ttl, re-emitting the fresh value here.
        blobStorage.policy
            .filterNotNull()
            .onEach { dispatchEvent(Event.UploadPolicyLoaded(it)) }
            .launchIn(viewModelScope)


        eventFlow
            .filterIsInstance<Event.OnImageSelected>()
            .mapNotNull { event ->
                val sourceMime = contentReader.mimeType(event.image)
                val cached = contentReader.copyToCache(
                    uri = event.image,
                    fileName = "user_profile_${System.nanoTime()}",
                    maxSize = 500,
                    mimeType = sourceMime,
                ) ?: return@mapNotNull null
                // The cache re-encodes (stripping EXIF); declare the type those bytes actually are.
                cached to uploadMimeFor(sourceMime)
            }
            .flowOn(dispatchers.IO)
            .onEach { (cached, mime) -> dispatchEvent(Event.OnImageCached(cached, mime)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CheckImage>()
            .mapNotNull { stateFlow.value.image.dataOrNull }
            .onEach { dispatchEvent(Event.UpdateProcessingState(loading = true)) }
            .map { moderationController.moderateImage(it) }
            .flatMapResult { result ->
                when (result.flaggedCategory) {
                    ModerationResult.FlaggedCategory.NONE -> Result.success(result.attestation)
                    else -> Result.failure(ImageModerationError.Flagged(result.flaggedCategory))
                }
            }
            .flatMapResult {
                // Moderation passed — upload the image bytes to storage in one coordinated
                // call, then set the returned blob as the profile picture.
                val uri = stateFlow.value.image.dataOrNull
                    ?: return@flatMapResult Result.failure(IllegalStateException("No image selected"))
                val bytes = contentReader.readBytes(uri)
                    ?: return@flatMapResult Result.failure(IllegalStateException("Unable to read image"))
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
                    stateFlow.value.image.dataOrNull?.let { contentReader.removeFromCache(it) }
                    dispatchEvent(Event.OnImageCleared)
                    when (cause) {
                        is ValidationException,
                        is ImageModerationError.Flagged,
                        is ImageModerationError.Denied -> {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_imageNotAllowed),
                                message = resources.getString(R.string.error_description_imageNotAllowed)
                            )
                        }

                        is ImageModerationError.UnsupportedFormat -> {
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
            )
            .launchIn(viewModelScope)
    }

    companion object {

        private val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                Event.CheckImage -> { state -> state }
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