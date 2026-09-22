package com.flipcash.app.messenger.internal.screens.profile.edit

import android.os.Parcelable
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.data.isLoaded
import com.flipcash.app.core.data.isLoading
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.White50
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeScaffold
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The group's picture, behind node 10187:110373's Icon row.
 *
 * Laid out as `PhotoSelectionScreen` is — a round well that opens the picker, the stored picture
 * inside it until a pick replaces it, Save underneath — so the two picture edits in the app are
 * the same screen to use. The view model behind it differs because the write does:
 * `EditChat` against a group rather than the signed-in user's own profile.
 */
@Composable
internal fun EditGroupPictureScreen(chatViewModel: ChatViewModel) {
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    val chatState by chatViewModel.stateFlow.collectAsStateWithLifecycle()
    val group = chatState.subject as? ChatSubject.Group

    val viewModel = hiltViewModel<EditGroupPictureViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(group?.chatId) {
        val resolved = group ?: return@LaunchedEffect
        viewModel.dispatchEvent(
            EditGroupPictureViewModel.Event.Initialize(
                chatId = resolved.chatId,
                picture = resolved.picture,
                title = resolved.groupTitle.orEmpty(),
            )
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<EditGroupPictureViewModel.Event.OnPictureAccepted>()
            .onEach { flowNavigator.back() }
            .launchIn(this)
    }

    val pickMedia = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            trace(tag = TAG, message = "image selected @ $uri", type = TraceType.User)
            viewModel.dispatchEvent(EditGroupPictureViewModel.Event.OnImageSelected(uri))
        } else {
            trace(tag = TAG, message = "No image selected", type = TraceType.User)
        }
    }

    Column {
        AppBarWithTitle(
            title = stringResource(R.string.title_setGroupPicture),
            titleAlignment = Alignment.CenterHorizontally,
            // The pick is dropped by leaving: the view model is scoped to this nav entry, so
            // popping it takes the draft with it and the stored picture stands.
            onBackIconClicked = { flowNavigator.back() },
        )
        CodeScaffold(
            modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
            // The bar sits outside the scaffold's horizontal inset, as PhotoSelectionScreen and
            // EditGroupNameScreen place theirs. Nesting it inside indents the back control by
            // [inset], putting it out of line with the back control on every other screen.
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
                            .padding(bottom = CodeTheme.dimens.grid.x3),
                        text = stringResource(R.string.action_save),
                        enabled = state.canSubmit,
                        isLoading = state.processingState.loading,
                        isSuccess = state.processingState.success,
                        onClick = {
                            viewModel.dispatchEvent(EditGroupPictureViewModel.Event.SaveClicked)
                        },
                    )
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .background(color = CodeTheme.colors.divider, shape = CircleShape)
                        .clip(CircleShape)
                        .clickable {
                            pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(targetState = state.image) { pending ->
                        when {
                            pending.isLoaded() -> {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalPlatformContext.current)
                                        .data(pending.data)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            pending.isLoading() && pending.dataOrNull != null -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CodeCircularProgressIndicator()
                                }
                            }

                            // No pick pending: the group's stored picture, so the screen opens on the
                            // current icon rather than an empty well. Save stays disabled until a pick
                            // is made, so this cannot be mistaken for one.
                            state.savedPicture != null -> {
                                ContactAvatar(
                                    image = state.savedPicture,
                                    displayName = state.groupTitle,
                                    access = BlobAccessContext.Owned,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            else -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = White50,
                                        modifier = Modifier.size(48.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val TAG = "EditGroupPicture"
