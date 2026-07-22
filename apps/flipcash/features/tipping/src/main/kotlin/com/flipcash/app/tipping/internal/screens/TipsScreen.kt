package com.flipcash.app.tipping.internal.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.tipping.TipStep
import com.flipcash.app.tipping.internal.TipFlowViewModel
import com.flipcash.features.tipping.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

@Composable
internal fun TipsScreen() {
    val viewModel = flowSharedViewModel<TipFlowViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    val navigator = LocalCodeNavigator.current
    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_tips),
                titleAlignment = Alignment.CenterHorizontally,
                endContent = {
                    AppBarDefaults.Close { navigator.hide() }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            stickyHeader {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = CodeTheme.dimens.inset)
                        .background(CodeTheme.colors.background),
                    contentAlignment = Alignment.Center
                ) {
                    CodeButton(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset),
                        text = "Show My Tipcard",
                        buttonState = ButtonState.Filled,
                        onClick = {
                            navigator.navigate(TipStep.TipCard)
                        },
                    )
                }
            }

            items(state.tipChats) { chat ->

            }
        }
    }
}
