package com.flipcash.app.userflags.internal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.data.Loadable
import com.getcode.navigation.scrim.LocalScrimController
import com.flipcash.app.userflags.internal.components.EditableRow
import com.flipcash.app.userflags.internal.components.ReadOnlyRow
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.text.SectionHeader
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.utils.sheetResignmentBehavior

@Composable
internal fun UserFlagsEditor(viewModel: UserFlagsViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    UserFlagsEditor(state, viewModel::dispatchEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserFlagsEditor(
    state: UserFlagsViewModel.State,
    dispatch: (UserFlagsViewModel.Event) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {

        when (state.flags) {
            is Loadable.Error -> Unit
            is Loadable.Loaded -> {
                val scrim = LocalScrimController.current
                var editingField by remember { mutableStateOf<EditableEntry<*>?>(null) }
                val editField = { field: EditableEntry<*> ->
                    scrim.show(
                        alignment = Alignment.BottomCenter,
                        enterTransition = slideInVertically { it },
                        exitTransition = slideOutVertically { it },
                        onDismiss = { editingField = null },
                        content = {
                            field.SheetContent(
                                onDismiss = { scrim.dismiss() },
                                dispatch = dispatch
                            )
                        }
                    )
                }

                val listState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScrollStateGradient(scrollState = listState, isLongGradient = true)
                        .sheetResignmentBehavior(listState),
                    state = listState,
                    contentPadding = PaddingValues(bottom = CodeTheme.dimens.grid.x3),
                ) {
                    // Read-only section
                    item { SectionHeader("Read-Only") }
                    items(state.readOnlyEntries) { entry ->
                        ReadOnlyRow(entry)
                    }

                    // Editable section
                    item { SectionHeader("Overridable") }
                    items(state.editableEntries) { entry ->
                        EditableRow(
                            modifier = Modifier.fillMaxWidth(),
                            entry = entry,
                            onClick = { editField(entry) },
                            onClear = { dispatch(UserFlagsViewModel.Event.ResetFlag(entry)) },
                        )
                    }
                }
            }

            is Loadable.Loading -> {
                CodeCircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}