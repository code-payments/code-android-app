package com.flipcash.app.scanner.internal

import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.core.AppRoute
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
) {
    suspend fun restoreState(deeplink: DeeplinkType.Navigatable, animationScale: Float) {
        when (deeplink) {
            is DeeplinkType.TokenInfo -> {
                delay(200.scaled(animationScale))
                navigator.show(
                    listOf(
                        ScreenRegistry.get(AppRoute.Sheets.Wallet),
                        ScreenRegistry.get(AppRoute.Token.Info(deeplink.mint))
                    )
                )
            }


            is DeeplinkType.ExternalWalletStep -> {
                val screens = when (val origin = deeplink.origin) {
                    OnRampDeeplinkOrigin.Menu -> buildOnRampScreenFlow(AppRoute.Sheets.Menu) + ScreenRegistry.get(AppRoute.OnRamp.AmountEntry)
                    is OnRampDeeplinkOrigin.Give -> buildOnRampScreenFlow(AppRoute.Main.Give(origin.tokenAddress)) + ScreenRegistry.get(AppRoute.OnRamp.AmountEntry)
                    OnRampDeeplinkOrigin.Wallet -> buildOnRampScreenFlow(AppRoute.Sheets.Wallet) + ScreenRegistry.get(AppRoute.OnRamp.AmountEntry)
                    OnRampDeeplinkOrigin.Reserves -> buildOnRampScreenFlow(AppRoute.Token.Info(Mint.usdc)) + ScreenRegistry.get(AppRoute.OnRamp.AmountEntry)
                    is OnRampDeeplinkOrigin.TokenInfo -> listOf(
                        ScreenRegistry.get(AppRoute.Sheets.Wallet),
                        ScreenRegistry.get(AppRoute.Token.Info(origin.mint)),
                        ScreenRegistry.get(AppRoute.Token.SwapTransact(TokenSwapPurpose.FundWithWallet(origin.mint)))
                    )
                }

                navigator.show(screens)
            }

            is DeeplinkType.EmailVerification -> {
                val origin = EmailDeeplinkOrigin.deserialize(deeplink.origin.orEmpty())
                val screens = when (origin) {
                    is EmailDeeplinkOrigin.OnRamp -> when (val source = origin.source) {
                        is AppRoute.Sheets.Menu -> {
                            buildOnRampScreenFlow(source) + ScreenRegistry.get(
                                AppRoute.Verification(
                                    origin = source,
                                    target = AppRoute.OnRamp.AmountEntry,
                                    includePhone = false,
                                    email = deeplink.email,
                                    emailVerificationCode = deeplink.code
                                )
                            )
                        }
                        else -> emptyList()
                    }

                    EmailDeeplinkOrigin.MyAccount ->
                        listOf(
                            ScreenRegistry.get(AppRoute.Sheets.Menu),
                            ScreenRegistry.get(AppRoute.Menu.MyAccount)
                        ) + ScreenRegistry.get(
                            AppRoute.Verification(
                                origin = AppRoute.Menu.MyAccount,
                                target = null,
                                includePhone = false,
                                email = deeplink.email,
                                emailVerificationCode = deeplink.code
                            )
                        )

                    null -> emptyList()
                }

                if (screens.isNotEmpty()) {
                    navigator.show(screens)
                }
            }
        }
    }
}

private fun buildOnRampScreenFlow(origin: List<AppRoute>) =
    origin.dropLast(1).map { ScreenRegistry.get(it) } +
    ScreenRegistry.get(AppRoute.OnRamp.ProviderList(origin.last())
)

private fun buildOnRampScreenFlow(origin: AppRoute) = buildOnRampScreenFlow(listOf(origin))