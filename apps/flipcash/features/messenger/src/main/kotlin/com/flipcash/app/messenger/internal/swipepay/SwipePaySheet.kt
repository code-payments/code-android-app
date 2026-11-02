package com.flipcash.app.messenger.internal.swipepay

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.app.core.ui.TokenSelectionPill
import com.flipcash.app.core.chat.ChatStep
import com.getcode.navigation.core.LocalCodeNavigator
import androidx.compose.runtime.staticCompositionLocalOf
import com.flipcash.features.messenger.R
import com.getcode.opencode.model.financial.Token
import com.getcode.navigation.scenes.InWindowBottomSheet
import com.getcode.navigation.scenes.InWindowSheetState
import com.getcode.navigation.scenes.rememberInWindowSheetState
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.theme.White10
import com.getcode.ui.components.SlideToConfirm
import com.getcode.view.LoadingSuccessState

/**
 * What a swipe-to-pay sheet shows, apart from the lines that say who and how much.
 *
 * Every flow that ends in this sheet (pay a request, send to a group, send from Currency Info)
 * builds one of these from its own state, so the sheet draws the same from any of them and a
 * destination that owns the sheet can take it as its state unchanged.
 */
@Immutable
internal data class SwipePayUiState(
    val token: Token?,
    val progress: LoadingSuccessState,
    /** True to show the currency picker on the pill; false for a currency fixed by the flow. */
    val tokenPickable: Boolean,
    /** True when only back closes the sheet: no drag, no tap on the scrim. */
    val backOnly: Boolean = false,
    /** The swipe's label: "Swipe to Send" unless the flow pays a request. */
    @StringRes val swipeLabel: Int = R.string.action_swipeToSend,
    val swipeTestTag: String? = null,
) {
    /** A swipe is in flight or has finished; the sheet holds still until the flow lets go. */
    val locked: Boolean get() = !progress.isIdle
}

/** What the sheet asks of the flow that owns it. */
@Immutable
internal class SwipePayActions(
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
)

@Stable
internal class SwipePaySheetState internal constructor(
    val sheet: InWindowSheetState,
)

/**
 * Opens the currency picker for a swipe-to-pay sheet. Unset, the sheet pushes
 * [ChatStep.SendCurrency] on the current navigator, which is right wherever that is the chat
 * flow's; a nested flow host that shadows the navigator provides the chat flow's own.
 */
internal val LocalSwipePayCurrencyPicker = staticCompositionLocalOf<(() -> Unit)?> { null }

/** The sheet's state. Locked while a swipe is in flight. */
@Composable
internal fun rememberSwipePaySheetState(locked: Boolean): SwipePaySheetState {
    val lockedNow by rememberUpdatedState(locked)
    val sheet = rememberInWindowSheetState { lockedNow }
    return SwipePaySheetState(sheet)
}

/**
 * The swipe-to-pay sheet: [header] (who), [amount], the currency pill, and the swipe.
 *
 * Drawn in the host's own window, so a bottom bar the route raises lands above it. A token pick
 * opens the currency list as a half sheet over this one, which stays up beneath it with its
 * state; the pick itself is the shared selected-token state, so the owning flow sees it through
 * its token without a callback here.
 */
@Composable
internal fun SwipePaySheet(
    state: SwipePayUiState,
    actions: SwipePayActions,
    sheetState: SwipePaySheetState,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    amount: @Composable () -> Unit,
) {
    val pickCurrency = LocalSwipePayCurrencyPicker.current
    val navigator = LocalCodeNavigator.current
    val openPicker = { pickCurrency?.invoke() ?: navigator.push(ChatStep.SendCurrency) }
    InWindowBottomSheet(
        state = sheetState.sheet,
        onDismissRequest = actions.onDismiss,
        modifier = modifier,
        dismissOnScrimTap = !state.backOnly,
        gesturesEnabled = !state.backOnly,
    ) {
        val swipe: @Composable (Modifier) -> Unit = { extra ->
            SlideToConfirm(
                onConfirm = actions.onConfirm,
                modifier = Modifier.fillMaxWidth().then(extra).let {
                    if (state.swipeTestTag != null) it.testTag(state.swipeTestTag) else it
                },
                enabled = state.progress.isIdle,
                isLoading = state.progress.loading,
                isSuccess = state.progress.success,
                label = stringResource(state.swipeLabel),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(top = CodeTheme.dimens.grid.x6, bottom = CodeTheme.dimens.grid.x4),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Section gap 40 (node 11622:2232) when the currency is fixed.
            verticalArrangement = Arrangement.spacedBy(
                if (state.tokenPickable) CodeTheme.dimens.grid.x3 else CodeTheme.dimens.grid.x8
            ),
        ) {
            header()
            if (state.tokenPickable) {
                amount()
                TokenSelectionPill(
                    token = state.token,
                    background = White10,
                    textStyle = CodeTheme.typography.textMedium,
                    imageSize = CodeTheme.dimens.staticGrid.x3,
                    contentPadding = PaddingValues(
                        horizontal = CodeTheme.dimens.grid.x1 + 1.dp,
                        vertical = CodeTheme.dimens.grid.x1 - 1.dp,
                    ),
                    onClick = { if (!state.locked) openPicker() },
                )
                swipe(Modifier.padding(top = CodeTheme.dimens.grid.x3))
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
                ) {
                    amount()
                    // Node 11881:2468: 36 tall, 20 coin, SemiBold 17, 5% fill. No chevron.
                    TokenSelectionPill(
                        modifier = Modifier.height(CodeTheme.dimens.grid.x7),
                        token = state.token,
                        background = White05,
                        textStyle = CodeTheme.typography.textMedium.copy(fontSize = 17.sp),
                        imageSize = CodeTheme.dimens.staticGrid.x4,
                        contentPadding = PaddingValues(
                            horizontal = CodeTheme.dimens.grid.x2,
                            vertical = CodeTheme.dimens.grid.x1,
                        ),
                        showsPicker = false,
                        onClick = {},
                    )
                }
                swipe(Modifier)
            }
        }
    }
}
