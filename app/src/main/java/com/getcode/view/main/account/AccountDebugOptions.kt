package com.getcode.view.main.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.getcode.R
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.util.rememberedClickable
import com.getcode.view.components.CodeSwitch

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
            R.string.account_debug_scan_times,
            stringResource(R.string.settings_scan_times_description),
            dataState.isDebugScanTimes
        ) { viewModel.dispatchEvent(AccountDebugOptionsViewModel.Event.SetLogScanTimes(it)) },
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
                    .padding(horizontal = CodeTheme.dimens.grid.x3)
                    .padding(end = CodeTheme.dimens.grid.x3),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .rememberedClickable { option.onChange(!option.dataState) }
                        .padding(vertical = CodeTheme.dimens.grid.x3)
                ) {
                    Text(
                        modifier = Modifier
                            .padding(vertical = CodeTheme.dimens.grid.x2),
                        text = stringResource(id = option.titleResId)
                    )
                    if (option.subtitleText.isNotEmpty()) {
                        Text(
                            modifier = Modifier
                                .padding(vertical = CodeTheme.dimens.grid.x1),
                            text = option.subtitleText,
                            style = CodeTheme.typography.body2,
                            color = BrandLight
                        )
                    }
                }

                CodeSwitch(
                    modifier = Modifier.wrapContentSize(),
                    checked = option.dataState, onCheckedChange = { option.onChange(it) }
                )
            }
        }
    }
}