package com.flipcash.app.myaccount.internal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.menu.MenuList
import com.getcode.theme.CodeTheme

@Composable
internal fun MyAccountScreen(viewModel: MyAccountScreenViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    MyAccountScreenContent(state = state, dispatch = viewModel::dispatchEvent)
}

@Composable
private fun MyAccountScreenContent(
    state: MyAccountScreenViewModel.State,
    dispatch: (MyAccountScreenViewModel.Event) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        AnimatedContent(
            modifier = Modifier.fillMaxWidth(),
            targetState = state.showAccountInfo,
            transitionSpec = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Down) togetherWith
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Up)
            }
        ) { show ->
            if (show) {
                AccountInfoHeader(
                    state = state,
                    dispatch = dispatch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = CodeTheme.dimens.inset,
                            vertical = CodeTheme.dimens.grid.x2
                        )
                )
            } else {
                Spacer(Modifier.fillMaxWidth())
            }
        }

        MenuList(
            modifier = Modifier.weight(1f),
            items = state.items,
            onItemClick = { dispatch(it.action) }
        )
    }
}