package com.flipcash.app.featureflags

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface FeatureFlagController {
    fun set(flag: FeatureFlag, value: Boolean)
    suspend fun get(flag: FeatureFlag): Boolean
    fun observe(): StateFlow<List<BetaFeature>>
    fun observe(flag: FeatureFlag): StateFlow<Boolean>
    fun reset(flag: FeatureFlag)
    fun reset()
}

object NoOpFeatureFlagController : FeatureFlagController {
    override fun set(flag: FeatureFlag, value: Boolean) = Unit

    override suspend fun get(flag: FeatureFlag): Boolean = false

    override fun observe(): StateFlow<List<BetaFeature>> =
        MutableStateFlow(FeatureFlag.entries.map { BetaFeature(it, it.default) })

    override fun observe(flag: FeatureFlag): StateFlow<Boolean> = MutableStateFlow(false)

    override fun reset(flag: FeatureFlag) = Unit
    override fun reset() = Unit
}

val LocalFeatureFlags = staticCompositionLocalOf<FeatureFlagController> { NoOpFeatureFlagController }