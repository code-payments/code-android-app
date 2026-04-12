package com.flipcash.app.core.ui.processing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.CodeScaffold
import com.getcode.view.LoadingSuccessState

/**
 * A reusable processing screen layout with a centered loading indicator,
 * title/subtitle text, and optional top/bottom bars.
 *
 * @param processingState The current loading/success/error state.
 * @param title Text to display for each state (loading, success, error).
 * @param subtitle Text to display below the title for each state.
 * @param topBar Optional top bar composable.
 * @param bottomBar Optional bottom bar composable.
 */
@Composable
fun FlowProcessingScreen(
    processingState: LoadingSuccessState,
    title: @Composable (LoadingSuccessState.State) -> Unit,
    subtitle: @Composable (LoadingSuccessState.State) -> Unit,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
) {
    CodeScaffold(
        topBar = topBar,
        bottomBar = bottomBar,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x6),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.24f)
                        .aspectRatio(1f),
                ) {
                    ProcessingLoadingIndicator(
                        processingState = processingState,
                        modifier = Modifier.matchParentSize(),
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    title(processingState.state)
                    subtitle(processingState.state)
                }
            }
        }
    }
}
