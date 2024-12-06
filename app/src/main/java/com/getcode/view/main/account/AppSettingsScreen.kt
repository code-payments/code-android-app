package com.getcode.view.main.account

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.getcode.services.model.PrefsBool
import com.getcode.ui.components.SettingsSwitchRow
import com.getcode.util.Biometrics
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppSettingsScreen(
    viewModel: AppSettingsViewModel
) {
    val context = LocalContext.current
    val state by viewModel.stateFlow.collectAsState()
    val scope = rememberCoroutineScope()

    LazyColumn {
        items(state.settings) { option ->
            if (option.visible) {
                SettingsSwitchRow(
                    modifier = Modifier.animateItemPlacement(),
                    enabled = option.available,
                    title = stringResource(id = option.name),
                    icon = option.icon,
                    subtitle = option.description?.let { stringResource(id = it) },
                    checked = option.enabled
                ) {
                    val toggle = {
                        viewModel.dispatchEvent(
                            AppSettingsViewModel.Event.SettingChanged(option.type, !option.enabled)
                        )
                    }

                    when (option.type) {
                        PrefsBool.CAMERA_START_BY_DEFAULT -> toggle()
                        PrefsBool.REQUIRE_BIOMETRICS -> {
                            scope.launch {
                                Biometrics.prompt(context, delay = 300)
                                    .onSuccess { toggle() }
                            }
                        }
                    }

                }
            }
        }
    }
}