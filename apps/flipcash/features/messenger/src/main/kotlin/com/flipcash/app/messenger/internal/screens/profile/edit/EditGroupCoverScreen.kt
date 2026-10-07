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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.shared.common.ui.profile.ProfileCover
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
 * The group's cover. The banner is the picker: the stored cover shows in it until a pick replaces
 * it, and Save stays off until one is made. Drawn at the height it has on Edit Group and clipped the
 * same way, so what is picked is what the profile will show.
 */
@Composable
internal fun EditGroupCoverScreen(chatViewModel: ChatViewModel) {
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    val chatState by chatViewModel.stateFlow.collectAsStateWithLifecycle()
    val group = chatState.subject as? ChatSubject.Group

    val viewModel = hiltViewModel<EditGroupCoverViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(group?.chatId) {
        val resolved = group ?: return@LaunchedEffect
        viewModel.dispatchEvent(
            EditGroupCoverViewModel.Event.Initialize(
                chatId = resolved.chatId,
                cover = resolved.coverPicture,
            )
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<EditGroupCoverViewModel.Event.OnCoverAccepted>()
            .onEach { flowNavigator.back() }
            .launchIn(this)
    }

    val pickMedia = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            trace(tag = TAG, message = "image selected @ $uri", type = TraceType.User)
            viewModel.dispatchEvent(EditGroupCoverViewModel.Event.OnImageSelected(uri))
        } else {
            trace(tag = TAG, message = "No image selected", type = TraceType.User)
        }
    }

    Column {
        AppBarWithTitle(
            title = stringResource(R.string.title_cover),
            titleAlignment = Alignment.CenterHorizontally,
            // The pick is dropped by leaving: the view model is scoped to this nav entry.
            onBackIconClicked = { flowNavigator.back() },
        )
        CodeScaffold(
            modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
            topBar = {},
            bottomBar = {
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
                        viewModel.dispatchEvent(EditGroupCoverViewModel.Event.SaveClicked)
                    },
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(top = CodeTheme.dimens.staticGrid.x4),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CoverBannerHeight)
                        .clip(CodeTheme.shapes.medium)
                        .background(CodeTheme.colors.surfaceVariant)
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

                            // No pick pending: the stored cover, so the screen opens on what the
                            // group has. Save stays disabled until a pick is made.
                            state.savedCover != null -> {
                                ProfileCover(
                                    image = state.savedCover,
                                    access = BlobAccessContext.Owned,
                                    modifier = Modifier.fillMaxSize(),
                                    height = Dp.Unspecified,
                                )
                            }

                            else -> {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = CodeTheme.colors.textSecondary,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val CoverBannerHeight = 214.dp

private const val TAG = "EditGroupCover"
