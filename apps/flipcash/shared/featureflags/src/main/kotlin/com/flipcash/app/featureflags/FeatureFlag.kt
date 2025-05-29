package com.flipcash.app.featureflags

import com.flipcash.shared.flags.BuildConfig

sealed interface FeatureFlag {
    val key: String
    val default: Boolean
    val launched: Boolean
    val visible: Boolean

    data object CredentialManager: FeatureFlag {
        override val key: String = "credential_manager_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible: Boolean = BuildConfig.DEBUG
    }

    data object VibrateOnScan: FeatureFlag {
        override val key: String = "scan_debug_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
        override val visible = true
    }

    companion object {
        val entries: List<FeatureFlag> = listOf(CredentialManager, VibrateOnScan)
    }
}



