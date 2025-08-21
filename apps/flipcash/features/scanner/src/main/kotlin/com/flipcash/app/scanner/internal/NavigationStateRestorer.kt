package com.flipcash.app.scanner.internal

import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.NavScreenProvider.HomeScreen.Verification.*
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.core.phantom.PhantomDeeplinkOrigin
import com.flipcash.app.core.verification.email.EmailDeeplinkOrigin
import com.getcode.navigation.core.CodeNavigator
import com.getcode.ui.core.scaled
import kotlinx.coroutines.delay

class NavigationStateRestorer(
    private val navigator: CodeNavigator,
) {
    suspend fun restoreState(deeplink: DeeplinkType.Navigatable, animationScale: Float) {
        when (deeplink) {
            is DeeplinkType.Pool -> {
                delay(200.scaled(animationScale))
                navigator.show(
                    listOf(
                        ScreenRegistry.get(NavScreenProvider.HomeScreen.Pools.Root),
                        ScreenRegistry.get(NavScreenProvider.HomeScreen.Pools.ChoiceSelection(rendezvous = deeplink.rendezvous))
                    )
                )
            }

            is DeeplinkType.PhantomConnection -> {
                val screens = when (val origin = deeplink.origin) {
                    PhantomDeeplinkOrigin.Menu -> buildOnRampScreenFlow(NavScreenProvider.HomeScreen.Menu.Root)
                    is PhantomDeeplinkOrigin.PoolWithId -> buildOnRampScreenFlow(NavScreenProvider.HomeScreen.Pools.ChoiceSelection(poolId = origin.id))
                    is PhantomDeeplinkOrigin.PoolWithRendezvous -> buildOnRampScreenFlow(NavScreenProvider.HomeScreen.Pools.ChoiceSelection(rendezvous = origin.keyPair))
                    PhantomDeeplinkOrigin.Cash -> buildOnRampScreenFlow(NavScreenProvider.HomeScreen.Cash)
                    PhantomDeeplinkOrigin.Balance -> buildOnRampScreenFlow(NavScreenProvider.HomeScreen.Balance)
                }

                navigator.show(screens)
            }

            is DeeplinkType.PhantomSignedTransaction -> {
                val screens = when (val origin = deeplink.origin) {
                    PhantomDeeplinkOrigin.Menu -> buildOnRampScreenFlow(NavScreenProvider.HomeScreen.Menu.Root)
                    is PhantomDeeplinkOrigin.PoolWithId -> buildOnRampScreenFlow(NavScreenProvider.HomeScreen.Pools.ChoiceSelection(poolId = origin.id))
                    is PhantomDeeplinkOrigin.PoolWithRendezvous -> buildOnRampScreenFlow(NavScreenProvider.HomeScreen.Pools.ChoiceSelection(rendezvous = origin.keyPair))
                    PhantomDeeplinkOrigin.Cash -> buildOnRampScreenFlow(NavScreenProvider.HomeScreen.Cash)
                    PhantomDeeplinkOrigin.Balance -> buildOnRampScreenFlow(NavScreenProvider.HomeScreen.Balance)
                } + ScreenRegistry.get(NavScreenProvider.HomeScreen.OnRamp.Amount)

                navigator.show(screens)
            }

            is DeeplinkType.EmailVerification -> {
                val origin = EmailDeeplinkOrigin.deserialize(deeplink.origin.orEmpty())
                val screens = when (origin) {
                    is EmailDeeplinkOrigin.OnRamp -> when (val source = origin.source) {
                        is NavScreenProvider.HomeScreen.Pools.ChoiceSelection -> {
                            buildOnRampScreenFlow(source)  + ScreenRegistry.get(
                                Flow(
                                    origin = source,
                                    target = NavScreenProvider.HomeScreen.OnRamp.Amount,
                                    includePhone = false,
                                    email = deeplink.email,
                                    emailVerificationCode = deeplink.code
                                )
                            )
                        }
                        is NavScreenProvider.HomeScreen.Menu.Root -> {
                            buildOnRampScreenFlow(source) + ScreenRegistry.get(
                                Flow(
                                    origin = source,
                                    target = NavScreenProvider.HomeScreen.OnRamp.Amount,
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
                            ScreenRegistry.get(NavScreenProvider.HomeScreen.Menu.Root),
                            ScreenRegistry.get(NavScreenProvider.HomeScreen.Menu.MyAccount.Root)
                        ) + ScreenRegistry.get(
                            Flow(
                                origin = NavScreenProvider.HomeScreen.Menu.MyAccount.Root,
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

private fun buildOnRampScreenFlow(origin: NavScreenProvider) = listOf(
    ScreenRegistry.get(origin),
    ScreenRegistry.get(NavScreenProvider.HomeScreen.OnRamp.ProviderList(origin)),
)