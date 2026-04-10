package com.flipcash.app.core.transfers

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.AppRoute
import com.flipcash.core.R
import com.getcode.solana.keys.Mint
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface TransferDirection : Parcelable {

    @get:Composable
    val title: String

    val nextScreen: AppRoute

    @get:Composable
    val description: String

    @get:Composable
    val learnMoreAction: String

    @get:Composable
    val continueAction: String

    data object Incoming : TransferDirection {
        @IgnoredOnParcel
        override val nextScreen: AppRoute = AppRoute.Menu.Deposit(Mint.usdf)
        override val title: String
            @Composable get() = stringResource(R.string.title_depositFunds)
        override val description: String
            @Composable get() = stringResource(R.string.title_learnToDeposit)
        override val learnMoreAction: String
            @Composable get() = stringResource(R.string.action_learnHowToDepositFunds)
        override val continueAction: String
            @Composable get() = stringResource(R.string.action_depositUsdc)
    }

    data object Outgoing : TransferDirection {
        @IgnoredOnParcel
        override val nextScreen: AppRoute = AppRoute.Transfers.Withdrawal(Mint.usdf)
        override val title: String
            @Composable get() = stringResource(R.string.title_withdrawFunds)
        override val description: String
            @Composable get() = stringResource(R.string.title_learnToWithdraw)
        override val learnMoreAction: String
            @Composable get() = stringResource(R.string.action_learnHowToWithdrawFunds)
        override val continueAction: String
            @Composable get() = stringResource(R.string.action_withdrawFundsNow)
    }
}