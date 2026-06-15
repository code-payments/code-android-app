package com.flipcash.app.featureflags

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface FeatureFlagController {
    fun enableBetaFeatures()
    fun disableBetaFeatures()
    fun observeOverride(): StateFlow<Boolean>
    fun set(flag: FeatureFlag<*>, value: Boolean)
    suspend fun get(flag: FeatureFlag<*>): Boolean
    fun observe(): StateFlow<List<BetaFeature>>
    fun observe(flag: FeatureFlag<*>): StateFlow<Boolean>
    fun setOption(flag: FeatureFlag<*>, optionKey: String)
    fun getOption(flag: FeatureFlag<*>): StateFlow<String>
    fun reset(flag: FeatureFlag<*>)
    fun reset()
}

object NoOpFeatureFlagController : FeatureFlagController {
    override fun enableBetaFeatures() = Unit
    override fun disableBetaFeatures() = Unit
    override fun observeOverride(): StateFlow<Boolean> = MutableStateFlow(false)
    override fun set(flag: FeatureFlag<*>, value: Boolean) = Unit

    override suspend fun get(flag: FeatureFlag<*>): Boolean = false

    override fun observe(): StateFlow<List<BetaFeature>> =
        MutableStateFlow(FeatureFlag.entries.map { BetaFeature(it, it.defaultEnabled) })

    override fun observe(flag: FeatureFlag<*>): StateFlow<Boolean> = MutableStateFlow(false)

    override fun setOption(flag: FeatureFlag<*>, optionKey: String) = Unit
    override fun getOption(flag: FeatureFlag<*>): StateFlow<String> = MutableStateFlow(flag.defaultOption)

    override fun reset(flag: FeatureFlag<*>) = Unit
    override fun reset() = Unit
}

val LocalFeatureFlags = staticCompositionLocalOf<FeatureFlagController> { NoOpFeatureFlagController }
