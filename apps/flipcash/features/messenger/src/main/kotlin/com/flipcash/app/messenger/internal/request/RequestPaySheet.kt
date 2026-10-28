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
import com.flipcash.app.messenger.internal.screens.boost.CurrencyPickerContent
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.Share
import com.flipcash.services.models.paymentrequest.statusLine
import com.flipcash.shared.chat.ui.request.RequestProgressBar
import com.flipcash.shared.chat.ui.request.progressText
import com.flipcash.shared.chat.ui.request.requestText
import com.flipcash.shared.chat.ui.request.text
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.shared.common.ui.profile.chatterName
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Token
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.SlideToConfirm
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.flipcash.app.messenger.internal.swipepay.SwipePayActions
import com.flipcash.app.messenger.internal.swipepay.SwipePaySheet
import com.flipcash.app.messenger.internal.swipepay.SwipePayUiState
import com.flipcash.app.messenger.internal.swipepay.rememberSwipePaySheetState
import kotlin.time.Instant

/**
 * The swipe-to-pay sheet for the viewer's unpaid share of [request], drawn while [pay] is open.
 * In-window, so the entry decorator's bar lands above it. Shared by Request details and the chat.
 */
@Composable
internal fun RequestPaySheet(
    pay: RequestPayFlow.State,
    request: PaymentRequest?,
    members: Map<ID, ChatMember>,
    viewerId: ID?,
    now: Instant,
    token: Token?,
    actions: SwipePayActions,
) {
    if (!pay.open || request == null) return
    val share = unpaidShareOf(request, viewerId, now) ?: return
    PayShareSheet(
        creator = members[request.creator],
        share = share,
        swipePay = SwipePayUiState(
            token = token,
            progress = pay.progress,
            tokenPickable = true,
            swipeTestTag = "request_pay_swipe",
        ),
        actions = actions,
    )
}

/**
 * Frame 10: who is being paid, the share, the token, and the swipe. The token pill swaps this
 * sheet's content for the currency list, as on the group send confirm sheet.
 */
@Composable
private fun PayShareSheet(
    creator: ChatMember?,
    share: Share,
    swipePay: SwipePayUiState,
    actions: SwipePayActions,
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
            Text(
                text = share.amount.requestText(),
                style = CodeTheme.typography.displayMedium,
                color = CodeTheme.colors.textMain,
            )
        },
    )
}

