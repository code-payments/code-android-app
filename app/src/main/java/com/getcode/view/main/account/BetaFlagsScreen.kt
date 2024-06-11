package com.getcode.view.main.account

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.getcode.R
import com.getcode.model.PrefsBool
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
import com.getcode.ui.components.SettingsRow
import dev.bmcreations.tipkit.engines.LocalTipsEngine

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

    val tipsEngine = LocalTipsEngine.current

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
            PrefsBool.BALANCE_CURRENCY_SELECTION_ENABLED,
            R.string.beta_balance_currency,
            stringResource(R.string.beta_balance_currency_description),
            state.currencySelectionBalanceEnabled
        ) { viewModel.dispatchEvent(BetaFlagsViewModel.Event.EnableCurrencySelectionInBalance(it)) },
        BetaFeature(
            PrefsBool.GIVE_REQUESTS_ENABLED,
            R.string.beta_give_requests_mode,
            stringResource(id = R.string.beta_give_requests_description),
            state.giveRequestsEnabled
        ) { viewModel.dispatchEvent(BetaFlagsViewModel.Event.EnableGiveRequests(it)) },
        BetaFeature(
            PrefsBool.BUY_MODULE_ENABLED,
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
            PrefsBool.TIPS_CHAT_ENABLED,
            R.string.beta_tipchats,
            stringResource(id = R.string.beta_tipchats_description),
            state.tipsChatEnabled,
        ) { viewModel.dispatchEvent(BetaFlagsViewModel.Event.EnableTipChats(it)) },
        BetaFeature(
            PrefsBool.TIPS_CHAT_CASH_ENABLED,
            R.string.beta_tipchats_cash,
            stringResource(id = R.string.beta_tipchats_cash_description),
            state.tipsChatCashEnabled,
        ) { viewModel.dispatchEvent(BetaFlagsViewModel.Event.EnableTipsChatCash(it)) },
        BetaFeature(
            PrefsBool.DISPLAY_ERRORS,
            R.string.beta_display_errors,
            "",
            state.displayErrors,
        ) { viewModel.dispatchEvent(BetaFlagsViewModel.Event.ShowErrors(it)) }
    ).filter { state.canMutate(it.flag) }

    LazyColumn {
        items(options) { option ->
            SettingsRow(
                modifier = Modifier.animateItemPlacement(),
                title = stringResource(id = option.titleResId),
                subtitle = option.subtitleText,
                checked = option.dataState
            ) {
                option.onChange(!option.dataState)
            }
        }

        item {
            val context = LocalContext.current
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.grid.x3),
                buttonState = ButtonState.Filled,
                text = stringResource(id = R.string.beta_resetTooltips),
                onClick = {
                    tipsEngine?.invalidateAllTips()
                    Toast.makeText(context, "Tooltips reset", Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}

private fun BetaFlagsViewModel.State.canMutate(flag: PrefsBool): Boolean {
    return when (flag) {
        PrefsBool.BUY_MODULE_ENABLED -> false
        PrefsBool.BALANCE_CURRENCY_SELECTION_ENABLED -> false
        PrefsBool.TIPS_CHAT_CASH_ENABLED -> tipsChatEnabled
        else -> true
    }
}
