package com.flipcash.app.featureflags

import com.flipcash.app.ksp.annotations.FeatureFlagMarker

sealed interface FeatureFlag {
    val key: String
    val default: Boolean
    val launched: Boolean
    val visible: Boolean
    val persistLogOut: Boolean

    @FeatureFlagMarker
    data object CredentialManager: FeatureFlag {
        override val key: String = "credential_manager_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible: Boolean = true
        override val persistLogOut: Boolean = true
    }

    @FeatureFlagMarker
    data object VibrateOnScan: FeatureFlag {
        override val key: String = "scan_debug_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object WelcomeBonusBill: FeatureFlag {
        override val key: String = "welcome_bonus_bill_enabled"
        override val default: Boolean = true
        override val launched: Boolean = true
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object TransactionDetails: FeatureFlag {
        override val key: String = "transaction_details_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object Pools: FeatureFlag {
        override val key: String = "pools_enabled"
        override val default: Boolean = true
        override val launched: Boolean = true
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object OnRamp: FeatureFlag {
        override val key: String = "onramp_enabled"
        override val default: Boolean = true
        override val launched: Boolean = true
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object BillCustomizer: FeatureFlag {
        override val key: String = "bill_customizer_enabled"
        override val default: Boolean = true
        override val launched: Boolean = true
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object CurrencyCreator: FeatureFlag {
        override val key: String = "currency_creator_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object CashReservesEnabled: FeatureFlag {
        override val key: String = "cash_reserves_enabled"
        override val default: Boolean = true
        override val launched: Boolean = true
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object MarketCapChart: FeatureFlag {
        override val key: String = "market_cap_chart_enabled"
        override val default: Boolean = true
        override val launched: Boolean = true
        override val visible: Boolean = true
        override val persistLogOut: Boolean = true
    }

    @FeatureFlagMarker
    data object CoinbaseOnRamp: FeatureFlag {
        override val key: String = "coinbase_onramp_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object CoinbaseOnRampSandbox: FeatureFlag {
        override val key: String = "coinbase_onramp_sandbox_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object TokenDiscovery: FeatureFlag {
        override val key: String = "token_discovery_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object TokenCreate: FeatureFlag {
        override val key: String = "token_create_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    companion object {
        val entries: List<FeatureFlag>
            get() = FeatureFlagEntries.entries

        val availableEntries: List<FeatureFlag>
            get() = entries
                .filterNot { it.launched }
                .filter { it.visible }
    }
}

val FeatureFlag.title: String
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
        FeatureFlag.TokenCreate -> "Token Creation"
        FeatureFlag.CurrencyCreator -> "Currency Creator"
    }

val FeatureFlag.message: String
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
        FeatureFlag.TokenCreate -> "When enabled, you'll gain access to create new currencies"
        FeatureFlag.CurrencyCreator -> "When enabled, you'll gain access to create new currencies"
    }



