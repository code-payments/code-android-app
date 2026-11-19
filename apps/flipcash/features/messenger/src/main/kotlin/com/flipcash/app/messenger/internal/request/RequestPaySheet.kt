package com.flipcash.app.messenger.internal.request

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.flipcash.shared.chat.ui.ActionCapsuleButton
import com.flipcash.shared.chat.ui.ActionCapsule
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.ui.TokenSelectionPill
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.paymentrequest.statusLine
import com.flipcash.shared.chat.ui.request.RequestProgressBar
import com.flipcash.shared.chat.ui.request.progressText
import com.flipcash.shared.chat.ui.request.requestText
import com.flipcash.shared.chat.ui.request.text
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.shared.common.ui.profile.chatterName
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.SlideToConfirm
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.flipcash.app.messenger.internal.swipepay.LocalSwipePayCurrencyPicker
import com.flipcash.app.messenger.internal.swipepay.SwipePayActions
import com.flipcash.app.messenger.internal.swipepay.SwipePaySheet
import com.flipcash.app.messenger.internal.swipepay.SwipePayUiState
import com.flipcash.app.messenger.internal.swipepay.rememberSwipePaySheetState

/**
 * The swipe-to-pay sheet for the share [pay] holds, drawn while [pay] is open. In-window, so the
 * entry decorator's bar lands above it. Shared by Request details and the chat.
 *
 * The amount comes from [pay], not the live request: the request reads paid during the success
 * hold, and the sheet keeps its amount until it has gone.
 *
 * A collection being funded ([RequestPayFlow.State.funding]) picks its currency from
 * [collectionCurrencies] in its own sheet, through [onPickCollectionCurrency], instead of the
 * wallet's currency list: the pick is this payment's alone. Its [funding] fee line opens Payment
 * Details over the sheet.
 */
@Composable
internal fun RequestPaySheet(
    pay: RequestPayFlow.State,
    members: Map<ID, ChatMember>,
    token: Token?,
    actions: SwipePayActions,
    collectionCurrencies: List<TokenWithLocalizedBalance> = emptyList(),
    onPickCollectionCurrency: (Mint) -> Unit = {},
    funding: CollectionFundingUi? = null,
) {
    if (!pay.open) return
    val amount = pay.amount ?: return
    val sheet = @Composable { feeLine: (@Composable () -> Unit)? ->
        PayShareSheet(
            creator = pay.creator?.let(members::get),
            amount = amount,
            swipePay = requestPaySwipeState(pay, token),
            actions = actions,
            feeLine = feeLine,
        )
    }
    if (!pay.funding) {
        sheet(null)
        return
    }
    var picking by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    val feeLine = funding?.modalFee?.let { fee ->
        @Composable { CollectionFeeLine(fee = fee, onDetails = { details = true }) }
    }
    CompositionLocalProvider(LocalSwipePayCurrencyPicker provides { picking = true }) { sheet(feeLine) }
    if (details && funding != null) {
        PaymentDetailsSheet(details = funding.details, onClosed = { details = false })
    }
    if (picking) {
        CollectionCurrencySheet(
            currencies = collectionCurrencies,
            selected = token?.address,
            onPick = onPickCollectionCurrency,
            onClosed = { picking = false },
        )
    }
}

/** The swipe for a share: the viewer pays the request, so it reads "Swipe to Pay". */
internal fun requestPaySwipeState(pay: RequestPayFlow.State, token: Token?): SwipePayUiState =
    SwipePayUiState(
        token = token,
        progress = pay.progress,
        tokenPickable = true,
        swipeLabel = R.string.action_swipeToPay,
        swipeTestTag = "request_pay_swipe",
    )

/**
 * Frame 10: who is being paid, the share (or a collection's amount), the token, and the swipe. The token pill opens the
 * currency list as a half sheet over this one, as on the group send confirm sheet.
 */
@Composable
private fun PayShareSheet(
    creator: ChatMember?,
    amount: Fiat,
    swipePay: SwipePayUiState,
    actions: SwipePayActions,
    feeLine: (@Composable () -> Unit)? = null,
) {
    SwipePaySheet(
        state = swipePay,
        actions = actions,
        sheetState = rememberSwipePaySheetState(locked = swipePay.locked),
        header = {
            MemberAvatar(creator, Modifier.requiredSize(CodeTheme.dimens.staticGrid.x10))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = memberName(creator),
                    style = CodeTheme.typography.textLarge,
                    color = CodeTheme.colors.textMain,
                    textAlign = TextAlign.Center,
                )
                creator?.userProfile?.username?.takeIf { it.isNotBlank() }?.let { username ->
                    Text(
                        text = "@$username",
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                    )
                }
            }
        },
        amount = {
            val amountText = @Composable {
                Text(
                    text = amount.requestText(),
                    style = CodeTheme.typography.displayMedium,
                    color = CodeTheme.colors.textMain,
                )
            }
            if (feeLine == null) {
                amountText()
            } else {
                // Frames 5.2, 6.4, 9.2: the fee line sits between the amount and the pill.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
                ) {
                    amountText()
                    feeLine()
                }
            }
        },
    )
}

