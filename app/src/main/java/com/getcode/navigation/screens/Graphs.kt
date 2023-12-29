package com.getcode.navigation.screens

import android.os.Parcelable
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.view.main.home.HomeViewModel
import kotlinx.parcelize.Parcelize

/**
 * Main graph for the app
 */
@Parcelize
internal sealed interface MainGraph : Screen, Parcelable, NamedScreen {
    /**
     * Common HomeViewModel for main graph to share
     */
    val homeViewModel: HomeViewModel
        @Composable get() = getViewModel<HomeViewModel>()

    fun readResolve(): Any = this
}


/**
 * Login based graph prior to authentication
 */
@Parcelize
internal sealed interface LoginGraph : Screen, Parcelable, NamedScreen {
    fun readResolve(): Any = this
}

@Parcelize
data class LoginArgs(
    val signInEntropy: String? = null,
    val isPhoneLinking: Boolean = false,
    val isNewAccount: Boolean = false,
    val phoneNumber: String? = null
): Parcelable

/**
 * Nested graph for the withdrawal flow within settings
 */
@Parcelize
internal sealed interface WithdrawalGraph : Screen, NamedScreen, Parcelable {
    val arguments: WithdrawalArgs
    fun readResolve(): Any = this
}

@Parcelize
data class WithdrawalArgs(
    val amountFiat: Double? = null,
    val amountKinQuarks: Long? = null,
    val amountText: String? = null,
    val currencyCode: String? = null,
    val currencyResId: Int? = null,
    val currencyRate: Double? = null,
    val resolvedDestination: String? = null,
): Parcelable