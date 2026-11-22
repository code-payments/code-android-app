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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.flipcash.app.core.ui.inDisplayCurrency
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
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeScaffold
import com.flipcash.app.messenger.internal.swipepay.LocalSwipePayCurrencyPicker
import com.flipcash.app.messenger.internal.swipepay.SwipePayActions
import com.flipcash.app.messenger.internal.swipepay.SwipePayPage
import com.flipcash.app.messenger.internal.swipepay.SwipePayReplacement
import com.flipcash.app.messenger.internal.swipepay.SwipePaySheet
import com.flipcash.app.messenger.internal.swipepay.SwipePaySheetState
import com.flipcash.app.messenger.internal.swipepay.SwipePayUiState
import com.flipcash.app.messenger.internal.swipepay.rememberSwipePaySheetState
import kotlinx.coroutines.flow.first

/**
 * The swipe-to-pay sheet for the share [pay] holds, drawn while [pay] is open. In-window, so the
 * entry decorator's bar lands above it. Shared by Request details and the chat.
 *
 * The amount comes from [pay], not the live request: the request reads paid during the success
 * hold, and the sheet keeps its amount until it has gone. Once [pay] closes, the sheet slides down
 * on the last frame it drew while open, as Currency Send's sheet does after a send (`finishSend`):
 * the flow has already reset, so its live state would draw an empty or replanned sheet.
 *
 * A collection being funded ([RequestPayFlow.State.funding]) picks its currency from
 * [collectionCurrencies] in its own sheet, through [onPickCollectionCurrency], instead of the
 * wallet's currency list: the pick is this payment's alone. Its [funding] fee line pushes Payment
 * Details inside the sheet, in place of its content, and back slides the content back. When the
 * balances can't cover it, [CollectionFundingUi.short] takes the swipe's place, its short line
 * opening the same Payment Details, and [onAddAndPay] adds the rest.
 * [RequestPayFlow.State.finishing] fades "Finishing Payment" in for the sheet's content until the
 * payment lands or times out, whichever entry started the top-up.
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
    onAddAndPay: () -> Unit = {},
) {
    val held = remember { HeldPayFrame() }
    // Bumped once a closed sheet has slid down: the next open gets a sheet state of its own, since
    // a caller's hide() leaves its state hidden for good.
    var sheets by remember { mutableIntStateOf(0) }
    val frame = if (pay.open) {
        RequestPayFrame(pay, members, token, collectionCurrencies, funding)
    } else {
        held.frame ?: return
    }
    if (pay.open) SideEffect { held.frame = frame }
    key(sheets) {
        RequestPayFrameSheet(
            frame = frame,
            closing = !pay.open,
            actions = actions,
            onPickCollectionCurrency = onPickCollectionCurrency,
            onAddAndPay = onAddAndPay,
            onHidden = {
                held.frame = null
                sheets++
            },
        )
    }
}

/** What [RequestPaySheet] draws, as of its last composition with the flow open. */
private data class RequestPayFrame(
    val pay: RequestPayFlow.State,
    val members: Map<ID, ChatMember>,
    val token: Token?,
    val collectionCurrencies: List<TokenWithLocalizedBalance>,
    val funding: CollectionFundingUi?,
)

/** The frame a closed [RequestPaySheet] slides down on. Not observed: [frame] is set after each open composition. */
private class HeldPayFrame {
    var frame: RequestPayFrame? = null
}

/**
 * The sheet for [frame]. Once [closing], it slides down, through `InWindowSheetState.hide` past its
 * lock, and then calls [onHidden].
 */
