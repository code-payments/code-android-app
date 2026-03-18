package com.flipcash.app.scanner.internal

import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.extensions.navigateTo
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.core.onramp.deeplinks.OnRampDeeplinkOrigin
import com.flipcash.app.core.tokens.TokenSwapPurpose
import com.flipcash.app.core.verification.email.EmailDeeplinkOrigin
import com.getcode.navigation.core.CodeNavigator
import com.getcode.solana.keys.Mint
import com.getcode.ui.core.scaled
import kotlinx.coroutines.delay

class NavigationStateRestorer(
    private val navigator: CodeNavigator,
    private val analytics: FlipcashAnalyticsService,
) {
    suspend fun restoreState(deeplink: DeeplinkType.Navigatable, animationScale: Float) {
        when (deeplink) {
            is DeeplinkType.TokenInfo -> {
                delay(200.scaled(animationScale))
                navigator.navigateTo(AppRoute.Sheets.Wallet)
                navigator.push(AppRoute.Token.Info(deeplink.mint, fromDeeplink = true))
            }


            is DeeplinkType.ExternalWalletStep -> {
                val screens = when (val origin = deeplink.origin) {
                    OnRampDeeplinkOrigin.Menu -> buildOnRampScreenFlow(AppRoute.Sheets.Menu) + AppRoute.OnRamp.AmountEntry
                    is OnRampDeeplinkOrigin.Give -> buildOnRampScreenFlow(AppRoute.Sheets.Give(origin.tokenAddress)) + AppRoute.OnRamp.AmountEntry
                    OnRampDeeplinkOrigin.Wallet -> buildOnRampScreenFlow(AppRoute.Sheets.Wallet) + AppRoute.OnRamp.AmountEntry
                    OnRampDeeplinkOrigin.Reserves -> buildOnRampScreenFlow(AppRoute.Token.Info(Mint.usdf)) + AppRoute.OnRamp.AmountEntry
                    is OnRampDeeplinkOrigin.TokenInfo -> listOf(
                        AppRoute.Sheets.Wallet,
                        AppRoute.Token.Info(origin.mint),
                        AppRoute.Token.SwapTransact(TokenSwapPurpose.FundWithWallet(origin.mint))
                    )
                }

                navigator.navigateTo(screens)
            }

            is DeeplinkType.EmailVerification -> {
                val origin = EmailDeeplinkOrigin.deserialize(deeplink.origin.orEmpty())
                val screens = when (origin) {
                    is EmailDeeplinkOrigin.OnRamp -> when (val source = origin.source) {
                        is AppRoute.Sheets.Menu -> {
                            buildOnRampScreenFlow(source) + AppRoute.Verification(
                                    origin = source,
                                    target = AppRoute.OnRamp.AmountEntry,
                                    includePhone = false,
                                    email = deeplink.email,
                                    emailVerificationCode = deeplink.code
                                )
                        }
                        else -> emptyList()
                    }

                    EmailDeeplinkOrigin.MyAccount ->
                        listOf(
                            AppRoute.Sheets.Menu,
                            AppRoute.Menu.MyAccount
                        ) + AppRoute.Verification(
                            origin = AppRoute.Menu.MyAccount,
                            target = null,
                            includePhone = false,
                            email = deeplink.email,
                            emailVerificationCode = deeplink.code
                        )

                    null -> emptyList()
                }

                if (screens.isNotEmpty()) {
                    analytics.deeplinkRouted(deeplink)
                    navigator.navigateTo(screens)
                } else {
                    analytics.deeplinkRouted(deeplink, IllegalStateException("Failed to route deeplink"))
                }
            }
        }
    }
}

private fun buildOnRampScreenFlow(origin: List<AppRoute>) =
    origin.dropLast(1) +
    AppRoute.OnRamp.ProviderList(origin.last())

private fun buildOnRampScreenFlow(origin: AppRoute) = buildOnRampScreenFlow(listOf(origin))
