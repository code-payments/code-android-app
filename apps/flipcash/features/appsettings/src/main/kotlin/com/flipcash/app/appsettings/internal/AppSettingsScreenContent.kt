package com.flipcash.app.appsettings.internal

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.appsettings.AppSettingValue
import com.flipcash.app.appsettings.LocalAppSettings
import com.getcode.libs.biometrics.Biometrics
import com.getcode.ui.components.SettingsSwitchRow
import kotlinx.coroutines.launch

@Composable
internal fun AppSettingsScreenContent() {
    val coordinator = LocalAppSettings.current
    val appSettings by coordinator.settings().collectAsStateWithLifecycle(emptyList(), LocalLifecycleOwner.current)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LazyColumn {
        items(appSettings) { option ->
            if (option.visible) {
                SettingsSwitchRow(
                    modifier = Modifier.animateItem(),
                    enabled = option.available,
                    title = stringResource(id = option.name),
                    icon = option.icon,
                    subtitle = option.description?.let { stringResource(id = it) },
                    checked = option.setting.enabled
                ) {
                    val toggle = {
                        coordinator.update(option.setting.type, !option.setting.enabled)
                    }

                    when (option.setting.type) {
                        AppSettingValue.BiometricsRequired -> {
                            scope.launch {
                                Biometrics.prompt(context, delay = 300)
                                    .onSuccess { toggle() }
                            }
                        }
                        AppSettingValue.CameraStartByDefault -> toggle()
                    }

                }
            }
        }
    }
}