@Composable
private fun RequestPayFrameSheet(
    frame: RequestPayFrame,
    closing: Boolean,
    actions: SwipePayActions,
    onPickCollectionCurrency: (Mint) -> Unit,
    onAddAndPay: () -> Unit,
    onHidden: () -> Unit,
) {
    val pay = frame.pay
    val funding = frame.funding
    val token = frame.token
    val amount = pay.amount ?: return
    val swipePay = requestPaySwipeState(pay, token)
    val sheetState = rememberSwipePaySheetState(locked = swipePay.locked)
    val closingNow by rememberUpdatedState(closing)
    val currentOnHidden by rememberUpdatedState(onHidden)
    LaunchedEffect(sheetState) {
        snapshotFlow { closingNow }.first { it }
        sheetState.sheet.hide()
        currentOnHidden()
    }
    val sheet = @Composable { feeLine: (@Composable () -> Unit)?, onDetails: () -> Unit, page: SwipePayPage? ->
        PayShareSheet(
            creator = pay.creator?.let(frame.members::get),
            amount = amount,
            swipePay = swipePay,
            sheetState = sheetState,
            actions = actions,
            feeLine = feeLine,
            showsHandle = !pay.funding,
            showsCents = pay.funding,
            action = funding?.short?.takeIf { pay.funding }?.let { short ->
                @Composable { modifier: Modifier ->
                    CollectionShortAction(short, swipePay, onAddAndPay, onDetails, modifier)
                }
            },
            // Every collection's sheet keeps its swap, so Finishing Payment fades in and out of
            // the same sheet: Google Pay's spinner to it, then it to the checkmark or the swipe.
            replacement = if (pay.funding) {
                SwipePayReplacement(shown = pay.finishing) { CollectionFinishing() }
            } else {
                null
            },
            page = page,
        )
    }
    if (!pay.funding) {
        sheet(null, {}, null)
        return
    }
    var picking by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    // The fee line and the short line open the same page. Not mid-payment: the page would hide
    // the swipe in flight.
    val openDetails = { if (!swipePay.locked) details = true }
    val closeDetails = { details = false }
    val feeLine = funding?.modalFee?.let { fee ->
        @Composable { CollectionFeeLine(fee = fee, onDetails = openDetails) }
    }
    val page = SwipePayPage(
        shown = details && funding != null,
        onBack = closeDetails,
        // Still composed while it slides out, after the plan it drew may have gone.
        content = { funding?.details?.let { PaymentDetailsPage(details = it, onBack = closeDetails) } },
    )
    CompositionLocalProvider(LocalSwipePayCurrencyPicker provides { picking = true }) {
        sheet(feeLine, openDetails, page)
    }
    if (picking && !closing) {
        CollectionCurrencySheet(
            currencies = frame.collectionCurrencies,
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
        pillDropdownMark = !pay.funding,
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
    sheetState: SwipePaySheetState,
    actions: SwipePayActions,
    feeLine: (@Composable () -> Unit)? = null,
    action: (@Composable (Modifier) -> Unit)? = null,
    replacement: SwipePayReplacement? = null,
    page: SwipePayPage? = null,
    showsHandle: Boolean = true,
    showsCents: Boolean = false,
) {
    SwipePaySheet(
        state = swipePay,
        actions = actions,
        sheetState = sheetState,
        action = action,
        replacement = replacement,
        page = page,
        header = {
            MemberAvatar(creator, Modifier.requiredSize(CodeTheme.dimens.staticGrid.x10))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = memberName(creator),
                    style = CodeTheme.typography.textLarge,
                    color = CodeTheme.colors.textMain,
                    textAlign = TextAlign.Center,
                )
                // A collection's sheet (5.2) names the creator without the handle.
                creator?.userProfile?.username?.takeIf { it.isNotBlank() && showsHandle }?.let { username ->
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
                    // A collection's sheet (5.2) draws "$10.00", in the currency's minor digits; a
                    // share's drops a whole amount's ".00".
                    text = if (showsCents) {
                        amount.inDisplayCurrency().formatted(Fiat.FormattingRule.None)
                    } else {
                        amount.requestText()
                    },
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

/**
 * Frames 4.2, 7.2, 8.2, in the swipe's place: "You’re $2.04 Short" with its ⓘ, "Add $5.00 and Pay
 * $11.00", and the minimum Google Pay takes. A tap on the short line calls [onDetails].
 */
@Composable
private fun CollectionShortAction(
    short: CollectionShortUi,
    swipePay: SwipePayUiState,
    onAddAndPay: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        CollectionShortLine(shortBy = short.shortBy, onDetails = onDetails)
        CodeButton(
            text = short.addButton,
            modifier = Modifier.fillMaxWidth().testTag("request_pay_add"),
            isLoading = swipePay.progress.loading,
            isSuccess = swipePay.progress.success,
            enabled = swipePay.progress.isIdle,
            buttonState = ButtonState.Filled,
            onClick = onAddAndPay,
        )
        Text(
            text = short.footnote,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Frames 1.3, 4.4, 7.5, 8.4: a top-up is landing and the payment runs after it. Drawn in the pay
 * sheet in place of its content, whichever entry started the top-up.
 */
@Composable
internal fun CollectionFinishing(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = CodeTheme.dimens.grid.x8)
            .testTag("request_pay_finishing"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        CodeCircularProgressIndicator(modifier = Modifier.size(CodeTheme.dimens.staticGrid.x8))
        Text(
            text = stringResource(R.string.title_collectionFinishingPayment),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.subtitle_collectionFinishingPayment),
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
