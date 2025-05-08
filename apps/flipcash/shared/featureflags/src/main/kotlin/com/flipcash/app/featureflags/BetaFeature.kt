package com.flipcash.app.featureflags

data class BetaFeature(
    val flag: FeatureFlag,
    val enabled: Boolean,
)