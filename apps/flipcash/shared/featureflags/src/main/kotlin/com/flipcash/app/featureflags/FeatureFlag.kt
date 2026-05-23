package com.flipcash.app.featureflags

import com.flipcash.app.featureflags.model.BackgroundResetTimeout
import com.flipcash.app.core.navigation.NavBarConfig
import com.flipcash.app.ksp.annotations.FeatureFlagMarker
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class FlagOption(val key: String, val label: String, val isDisabled: Boolean = false)
sealed interface FeatureFlag<T: Any> {
    val key: String
    val default: T
    val launched: Boolean
    val visible: Boolean
    val persistLogOut: Boolean
    val options: List<FlagOption> get() = emptyList()
    val defaultOption: String
        get() = if (default is Enum<*>) (default as Enum<*>).name else ""
    val defaultEnabled: Boolean
        get() = if (isOptionFlag) {
            options.find { it.key == defaultOption }?.isDisabled != true
        } else {
            default as Boolean
        }
    val isOptionFlag: Boolean get() = options.isNotEmpty()

    @FeatureFlagMarker
    data object CredentialManager: FeatureFlag<Boolean> {
        override val key: String = "credential_manager_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible: Boolean = true
        override val persistLogOut: Boolean = true
    }

    @FeatureFlagMarker
    data object VibrateOnScan: FeatureFlag<Boolean> {
        override val key: String = "scan_debug_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object WelcomeBonusBill: FeatureFlag<Boolean> {
        override val key: String = "welcome_bonus_bill_enabled"
        override val default: Boolean = true
        override val launched: Boolean = true
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object TransactionDetails: FeatureFlag<Boolean> {
        override val key: String = "transaction_details_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object Pools: FeatureFlag<Boolean> {
        override val key: String = "pools_enabled"
        override val default: Boolean = true
        override val launched: Boolean = true
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object OnRamp: FeatureFlag<Boolean> {
        override val key: String = "onramp_enabled"
        override val default: Boolean = true
        override val launched: Boolean = true
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object BillCustomizer: FeatureFlag<Boolean> {
        override val key: String = "bill_customizer_enabled"
        override val default: Boolean = true
        override val launched: Boolean = true
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object CurrencyCreator: FeatureFlag<Boolean> {
        override val key: String = "currency_creator_enabled"
        override val default: Boolean = true
        override val launched: Boolean = true
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object CashReservesEnabled: FeatureFlag<Boolean> {
        override val key: String = "cash_reserves_enabled"
        override val default: Boolean = true
        override val launched: Boolean = true
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object MarketCapChart: FeatureFlag<Boolean> {
        override val key: String = "market_cap_chart_enabled"
        override val default: Boolean = true
        override val launched: Boolean = true
        override val visible: Boolean = true
        override val persistLogOut: Boolean = true
    }

    @FeatureFlagMarker
    data object CoinbaseOnRamp: FeatureFlag<Boolean> {
        override val key: String = "coinbase_onramp_enabled"
        override val default: Boolean = true
        override val launched: Boolean = true
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object CoinbaseOnRampSandbox: FeatureFlag<Boolean> {
        override val key: String = "coinbase_onramp_sandbox_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object TokenDiscovery: FeatureFlag<Boolean> {
        override val key: String = "token_discovery_enabled"
        override val default: Boolean = true
        override val launched: Boolean = true
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object BillTextures : FeatureFlag<Boolean> {
        override val key: String = "bill_textures_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object DepositUsdc: FeatureFlag<Boolean> {
        override val key: String = "deposit_usdc_enabled"
        override val default: Boolean = true
        override val launched: Boolean = true
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object BackgroundReset : FeatureFlag<BackgroundResetTimeout> {
        override val key: String = "idle_reset"
        override val default = BackgroundResetTimeout.FiveMinutes
        override val launched: Boolean = false
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
        override val options: List<FlagOption> = BackgroundResetTimeout.entries
            .map { FlagOption(it.name, it.label, isDisabled = it.duration == null) }
    }

    @FeatureFlagMarker
    data object NavBar : FeatureFlag<NavBarConfig> {
        override val key: String = "nav_bar_config"
        override val default: NavBarConfig = NavBarConfig.Default
        override val launched: Boolean = false
        override val visible: Boolean = false
        override val persistLogOut: Boolean = false
        override val defaultOption: String get() = default.serialize()
    }

    companion object {
        val entries: List<FeatureFlag<*>>
            get() = FeatureFlagEntries.entries

        val availableEntries: List<FeatureFlag<*>>
            get() = entries
                .filterNot { it.launched }
                .filter { it.visible }
    }
}

val FeatureFlag<*>.title: String
    get() = when (this) {
        is FeatureFlag.CredentialManager -> "Credential Manager"
        FeatureFlag.VibrateOnScan -> "Vibrate on Scan"
        FeatureFlag.WelcomeBonusBill -> "Receive Welcome Bonus as a Bill"
        FeatureFlag.TransactionDetails -> "Transaction Details"
        FeatureFlag.Pools -> "Betting Pools"
        FeatureFlag.OnRamp -> "Onramp"
        FeatureFlag.BillCustomizer -> "Bill Customizer"
        FeatureFlag.CashReservesEnabled -> "Cash Reserves"
        FeatureFlag.MarketCapChart -> "Market Cap Chart"
        FeatureFlag.CoinbaseOnRamp -> "Coinbase Onramp"
        FeatureFlag.CoinbaseOnRampSandbox -> "Coinbase Onramp Sandbox"
        FeatureFlag.TokenDiscovery -> "Token Discovery"
        FeatureFlag.CurrencyCreator -> "Currency Creator"
        FeatureFlag.BillTextures -> "Bill Textures"
        FeatureFlag.DepositUsdc -> "Deposit USDC"
        FeatureFlag.BackgroundReset -> "Background Reset"
        FeatureFlag.NavBar -> "Navigation Bar"
    }

val FeatureFlag<*>.message: String
    get() = when (this) {
        FeatureFlag.CredentialManager -> "When enabled, you will gain the ability to utilize Google's Password Manager for storing and recovering access keys for easier login experience"
        FeatureFlag.VibrateOnScan -> "When enabled, the device will vibrate once to indicate that the camera has registered the code on the bill"
        FeatureFlag.WelcomeBonusBill -> "When enabled, the welcome bonus after creating an account will be presented as a bill that will be placed in your wallet instead of simply toasting"
        FeatureFlag.TransactionDetails -> "When enabled, you'll gain the ability to view details of each transaction from the balance screen"
        FeatureFlag.Pools -> "When enabled, you'll be able to participate in and create betting pools with other users for a chance to win a share of the prize"
        FeatureFlag.OnRamp -> "When enabled, you'll gain the ability to fund your wallet from external sources via providers using a debit card or via another wallet (like Phantom)"
        FeatureFlag.BillCustomizer -> "When enabled, you'll gain access to the bill customization playground"
        FeatureFlag.CashReservesEnabled -> "When enabled, USDC will be brandished as Cash Reserves throughout the app"
        FeatureFlag.MarketCapChart -> "When enabled, you'll gain access to the market cap chart in token info"
        FeatureFlag.CoinbaseOnRamp -> "When enabled, you'll gain access to the Coinbase onramp for token buys"
        FeatureFlag.CoinbaseOnRampSandbox -> "When enabled, Coinbase onramp purchases will use the sandbox environment for testing"
        FeatureFlag.TokenDiscovery -> "When enabled, you'll gain access to leaderboards for tokens and discovery"
        FeatureFlag.CurrencyCreator -> "When enabled, you'll gain access to create new currencies"
        FeatureFlag.BillTextures -> "When enabled, you'll gain the ability to select textures for bills during currency creation"
        FeatureFlag.DepositUsdc -> "When enabled, you'll gain the ability to deposit USDC directly from any external wallet app instead of purchasing a currency first and sell"
        FeatureFlag.BackgroundReset -> "Automatically returns the app to the camera screen after a period of inactivity with the app in the background"
        FeatureFlag.NavBar -> "Customize the order and labels of navigation bar buttons"
    }



