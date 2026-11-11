package com.flipcash.app.tipping.internal.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.flipcash.app.tipping.internal.CreateGroupViewModel
import com.flipcash.features.tipping.R
import com.flipcash.shared.amountentry.AmountEntryScreen
import com.getcode.navigation.scenes.LocalBottomSheetDismissDispatcher
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The `…` preset — a balance requirement the three chips don't cover.
 *
 * The design names the chip but not where it goes, so it opens the keypad the rest of the app enters
 * amounts on, in the account's preferred currency. The draft stores the result in USD; the
 * conversion is [CreateGroupViewModel]'s, since that is where the rate lives.
 *
 * Dismissal follows `OnAmountSelected` rather than the confirm tap: an entry of zero is dropped
 * without becoming an amount, and closing the sheet on it would read as the form having taken it.
 */
@Composable
internal fun NewGroupCustomAmountSheet(viewModel: CreateGroupViewModel) {
    // Exit through the sheet so it animates down rather than having its scene deleted mid-frame.
    val dismissSheet = LocalBottomSheetDismissDispatcher.current

    AmountEntryScreen(
        controller = viewModel.amountDelegate,
        onConfirm = { viewModel.dispatchEvent(CreateGroupViewModel.Event.ConfirmCustomAmount) },
        largeHeader = true,
        appBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_minimumBalanceRequired),
                titleAlignment = Alignment.CenterHorizontally,
                endContent = { AppBarDefaults.Close(onClick = dismissSheet) },
            )
        },
        headerCaption = {
            Text(
                text = stringResource(R.string.subtitle_groupBalanceRequirement),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                textAlign = TextAlign.Start,
            )
        },
    )

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<CreateGroupViewModel.Event.OnAmountSelected>()
            .onEach { dismissSheet() }
            .launchIn(this)
    }
}
