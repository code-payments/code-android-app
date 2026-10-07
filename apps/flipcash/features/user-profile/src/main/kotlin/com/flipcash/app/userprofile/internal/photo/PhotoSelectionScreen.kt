package com.flipcash.app.userprofile.internal.photo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.flipcash.app.core.data.isLoaded
import com.flipcash.app.core.data.isLoading
import com.flipcash.app.core.ui.transitions.SharedTransition
import com.flipcash.app.core.ui.transitions.sharedBoundsTransition
import com.flipcash.app.core.userprofile.UpdateProfileResult
import com.flipcash.app.core.userprofile.UpdateProfileStep
import com.flipcash.core.R
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.shared.common.ui.profile.ProfileCover
import com.flipcash.shared.common.ui.profile.ProfileCoverAspectRatio
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.White50
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.rememberKeyboardController
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
internal fun PhotoSelectionScreen(slot: PhotoSelectionViewModel.Slot = PhotoSelectionViewModel.Slot.Avatar) {
    val flowNavigator = rememberFlowNavigator<UpdateProfileStep, UpdateProfileResult>()

    val viewModel = hiltViewModel<PhotoSelectionViewModel, PhotoSelectionViewModel.Factory>(
        creationCallback = { factory -> factory.create(slot) },
    )
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    val keyboard = rememberKeyboardController()

    Column {
        AppBarWithTitle(
            title = stringResource(
                if (slot == PhotoSelectionViewModel.Slot.Cover) R.string.title_cover else R.string.title_setProfilePicture
            ),
            titleAlignment = Alignment.CenterHorizontally,
            onBackIconClicked = {
                keyboard.hideIfVisible {
                    // Leaving throws the pick away rather than carrying it back in; the stored
                    // picture is whatever it was before the step opened.
                    viewModel.dispatchEvent(PhotoSelectionViewModel.Event.DiscardChanges)
                    flowNavigator.back()
                }
            },
        )
        PhotoSelectionScreenContent(state, viewModel::dispatchEvent)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<PhotoSelectionViewModel.Event.OnImageApproved>()
            .onEach { flowNavigator.proceed() }
            .launchIn(this)
    }
}

@Composable
private fun PhotoSelectionScreenContent(
    state: PhotoSelectionViewModel.State,
    dispatchEvent: (PhotoSelectionViewModel.Event) -> Unit,
) {
    val pickMedia = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            trace(tag = "UserProfile", message = "image selected @ $uri", type = TraceType.User)
            dispatchEvent(PhotoSelectionViewModel.Event.OnImageSelected(uri))
        } else {
            trace(tag = "UserProfile", message = "No image selected", type = TraceType.User)
        }
    }

    CodeScaffold(
        modifier = Modifier
            .padding(horizontal = CodeTheme.dimens.inset),
        // The app bar carries the title now; the body is just the photo and the name.
        topBar = {},
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset),
            ) {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = CodeTheme.dimens.staticGrid.x4),
                    text = stringResource(R.string.action_save),
                    enabled = state.isChanged && state.processingState.isIdle,
                    isLoading = state.processingState.loading,
                    isSuccess = state.processingState.success,
                    onClick = {
                        dispatchEvent(PhotoSelectionViewModel.Event.ConfirmImageChange)
                    },
                )
            }
        }
    ) { padding ->
        val isCover = state.slot == PhotoSelectionViewModel.Slot.Cover
        // The cover sits at the top like iOS's ChangeCoverPictureScreen; the avatar stays centred.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(top = if (isCover) CodeTheme.dimens.staticGrid.x4 else 0.dp),
            contentAlignment = if (isCover) Alignment.TopCenter else Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset),
            ) {
                // The avatar is a circle that shares its bounds with the name step; the cover is
                // previewed at the banner's own aspect so the crop matches what the profile shows.
                val pictureShape = if (isCover) CodeTheme.shapes.medium else CircleShape
                Box(
                    modifier = (if (isCover) {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(ProfileCoverAspectRatio)
                    } else {
                        Modifier
                            .size(150.dp)
                            .sharedBoundsTransition(
                                transition = SharedTransition.CurrencyIcon,
                            )
                    })
                        .background(
                            color = if (isCover) CodeTheme.colors.surfaceVariant else CodeTheme.colors.divider,
                            shape = pictureShape,
                        ).clip(pictureShape)
                        .clickable {
                            pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(targetState = state.image) { icon ->
                        when {
                            icon.isLoaded() -> {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalPlatformContext.current)
                                        .data(icon.data)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    onError = {
                                        it.result.throwable.printStackTrace()
                                    },
                                )
                            }
                            icon.isLoading() && icon.dataOrNull != null -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CodeCircularProgressIndicator()
                                }
                            }
                            // No pick pending: show whatever picture is already stored, so the
                            // step opens on the current avatar rather than an empty well. It is a
                            // server-side MediaItem, so it can't be mistaken for a pick — Save
                            // stays disabled until one is made.
                            state.savedPicture != null && isCover -> {
                                ProfileCover(
                                    image = state.savedPicture,
                                    access = BlobAccessContext.Owned,
                                    modifier = Modifier.fillMaxSize(),
                                    height = Dp.Unspecified,
                                )
                            }
                            state.savedPicture != null -> {
                                ContactAvatar(
                                    image = state.savedPicture,
                                    displayName = state.name,
                                    access = BlobAccessContext.Owned,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            else -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = if (isCover) CodeTheme.colors.textSecondary else White50,
                                        modifier = Modifier.size(48.dp),
                                    )
                                }
                            }
                        }
                    }
                }


                if (!isCover) {
                    Text(
                        modifier = Modifier.sharedBoundsTransition(
                            transition = SharedTransition.CurrencyName,
                        ),
                        text = state.name.ifBlank { stringResource(R.string.placeholder_profileDisplayName) },
                        style = CodeTheme.typography.displaySmall,
                        color = Color.White,
                    )
                }
            }
        }
    }
}