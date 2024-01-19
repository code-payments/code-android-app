package com.getcode.navigation.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.R
import com.getcode.analytics.AnalyticsScreenWatcher
import com.getcode.manager.AnalyticsManager
import com.getcode.util.getActivityScopedViewModel
import com.getcode.util.getStackScopedViewModel
import com.getcode.view.main.account.withdraw.AccountWithdrawAddress
import com.getcode.view.main.account.withdraw.AccountWithdrawAmount
import com.getcode.view.main.account.withdraw.AccountWithdrawAmountViewModel
import com.getcode.view.main.account.withdraw.AccountWithdrawSummary
import com.getcode.view.main.giveKin.GiveKinSheetViewModel
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize


@Parcelize
internal data object WithdrawalAmountScreen : WithdrawalGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @IgnoredOnParcel
    override val arguments: WithdrawalArgs = WithdrawalArgs()

    override val name: String
        @Composable get() = stringResource(id = R.string.title_withdrawKin)

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is WithdrawalAmountScreen }) {
            AccountWithdrawAmount(viewModel = getViewModel())
        }

        AnalyticsScreenWatcher(
            lifecycleOwner = LocalLifecycleOwner.current,
            event = AnalyticsManager.Screen.Withdraw
        )

    }
}

@Parcelize
data class WithdrawalAddressScreen(override val arguments: WithdrawalArgs = WithdrawalArgs()) :
    WithdrawalGraph, ModalContent {

    constructor(
        amountFiat: Double? = null,
        amountKinQuarks: Long? = null,
        amountText: String? = null,
        currencyCode: String? = null,
        currencyResId: Int? = null,
        currencyRate: Double? = null,
        resolvedDestination: String? = null,
    ) : this(
        WithdrawalArgs(
            amountFiat,
            amountKinQuarks,
            amountText,
            currencyCode,
            currencyResId,
            currencyRate,
            resolvedDestination
        )
    )

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_withdrawKin)

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is WithdrawalAddressScreen }) {
            AccountWithdrawAddress(getViewModel(), arguments)
        }
    }
}

@Parcelize
data class WithdrawalSummaryScreen(override val arguments: WithdrawalArgs = WithdrawalArgs()) :
    WithdrawalGraph, ModalContent {

    constructor(
        amountFiat: Double? = null,
        amountKinQuarks: Long? = null,
        amountText: String? = null,
        currencyCode: String? = null,
        currencyResId: Int? = null,
        currencyRate: Double? = null,
        resolvedDestination: String? = null,
    ) : this(
        WithdrawalArgs(
            amountFiat,
            amountKinQuarks,
            amountText,
            currencyCode,
            currencyResId,
            currencyRate,
            resolvedDestination
        )
    )

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_withdrawKin)

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is WithdrawalSummaryScreen }) {
            AccountWithdrawSummary(getViewModel(), arguments)
        }
    }
}