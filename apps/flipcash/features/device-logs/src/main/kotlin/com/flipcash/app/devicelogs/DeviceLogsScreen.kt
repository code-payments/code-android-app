package com.flipcash.app.devicelogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.core.android.IntentUtils
import com.flipcash.app.devicelogs.internal.DeviceLogsScreen
import com.flipcash.app.devicelogs.internal.DeviceLogsScreenViewModel
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.utils.TraceManager
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun DeviceLogsScreen() {
    val navigator = LocalCodeNavigator.current
    val context = LocalContext.current
    val viewModel = hiltViewModel<DeviceLogsScreenViewModel>()

    var overflowOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_deviceLogs),
            titleAlignment = Alignment.CenterHorizontally,
            isInModal = true,
            backButton = true,
            onBackIconClicked = { navigator.pop() },
            endContent = {
                Box {
                    AppBarDefaults.Overflow { overflowOpen = true }
                    DropdownMenu(
                        expanded = overflowOpen,
                        containerColor = CodeTheme.colors.brandLight,
                        onDismissRequest = { overflowOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.action_clearLogs),
                                    style = CodeTheme.typography.textSmall,
                                    color = CodeTheme.colors.textMain,
                                )
                            },
                            onClick = {
                                overflowOpen = false
                                viewModel.dispatchEvent(DeviceLogsScreenViewModel.Event.ClearLogs)
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.action_shareLogs),
                                    style = CodeTheme.typography.textSmall,
                                    color = CodeTheme.colors.textMain,
                                )
                            },
                            onClick = {
                                overflowOpen = false
                                viewModel.dispatchEvent(DeviceLogsScreenViewModel.Event.ShareLogs)
                            },
                        )
                    }
                }
            },
        )

        DeviceLogsScreen(viewModel)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<DeviceLogsScreenViewModel.Event.ShareLogs>()
            .onEach {
                val logFile = TraceManager.getLogFile()
                if (logFile != null) {
                    val intent = IntentUtils.shareFile(context, logFile, "text/plain")
                    context.startActivity(intent)
                }
            }
            .launchIn(this)
    }
}
