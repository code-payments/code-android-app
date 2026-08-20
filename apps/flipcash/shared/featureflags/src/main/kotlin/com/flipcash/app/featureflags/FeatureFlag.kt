package com.flipcash.app.featureflags

import android.os.Build
import com.flipcash.app.featureflags.model.BackgroundResetTimeout
import com.flipcash.app.core.navigation.NavBarConfig
import com.flipcash.app.ksp.annotations.FeatureFlagMarker

enum class FeatureTrack {
    /** Visible to all users including production. */
    Production,
    Alpha,
    Beta,
    /** Only visible on internal builds (or with beta override). */
    Internal,
}

data class FlagOption(val key: String, val label: String, val isDisabled: Boolean = false)
sealed interface FeatureFlag<T: Any> {
    val key: String
    val default: T
    val launched: Boolean
    val visible: Boolean
    val persistLogOut: Boolean
    val minTrack: FeatureTrack get() = FeatureTrack.Internal
    val onboarding: Boolean get() = false
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
        override val onboarding: Boolean = true
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
    data object TransactionDetails: FeatureFlag<Boolean> {
        override val key: String = "transaction_details_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
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
    data object BillTextures : FeatureFlag<Boolean> {
        override val key: String = "bill_textures_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
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
    data object ContactPickerMode : FeatureFlag<Boolean> {
        override val key: String = "contact_picker_mode"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN
        override val persistLogOut: Boolean = true
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

    @FeatureFlagMarker
    data object ShowNetworkState: FeatureFlag<Boolean> {
        override val key: String = "show_network_state_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object FrostedTipCard: FeatureFlag<Boolean> {
        override val key: String = "frosted_tip_card_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object NewUi: FeatureFlag<Boolean> {
        override val key: String = "new_ui_enabled"
        override val default: Boolean = true
        // Launched: the new UI is now the only shell. `launched` makes the controller short-circuit
        // to `default` — so a user who had toggled this OFF during the beta is moved onto it (their
        // stored `false` is ignored and cleared on next launch) — and drops it from
        // `availableEntries`, removing the toggle from Labs. The v1 code it gated is torn out
        // separately.
        override val launched: Boolean = true
        override val visible: Boolean = true
        override val persistLogOut: Boolean = true
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
        FeatureFlag.TransactionDetails -> "Transaction Details"
        FeatureFlag.CoinbaseOnRampSandbox -> "Coinbase Onramp Sandbox"
        FeatureFlag.BillTextures -> "Bill Textures"
        FeatureFlag.BackgroundReset -> "Background Reset"
        FeatureFlag.ContactPickerMode -> "Contact Picker Mode"
        FeatureFlag.NavBar -> "Navigation Bar"
        FeatureFlag.ShowNetworkState -> "Network Offline Indicator"
        FeatureFlag.FrostedTipCard -> "Frosted Tip Card"
        FeatureFlag.NewUi -> "New UI"
    }

val FeatureFlag<*>.message: String
    get() = when (this) {
        FeatureFlag.CredentialManager -> "When enabled, you will gain the ability to utilize Google's Password Manager for storing and recovering access keys for easier login experience"
        FeatureFlag.VibrateOnScan -> "When enabled, the device will vibrate once to indicate that the camera has registered the code on the bill"
        FeatureFlag.TransactionDetails -> "When enabled, you'll gain the ability to view details of each transaction from the balance screen"
        FeatureFlag.CoinbaseOnRampSandbox -> "When enabled, Coinbase onramp purchases will use the sandbox environment for testing"
        FeatureFlag.BillTextures -> "When enabled, you'll gain the ability to select textures for bills during currency creation"
        FeatureFlag.BackgroundReset -> "Automatically returns the app to the camera screen after a period of inactivity with the app in the background"
        FeatureFlag.ContactPickerMode -> "When enabled, contacts will be accessed via the system contact picker instead of requesting full READ_CONTACTS permission"
        FeatureFlag.NavBar -> "Customize the order and labels of navigation bar buttons"
        FeatureFlag.ShowNetworkState -> "When enabled, you'll gain the ability to see the network state on the Scanner when offline"
        FeatureFlag.FrostedTipCard -> "When enabled, the tip card in the scanner renders as frosted glass over a blurred snapshot of the camera instead of a solid card"
        FeatureFlag.NewUi -> "When enabled, the app will use the tipping first UI"
    }



