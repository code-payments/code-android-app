package com.getcode.view.main.account

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.getcode.R
import com.getcode.model.PrefsBool
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.ui.utils.rememberedClickable
import com.getcode.ui.components.CodeSwitch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BetaFlagsScreen(
    viewModel: BetaFlagsViewModel,
) {
    data class BetaFeature(
        val flag: PrefsBool,
        val titleResId: Int,
        val subtitleText: String,
        val dataState: Boolean,
        val onChange: (Boolean) -> Unit
    )

    val state by viewModel.stateFlow.collectAsState()

    val options = listOf(
        BetaFeature(
            PrefsBool.VIBRATE_ON_SCAN,
            R.string.beta_vibrate_on_scan,
            stringResource(R.string.beta_vibrate_on_scan_description),
            state.isVibrateOnScan
        ) { viewModel.dispatchEvent(BetaFlagsViewModel.Event.SetVibrateOnScan(it)) },
        BetaFeature(
            PrefsBool.SHOW_CONNECTIVITY_STATUS,
            R.string.beta_network_dropoff,
            stringResource(R.string.beta_network_connectivity_description),
            state.showNetworkDropOff
        ) { viewModel.dispatchEvent(BetaFlagsViewModel.Event.ShowNetworkDropOff(it)) },
        BetaFeature(
            PrefsBool.BUCKET_DEBUGGER_ENABLED,
            R.string.beta_bucket_debugger,
            stringResource(R.string.beta_bucket_debugger_description),
            state.canViewBuckets
        ) { viewModel.dispatchEvent(BetaFlagsViewModel.Event.UseDebugBuckets(it)) },
        BetaFeature(
            PrefsBool.GIVE_REQUESTS_ENABLED,
            R.string.beta_give_requests_mode,
            stringResource(id = R.string.beta_give_requests_description),
            state.giveRequestsEnabled
        ) { viewModel.dispatchEvent(BetaFlagsViewModel.Event.EnableGiveRequests(it)) },
        BetaFeature(
            PrefsBool.BUY_KIN_ENABLED,
            R.string.beta_buy_kin,
            stringResource(id = R.string.beta_buy_kin_description),
            state.buyKinEnabled
        ) { viewModel.dispatchEvent(BetaFlagsViewModel.Event.EnableBuyKin(it)) },
        BetaFeature(
            PrefsBool.ESTABLISH_CODE_RELATIONSHIP,
            R.string.beta_code_relationship,
            stringResource(id = R.string.beta_code_relationship_description),
            state.establishCodeRelationship,
        ) { viewModel.dispatchEvent(BetaFlagsViewModel.Event.EnableCodeRelationshipEstablish(it)) },
        BetaFeature(
            PrefsBool.CHAT_UNSUB_ENABLED,
            R.string.beta_chat_unsub,
            stringResource(id = R.string.beta_chat_unsub_description),
            state.chatUnsubEnabled,
        ) { viewModel.dispatchEvent(BetaFlagsViewModel.Event.EnableChatUnsubscribe(it)) },
        BetaFeature(
            PrefsBool.TIPS_ENABLED,
            R.string.beta_tipcard,
            stringResource(id = R.string.beta_tipcard_description),
            state.tipsEnabled,
        ) { viewModel.dispatchEvent(BetaFlagsViewModel.Event.EnableTipCard(it)) },
        BetaFeature(
            PrefsBool.MESSAGE_PAYMENT_NODE_V2,
            R.string.beta_chatmessage_v2_ui,
            stringResource(id = R.string.beta_chatmessage_v2_ui_description),
            state.chatMessageV2Enabled,
        ) { viewModel.dispatchEvent(BetaFlagsViewModel.Event.EnableChatMessageV2Ui(it)) },
        BetaFeature(
            PrefsBool.TIPS_CHAT_ENABLED,
            R.string.beta_tipchats,
            stringResource(id = R.string.beta_tipchats_description),
            state.tipsChatEnabled,
        ) { viewModel.dispatchEvent(BetaFlagsViewModel.Event.EnableTipChats(it)) },
        BetaFeature(
            PrefsBool.LOG_SCAN_TIMES,
            R.string.beta_scan_times,
            stringResource(R.string.beta_scan_times_description),
            state.debugScanTimesEnabled
        ) { viewModel.dispatchEvent(BetaFlagsViewModel.Event.SetLogScanTimes(it)) },
        BetaFeature(
            PrefsBool.DISPLAY_ERRORS,
            R.string.beta_display_errors,
            "",
            state.displayErrors,
        ) { viewModel.dispatchEvent(BetaFlagsViewModel.Event.ShowErrors(it)) }
    ).filter { state.canMutate(it.flag) }

    LazyColumn {
        items(options) { option ->
            Row(
                modifier = Modifier
                    .rememberedClickable { option.onChange(!option.dataState) }
                    .padding(horizontal = CodeTheme.dimens.grid.x3)
                    .padding(end = CodeTheme.dimens.grid.x3),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
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
                            style = CodeTheme.typography.caption,
                            color = BrandLight
                        )
                    }
                }

                CodeSwitch(
                    modifier = Modifier.wrapContentSize(),
                    checked = option.dataState,
                    onCheckedChange = null,
                )
            }
        }
    }
}

private fun BetaFlagsViewModel.State.canMutate(flag: PrefsBool): Boolean {
    return when (flag) {
        PrefsBool.BUY_KIN_ENABLED -> false
        PrefsBool.TIPS_CHAT_ENABLED -> chatMessageV2Enabled && tipsEnabled
        else -> true
    }
}