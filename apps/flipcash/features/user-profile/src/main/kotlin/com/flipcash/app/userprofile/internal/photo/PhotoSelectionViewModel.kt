package com.flipcash.app.userprofile.internal.photo

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.flipcash.app.blob.BlobStorageCoordinator
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.extensions.flatMapResult
import com.flipcash.app.core.extensions.onResult
import com.flipcash.services.models.blob.ImageConstraints
import com.flipcash.services.models.blob.UploadPolicy
import com.flipcash.features.userprofile.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.controllers.ModerationController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.BlobRejectedException
import com.flipcash.services.models.ImageModerationError
import com.flipcash.services.models.ModerationResult
import com.flipcash.services.models.TextModerationError
import com.flipcash.services.models.chat.RejectionReason
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.floor
import kotlin.math.sqrt
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
                // The cache re-encodes to JPEG/PNG, so gate on the type we'd actually upload —
                // not the source type, which may normalize into an accepted format (e.g. HEIC → PNG).
                val uploadMime = uploadMimeFor(sourceMime)
                val policy = stateFlow.value.uploadPolicy
                val constraints = policy?.constraintsFor(uploadMime)
                if (policy != null && constraints == null) {
                    rejectImage(
                        title = R.string.error_title_imageNotSupported,
                        message = R.string.error_description_imageNotSupported,
                    )
                    return@mapNotNull null
                }
                // Re-encode within the policy's dimension + pixel caps, then keep shrinking the
                // longest edge until the bytes fit maxSizeBytes — resize to fit, don't reject.
                val maxBytes = constraints?.maxSizeBytes
                val cached = cacheWithinPolicy(
                    uri = event.image,
                    sourceMime = sourceMime,
                    image = constraints?.image,
                    maxBytes = maxBytes,
                ) ?: return@mapNotNull null
                // Last resort: if even the smallest re-encode can't meet the byte ceiling, reject.
                if (maxBytes != null && (contentReader.size(cached) ?: 0L) > maxBytes) {
                    contentReader.removeFromCache(cached)
                    rejectImage(
                        title = R.string.error_title_imageTooLarge,
                        message = R.string.error_description_imageTooLarge,
                    )
                    return@mapNotNull null
                }
                // The cache re-encodes (stripping EXIF); declare the type those bytes actually are.
                cached to uploadMime
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
                    stateFlow.value.image.dataOrNull?.let { contentReader.removeFromCache(it) }
                    dispatchEvent(Event.OnImageCleared)
                    handleUploadFailure(cause)
                }
            )
            .launchIn(viewModelScope)
    }

    /** Clears the pending selection and surfaces [title]/[message] to the user. */
    private fun rejectImage(@StringRes title: Int, @StringRes message: Int) {
        dispatchEvent(Event.OnImageCleared)
        BottomBarManager.showAlert(
            title = resources.getString(title),
            message = resources.getString(message),
        )
    }

    /**
     * Re-encodes [uri] into the cache honoring [image]'s dimension caps, then shrinks the
     * longest-edge target until the output fits [maxBytes] — resizing to fit rather than rejecting.
     * Returns null only if re-encoding fails outright; otherwise the smallest attempt (which the
     * caller re-checks, since a byte ceiling smaller than [MIN_MAX_EDGE] can produce is pathological).
     */
    private fun cacheWithinPolicy(
        uri: Uri,
        sourceMime: String?,
        image: ImageConstraints?,
        maxBytes: Long?,
    ): Uri? {
        var edge = maxEdgeFor(image)
        var last: Uri? = null
        repeat(MAX_RESIZE_ATTEMPTS) {
            // Drop the previous over-ceiling attempt before making a smaller one.
            last?.let { contentReader.removeFromCache(it) }
            val candidate = contentReader.copyToCache(
                uri = uri,
                fileName = "user_profile_${System.nanoTime()}",
                maxSize = edge,
                mimeType = sourceMime,
            ) ?: return null
            last = candidate
            val size = contentReader.size(candidate) ?: 0L
            if (maxBytes == null || size <= maxBytes) return candidate
            // Encoded bytes track pixel area (edge²); scale the edge by √(ceiling/actual) with a
            // safety margin to converge, floored at MIN_MAX_EDGE.
            val next = floor(edge * sqrt(maxBytes.toDouble() / size) * RESIZE_SAFETY)
                .toInt()
                .coerceAtLeast(MIN_MAX_EDGE)
            if (next >= edge) return candidate // already at the floor — hand back the best effort
            edge = next
        }
        return last
    }

    /**
     * The longest-edge cap that satisfies every dimension constraint in [image]: the smallest of
     * maxWidth, maxHeight, and √maxPixels (bounding the longest edge by √maxPixels keeps total area
     * ≤ maxPixels). Falls back to [DEFAULT_MAX_EDGE] when the policy names no image constraints.
     */
    private fun maxEdgeFor(image: ImageConstraints?): Int {
        val caps = listOfNotNull(
            image?.maxWidth,
            image?.maxHeight,
            image?.maxPixels?.let { floor(sqrt(it.toDouble())).toInt() },
        ).filter { it > 0 }
        return caps.minOrNull() ?: DEFAULT_MAX_EDGE
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

        // Longest-edge downscale target used when the upload policy specifies no dimension caps.
        private const val DEFAULT_MAX_EDGE = 500

        // Floor for the resize-to-fit loop — below this a profile image is no longer worth keeping.
        private const val MIN_MAX_EDGE = 64

        // How many times to shrink-and-retry before handing back the smallest attempt.
        private const val MAX_RESIZE_ATTEMPTS = 5

        // Under-shoot the estimated fitting edge so re-encode overhead doesn't push us back over.
        private const val RESIZE_SAFETY = 0.9

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