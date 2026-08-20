package com.flipcash.app.myaccount.internal.myaccount

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.menu.MenuList
import com.getcode.libs.biometrics.Biometrics
import com.getcode.ui.components.ListItemDefaults
import com.getcode.ui.theme.CodeToggleSwitch
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Flipping the biometrics requirement has to be authenticated by the biometrics themselves,
    // so the row (and its switch) route through a prompt before the toggle is dispatched.
    val toggleBiometrics = {
        if (state.biometricsAvailable) {
            scope.launch {
                Biometrics.prompt(context, delay = 300)
                    .onSuccess { dispatch(MyAccountScreenViewModel.Event.OnBiometricsToggled) }
            }
        }
        Unit
    }

    MenuList(
        modifier = Modifier.fillMaxSize(),
        items = state.items,
        onItemClick = { item ->
            if (item == RequireBiometrics) toggleBiometrics() else dispatch(item.action)
        },
        endSlot = { item ->
            if (item == RequireBiometrics) {
                CodeToggleSwitch(
                    checked = state.biometricsRequired,
                    enabled = state.biometricsAvailable,
                    onCheckedChange = { toggleBiometrics() },
                )
            } else {
                ListItemDefaults.Chevron()
            }
        }
    )
}
