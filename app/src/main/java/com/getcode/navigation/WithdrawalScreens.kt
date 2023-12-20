package com.getcode.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.R
import com.getcode.theme.sheetHeight
import com.getcode.view.components.SheetTitle
import com.getcode.view.main.account.withdraw.AccountWithdrawAddress
import com.getcode.view.main.account.withdraw.AccountWithdrawAmount
import com.getcode.view.main.account.withdraw.AccountWithdrawSummary

data class WithdrawalArgs(
    val amountFiat: Double? = null,
    val amountKinQuarks: Long? = null,
    val amountText: String? = null,
    val currencyCode: String? = null,
    val currencyResId: Int? = null,
    val currencyRate: Double? = null,
    val resolvedDestination: String? = null,
)

sealed interface WithdrawalGraph : Screen, NamedScreen {
    val arguments: WithdrawalArgs
    fun readResolve(): Any = this
}


internal data object WithdrawalAmountScreen : WithdrawalGraph {

    override val arguments: WithdrawalArgs = WithdrawalArgs()

    override val name: String
        @Composable get() = stringResource(id = R.string.title_withdrawKin)

    @Composable
    override fun Content() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(sheetHeight)
        ) {
            val navigator = LocalCodeNavigator.current

            SheetTitle(
                title = name,
                modifier = Modifier.padding(horizontal = 20.dp),
                // hide while transitioning to/from other destinations
                backButton = navigator.lastItem is WithdrawalAmountScreen,
                closeButton = false,
                onBackIconClicked = { navigator.pop() }
            )
            AccountWithdrawAmount(viewModel = getViewModel())
        }
    }
}

data class WithdrawalAddressScreen(override val arguments: WithdrawalArgs = WithdrawalArgs()) : WithdrawalGraph {

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

    override val name: String
        @Composable get() = stringResource(id = R.string.title_withdrawKin)

    @Composable
    override fun Content() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(sheetHeight)
        ) {
            val navigator = LocalCodeNavigator.current

            SheetTitle(
                title = name,
                modifier = Modifier.padding(horizontal = 20.dp),
                // hide while transitioning to/from other destinations
                backButton = navigator.lastItem is WithdrawalAddressScreen,
                closeButton = false,
                onBackIconClicked = { navigator.pop() }
            )
            AccountWithdrawAddress(getViewModel(), arguments)
        }
    }
}

data class WithdrawalSummaryScreen(override val arguments: WithdrawalArgs = WithdrawalArgs()) : WithdrawalGraph {

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

    override val name: String
        @Composable get() = stringResource(id = R.string.title_withdrawKin)

    @Composable
    override fun Content() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(sheetHeight)
        ) {
            val navigator = LocalCodeNavigator.current

            SheetTitle(
                modifier = Modifier.padding(horizontal = 20.dp),
                title = name,
                // hide while transitioning to/from other destinations
                backButton = navigator.lastItem is WithdrawalSummaryScreen,
                closeButton = false,
                onBackIconClicked = { navigator.pop() }
            )
            AccountWithdrawSummary(getViewModel(), arguments)
        }
    }
}