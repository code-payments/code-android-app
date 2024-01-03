package com.getcode.view.main.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.R
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme

@Composable
fun AccountDebugOptions(
    viewModel: AccountDebugOptionsViewModel,
) {
    data class DebugOption(
        val titleResId: Int,
        val subtitleText: String,
        val dataState: Boolean,
        val onChange: (Boolean) -> Unit
    )

    val dataState by viewModel.stateFlow.collectAsState()

    val options = listOf(
        DebugOption(
            R.string.account_debug_bucket_debugger,
            stringResource(R.string.settings_bucket_debugger_description),
            dataState.isDebugBuckets
        ) { viewModel.dispatchEvent(AccountDebugOptionsViewModel.Event.UseDebugBuckets(it)) },
        DebugOption(
            R.string.account_debug_vibrate_on_scan,
            stringResource(R.string.settings_vibrate_on_scan_description),
            dataState.isVibrateOnScan
        ) { viewModel.dispatchEvent(AccountDebugOptionsViewModel.Event.SetVibrateOnScan(it)) },
        DebugOption(
            R.string.account_debug_display_errors,
            "",
            dataState.isDisplayErrors,
        ) { viewModel.dispatchEvent(AccountDebugOptionsViewModel.Event.ShowErrors(it)) }

    )

    Column {
        for (option in options) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 15.dp)
                    .padding(end = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { option.onChange(!option.dataState) }
                        .padding(vertical = 14.dp)
                ) {
                    Text(
                        modifier = Modifier
                            .padding(vertical = 10.dp),
                        text = stringResource(id = option.titleResId)
                    )
                    if (option.subtitleText.isNotEmpty()) {
                        Text(
                            modifier = Modifier
                                .padding(vertical = 5.dp),
                            text = option.subtitleText,
                            style = CodeTheme.typography.body2,
                            color = BrandLight
                        )
                    }
                }

                Switch(
                    modifier = Modifier.wrapContentSize(),
                    checked = option.dataState, onCheckedChange = { option.onChange(it) }
                )
            }
        }
    }
}