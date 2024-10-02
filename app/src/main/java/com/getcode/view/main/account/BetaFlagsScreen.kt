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
import com.getcode.model.Immutable
import com.getcode.model.PrefsBool
import com.getcode.network.repository.BetaOptions
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
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
        val onChange: (Boolean) -> Unit = { value ->
            viewModel.dispatchEvent(BetaFlagsViewModel.Event.Toggle(flag, value))
        }
    )

    val state by viewModel.stateFlow.collectAsState()

    val tipsEngine = LocalTipsEngine.current

    val options = listOf(
        BetaFeature(
            PrefsBool.KADO_WEBVIEW_ENABLED,
            R.string.beta_kado_webview,
            stringResource(id = R.string.beta_kado_webview_description),
            state.kadoWebViewEnabled,
        ),
        BetaFeature(
            PrefsBool.SHARE_TWEET_TO_TIP,
            R.string.beta_share_tweet_tip,
            stringResource(id = R.string.beta_share_tweet_tip_description),
            state.shareTweetToTip,
        ),
        BetaFeature(
            PrefsBool.CAMERA_GESTURES_ENABLED,
            R.string.beta_camera_gestures,
            stringResource(id = R.string.beta_camera_gestures_description),
            state.cameraGesturesEnabled,
        ),
        BetaFeature(
            PrefsBool.CAMERA_DRAG_INVERTED,
            R.string.beta_camera_invert_drag,
            stringResource(id = R.string.beta_camera_invert_drag_description),
            state.invertedDragZoom,
        ),
        BetaFeature(
            PrefsBool.TIP_CARD_FLIPPABLE,
            R.string.beta_tipcard_can_flip,
            stringResource(id = R.string.beta_tipcard_can_flip_description),
            state.canFlipTipCard,
        ),
        BetaFeature(
            PrefsBool.GALLERY_ENABLED,
            R.string.beta_photo_gallery,
            stringResource(id = R.string.beta_photo_gallery_description),
            state.galleryEnabled,
        ),
        BetaFeature(
            PrefsBool.CONVERSATIONS_ENABLED,
            R.string.beta_conversations,
            stringResource(id = R.string.beta_conversations_description),
            state.conversationsEnabled,
        ),
        BetaFeature(
            PrefsBool.VIBRATE_ON_SCAN,
            R.string.beta_vibrate_on_scan,
            stringResource(R.string.beta_vibrate_on_scan_description),
            state.tickOnScan
        ),
        BetaFeature(
            PrefsBool.SHOW_CONNECTIVITY_STATUS,
            R.string.beta_network_dropoff,
            stringResource(R.string.beta_network_connectivity_description),
            state.showNetworkDropOff
        ),
        BetaFeature(
            PrefsBool.BUCKET_DEBUGGER_ENABLED,
            R.string.beta_bucket_debugger,
            stringResource(R.string.beta_bucket_debugger_description),
            state.canViewBuckets
        ),
        BetaFeature(
            PrefsBool.BALANCE_CURRENCY_SELECTION_ENABLED,
            R.string.beta_balance_currency,
            stringResource(R.string.beta_balance_currency_description),
            state.balanceCurrencySelectionEnabled
        ),
        BetaFeature(
            PrefsBool.GIVE_REQUESTS_ENABLED,
            R.string.beta_give_requests_mode,
            stringResource(id = R.string.beta_give_requests_description),
            state.giveRequestsEnabled
        ),
        BetaFeature(
            PrefsBool.BUY_MODULE_ENABLED,
            R.string.beta_buy_kin,
            stringResource(id = R.string.beta_buy_kin_description),
            state.buyModuleEnabled
        ),
        BetaFeature(
            PrefsBool.CHAT_UNSUB_ENABLED,
            R.string.beta_chat_unsub,
            stringResource(id = R.string.beta_chat_unsub_description),
            state.chatUnsubEnabled,
        ),
        BetaFeature(
            PrefsBool.TIPS_ENABLED,
            R.string.beta_tipcard,
            stringResource(id = R.string.beta_tipcard_description),
            state.tipsEnabled,
        ),
        BetaFeature(
            PrefsBool.TIP_CARD_ON_HOMESCREEN,
            R.string.beta_tipcard_on_homescreen,
            stringResource(id = R.string.beta_tipcard_on_homescreen_description),
            state.tipCardOnHomeScreen,
        ),
        BetaFeature(
            PrefsBool.CONVERSATION_CASH_ENABLED,
            R.string.beta_conversations_cash,
            stringResource(id = R.string.beta_conversations_cash_description),
            state.conversationCashEnabled,
        ),
        BetaFeature(
            PrefsBool.DISPLAY_ERRORS,
            R.string.beta_display_errors,
            "",
            state.displayErrors,
        )
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

private fun BetaOptions.canMutate(flag: PrefsBool): Boolean {
    return when (flag) {
        is Immutable -> false
        PrefsBool.CONVERSATION_CASH_ENABLED -> conversationsEnabled
        else -> true
    }
}
