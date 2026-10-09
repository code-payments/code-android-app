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

    @FeatureFlagMarker
    data object WebLinkPreviews : FeatureFlag<Boolean> {
        override val key: String = "web_link_previews"
        override val default: Boolean = true
        // Not launched: a launched flag is forced to its default and its toggle is hidden
        // (InternalFeatureFlagController.get), which would leave no way to turn previews off.
        override val launched: Boolean = false
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
    }

    @FeatureFlagMarker
    data object ChatFormattingComposer : FeatureFlag<Boolean> {
        override val key: String = "chat_formatting_composer"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible: Boolean = true
        override val persistLogOut: Boolean = false
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
        FeatureFlag.WebLinkPreviews -> "Web Link Previews"
        FeatureFlag.ChatFormattingComposer -> "Chat Formatting Tools"
    }

val FeatureFlag<*>.message: String
    get() = when (this) {
        FeatureFlag.VibrateOnScan -> "When enabled, the device will vibrate once to indicate that the camera has registered the code on the bill"
        FeatureFlag.TransactionDetails -> "When enabled, you'll gain the ability to view details of each transaction from the balance screen"
        FeatureFlag.CoinbaseOnRampSandbox -> "When enabled, Coinbase onramp purchases will use the sandbox environment for testing"
        FeatureFlag.BillTextures -> "When enabled, you'll gain the ability to select textures for bills during currency creation"
        FeatureFlag.BackgroundReset -> "Automatically returns the app to the camera screen after a period of inactivity with the app in the background"
        FeatureFlag.ShowNetworkState -> "When enabled, you'll gain the ability to see the network state on the Scanner when offline"
        FeatureFlag.WebLinkPreviews -> "When enabled, a link to a web page in a chat shows the page's title, description and image, fetched from this device"
        FeatureFlag.ChatFormattingComposer -> "When enabled, the chat composer offers bold, italic, strikethrough, code, link, list and quote formatting. Formatted messages always display; this only adds the tools to write them"
    }
