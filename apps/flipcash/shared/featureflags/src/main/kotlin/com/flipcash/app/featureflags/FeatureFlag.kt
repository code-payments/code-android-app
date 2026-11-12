package com.flipcash.app.featureflags

import com.flipcash.app.featureflags.model.BackgroundResetTimeout
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
    /**
     * Labs lists this flag to staff even without the beta override, which otherwise hides every flag
     * below [FeatureTrack.Production]. For staff-only UI that staff turn on for themselves.
     */
    val staffVisible: Boolean get() = false
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
    data object ShowNetworkState: FeatureFlag<Boolean> {
        override val key: String = "show_network_state_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    /** Settings > Link a Desktop: hands this account's seed to a desktop over the LAN. */
    @FeatureFlagMarker
    data object LinkDesktop: FeatureFlag<Boolean> {
        override val key: String = "link_desktop_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    /** Shows the received bill over the open chat when someone sends this account cash in it. */
    @FeatureFlagMarker
    data object ChatCashBill: FeatureFlag<Boolean> {
        override val key: String = "chat_cash_bill"
        override val default: Boolean = true
        override val launched: Boolean = false
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
        override val minTrack: FeatureTrack = FeatureTrack.Production
    }

    /**
     * The Group Money redesign's two-tab shell (frames node 13729:413 and node 13729:744): Groups and
     * Money in place of today's four tabs. Takes effect only for staff or with the beta override.
     */
    @FeatureFlagMarker
    data object GroupMoneyUi: FeatureFlag<Boolean> {
        override val key: String = "group_money_ui_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
        override val staffVisible: Boolean = true
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
        FeatureFlag.VibrateOnScan -> "Vibrate on Scan"
        FeatureFlag.TransactionDetails -> "Transaction Details"
        FeatureFlag.CoinbaseOnRampSandbox -> "Coinbase Onramp Sandbox"
        FeatureFlag.BillTextures -> "Bill Textures"
        FeatureFlag.BackgroundReset -> "Background Reset"
        FeatureFlag.ShowNetworkState -> "Network Offline Indicator"
        FeatureFlag.LinkDesktop -> "Link a Desktop"
        FeatureFlag.ChatCashBill -> "Chat Cash Bill"
        FeatureFlag.GroupMoneyUi -> "Groups and Money"
    }

val FeatureFlag<*>.message: String
    get() = when (this) {
        FeatureFlag.VibrateOnScan -> "When enabled, the device will vibrate once to indicate that the camera has registered the code on the bill"
        FeatureFlag.TransactionDetails -> "When enabled, you'll gain the ability to view details of each transaction from the balance screen"
        FeatureFlag.CoinbaseOnRampSandbox -> "When enabled, Coinbase onramp purchases will use the sandbox environment for testing"
        FeatureFlag.BillTextures -> "When enabled, you'll gain the ability to select textures for bills during currency creation"
        FeatureFlag.BackgroundReset -> "Automatically returns the app to the camera screen after a period of inactivity with the app in the background"
        FeatureFlag.ShowNetworkState -> "When enabled, you'll gain the ability to see the network state on the Scanner when offline"
        FeatureFlag.LinkDesktop -> "When enabled, Settings shows Link a Desktop, which sends this account to the Flipcash desktop app over your local network"
        FeatureFlag.ChatCashBill -> "Show the bill when someone sends you cash in the chat you have open."
        FeatureFlag.GroupMoneyUi -> "When enabled, the tab bar has two tabs, Groups and Money, in place of Scan, Chat, Wallet and You"
    }

/**
 * Whether Labs lists this flag. [showAllFlags] is the beta override (Labs keeps it on until the
 * screen is left), which lists everything. Without it, Labs lists [FeatureTrack.Production] flags,
 * onboarding flags on the onboarding Labs, and [FeatureFlag.staffVisible] flags to staff.
 */
fun FeatureFlag<*>.isShownInLabs(showAllFlags: Boolean, isStaff: Boolean, onboarding: Boolean): Boolean =
    showAllFlags ||
        minTrack == FeatureTrack.Production ||
        (onboarding && this.onboarding) ||
        (isStaff && staffVisible)
