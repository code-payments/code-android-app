package com.getcode.view.main.account

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.getcode.ui.components.SettingsRow

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppSettingsScreen(
    viewModel: AppSettingsViewModel
) {
    val state by viewModel.stateFlow.collectAsState()

    LazyColumn {
        items(state.settings) { option ->
            if (option.visible) {
                SettingsRow(
                    modifier = Modifier.animateItemPlacement(),
                    enabled = option.available,
                    title = stringResource(id = option.name),
                    icon = option.icon,
                    subtitle = option.description?.let { stringResource(id = it) },
                    checked = option.enabled
                ) {
                    viewModel.dispatchEvent(
                        AppSettingsViewModel.Event.SettingChanged(option.type, !option.enabled)
                    )
                }
            }
        }
    }
}