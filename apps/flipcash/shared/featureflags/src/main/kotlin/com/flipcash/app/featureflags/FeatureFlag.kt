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

    companion object {
        val entries: List<FeatureFlag>
            get() = FeatureFlagEntries.entries
    }
}



