package com.flipcash.app.core.transfers

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.core.R
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface TransferDirection : Parcelable {

    @get:Composable
    val title: String

    val nextScreen: NavScreenProvider

    @get:Composable
    val description: String

    @get:Composable
    val learnMoreAction: String

    @get:Composable
    val continueAction: String

    data object Incoming : TransferDirection {
        @IgnoredOnParcel
        override val nextScreen: NavScreenProvider =
            NavScreenProvider.HomeScreen.Menu.Transfers.Deposit
        override val title: String
            @Composable get() = stringResource(R.string.title_depositUsdc)
        override val description: String
            @Composable get() = stringResource(R.string.title_learnToDeposit)
        override val learnMoreAction: String
            @Composable get() = stringResource(R.string.action_learnHowToDepositFunds)
        override val continueAction: String
            @Composable get() = stringResource(R.string.action_depositFundsNow)
    }

    data object Outgoing : TransferDirection {
        @IgnoredOnParcel
        override val nextScreen: NavScreenProvider =
            NavScreenProvider.HomeScreen.Menu.Transfers.Withdrawal.Amount
        override val title: String
            @Composable get() = stringResource(R.string.title_withdrawUsdc)
        override val description: String
            @Composable get() = stringResource(R.string.title_learnToWithdraw)
        override val learnMoreAction: String
            @Composable get() = stringResource(R.string.action_learnHowToWithdrawFunds)
        override val continueAction: String
            @Composable get() = stringResource(R.string.action_withdrawFundsNow)
    }
}