package com.flipcash.app.featureflags

sealed interface FeatureFlag {
    val key: String
    val default: Boolean
    val launched: Boolean

    companion object {
        val entries: List<FeatureFlag> = emptyList()
    }
}