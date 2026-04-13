package com.flipcash.app.currencycreator.internal.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.flipcash.app.core.data.isLoaded
import com.flipcash.app.core.data.isLoading
import com.flipcash.app.core.tokens.CurrencyCreatorResult
import com.flipcash.app.core.tokens.CurrencyCreatorStep
import com.flipcash.app.core.ui.transitions.CircleOverlayClip
import com.flipcash.app.core.ui.transitions.SharedTransition
import com.flipcash.app.core.ui.transitions.sharedBoundsTransition
import com.flipcash.app.currencycreator.internal.CurrencyCreatorViewModel
import com.flipcash.core.R
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.theme.White50
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeScaffold
import com.getcode.utils.TraceType
import com.getcode.utils.trace

@Composable
internal fun IconSelectionScreen() {
    val viewModel = flowSharedViewModel<CurrencyCreatorViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    IconSelectionContent(state, viewModel::dispatchEvent)
}


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun IconSelectionContent(
    state: CurrencyCreatorViewModel.State,
    dispatch: (CurrencyCreatorViewModel.Event) -> Unit
) {
    val flowNavigator = rememberFlowNavigator<CurrencyCreatorStep, CurrencyCreatorResult>()
    val pickMedia = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            trace(tag = "CurrencyCreator", message = "image selected @ $uri", type = TraceType.User)
            dispatch(CurrencyCreatorViewModel.Event.OnIconSelected(uri))
        } else {
            trace(tag = "CurrencyCreator", message = "No image selected", type = TraceType.User)
        }
    }

    CodeScaffold(
        modifier = Modifier
            .padding(horizontal = CodeTheme.dimens.inset),
        topBar = {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(top = CodeTheme.dimens.grid.x8),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
            ) {
                Text(
                    text = stringResource(R.string.title_currencyCreatorIconSelection),
                    style = CodeTheme.typography.textLarge,
                    color = CodeTheme.colors.textMain,
                )

                Text(
                    modifier = Modifier
                        .padding(horizontal = CodeTheme.dimens.inset),
                    text = stringResource(R.string.subtitle_currencyCreatorIconSelection),
                    style = CodeTheme.typography.textSmall,
                    textAlign = TextAlign.Center,
                    color = CodeTheme.colors.textSecondary,
                )
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset),
            ) {
                Text(
                    text = stringResource(R.string.subtitle_currencyCreatorIconRecommended),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                )

                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = CodeTheme.dimens.grid.x3),
                    text = stringResource(R.string.action_next),
                    enabled = state.icon.isLoaded(),
                    onClick = {
                        flowNavigator.navigateTo(CurrencyCreatorStep.DescriptionSelection())
                    },
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset),
            ) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .sharedBoundsTransition(
                            transition = SharedTransition.CurrencyIcon,
                        )
                        .background(
                            color = CodeTheme.colors.divider,
                            shape = CircleShape,
                        ).clip(CircleShape)
                        .clickable {
                            pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(targetState = state.icon) { icon ->
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

                val name = state.nameFieldState.text

                Text(
                    modifier = Modifier.sharedBoundsTransition(
                        transition = SharedTransition.CurrencyName,
                    ),
                    text = if (name.isNotBlank()) name.toString() else stringResource(R.string.placeholder_currencyName),
                    style = CodeTheme.typography.displaySmall,
                    color = Color.White,
                )
            }
        }
    }
}