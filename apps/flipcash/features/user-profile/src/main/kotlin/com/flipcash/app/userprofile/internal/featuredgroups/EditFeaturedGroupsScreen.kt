package com.flipcash.app.userprofile.internal.featuredgroups

import androidx.compose.foundation.Image
import com.getcode.ui.core.verticalScrollStateGradient
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import com.getcode.ui.components.glass.floatingGlass
import com.getcode.ui.components.TopScrollEdge
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.userprofile.UpdateProfileResult
import com.flipcash.app.core.userprofile.UpdateProfileStep
import com.flipcash.core.R
import com.flipcash.shared.common.ui.profile.FeaturedGroupRow
import com.flipcash.shared.common.ui.profile.rememberFeaturedGroupItems
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.SearchInput
import com.getcode.ui.theme.CodeButton
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** A group past the limit is shown, but not offered, while the selection is full. */
private const val DisabledAlpha = 0.4f

/** Save's height: the list starts to fade from the button's top edge. */
private val FadeAboveSaveBottom = 60.dp

@Composable
internal fun EditFeaturedGroupsScreen() {
    val flowNavigator = rememberFlowNavigator<UpdateProfileStep, UpdateProfileResult>()

    val viewModel = hiltViewModel<EditFeaturedGroupsViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    EditFeaturedGroupsScreenContent(
        state = state,
        onBack = { flowNavigator.back() },
        dispatchEvent = viewModel::dispatchEvent,
    )

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<EditFeaturedGroupsViewModel.Event.OnSaved>()
            .onEach { flowNavigator.proceed() }
            .launchIn(this)
    }
}

@Composable
private fun EditFeaturedGroupsScreenContent(
    state: EditFeaturedGroupsViewModel.State,
    onBack: () -> Unit,
    dispatchEvent: (EditFeaturedGroupsViewModel.Event) -> Unit,
) {
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(0.dp) }
    var barHeight by remember { mutableStateOf(0.dp) }
    val hazeState = rememberHazeState()
    val listState = rememberLazyListState()
    val saveBottomPadding = CodeTheme.dimens.staticGrid.x5
    val navBottom = WindowInsets.navigationBars.getBottom(density).toFloat()

    val loading = state.loadState == EditFeaturedGroupsViewModel.LoadState.Loading
    val items = rememberFeaturedGroupItems(state.visibleCandidates)

    // The list runs under the header and Save; search and Save frost what passes behind them.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CodeTheme.colors.background)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = CodeTheme.colors.textSecondary,
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    // Rows pass behind the Save glass and fade out from its top edge to the bottom of the screen.
                    .verticalScrollStateGradient(
                        scrollState = listState,
                        showAtStart = false,
                        fadeSize = FadeAboveSaveBottom + saveBottomPadding +
                            with(density) { navBottom.toDp() },
                    )
                    .hazeSource(hazeState),
                contentPadding = PaddingValues(top = headerHeight, bottom = barHeight),
            ) {
                item(key = "count") {
                    Text(
                        modifier = Modifier.padding(
                            horizontal = CodeTheme.dimens.inset,
                            vertical = CodeTheme.dimens.staticGrid.x4,
                        ),
                        text = stringResource(
                            R.string.label_featuredGroupsSelected,
                            state.selection.size.toString(),
                            FeaturedGroupsLimit.toString(),
                        ),
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.textSecondary,
                    )
                }
                items(items, key = { it.chatId.toString() }) { group ->
                    val selected = group.chatId in state.selection
                    val enabled = state.isEnabled(group.chatId)
                    FeaturedGroupRow(
                        group = group,
                        modifier = Modifier
                            .alpha(if (enabled) 1f else DisabledAlpha)
                            .clickable(enabled = enabled, role = Role.Checkbox) {
                                dispatchEvent(EditFeaturedGroupsViewModel.Event.Toggle(group.chatId))
                            }
                            .padding(
                                horizontal = CodeTheme.dimens.inset,
                                vertical = CodeTheme.dimens.staticGrid.x2,
                            ),
                        trailing = {
                            Image(
                                painter = painterResource(
                                    if (selected) R.drawable.ic_checked else R.drawable.ic_unchecked
                                ),
                                contentDescription = null,
                            )
                        },
                    )
                }
                if (state.candidates.isNotEmpty()) {
                    item(key = "footer") {
                        Text(
                            modifier = Modifier.padding(
                                horizontal = CodeTheme.dimens.inset,
                                vertical = CodeTheme.dimens.staticGrid.x4,
                            ),
                            text = stringResource(R.string.footer_featuredGroups),
                            style = CodeTheme.typography.textSmall,
                            color = CodeTheme.colors.textSecondary,
                        )
                    }
                }
            }

            val message = when {
                state.loadState == EditFeaturedGroupsViewModel.LoadState.Failed ->
                    R.string.text_featuredGroupsLoadFailed
                state.candidates.isEmpty() -> R.string.text_featuredGroupsEmpty
                else -> null
            }
            if (message != null) {
                Text(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = CodeTheme.dimens.grid.x8),
                    text = stringResource(message),
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Rows blur in as they pass under the header (as iOS's soft scroll edge does) rather than
        // meeting a hard edge or a fade.
        TopScrollEdge(hazeState = hazeState, listState = listState, height = headerHeight)

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { headerHeight = with(density) { it.height.toDp() } },
        ) {
            AppBarWithTitle(
                title = stringResource(R.string.title_featuredGroupsPicker),
                titleAlignment = Alignment.CenterHorizontally,
                hazeState = hazeState,
                onBackIconClicked = onBack,
            )
            SearchInput(
                state = state.queryState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(top = CodeTheme.dimens.staticGrid.x2)
                    .floatingGlass(hazeState),
                placeholder = stringResource(R.string.hint_searchPublicGroups),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
        ) {
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(
                        top = CodeTheme.dimens.staticGrid.x3,
                        bottom = saveBottomPadding,
                    )
                    .floatingGlass(hazeState, CodeTheme.shapes.small),
                text = stringResource(R.string.action_save),
                enabled = state.canSave,
                isLoading = state.processingState.loading,
                isSuccess = state.processingState.success,
                onClick = { dispatchEvent(EditFeaturedGroupsViewModel.Event.Save) },
            )
        }
    }
}

